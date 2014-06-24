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

import util.OpenNLP;
import util.Tree;
import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.OntologyConcept;
import cyc.CycConstants;

public class SentenceParserHeuristic {
	private static final String[] COPULAS = { "is", "are", "was", "were", "be",
			"am", "being", "been" };
	private static final Collection<ExtractionPattern> EXTRACTION_PATTERNS;
	private final static Logger logger_ = LoggerFactory
			.getLogger(SentenceParserHeuristic.class);
	private static final Pattern[] SENTENCE_SIMPLIFIER = {
			Pattern.compile("^In [^,.]+, "),
			Pattern.compile("(?<=[^,.]+), [^,.]+,(?= (is|was|are|were))") };
	private CycMapper mapper_;

	public SentenceParserHeuristic(CycMapper mapper) {
		OpenNLP.getParser();
		mapper_ = mapper;
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
	private Tree<String> composeAdjNouns(Parse parse,
			SortedMap<String, String> anchors) {
		Tree<String> strings = new Tree<String>(null);
		String text = parse.getCoveredText();

		// Add all visible anchors
		String anchorText = reAnchorString(text, anchors);
		Matcher m = WikiParser.ANCHOR_PARSER_ROUGH.matcher(anchorText);
		// Keep track of which text is in what anchors
		Map<String, Tree<String>> anchorMap = new HashMap<>();
		// Keep track of any multiple-work anchors.
		Map<String, Tree<String>> subAnchorMap = new HashMap<>();
		while (m.find()) {
			Tree<String> anchorTree = new Tree<String>(m.group());
			// Split up anchor text
			String anchor = (m.group(2) == null) ? m.group(1) : m.group(2);
			String[] split = anchor.split("\\s+");
			if (split.length > 1)
				for (String str : split)
					if (!subAnchorMap.containsKey(str))
						subAnchorMap.put(str, anchorTree);
			anchorMap.put(anchor, anchorTree);
			strings.addSubTree(anchorTree);
		}

		// Work backwards through the children, adding nouns, then adjectives to
		// the nouns
		boolean createNewNounSet = false;
		boolean includesNN = false;
		Tree<String> nounTree = null;
		Parse[] children = parse.getChildren();
		for (int i = children.length - 1; i >= 0; i--) {
			String childType = children[i].getType();
			String childText = children[i].getCoveredText();
			if (childType.startsWith("NN") || childType.equals("NP")) {
				// If new noun hit, create a new noun set.
				if (createNewNounSet) {
					if (includesNN && nounTree != null)
						strings.addSubTree(nounTree);
					nounTree = null;
					createNewNounSet = false;
					includesNN = false;
				}
				includesNN = childType.startsWith("NN");

				// Get the current tree root and add to it.
				if (nounTree == null) {
					nounTree = newTree(childText, anchors, anchorMap, strings);
				} else {
					Tree<String> parentTree = newTree(childText + " "
							+ nounTree.getValue(), anchors, anchorMap, strings);
					parentTree.addSubTree(nounTree);
					nounTree = parentTree;
				}
			} else if (childType.startsWith("JJ") || childType.equals("ADJP")) {
				if (nounTree != null) {
					// Insert as child of anchor if part of anchor
					if (subAnchorMap.containsKey(childText))
						subAnchorMap.get(childText).addSubValue(childText);
					else
						strings.addSubValue(reAnchorString(childText, anchors));

					strings.addSubTree(buildAdjectivePairs(nounTree, childText,
							anchors, anchorMap, strings));
				}

				createNewNounSet = true;
			} else {
				createNewNounSet = true;
			}
		}

		// Adding the noun tree if it is not yet added.
		if (includesNN && nounTree != null)
			strings.addSubTree(nounTree);
		return strings;
	}

	/**
	 * Creates a new tree, noting it under the anchor trees if it is a complete
	 * anchor.
	 * 
	 * @param textFragment
	 *            The text to add as a tree.
	 * @param anchors
	 *            The replacement map for the anchors.
	 * @param anchorMap
	 *            A map between text and anchors.
	 * @param results
	 *            The collection of all results fragments.
	 * 
	 * @return A new tree representing the text.
	 */
	private Tree<String> newTree(String textFragment,
			SortedMap<String, String> anchors,
			Map<String, Tree<String>> anchorMap, Tree<String> results) {
		// If the string represents an anchor
		if (anchorMap.containsKey(textFragment)) {
			Tree<String> anchorTree = anchorMap.get(textFragment);
			results.getSubTrees().remove(anchorTree);
			return anchorTree;
		} else {
			String anchoredText = WikiParser.cleanAllMarkup(textFragment);
			Tree<String> anchorTree = new Tree<String>(anchoredText);
			return anchorTree;
		}
	}

	/**
	 * Recursively builds adjective-noun pairs recursively via the noun tree.
	 * 
	 * @param nounTree
	 *            The tree of nouns.
	 * @param adjective
	 *            The adjective.
	 * @param anchors
	 *            The replacement map for the anchors.
	 * @param anchorMap
	 *            A mapping between text and anchors.
	 * @param results
	 *            The results to add to.
	 * @return A new tree representing the adjective pair added to the nouns.
	 */
	private Tree<String> buildAdjectivePairs(Tree<String> nounTree,
			String adjective, SortedMap<String, String> anchors,
			Map<String, Tree<String>> anchorMap, Tree<String> results) {
		Collection<Tree<String>> subTrees = nounTree.getSubTrees();
		Tree<String> subTree = null;
		if (!subTrees.isEmpty())
			subTree = buildAdjectivePairs(subTrees.iterator().next(),
					adjective, anchors, anchorMap, results);
		Tree<String> thisTree = newTree(adjective + " " + nounTree.getValue(),
				anchors, anchorMap, results);
		if (subTree != null)
			thisTree.addSubTree(subTree);
		return thisTree;
	}

	/**
	 * Creates assertions using a set of predicates and a wikifiable string.
	 * 
	 * @param predicateSet
	 *            The possible predicates.
	 * @param parse
	 *            The current parse (usually NP) to extract noun(s) from.
	 * @param anchors
	 *            The replacement map for the anchors.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The Cyc access.
	 * @param provenance
	 *            The heuristic calling this method.
	 * @param info
	 *            The information to add the assertions to.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private Collection<AssertionQueue> createAssertions(
			WeightedSet<OntologyConcept> predicateSet, Parse parse,
			SortedMap<String, String> anchors, WMISocket wmi,
			OntologySocket ontology, HeuristicProvenance provenance)
			throws Exception {
		Collection<AssertionQueue> aqs = new ArrayList<>();
		if (predicateSet.isEmpty())
			return aqs;
		logger_.trace("createAssertions: " + predicateSet.toString());
		// Return the possible noun strings
		Tree<String> nounStrs = composeAdjNouns(parse, anchors);
		// System.out.println(nounStrs);
		logger_.trace("createAssertions: "
				+ nounStrs.toString().replaceAll("\\\\\n", " "));

		// Iterate through and build the hierarchy of concepts
		HierarchicalWeightedSet<OntologyConcept> arguments = recurseNounStrs(
				nounStrs, wmi, ontology);

		// Clean up and create assertions
		arguments = (HierarchicalWeightedSet<OntologyConcept>) arguments
				.cleanEmptyParents();
		AssertionQueue aq = MinedAssertion.createAllAssertions(predicateSet,
				arguments, provenance, ontology);
		aq = (AssertionQueue) aq.cleanEmptyParents();
		if (aq.size() == 0)
			aqs.addAll(aq.getSubAssertionQueues());
		else
			aqs.add(aq);
		return aqs;
	}

	private String disambiguateTree(Parse parse, String[] predicateStrs,
			SortedMap<String, String> anchors, WMISocket wmi,
			OntologySocket cyc, MiningHeuristic heuristic,
			Collection<AssertionQueue> results) throws Exception {
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
				Arrays.copyOf(predicateStrs, predicateStrs.length), anchors,
				wmi, cyc, heuristic, results);

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
					anchors, wmi, cyc, heuristic, results);
			if (result == null) {
				canCreate = false;
			}
		}

		if (type.equals("VP") || type.equals("PP"))
			return null;

		// Can create and we have a target and predicate(s)
		if (canCreate && type.equals("NP") && !predicateStrs[0].isEmpty()) {
			Collection<AssertionQueue> assertions = null;
			for (String predStr : predicateStrs) {
				WeightedSet<OntologyConcept> predicateSet = null;
				if (isCopula(predStr)) {
					predicateSet = new WeightedSet<>(1);
					predicateSet.add(CycConstants.ISA_GENLS.getConcept());
				} else {
					// TODO Figure out a safe way to parse predicates. Probably
					// need to look at the parse code again.
//					predStr = reAnchorString(predStr, anchors);
//					predicateSet = mapper_.mapRelationToPredicate(predStr, wmi,
//							cyc);
				}

				if (predicateSet == null || predicateSet.isEmpty())
					continue;
				Collection<AssertionQueue> currAssertions = createAssertions(
						predicateSet,
						parse,
						anchors,
						wmi,
						cyc,
						new HeuristicProvenance(heuristic, predStr + "+" + text));
				if (!currAssertions.isEmpty()) {
					if (assertions == null)
						assertions = currAssertions;
					else
						// TODO Would really like to add lower instead...
						assertions.addAll(currAssertions);
				}
			}
			if (assertions != null)
				results.addAll(assertions);
		}

		if (!canCreate)
			return null;

		return text;
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

	private synchronized Parse parseLine(String cleanSentence) {
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
	private HierarchicalWeightedSet<OntologyConcept> recurseNounStrs(
			Tree<String> stringTree, WMISocket wmi, OntologySocket ontology) {
		HierarchicalWeightedSet<OntologyConcept> results = new HierarchicalWeightedSet<>();
		String str = stringTree.getValue();
		if (str != null && !str.trim().isEmpty()) {
			// TODO This should only produce a single level of results
			results.addAll(mapper_.mapTextToCyc(str.trim(), false, false,
					false, wmi, ontology));
		}

		// Recurse down to sub trees.
		Collection<Tree<String>> subTrees = stringTree.getSubTrees();
		for (Tree<String> subTree : subTrees) {
			HierarchicalWeightedSet<OntologyConcept> subResults = recurseNounStrs(
					subTree, wmi, ontology);
			if (!subResults.isEmpty())
				results.addLower(subResults);
		}
		return results;
	}

	/**
	 * Extracts a set of assertions from a sentence using parsing techniques to
	 * identify plain-text assertions.
	 * 
	 * @param sentence
	 *            The sentence to extract assertions from.
	 * @param wmi
	 *            The WMI access.
	 * @param cyc
	 *            The Cyc access.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public Collection<AssertionQueue> extractAssertions(String sentence,
			WMISocket wmi, OntologySocket cyc, MiningHeuristic heuristic)
			throws Exception {
		logger_.trace("mineSentence: " + sentence);
		SortedMap<String, String> anchors = locateAnchors(sentence);
		sentence = sentence.replaceAll("'{3,}.+?'{3,}", "THING");
		sentence = sentence.replaceAll("\\?{2,}", "");
		String cleanSentence = WikiParser.cleanAllMarkup(sentence);

		Parse parse = parseLine(cleanSentence);
		// parse.show();
		Collection<AssertionQueue> results = new ArrayList<>();
		logger_.trace("disambiguateTree: " + sentence);
		disambiguateTree(parse, null, anchors, wmi, cyc, heuristic, results);
		return results;
	}

	static {
		EXTRACTION_PATTERNS = new ArrayList<>();
		EXTRACTION_PATTERNS.add(new ExtractionPattern("VB", "NP"));
	}

	public static void main(String[] args) throws Exception {
		ResourceAccess.newInstance();
		CycMapper mapper = new CycMapper(null);
		SentenceParserHeuristic sph = new SentenceParserHeuristic(mapper);
		WMISocket wmi = ResourceAccess.requestWMISocket();
		OntologySocket cyc = ResourceAccess.requestOntologySocket();

		String input = "";
		while (!input.equalsIgnoreCase("exit")) {
			System.out.println("Enter sentence to parse:");
			BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
			input = in.readLine();
			Collection<AssertionQueue> assertions = sph.extractAssertions(
					input, wmi, cyc, null);
			System.out.println(assertions);
		}
	}
}
