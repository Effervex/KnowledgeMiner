/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import util.UtilityMethods;
import util.wikipedia.WikiParser;
import cyc.CycConstants;
import cyc.OntologyConcept;
import cyc.StringConcept;

/**
 * A mining heuristic that parses the first sentence of a Wikipedia article for
 * information. This heuristic produces new relations, term standing and new
 * terms.
 * 
 * @author Sam Sarjant
 */
public class FirstSentenceMiner extends WikipediaArticleMiningHeuristic {

	/** The number of words that can be skipped when extracting collections. */
	private static final int REGEXP_LEEWAY = 2;

	/**
	 * A tricky pattern for parsing synonyms from strings. Groups are (1+3+5?),
	 * (2?+3), and (4?+5?)
	 */
	private static final Pattern SYNONYM_PATTERN = Pattern
			.compile("'{3,5}([^\"]+?)(?:\"(.+?)\")?([^\"]+?)'{3,5}(?:\\s*\"'{3,5}(.+?)'{3,5}\"\\s*'{3,5}(.+?)'{3,5})?");

	/** The regular expressions and their standing for first sentences. */
	private static Map<Pattern, TermStanding> sentenceRegExps_;

	/** If the text should be wikified first. */
	public static boolean wikifyText_ = false;

	/**
	 * Constructor for a new FirstSentenceMiner.java
	 * 
	 * @param mapper
	 *            The mapping part of KnowledgeMiner
	 * @param miner
	 *            The miner.
	 */
	public FirstSentenceMiner(CycMapper mapper, CycMiner miner) {
		// Cannot precompute due to ontolinks in comments
		super(false, mapper, miner);

		initialiseRegExps();
	}

	/**
	 * The fragment extracting method, part A. This method is the high level
	 * method which controls patterns within the fragments.
	 * 
	 * The pattern that it looks for is to find a Y-fragment, or a Y-fragment
	 * followed by an S-fragment and another A-fragment.
	 * 
	 * @param terms
	 *            The terms list being filled.
	 * @param fragment
	 *            The fragment being examined.
	 */
	private void fragmentExtractA(ArrayList<String> terms, String fragment) {
		fragment = fragmentExtractB(terms, fragment);
		// If the extracting was successful, check for S-fragment
		if (fragment != null) {
			fragment = fragmentExtractS(fragment);
			if (fragment != null) {
				fragmentExtractA(terms, fragment);
			}
		}
	}

	/**
	 * The fragment extracting method, part B. This method looks for Wiki links
	 * at the head of the fragment.
	 * 
	 * These links can be in various forms, but all begin with "[[" and contain
	 * "]]" somewhere within.
	 * 
	 * @param terms
	 *            The terms list being filled.
	 * @param fragment
	 *            The fragment being examined.
	 * @return Whatever fragment is left over after extraction or null if no
	 *         extraction.
	 */
	private String fragmentExtractB(ArrayList<String> terms, String fragment) {
		String regExp = "(?:(?:\\[\\[(.+?)\\]\\]([a-z]+)?)|(?:[^ ]+(?= of )))(.+)";
		Matcher matcher = UtilityMethods.getRegMatcher(fragment, regExp);
		if (matcher.matches()) {
			String result = matcher.group(1);
			String proximalString = matcher.group(2);
			if (proximalString != null) {
				result += "|" + result + proximalString;
			}

			// Add the term
			if (result != null)
				terms.add(result);
			return matcher.group(3);
		}
		return null;
	}

	/**
	 * The fragment extracting method, part S. This method looks for delimiting
	 * patterns at the head of the fragment.
	 * 
	 * Patterns include strings such as " ", ", ", " and " among others.
	 * 
	 * @param fragment
	 *            The fragment being examined.
	 * @return Whatever fragment is left over after extraction or null if no
	 *         extraction.
	 */
	private String fragmentExtractS(String fragment) {
		String regExp = "(?:,? and | of | |, |-|/)(.+)";
		Matcher matcher = UtilityMethods.getRegMatcher(fragment, regExp);
		if (matcher.matches())
			return matcher.group(1);
		return null;
	}

	/**
	 * Initialises the first sentence parsing regular expressions.
	 */
	private void initialiseRegExps() {
		if (sentenceRegExps_ != null)
			return;

		sentenceRegExps_ = new HashMap<Pattern, TermStanding>();
		// RegExp string for 'is a' cases (The X is a, X is the)
		// Excluding the 'is a * of' case
		sentenceRegExps_
				.put(Pattern
						.compile("(?:[^' ]* ){0,"
								+ (REGEXP_LEEWAY + 1)
								+ "}'''.+?'''.*? is (?:an?|the) ((?:[\\w-'/:\\.]+(?= )|\\[\\[[^\\[\\]]+\\]\\])(?! of ).+)"),
						TermStanding.UNKNOWN);

		// 'A X is a' case. Does not have 'A X is the', so is a separate case.
		sentenceRegExps_.put(Pattern.compile("An? '''.+?'''.*? is an? (.+)"),
				TermStanding.INDIVIDUAL);

		// 'X was a Y' case. Or 'The X was a Y.' No 'X was the'
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? was an? (.+)"), TermStanding.UNKNOWN);

		// 'The X were a Y' or 'X were a Y.'
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? were an? (.+)"),
				TermStanding.INDIVIDUAL);

