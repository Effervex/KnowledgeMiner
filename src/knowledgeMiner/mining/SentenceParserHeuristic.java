/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package knowledgeMiner.mining;

import io.IOManager;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import opennlp.tools.parser.Parse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.Tree;
import util.text.OpenNLP;
import util.wikipedia.WikiParser;
import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;

public class SentenceParserHeuristic extends MiningHeuristic {
	private static final String[] COPULAS = { "is", "are", "was", "were", "be",
			"am", "being", "been" };
	private static final Collection<ExtractionPattern> EXTRACTION_PATTERNS;
	private final static Logger logger_ = LoggerFactory
			.getLogger(SentenceParserHeuristic.class);
	private static final Pattern[] SENTENCE_SIMPLIFIER = {
			Pattern.compile("^In [^,.]+, "),
			Pattern.compile("(?<=[^,.]+), [^,.]+,(?= (is|was|are|were))") };

	public SentenceParserHeuristic(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner);
		OpenNLP.getParser();
	}

	private String disambiguateTree(Parse parse, String[] predicateStrs,
			MappableConcept focusConcept, SortedMap<String, String> anchors,
			WMISocket wmi, OntologySocket cyc, MiningHeuristic heuristic,
			Collection<PartialAssertion> results) throws Exception {
		if (predicateStrs == null) {
			predicateStrs = new String[1];
			predicateStrs[0] = "";
		}

		Parse[] children = parse.getChildren();
		String type = parse.getType();
		String text = parse.getCoveredText();

		// No children? Return value
		if (children.length == 0)
			return text;

		// Recurse to 'left'
		int childIndex = 0;
		String left = disambiguateTree(children[childIndex++],
				Arrays.copyOf(predicateStrs, predicateStrs.length),
				focusConcept, anchors, wmi, cyc, heuristic, results);

		// If VP or PP, add to predicate
		boolean canCreate = true;
		if (left != null) {
			if (type.equals("VP"))
				predicateStrs[0] = left.trim();
			else if (type.equals("PP")) {
				// If PP, split recursion into two predicates
				predicateStrs[0] = (predicateStrs[0] + " " + left).trim();
				if (!predicateStrs[0].equals(left)) {
					predicateStrs = Arrays.copyOf(predicateStrs,
							predicateStrs.length + 1);
					predicateStrs[predicateStrs.length - 1] = left;
				}
			}
		} else
			canCreate = false;

		for (; childIndex < children.length; childIndex++) {
			Parse childParse = children[childIndex];
			String result = disambiguateTree(childParse,
					Arrays.copyOf(predicateStrs, predicateStrs.length),
					focusConcept, anchors, wmi, cyc, heuristic, results);
			if (result == null) {
				canCreate = false;
			}
		}

		if (type.equals("VP") || type.equals("PP"))
			return null;

		// Can create and we have a target and predicate(s)
		if (canCreate && type.equals("NP") && !predicateStrs[0].isEmpty()) {
			for (String predStr : predicateStrs) {
				OntologyConcept predicate = null;
				if (isCopula(predStr)) {
					predicate = CycConstants.ISA_GENLS.getConcept();
				} else {
					// TODO Figure out a safe way to parse predicates. Probably
					// need to look at the parse code again.
					// predStr = reAnchorString(predStr, anchors);
					// predicateSet = mapper_.mapRelationToPredicate(predStr,
					// wmi,
					// cyc);
				}

				if (predicate == null)
					continue;

				// Return the possible noun strings
				Collection<Tree<String>> nounStrs = composeAdjNounsTree(parse,
						anchors);
				logger_.trace("createAssertions: " + predicate.toString() + " "
						+ nounStrs.toString().replaceAll("\\\\\n", " "));

				// Recurse through the tree and build the partial assertions
				HeuristicProvenance provenance = new HeuristicProvenance(
						heuristic, predStr + "+" + text);
				Collection<PartialAssertion> currAssertions = recurseStringTree(
						predicate, focusConcept, nounStrs, provenance);

				// Add the assertions
				for (PartialAssertion pa : currAssertions)
					if (!results.contains(pa)) {
						results.add(pa);
						canCreate = false;
					}
			}
		}

		if (!canCreate)
			return null;

		return text;
	}

	/**
	 * Extracts the anchors from the text and returns them in tree form.
	 *
	 * @param text
	 *            The text to reinsert anchors into, then extract anchors from
	 * @param anchors
	 *            The reanchoring map.
	 * @param anchorMap
	 *            The map of anchors to add to.
	 * @return A collection of extracted anchors.
	 */
	private Collection<Tree<String>> extractAnchors(String text,
			SortedMap<String, String> anchors,
			Map<String, Tree<String>> anchorMap) {
		Collection<Tree<String>> anchorCol = new ArrayList<>();
		// Reanchor the string and extract all anchors
		String anchorText = reAnchorString(text, anchors);
		Matcher m = WikiParser.ANCHOR_PARSER_ROUGH.matcher(anchorText);
		while (m.find()) {
			Tree<String> anchorTree = new Tree<String>(m.group());
			// Split up anchor text
			String anchor = (m.group(2) == null) ? m.group(1) : m.group(2);
			anchorMap.put(anchor, anchorTree);
			anchorCol.add(anchorTree);
		}
		return anchorCol;
	}

	/**
	 * If the current verbPhrase is a copula.
	 * 
	 * @param verbPhrase
	 *            The phrase to check.
	 * @return True if the verb phrase is a copula.
	 */
	private boolean isCopula(String verbPhrase) {
		for (String copula : COPULAS) {
			if (verbPhrase.equalsIgnoreCase(copula))
				return true;
		}
		return false;
	}

	/**
	 * Pairs the nouns and adjectives together to produce a number of result
	 * text fragments to be resolved to concepts.
	 *
	 * @param parse
	 *            The parse to process and pair.
	 * @param anchors
	 *            The anchor map.
	 * @param existingAnchorTrees
	 *            The anchor trees already added to the results (for reuse and
	 *            subtree-ing)
	 * @return A collection of possible mappable entities composed of at least
	 *         one noun and possible adjectives (with sub-adjectives).
	 */
	private Collection<Tree<String>> pairNounAdjs(Parse parse,
			SortedMap<String, String> anchors,
			Map<String, Tree<String>> existingAnchorTrees) {
		Collection<Tree<String>> results = new ArrayList<>();
		boolean createNewNounSet = false;
		ArrayList<String> nounPhrases = new ArrayList<>();
		Parse[] children = parse.getChildren();
		for (int i = children.length - 1; i >= 0; i--) {
			String childType = children[i].getType();
			String childText = children[i].getCoveredText();
			if (childType.startsWith("NN") || childType.equals("NP")) {
				// Note the noun, adding it to the front of the existing NP.
				if (createNewNounSet)
					nounPhrases.clear();
				String existingNounPhrase = "";
				if (!nounPhrases.isEmpty())
					existingNounPhrase = nounPhrases
							.get(nounPhrases.size() - 1);
				String np = (childText + " " + existingNounPhrase).trim();
				nounPhrases.add(np);

				// Add to the tree (if not a pure anchor)
				if (!anchors.containsKey(np))
					results.add(new Tree<String>(reAnchorString(np, anchors)));
			} else if (childType.startsWith("JJ") || childType.equals("ADJP")) {
				// Only process if we have an NP
				if (!nounPhrases.isEmpty()) {
					// For every nounPhrase
					StringBuilder adjective = new StringBuilder();
					for (int j = i; children[j].getType().startsWith("JJ")
							|| children[j].getType().equals("ADJP"); j++) {
						// Build adjective combinations
						if (adjective.length() != 0)
							adjective.append(" ");
						adjective.append(children[j].getCoveredText());
						for (String np : nounPhrases) {
							// Create the tree (with sub adjective tree)
							String adjNP = adjective + " " + np;
							Tree<String> adjP = null;
							// Check for an existing anchor tree
							if (existingAnchorTrees.containsKey(adjNP))
								adjP = existingAnchorTrees.get(adjNP);
							else
								adjP = new Tree<String>(reAnchorString(adjNP,
										anchors));
							if (!anchors.containsKey(adjective.toString()))
								adjP.addSubValue(reAnchorString(
										adjective.toString(), anchors));

							// Add to the tree
							results.add(adjP);
						}
					}
				}
				createNewNounSet = true;
			} else {
				createNewNounSet = true;
			}
		}
		return results;
	}

	/**
	 * Cleans the indices off the end of strings.
	 * 
	 * @param str
	 *            A string with indices on the end of each word.
	 * @param anchors
	 *            The anchor map to apply.
	 * @return A string without index suffixes.
	 */
	private String reAnchorString(String str, SortedMap<String, String> anchors) {
		if (anchors == null)
			return str;
		String result = str.replaceAll("(\\S+)-\\d+(?= |$)", "$1");
		for (String anchor : anchors.keySet()) {
			if (anchors.get(anchor) != null)
				result = WikiParser.replaceAll(result, anchor,
						anchors.get(anchor));
		}
		return result;
	}

	/**
	 * Recurse down text fragments and attempt to find mapped concepts for each
	 * fragment, maintaining the same hierarchy.
	 * 
	 * @param parentCols
	 *            The noun strings to recurse through.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The results set to add to.
	 */
	private Collection<PartialAssertion> recurseStringTree(
			OntologyConcept predicate, MappableConcept focusConcept,
			Collection<Tree<String>> nounStrs, HeuristicProvenance provenance) {
		Collection<PartialAssertion> assertions = new ArrayList<>();
		for (Tree<String> t : nounStrs) {
			PartialAssertion pa = new PartialAssertion(predicate, provenance,
					focusConcept, new TextMappedConcept(t.getValue(), false,
							false));
			if (!t.getSubTrees().isEmpty())
				for (PartialAssertion subPA : recurseStringTree(predicate,
						focusConcept, t.getSubTrees(), provenance)) {
					pa.addSubAssertion(subPA);
				}
			assertions.add(pa);
		}
		return assertions;
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.TAXONOMIC.ordinal()] = true;
		infoTypes[InformationType.NON_TAXONOMIC.ordinal()] = true;
	}

	/**
	 * Compose the hierarchical set of noun-adjective combinations from the NP.
	 * First gets all anchors, then processes each JJ and NN combination, adding
	 * them directly or as subvalues of the anchors.
	 * 
	 * @param parse
	 *            The parse to compose the strings from.
	 * @param anchors
	 *            The anchors to insert during composition.
	 * @return A Hierarchical Weighted Set of strings representing the order of
	 *         strings that should be attempted to assert.
	 */
	public Collection<Tree<String>> composeAdjNounsTree(Parse parse,
			SortedMap<String, String> anchors) {
		String text = parse.getCoveredText();
		Collection<Tree<String>> results = new ArrayList<>();

		// Add all visible anchors
		// Keep track of which text is in what anchors
		Map<String, Tree<String>> anchorMap = new HashMap<>();
		results.addAll(extractAnchors(text, anchors, anchorMap));

		// Work backwards through the children, adding nouns, then adjectives to
		// the nouns
		results.addAll(pairNounAdjs(parse, anchors, anchorMap));
		return results;
	}

	/**
	 * Extracts a set of assertions from a sentence using parsing techniques to
	 * identify plain-text assertions.
	 * 
	 * @param sentence
	 *            The sentence to extract assertions from.
	 * @param focusConcept
	 *            The concept the sentence is being mined for.
	 * @param wmi
	 *            The WMI access.
	 * @param cyc
	 *            The Cyc access.
	 * @param heuristic
	 *            The heuristic to which assertions are assigned to.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public Collection<PartialAssertion> extractAssertions(String sentence,
			MappableConcept focusConcept, WMISocket wmi, OntologySocket cyc,
			MiningHeuristic heuristic) throws Exception {
		logger_.trace("mineSentence: " + sentence);
		SortedMap<String, String> anchors = locateAnchors(sentence);
		sentence = sentence.replaceAll("'{3,}.+?'{3,}", "THING");
		sentence = sentence.replaceAll("\\?{2,}", "");
		String cleanSentence = WikiParser.cleanAllMarkup(sentence);

		Parse parse = parseLine(cleanSentence);
		// parse.show();
		Collection<PartialAssertion> results = new ArrayList<>();
		logger_.trace("disambiguateTree: " + sentence);
		disambiguateTree(parse, null, focusConcept, anchors, wmi, cyc,
				heuristic, results);
		return results;
	}

	static {
		EXTRACTION_PATTERNS = new ArrayList<>();
		EXTRACTION_PATTERNS.add(new ExtractionPattern("VB", "NP"));
	}

	/**
	 * Locates the anchors in a string and forms a replacement map for them.
	 * 
	 * @param sentence
	 *            The sentence to search for anchors.
	 * @return A SortedMap of anchors, ordered in largest text size to smallest.
	 */
	public static SortedMap<String, String> locateAnchors(String sentence) {
		SortedMap<String, String> anchorMap = new TreeMap<>(
				new Comparator<String>() {
					@Override
					public int compare(String o1, String o2) {
						int result = Double.compare(o1.length(), o2.length());
						if (result != 0)
							return -result;
						return o1.compareTo(o2);
					}
				});
		Matcher m = WikiParser.ANCHOR_PARSER.matcher(sentence);
		while (m.find()) {
			String replString = (m.group(2) != null) ? m.group(2) : m.group(1);
			anchorMap.put(replString, m.group());
		}
		return anchorMap;
	}

	public static void main(String[] args) throws Exception {
		ResourceAccess.newInstance();
		CycMapper mapper = new CycMapper();
		CycMiner miner = new CycMiner(null, mapper);
		SentenceParserHeuristic sph = new SentenceParserHeuristic(mapper, miner);
		WMISocket wmi = ResourceAccess.requestWMISocket();
		OntologySocket cyc = ResourceAccess.requestOntologySocket();

		String input = "";
		MappableConcept mappable = new TextMappedConcept("PLACEHOLDER", false,
				false);
		while (!input.equalsIgnoreCase("exit")) {
			System.out.println("Enter sentence to parse:");
			BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
			input = in.readLine();
			Collection<PartialAssertion> assertions = sph.extractAssertions(
					input, mappable, wmi, cyc, null);
			System.out.println(assertions);
		}
	}

	public static synchronized Parse parseLine(String cleanSentence) {
		Parse parse = null;
		while (parse == null) {
			parse = OpenNLP.parseLine(cleanSentence);
			// Could not parse
			if (parse.getType().equals("INC")) {
				try {
					IOManager.getInstance().writeFirstSentence(-1,
							cleanSentence);
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Simplify the sentence
				for (Pattern p : SENTENCE_SIMPLIFIER) {
					String simplifiedSentence = p.matcher(cleanSentence)
							.replaceFirst("");
					// Replace the clean sentence
					if (!simplifiedSentence.equals(cleanSentence)) {
						cleanSentence = StringUtils
								.capitalize(simplifiedSentence);
						parse = null;
						break;
					}
				}
			}
		}
		return parse;
	}
}
