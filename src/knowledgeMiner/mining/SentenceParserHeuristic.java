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
import opennlp.tools.parser.Parse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.Tree;
import util.text.OpenNLP;
import util.text.StanfordNLP;
import util.wikipedia.WikiParser;
import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.MappableConcept;

public class SentenceParserHeuristic extends MiningHeuristic {
	private static final String[] COPULAS = { "is", "are", "was", "were", "be",
			"am", "being", "been" };
	private final static Logger logger_ = LoggerFactory
			.getLogger(SentenceParserHeuristic.class);
	private static final Pattern[] SENTENCE_SIMPLIFIER = {
			Pattern.compile("^In [^,.]+, "),
			Pattern.compile("(?<=[^,.]+), [^,.]+,(?= (is|was|are|were))") };
	public static final String SENTENCE_PREFIX = "TOPICS is a ";
	public static final int STANFORD_NLP = 1;
	public static final int OPEN_NLP = 0;
	/** The type of parser currently being used. */
	public static int parser_ = STANFORD_NLP;

	public SentenceParserHeuristic(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner);
		if (parser_ == STANFORD_NLP)
			StanfordNLP.getInstance();
		else
			OpenNLP.getParser();
	}

	/**
	 * Disambiguates a parse tree recursively, by working it's way down through
	 * NP, VP and PP and combining them into a semi-coherent assertion.
	 * 
	 * This is the Stanford Parser version.
	 *
	 * @param parse
	 *            The parse to traverse.
	 * @param predicateStrs
	 *            The current predicate strings.
	 * @param focusConcept
	 *            The focus concept to record in the assertions.
	 * @param anchors
	 *            The anchor text to add back in.
	 * @param heuristic
	 *            The heuristic that triggered this call.
	 * @param results
	 *            The results to add to.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The string represented by this tree.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private String disambiguateTree(Object parse, String[] predicateStrs,
			MappableConcept focusConcept, SortedMap<String, String> anchors,
			MiningHeuristic heuristic, Collection<PartialAssertion> results,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		if (predicateStrs == null) {
			predicateStrs = new String[1];
			predicateStrs[0] = "";
		}

		Object[] children = getChildren(parse);
		String type = getType(parse);

		// No children? Return value
		if (children.length == 0)
			return getCoveredText(parse);

		// Recurse to 'left'
		int childIndex = 0;
		String left = disambiguateTree(children[childIndex++],
				Arrays.copyOf(predicateStrs, predicateStrs.length),
				focusConcept, anchors, heuristic, results, wmi, ontology);

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
			Object childParse = children[childIndex];
			String result = disambiguateTree(childParse,
					Arrays.copyOf(predicateStrs, predicateStrs.length),
					focusConcept, anchors, heuristic, results, wmi, ontology);
			if (result == null)
				canCreate = false;
		}

		if (type.equals("VP") || type.equals("PP"))
			return null;

		// Can create and we have a target and predicate(s)
		if (canCreate && type.equals("NP") && !predicateStrs[0].isEmpty()) {
			for (String predStr : predicateStrs) {
				AssertionArgument predicate = null;
				if (isCopula(predStr)) {
					predicate = CycConstants.ISA_GENLS.getConcept();
				} else {
					// TODO Figure out a safe way to parse predicates. Probably
					// need to look at the parse code again.
					// predStr = reAnchorString(predStr, anchors);
					// predicate = new TextMappedConcept(predStr, true, true);
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
						heuristic, predStr + "+" + getCoveredText(parse));
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

		return getCoveredText(parse);
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
	private Collection<Tree<String>> pairNounAdjs(Object parse,
			SortedMap<String, String> anchors,
			Map<String, Tree<String>> existingAnchorTrees) {
		Collection<Tree<String>> results = new ArrayList<>();
		boolean createNewNounSet = false;
		ArrayList<String> nounPhrases = new ArrayList<>();
		Object[] children = getChildren(parse);
		for (int i = children.length - 1; i >= 0; i--) {
			String childType = getType(children[i]);
			if (childType.startsWith("NN") || childType.equals("NP")) {
				// Note the noun, adding it to the front of the existing NP.
				if (createNewNounSet)
					nounPhrases.clear();
				String existingNounPhrase = "";
				if (!nounPhrases.isEmpty())
					existingNounPhrase = nounPhrases
							.get(nounPhrases.size() - 1);
				String np = (getCoveredText(children[i]) + " " + existingNounPhrase)
						.trim();
				nounPhrases.add(np);

				// Add to the tree (if not a pure anchor)
				if (!anchors.containsKey(np))
					results.add(new Tree<String>(reAnchorString(np, anchors)));
				createNewNounSet = false;
			} else if (childType.startsWith("JJ") || childType.equals("ADJP")) {
				// Only process if we have an NP
				if (!nounPhrases.isEmpty()) {
					// For every nounPhrase
					StringBuilder adjective = new StringBuilder();
					for (int j = i; getType(children[j]).startsWith("JJ")
							|| getType(children[j]).equals("ADJP"); j++) {
						// Build adjective combinations
						if (adjective.length() != 0)
							adjective.append(" ");
						adjective.append(getCoveredText(children[j]));
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
		for (Map.Entry<String, String> entry : anchors.entrySet()) {
			if (entry.getValue() != null)
				result = WikiParser.replaceAll(result, entry.getKey(),
						entry.getValue());
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
			AssertionArgument predicate, MappableConcept focusConcept,
			Collection<Tree<String>> nounStrs, HeuristicProvenance provenance) {
		Collection<PartialAssertion> assertions = new ArrayList<>();
		// Recurse through every discovered noun combination
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
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
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
	public Collection<Tree<String>> composeAdjNounsTree(Object parse,
			SortedMap<String, String> anchors) {
		String text = getCoveredText(parse);
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
	 * @param wikifyText
	 *            If the text should be wikified.
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
			MappableConcept focusConcept, boolean wikifyText, WMISocket wmi,
			OntologySocket cyc, MiningHeuristic heuristic) throws Exception {
		logger_.trace("mineSentence: " + sentence);

//		if (wikifyText)
			sentence = wmi.annotate(sentence, 0, false);

		Map<String, Double> anchorWeights = new HashMap<>();
		SortedMap<String, String> anchors = locateAnchors(sentence,
				anchorWeights);
		sentence = sentence.replaceAll("'{3,}.+?'{3,}", "THING");
		sentence = sentence.replaceAll("\\?{2,}", "");
		String cleanSentence = WikiParser.cleanAllMarkup(sentence);

		Object parse = parseLine(cleanSentence);
		// edu.stanford.nlp.trees.Tree parse = parseLineSt(cleanSentence);
		// parse.show();
		Collection<PartialAssertion> results = new ArrayList<>();
		logger_.trace("disambiguateTree: " + sentence);
		disambiguateTree(parse, null, focusConcept, anchors, heuristic,
				results, wmi, cyc);
		return results;
	}

	/**
	 * If the current verbPhrase is a copula.
	 * 
	 * @param verbPhrase
	 *            The phrase to check.
	 * @return True if the verb phrase is a copula.
	 */
	public static boolean isCopula(String verbPhrase) {
		for (String copula : COPULAS) {
			if (verbPhrase.equalsIgnoreCase(copula))
				return true;
		}
		return false;
	}

	/**
	 * Locates the anchors in a string and forms a replacement map for them.
	 * 
	 * @param sentence
	 *            The sentence to search for anchors.
	 * @param anchorWeights
	 *            An optional map to record any weight information for the
	 *            anchors. If no weights in the text, all weights are assumed to
	 *            be 1.0.
	 * @return A SortedMap of anchors, ordered in largest text size to smallest.
	 */
	public static SortedMap<String, String> locateAnchors(String sentence,
			Map<String, Double> anchorWeights) {
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
			if (replString.length() > 1)
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
					input, mappable, true, wmi, cyc, null);
			System.out.println(assertions);
		}
	}

	/**
	 * Parses a sentence with the parser, cleaning the sentence further if
	 * needed.
	 *
	 * @param cleanSentence
	 *            The sentence to parse.
	 * @return The parsed sentence, either as OpenNLP parse, or StanfordNLP
	 *         Tree.
	 */
	public static synchronized Object parseLine(String cleanSentence) {
		Object parse = null;
		while (parse == null) {
			if (parser_ == STANFORD_NLP)
				parse = StanfordNLP.getInstance().apply(cleanSentence);
			else if (parser_ == OPEN_NLP)
				parse = OpenNLP.parseLine(cleanSentence);
			// Could not parse
			if (getType(parse).equals("INC")) {
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

	/**
	 * A convenience method for extracting type information from a parse
	 * (regardless of parser).
	 *
	 * @param parse
	 *            The parse to extract type information from.
	 * @return The type information as a string.
	 */
	public static String getType(Object parse) {
		if (parser_ == STANFORD_NLP)
			return ((edu.stanford.nlp.trees.Tree) parse).value();
		else
			return ((Parse) parse).getType();
	}

	/**
	 * A convenience method for extracting covered text information from a parse
	 * (regardless of parser).
	 *
	 * @param parse
	 *            The parse to extract covered text information from.
	 * @return The covered text of the parse.
	 */
	public static String getCoveredText(Object parse) {
		if (parser_ == STANFORD_NLP)
			return StringUtils.join(
					((edu.stanford.nlp.trees.Tree) parse).yieldWords(), ' ');
		else
			return ((Parse) parse).getCoveredText();
	}

	/**
	 * A convenience method for extracting children information from a parse
	 * (regardless of parser).
	 *
	 * @param parse
	 *            The parse to extract children information from.
	 * @return The children of the parse.
	 */
	public static Object[] getChildren(Object parse) {
		if (parser_ == STANFORD_NLP)
			return ((edu.stanford.nlp.trees.Tree) parse).children();
		else
			return ((Parse) parse).getChildren();
	}
}