		// 'X are a Y.'
		sentenceRegExps_.put(Pattern.compile("'''.+?'''.*? are an? (.+)"),
				TermStanding.INDIVIDUAL);

		// 'X was one of the Y' or 'X is one of the Y'
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? (?:i|wa)s one of the (.+)"),
				TermStanding.INDIVIDUAL);

		// 'X is a * of Y'
		// TODO This pattern seems bad.
		sentenceRegExps_
				.put(Pattern
						.compile("(?:[^' ]* ){0,"
								+ (REGEXP_LEEWAY + 1)
								+ "}'''.+?'''.*? (?:(?:wa)|i)s an? (?:[\\w-'/:\\.]+|\\[\\[[^\\[\\]]+\\]\\] of .+())"),
						TermStanding.UNKNOWN);

		// RegExp string for 'is a' cases (The X is a, X is the)
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? is (?:an?|the) (.+)"),
				TermStanding.UNKNOWN);

		// 'A X is a' case. Does not have 'A X is the', so is a separate case.
		sentenceRegExps_.put(Pattern.compile("An? '''.+?'''.*? is an? (.+)"),
				TermStanding.COLLECTION);

		// 'X are the' case (The X are the)
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? are the (.+)"),
				TermStanding.COLLECTION);

		// Xs are a Y
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?(?:(?:s'''.*?)|(?:'''.*?s)) are a (.+)"),
				TermStanding.COLLECTION);

		// 'Xs are Y' and 'The Xs are Y'. Note that the 's' may not be directly
		// following the article title and such, may not be in bold.
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?(?:(?:s'''.*?)|(?:'''.*?s)) are (.+)"),
				TermStanding.COLLECTION);

		// 'The X were a Y' or 'X were a Y.'
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? were an? (.+)"),
				TermStanding.COLLECTION);

		// 'The X is one of the Y'
		sentenceRegExps_.put(
				Pattern.compile("(?:[^' ]* ){0," + (REGEXP_LEEWAY + 1)
						+ "}'''.+?'''.*? is one of the (.+)"),
				TermStanding.COLLECTION);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		int article = info.getArticle();
		String title = wmi.getPageTitle(article, false);
		// Do not mine lists
		if (WikiParser.isAListOf(title))
			return;
		String firstSentence = wmi.getFirstSentence(article);
		if (firstSentence == null)
			return;

		// Check if the sentence matches any of the sentence patterns.
		if (informationRequested(informationRequested, InformationType.STANDING)) {
			regExpMatch(title, firstSentence, info, wmi);
		}

		// Extract synonyms
		if (informationRequested(informationRequested, InformationType.SYNONYM)) {
			ArrayList<String> synonyms = extractSynonyms(firstSentence);
			for (String synonym : synonyms)
				info.addAssertion(new PartialAssertion(
						CycConstants.SYNONYM_RELATION.getConcept(),
						basicProvenance_, info.getMappableSelfRef(),
						new StringConcept(synonym)));
		}

		if (informationRequested(informationRequested, InformationType.COMMENT)) {
			// Assert the sentence itself as a comment
			String paragraph = wmi.getFirstParagraph(article);
			if (!paragraph.isEmpty()) {
				// Replace anchors with ontolinks
				paragraph = WikiParser.cleanupUselessMarkup(paragraph);
				paragraph = WikiParser
						.cleanupExternalLinksAndStyling(paragraph);
				paragraph = paragraph.replaceAll("'{2,}", "'");
				paragraph = replaceAnchorsWithOntolinks(paragraph, wmi, cyc);

				paragraph = NLPToSyntaxModule.convertToAscii(paragraph);
				paragraph = paragraph.replaceAll("\n+", "<p>");
				info.addAssertion(new PartialAssertion(
						CycConstants.WIKIPEDIA_COMMENT.getConcept(),
						basicProvenance_, info.getMappableSelfRef(),
						new StringConcept(paragraph)));
			}
		}
	}

	/**
	 * Replaces the anchors with dynamic links to concepts.
	 *
	 * @param paragraph
	 *            The text to replace anchors in.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The same text with all anchors replaced by dynamic links to
	 *         concepts (with plain text annotations).
	 */
	private String replaceAnchorsWithOntolinks(String paragraph, WMISocket wmi,
			OntologySocket ontology) {
		// Find anchors
		int start = 0;
		StringBuilder builder = new StringBuilder();
		Matcher m = WikiParser.ANCHOR_PARSER.matcher(paragraph);
		while (m.find()) {
			try {
				builder.append(paragraph.substring(start, m.start()));

				int article = wmi.getArticleByTitle(m.group(1));
				// Replace anchors with ontolinks
				boolean knownConcept = false;
				if (article != -1) {
					OntologyConcept concept = KnowledgeMiner.getKnownMapping(
							article, ontology);
					if (concept != null) {
						builder.append("[[" + concept.getConceptName() + "|");
						knownConcept = true;
					}
				}

				if (m.group(2) != null)
					builder.append(m.group(2));
				else
					builder.append(m.group(1));
				if (knownConcept && article != -1)
					builder.append("]]");
				start = m.end();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (start != paragraph.length())
			builder.append(paragraph.substring(start, paragraph.length()));
		return builder.toString();
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		informationProduced[InformationType.SYNONYM.ordinal()] = true;
		informationProduced[InformationType.COMMENT.ordinal()] = true;
		informationProduced[InformationType.STANDING.ordinal()] = true;
	}

	/**
	 * Extracts parent collections from a collection fragment of the first
	 * sentence.
	 * 
	 * @param collectionFragment
	 *            The fragment of the first sentence that is likely to contain
	 *            parent collections.
	 * @param wikifyText
	 *            If the collection fragment text should be wikified to create
	 *            links.
	 * @param wmi
	 *            WMI access.
	 * @return The terms identified as parents.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public ArrayList<String> extractParentLabels(String collectionFragment,
			boolean wikifyText, WMISocket wmi) throws Exception {
		ArrayList<String> parentCollections = new ArrayList<>();
		if (collectionFragment == null || collectionFragment.isEmpty())
			return parentCollections;

		// Wikify text?
		if (wikifyText)
			collectionFragment = wmi.annotate(collectionFragment, 0, false);

		// Check if the first word is valid
		for (int i = 0; i <= REGEXP_LEEWAY; i++) {
			if (fragmentExtractB(parentCollections, collectionFragment) != null) {
				parentCollections.clear();
				fragmentExtractA(parentCollections, collectionFragment);
				break;
			}
			int nextWord = collectionFragment.indexOf(' ');
			collectionFragment = collectionFragment.substring(nextWord + 1);
		}

		return parentCollections;
	}

	/**
	 * Extracts synonyms from a Wiki formatted string by capturing the items in
	 * bold.
	 * 
	 * @param firstSentence
	 *            The first sentence used for extraction.
	 * @return The items in bold.
	 */
	public ArrayList<String> extractSynonyms(String firstSentence) {
		Matcher matcher = SYNONYM_PATTERN.matcher(firstSentence.replaceAll(
				"[\\(\\)]", ""));
		ArrayList<String> synonyms = new ArrayList<>();
		while (matcher.find()) {
			// 1+3+5?
			StringBuilder buffer = new StringBuilder(matcher.group(1)
					+ matcher.group(3));
			if (matcher.start(5) != -1)
				buffer.append(" " + matcher.group(5));
			String result = WikiParser.cleanAllMarkup(buffer.toString());
			result = NLPToSyntaxModule.convertToAscii(result);
			synonyms.add(result);

			// 2?+3
			if (matcher.start(2) != -1) {
				result = matcher.group(2) + matcher.group(3);
				result = WikiParser.cleanAllMarkup(result);
				result = NLPToSyntaxModule.convertToAscii(result);
				synonyms.add(result);
			}

			// 4?+5?
			if (matcher.start(4) != -1) {
				result = matcher.group(4) + " " + matcher.group(5);
				result = WikiParser.cleanAllMarkup(result);
				result = NLPToSyntaxModule.convertToAscii(result);
				synonyms.add(result);
			}
		}
		return synonyms;
	}

	/**
	 * Checks if any of the first sentence regular expressions match this first
	 * sentence's structure. The standing of the sentence is recorded during
	 * this.
	 * 
	 * @param title
	 *            The title of the article.
	 * @param firstSentence
	 *            The sentence to match.
	 * @param info
	 *            The mined information to add to.
	 * @param wmi
	 *            WMI access.
	 * @return The fragment of the sentence that may represent potential parent
	 *         collections.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public String regExpMatch(String title, String firstSentence,
			MinedInformation info, WMISocket wmi) throws Exception {
		String collectionFragment = null;
		// Check every pattern
		int firstMatch = Integer.MAX_VALUE;
		for (Pattern regExp : sentenceRegExps_.keySet()) {
			Matcher matcher = regExp.matcher(firstSentence);
			if (matcher.matches()) {
				// Note the collection fragment and standing
				if (matcher.start(1) < firstMatch) {
					firstMatch = matcher.start(1);
					collectionFragment = matcher.group(1);
				}
				TermStanding matcherStanding = sentenceRegExps_.get(regExp);
				if (matcherStanding != TermStanding.UNKNOWN) {
					// Record the standing, resolving clashes.
					info.addStandingInformation(matcherStanding, getWeight(),
							basicProvenance_);
				}
			}
		}

		if (collectionFragment == null) {
			return null;
		}
		return NLPToSyntaxModule.convertToAscii(collectionFragment);
	}
}
