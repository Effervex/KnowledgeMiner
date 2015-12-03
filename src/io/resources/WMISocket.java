/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import util.IllegalDelimiterException;
import util.Pair;
import util.collection.WeightedSet;
import util.wikipedia.InfoboxData;
import util.wikipedia.WikiParser;

/**
 * This class connects to and is a wrapper for various WMI methods.
 * 
 * @author Sam Sarjant
 */
public class WMISocket extends WikipediaSocket {
	/** The pattern for separating page title context. */
	private static final Pattern CONTEXT_PATTERN = Pattern
			.compile("(.+) \\((.+)\\)$");

	private static final Pattern FORCE_PARSE_PATTERN = Pattern
			.compile("(.+?)\\W+(\\w+)\\s*=");

	private static final Pattern LABELS_PATTERN = Pattern
			.compile("(\\d+),(\\d+),(.+)");

	private static final Pattern REVERSE_ABBREVIATION_PATTERN = Pattern
			.compile("\\.[a-z]{0,4}[A-Z].*");

	/** The pattern for parsing a senses element. */
	private static final Pattern SENSES_PATTERN = Pattern
			.compile("(\\d+),([\\d.E-]+),(.+)");

	private static final Pattern SENTENCE_END = Pattern
			.compile("\\.((')|\")?\\s*(?=[^a-z]|$).*");

	protected static final String PARSER_ANNOTATE = "Annotate";

	protected static final String PARSER_ID_COLLECTION = "IDCollection";

	protected static final String PARSER_LABELS = "Labels";

	protected static final String PARSER_MARKUP = "Markup";

	protected static final String PARSER_PAGE_DETAILS = "PageDetails";

	protected static final String PARSER_SENSES = "Senses";

	protected static final String PARSER_TOPIC = "Topic";

	/** The marker for starting a new infobox. */
	public static final Pattern INFOBOX_STARTER = Pattern.compile(
			"\\{\\{\\s*(?:(?:(?:Infobox)([\\w -]*))|(\\w+obox))",
			Pattern.CASE_INSENSITIVE);

	/** If an infobox has no type. */
	public static final String NO_TYPE = "xnotypex";

	/** The port number to connect to for WMI. */
	public static final int WMI_PORT = 2424;

	public WMISocket(WMIAccess access) {
		super(access);
		try {
			command("set /env/singleline true", true);
			command("set /env/endmessage ", true);
			command("set /env/prompt ", true);
			command("set /env/time false", true);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Register the parsing methods
		registerParsingMethod(PARSER_ANNOTATE, new AnnotateParser());
		registerParsingMethod(PARSER_TOPIC, new TopicParser());
		registerParsingMethod(PARSER_ID_COLLECTION, new IDCollectionParser());
		registerParsingMethod(PARSER_MARKUP, new MarkupParser());
		registerParsingMethod(PARSER_PAGE_DETAILS, new PageDetailsParser());
		registerParsingMethod(PARSER_SENSES, new SensesParser());
		registerParsingMethod(PARSER_LABELS, new LabelsParser());
	}

	/**
	 * Sends and parses a command to WMI, returning the appropriate parsed
	 * result. Performs caching operations as well.
	 * 
	 * @param <T>
	 *            The result class.
	 * @param command
	 *            The command.
	 * @param argument
	 *            The command's argument.
	 * @param parsingMethod
	 *            The parsing method.
	 * @return The parsed result of the command, or null.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	private <T> T command(String command, String argument,
			WikipediaMethod<T> parsingMethod) throws IOException {
		T result = (T) access_.getCachedCommand(command, argument);
		if (result != null) {
			try {
				result = parsingMethod.cloneResult(result);
			} catch (Exception e) {
				result = null;
			}
		}
		if (result == null) {
			boolean singleline = true;
			if (command.equals("markup") || command.equals("topics")) {
				singleline = false;
				command("set /env/endmessage " + MESSAGE_END, true);
				command("set /env/singleline false", false);
			}

			String commandResult = command(command + " " + argument, singleline);
			boolean retry = false;
			do {
				retry = false;
				try {
					result = (T) cacheResult(command, argument, commandResult,
							parsingMethod, null);
				} catch (Exception e) {
					e.printStackTrace();
					// Pipe through the rest
					readRemaining();
					retry = true;
				}
			} while (retry);

			if (command.equals("markup") || command.equals("topics")) {
				command("set /env/singleline true", true);
				command("set /env/endmessage ", true);
			}
		}
		return result;
	}

	/**
	 * Sends and parses a command to WMI, during which information is extracted
	 * and split into multiple information sources. Returns the information for
	 * one of those sources.
	 * 
	 * @param <T>
	 *            The result class for the initial command.
	 * @param <A>
	 *            The result class for the subcommand.
	 * @param command
	 *            The command.
	 * @param argument
	 *            The command's argument.
	 * @param parsingMethod
	 *            The parsing method.
	 * @param subCommand
	 *            The sub command to extract.
	 * @param clazz
	 *            The class of the subcommand.
	 * @return The parsed result of the command, or null.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	private <T, A> A command(String command, String argument,
			WikipediaMethod<T> parsingMethod, String subCommand, Class<A> clazz)
			throws IOException {
		A result = (A) access_.getCachedCommand(subCommand, argument);
		if (result == null) {
			boolean singleline = true;
			if (command.equals("markup")) {
				singleline = false;
				command("set /env/endmessage " + MESSAGE_END, true);
				command("set /env/singleline false", false);
			}

			String commandResult = command(command + " " + argument, singleline);
			result = (A) cacheResult(command, argument, commandResult,
					parsingMethod, subCommand);

			if (command.equals("markup")) {
				command("set /env/singleline true", true);
				command("set /env/endmessage ", true);
			}
		}
		// TODO Not cloning the sub-result.
		return result;
	}

	@Override
	protected int getPort() {
		return WMI_PORT;
	}

	/**
	 * Annotates some text by replacing words with links.
	 * 
	 * @param text
	 *            The text to annotate.
	 * @param minWeight
	 *            The minimum weight to create annotations for.
	 * @param withWeight
	 *            If the weight should be included in the annotation.
	 * @return The annotated text.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public String annotate(String text, double minWeight, boolean withWeight,
			Collection<Integer> context) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(minWeight + " ");
		if (withWeight)
			sb.append("T ");
		else
			sb.append("F ");
		sb.append(DELIMITER + "\n" + text + "\n" + DELIMITER);
		return (String) command("annotate", sb.toString(),
				getParsingMethod(PARSER_ANNOTATE));
	}

	/**
	 * Sends a command string to WMI. It should be in a standard, recognisable
	 * format.
	 * 
	 * @param commandString
	 *            The string to send to WMI.
	 * @param singleline
	 *            If the command should only parse a single line (i.e.
	 *            /env/singleline is set to true).
	 * @return The result String of the command, or an empty string if the
	 *         command was not recognised.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public String command(String commandString, boolean singleline)
			throws IOException {
		String output = null;
		try {
			output = querySocket(commandString);
			if (singleline)
				return output;
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Check for command mangling
		if (output.startsWith("Unknown command")) {
			throw new IOException("Invalid command: '" + commandString + "'");
		}

		// Read the output, ignoring the Elapsed Time line
		StringBuilder buffer = new StringBuilder();
		boolean first = true;
		while (!output.equals(MESSAGE_END)) {
			if (!first)
				buffer.append("\n");
			buffer.append(output);
			first = false;
			// Read in next line
			output = readLine();
		}

		// Cache the results
		return buffer.toString();
	}

	/**
	 * Gets an article by a title (via the "art" command).
	 * 
	 * @param title
	 *            The title of the article.
	 * @return The integer identifier of the article or -1 if no match found.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Integer> getArticleByTitle(String... titles) throws IOException {
		return (List<Integer>) batchCommand("art", titles,
				getParsingMethod("Integer"));
	}

	/**
	 * Gets the categories associated with the given article.
	 * 
	 * @param articleID
	 *            The article for which we get the associated categories.
	 * @return The categories associated.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Collection<Integer> getArticleCategories(int articleID)
			throws IOException {
		return (Collection<Integer>) command("categories", articleID + "",
				getParsingMethod(PARSER_ID_COLLECTION));
	}

	/**
	 * Gets the children articles of a given category (via the "childart"
	 * method).
	 * 
	 * @param categoryID
	 *            The category to get children from.
	 * @return A collection of article IDs for which the category is a parent.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Collection<Integer>> getChildArticles(Integer... categoryIDs)
			throws IOException {
		return (List<Collection<Integer>>) batchCommand("childart",
				categoryIDs, getParsingMethod(PARSER_ID_COLLECTION));
	}

	/**
	 * Gets the child categories of a given category (via the "children"
	 * method).
	 * 
	 * @param categoryIDs
	 *            The categories to get child categories from.
	 * @return A collection of category IDs which are subcategories of the
	 *         parameter category.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Collection<Integer>> getChildCategories(Integer... categoryIDs)
			throws IOException {
		return (List<Collection<Integer>>) batchCommand("children",
				categoryIDs, getParsingMethod(PARSER_ID_COLLECTION));
	}

	@Override
	public int getEquivalentCategory(int articleID) {
		return -1;
	}

	@Override
	public int getEquivalentArticle(int categoryID) {
		return -1;
	}

	@Override
	public String getFirstParagraph(int articleID) throws IOException {
		return command("markup", articleID + "",
				getParsingMethod(PARSER_MARKUP), "paragraph", String.class);
	}

	/**
	 * Gets the first sentence of an article. Due to a bug in WM, this is
	 * achieved by parsing the markup (via the "markup" command).
	 * 
	 * @param articleID
	 *            The article for which we need the first sentence.
	 * @return The first sentence of the article.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public String getFirstSentence(int articleID) throws IOException {
		return command("markup", articleID + "",
				getParsingMethod(PARSER_MARKUP), "first", String.class);
	}

	/**
	 * Gets the type of infobox associated with this page.
	 * 
	 * @param articleID
	 *            The article for which we need the first sentence.
	 * @return The infobox type used in the article, or null if no infobox.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<InfoboxData> getInfoboxData(int articleID) throws IOException {
		return command("markup", articleID + "",
				getParsingMethod(PARSER_MARKUP), "infoboxdata", List.class);
	}

	/**
	 * Get the list of articles that link into the given article (via the
	 * "inlinks" command).
	 * 
	 * @param articleID
	 *            The article to get in links for.
	 * @return A collection of all articles that link to this article.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Collection<Integer> getInLinks(int articleID) throws IOException {
		return (Collection<Integer>) command("inlinks", articleID + "",
				getParsingMethod(PARSER_ID_COLLECTION));
	}

	/**
	 * Gets the labels linking into the given article and their counts (via the
	 * 'labels' command).
	 * 
	 * @param articleID
	 *            The article ID to get the labels for.
	 * @return A sorted list (descending order) of labels and their occurrence
	 *         counts.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	@Override
	public WeightedSet<String> getLabels(int articleID) throws IOException {
		return (WeightedSet<String>) command("labelcounts", articleID + "",
				getParsingMethod(PARSER_LABELS));
	}

	/**
	 * Gets the markup of this article (via the "markup" command).
	 * 
	 * @param articleID
	 *            The article for which the markup is collected.
	 * @return The raw markup of the article.
	 * @throws IOException
	 */
	public String getMarkup(int articleID) throws IOException {
		return command("markup", articleID + "", new MarkupParser());
	}

	/**
	 * Gets the most likely article given a single term (via the "mostlikely"
	 * command).
	 * 
	 * @param term
	 *            The term used to search for the most likely article.
	 * @param object
	 * @return An integer identifier of the most likely article or -1 if no
	 *         match found.
	 * @throws IOException
	 *             Should something go awry.
	 */
	@Override
	public int getMostLikelyArticle(String term) throws IOException {
		return (int) command("mostlikely", term, getParsingMethod("Integer"));
	}

	@Override
	public int getNextArticle(int id) throws IOException {
		String result = command("next " + id, true);
		return Integer.parseInt(result.split("\\|")[0]);
	}

	/**
	 * Get the list of articles that this article links to (via the "outlinks"
	 * command).
	 * 
	 * @param articleID
	 *            The article to get out links for.
	 * @return A collection of all articles this article links to.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Collection<Integer> getOutLinks(int articleID) throws IOException {
		return (Collection<Integer>) command("outlinks", articleID + "",
				getParsingMethod(PARSER_ID_COLLECTION));
	}

	/**
	 * Gets an article's title with or without scope (via the "page" command).
	 * 
	 * @param article
	 *            The article to get the title for.
	 * @param withScope
	 *            If the article should include the scope (context in brackets).
	 * @return The article title or null if no such article.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public List<String> getArtTitle(boolean withScope, Integer... articleIDs)
			throws IOException {
		String subcommand = (withScope) ? "fulltitle" : "shorttitle";
		return batchCommand("page", articleIDs,
				getParsingMethod(PARSER_PAGE_DETAILS), subcommand, String.class);
	}

	/**
	 * Gets the title context of an article (the part in brackets).
	 * 
	 * @param articleID
	 *            The article to get context for.
	 * @return The context of the article (or an empty string if none).
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public String getArtTitleContext(int articleID) throws IOException {
		return command("page", articleID + "",
				getParsingMethod(PARSER_PAGE_DETAILS), "titlecontext",
				String.class);
	}

	/**
	 * Gets the type of page that this ID represents (via the 'page' command).
	 * 
	 * @param pageIDs
	 *            The page ID to determine type for.
	 * @return The type of page (article, category, redirect, disambiguation)
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public List<String> getPageType(Integer... pageIDs) throws IOException {
		return batchCommand("page", pageIDs,
				getParsingMethod(PARSER_PAGE_DETAILS), "pagetype", String.class);
	}

	/**
	 * Gets the parent categories of a given category (via the "parents"
	 * method).
	 * 
	 * @param categoryIDs
	 *            The categories to get parent categories from.
	 * @return A collection of category IDs which are supercategories of the
	 *         parameter category.
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Collection<Integer>> getParentCategories(Integer... categoryIDs)
			throws IOException {
		return (List<Collection<Integer>>) batchCommand("parents", categoryIDs,
				getParsingMethod(PARSER_ID_COLLECTION));
	}

	@Override
	public int getPrevArticle(int id) throws IOException {
		String result = command("prev " + id, true);
		return Integer.parseInt(result.split("\\|")[0]);
	}

	/**
	 * Gets the redirect target for this redirect (or -1).
	 * 
	 * @param articleID
	 *            The redirect article ID.
	 * @return The target redirect.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public int getRedirect(int articleID) throws IOException {
		return (int) command("redirect", articleID + "",
				getParsingMethod("Integer"));
	}

	/**
	 * Gets the relatedness between pairs of Wikipedia articles. This is
	 * calculated by comparing the labels in and out of the articles.
	 * 
	 * @param articles
	 *            The pairs of articles. Must be even.
	 * @return An array of relatedness measures for each pair between 0 and 1,
	 *         where 1 is strongly related (identical).
	 * @throws IOException
	 *             Should something go awry...
	 */
	@Override
	public List<List<Double>> getRelatednessList(Integer[] baseArticles,
			Integer... comparisonArticles) throws IOException {
		int comparisonLength = comparisonArticles.length;
		int baseLength = baseArticles.length;
		String[] arguments = new String[comparisonLength * baseLength];
		for (int i = 0; i < baseLength; i++) {
			int baseArticle = baseArticles[i];
			for (int j = 0; j < comparisonArticles.length; j++) {
				// Regularise the order of the arguments
				int index = i * comparisonLength + j;
				if (baseArticle < comparisonArticles[j])
					arguments[index] = baseArticle + " "
							+ comparisonArticles[j];
				else
					arguments[index] = comparisonArticles[j] + " "
							+ baseArticle;
			}
		}

		@SuppressWarnings("unchecked")
		List<Double> result = (List<Double>) batchCommand("relatedness",
				arguments, getParsingMethod(PARSER_DOUBLE));

		// Split the result into sublists
		List<List<Double>> lists = new ArrayList<>(baseLength);
		for (int i = 0; i < baseLength; i++) {
			lists.add(result.subList(i * comparisonLength, (i + 1)
					* comparisonLength));
		}
		return lists;
	}

	/**
	 * Gets the relatedness between pairs of Wikipedia articles. This is
	 * calculated by comparing the labels in and out of the articles.
	 * 
	 * @param articles
	 *            The pairs of articles. Must be even.
	 * @return An array of relatedness measures for each pair between 0 and 1,
	 *         where 1 is strongly related (identical).
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Double> getRelatednessPair(int... articles) throws IOException {
		if (articles.length % 2 != 0)
			throw new IllegalArgumentException(
					"Should be even number of arguments!");
		String[] arguments = new String[articles.length / 2];
		for (int i = 0; i < articles.length; i += 2) {
			// Regularise the order of the arguments
			if (articles[i] < articles[i + 1])
				arguments[i / 2] = articles[i] + " " + articles[i + 1];
			else
				arguments[i / 2] = articles[i + 1] + " " + articles[i];
		}

		return (List<Double>) batchCommand("relatedness", arguments,
				getParsingMethod(PARSER_DOUBLE));
		// ////////////////////////////////////////////////////// //
		// THIS IS JUST A HACK. MAY NOT REFLECT ORIGINAL WMI CODE //
		// ////////////////////////////////////////////////////// //
		// Collection<Integer> inLinksA = getInLinks(artA);
		// Collection<Integer> inLinksB = getInLinks(artB);
		// double inLinkRelatedness = 1;
		// try {
		// inLinkRelatedness = calculateSetRelatedness(inLinksA, inLinksB,
		// articleCount_);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		//
		// Collection<Integer> outLinksA = getOutLinks(artA);
		// Collection<Integer> outLinksB = getOutLinks(artB);
		// double outLinkRelatedness = calculateSetRelatedness(outLinksA,
		// outLinksB, articleCount_);
		//
		// return (inLinkRelatedness + outLinkRelatedness) / 2;
	}

	/**
	 * Gets the topics of a text, using the (via the "topics" command).
	 * 
	 * @param term
	 *            The text for which topics are found.
	 * 
	 * @return A {@link WeightedSet} of topics, where each topic is represented
	 *         by a pair (text, article).
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	@Override
	public WeightedSet<Integer> getTopics(String text,
			Collection<Integer> context) throws IOException {
		String noBrackets = WikiParser.cleanAllMarkup(text);
		return (WeightedSet<Integer>) command("topics", DELIMITER + "\n"
				+ noBrackets + "\n" + DELIMITER, getParsingMethod(PARSER_TOPIC));
	}

	/**
	 * Batch processes a number of terms and returns a weighted set of articles
	 * for each term (via the "senses" command).
	 * 
	 * @param terms
	 *            The terms to get weighted article for.
	 * @return A list of weighted sets for each term.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<WeightedSet<Integer>> getWeightedArticles(String... terms)
			throws IOException {
		return (List<WeightedSet<Integer>>) batchCommand("senses", terms,
				getParsingMethod(PARSER_SENSES));
	}

	/**
	 * Parses a double value from a String.
	 * 
	 * @author Sam Sarjant
	 */
	private final class AnnotateParser extends WikipediaMethod<String> {
		@Override
		public String parseResult(String result, String source) {
			return result;
		}
	}

	/**
	 * Parses a variably sized collection of IDs.
	 * 
	 * @author Sam Sarjant
	 */
	private final class IDCollectionParser extends
			WikipediaMethod<Collection<Integer>> {
		@Override
		public Collection<Integer> cloneResult(Collection<Integer> result) {
			return new ArrayList<Integer>(result);
		}

		@Override
		public Collection<Integer> parseResult(String result, String source) {
			// Returns a variably sized list of article IDs
			String[] split = result.split("\\|");
			int numResults = Math.max(0, Integer.parseInt(split[0]));
			Collection<Integer> ids = new ArrayList<Integer>(numResults);
			for (int i = 1; i <= numResults; i++) {
				if (!split[i].trim().isEmpty())
					ids.add(Integer.parseInt(split[i]));
			}
			return ids;
		}
	}

	/**
	 * Parses the labels of an article and their counts into a weightedset.
	 *
	 * @author Sam Sarjant
	 */
	private final class LabelsParser extends
			WikipediaMethod<WeightedSet<String>> {
		@Override
		public WeightedSet<String> parseResult(String result, String source) {
			// Returns a variably sized list of elements, where each
			// element includes an integer id, a weight, and a title
			String[] split = result.split("\\|");
			String[] counts = split[0].split(",");
			if (counts[0].equals("-1"))
				return new WeightedSet<String>(0);
			int numResults = Integer.parseInt(counts[2]);
			WeightedSet<String> labels = new WeightedSet<>(numResults);
			for (int i = 1; i <= numResults; i++) {
				Matcher m = LABELS_PATTERN.matcher(split[i]);
				if (m.find()) {
					int occurrences = Integer.parseInt(m.group(1));
					String label = m.group(3);
					labels.add(label, occurrences);
				}
			}
			return labels;
		}
	}

	/**
	 * Parses the markup into subinformation.
	 * 
	 * @author Sam Sarjant
	 */
	private final class MarkupParser extends WikipediaMethod<String> {

		/**
		 * Records an infobox relation.
		 * 
		 * @param leftSide
		 *            The relation predicate.
		 * @param rightSide
		 *            The value of the relation.
		 * @param infoboxData
		 *            The data to add the relation to.
		 */
		private void createRelation(String leftSide, String rightSide,
				InfoboxData infoboxData) {
			if (leftSide == null || rightSide == null)
				return;

			leftSide = StringUtils.strip(leftSide, "|");
			leftSide = leftSide.trim();
			rightSide = StringUtils.strip(rightSide, "|");
			rightSide = rightSide.trim();
			if (leftSide != null && !leftSide.isEmpty() && !rightSide.isEmpty())
				infoboxData.putRelation(leftSide.trim(), rightSide);
		}

		private String parseFirstParagraph(String cleanerMarkup, String source) {
			int index = cleanerMarkup.indexOf("\n\n");
			if (index != -1)
				return cleanerMarkup.substring(0, index).trim();
			index = cleanerMarkup.indexOf("==");
			if (index != -1)
				return cleanerMarkup.substring(0, index).trim();
			return cleanerMarkup;
		}

		/**
		 * Parses the first sentence of the markup. This may require multiple
		 * operations to navigate sentence ambiguities.
		 * 
		 * @param markup
		 *            The markup to parse the first sentence from.
		 * @param articleID
		 *            The article being parsed.
		 * @return The first sentence or an empty string if not found.
		 */
		private String parseFirstSentence(String markup, String articleID) {
			StringBuilder firstSentence = new StringBuilder();
			boolean satisfied = false;
			int startPoint = 0;

			try {
				while (!satisfied) {
					satisfied = false;
					String result = null;
					// Find the first occurrence of a '.'
					result = WikiParser.first(markup, startPoint,
							new String[] { "." }, new String[] { "\n\n" });
					firstSentence.append(result);

					// Determine if this full stop is the actual end of sentence
					int periodIndex = firstSentence.length() - 1;
					startPoint = periodIndex + 1;
					if (startPoint == markup.length() && markup.contains(".")) {
						return markup.replaceAll(" ?\n", " ").trim();
					}
					int endPoint = Math.min(markup.length(), periodIndex + 5);
					// If no space (except punctuation), continue
					String substring = markup.substring(periodIndex, endPoint);
					Matcher m = SENTENCE_END.matcher(substring);
					if (m.matches()) {
						satisfied = true;

						// The period may be for the end of an abbreviation or
						// title
						m = REVERSE_ABBREVIATION_PATTERN.matcher(firstSentence
								.reverse().toString());
						if (m.matches())
							satisfied = false;
						firstSentence.reverse();
					} else
						// Can't find a full stop anywhere.
						return "";
				}
			} catch (Exception e) {
				return "";
			}

			String result = firstSentence.toString().replaceAll(" ?\n", " ");
			result = result.trim();
			return result;
		}

		/**
		 * Search for and parse an infobox from the markup.
		 * 
		 * @param markup
		 *            The markup to parse.
		 * @param articleID
		 *            The article being parsed.
		 * @param subResults
		 *            The sub results of the parser.
		 * @return The markup, possibly cleaned up by fixing open brackets.
		 */
		private String parseInfobox(String markup, String articleID,
				Map<String, Object> subResults) {
			// Parse the first occurrence of an infobox
			List<InfoboxData> infoboxes = new ArrayList<>();
			Matcher m = INFOBOX_STARTER.matcher(markup);
			while (m.find()) {
				// If found, extract the type
				int startPoint = m.end();
				String type = NO_TYPE;
				if (m.group(1) != null)
					type = m.group(1);
				else if (m.group(2) != null)
					type = m.group(2);
				type = type.replaceAll("_", " ");
				type = type.trim();

				InfoboxData infoboxData = new InfoboxData(type);
				infoboxes.add(infoboxData);
				// Parse the relations of the infobox.
				markup = parseRelations(startPoint, markup, infoboxData);
			}
			subResults.put("infoboxdata", infoboxes);
			return markup;
		}

		/**
		 * Parses a half of the relations, depending on the currently expected
		 * side.
		 * 
		 * @param markup
		 *            The markup to parse.
		 * @param currIndex
		 *            The index to begin parsing from.
		 * @param leftSide
		 *            True if this is currently left side (else right side).
		 * @return The parsed half of the relation.
		 */
		private Pair<String, Integer> parseRelationHalf(String markup,
				int currIndex, boolean leftSide) {
			String[] delimiters = null;
			if (leftSide) {
				// Left side
				delimiters = new String[] { "=", "}}" };
			} else {
				// Right side
				delimiters = new String[] { "|", "}}" };
			}

			// Attempt to parse the relation
			String group = null;
			boolean preliminaryCutoff = false;
			// First try to parse with } and == as exception
			try {
				group = WikiParser.first(markup, currIndex, delimiters,
						new String[] { "}", "==" });
			} catch (IllegalDelimiterException e1) {
				if (e1.getDelimiter().equals("}")) {
					// Search for an '=' in the substring
					int equalsIndex = e1.getSubstring().indexOf('=');
					if (equalsIndex == -1) {
						// This is the last relation (right side).
						return new Pair<String, Integer>(e1.getSubstring()
								+ "}", currIndex + e1.getSubstring().length()
								+ 1);
					} else {
						// Break, and proceed from the equals sign.
						equalsIndex += currIndex;
						Matcher m = FORCE_PARSE_PATTERN.matcher(markup);
						if (m.find(currIndex)) {
							if (leftSide)
								return new Pair<String, Integer>(m.group(2)
										+ " =", equalsIndex + 1);
							else
								return new Pair<String, Integer>(m.group(1)
										+ "\n|", m.end(1) + 1);
						}
					}
				} else if (e1.getDelimiter().equals("=="))
					preliminaryCutoff = true;
			}

			// Deal with unfinished infoboxes.
			preliminaryCutoff |= group == markup;
			if (preliminaryCutoff) {
				int cutoff = markup.indexOf("\n", currIndex);
				if (leftSide)
					return new Pair<String, Integer>("}}", cutoff + 1);
				else
					return new Pair<String, Integer>(markup.substring(
							currIndex, cutoff) + "\n}}", cutoff + 1);
			} else {
				int newIndex = currIndex + group.length();
				return new Pair<String, Integer>(group, newIndex);
			}
		}

		/**
		 * Iteratively parse the relations of an infobox from the text, halting
		 * once the infobox is complete. Each relation is recorded in plain
		 * text; further meaning will require additional parsing heuristics.
		 * 
		 * @param startPoint
		 *            The point at which to begin parsing.
		 * @param markup
		 *            The text to parse.
		 * @param infoboxData
		 *            The data being parsed.
		 * @return The end character directly after the end of the infobox.
		 */
		private String parseRelations(int startPoint, String markup,
				InfoboxData infoboxData) {
			StringBuilder newMarkup = new StringBuilder(markup.substring(0,
					startPoint));
			int currIndex = startPoint;
			int prevIndex = -1;
			String leftRelation = null;

			boolean leftSide = false;
			while (currIndex < markup.length() && prevIndex != currIndex) {
				Pair<String, Integer> relation = parseRelationHalf(markup,
						currIndex, leftSide);
				String group = relation.objA_;
				prevIndex = currIndex;
				currIndex = relation.objB_;
				newMarkup.append(group);

				// If group hits break delimiter, finish.
				int delimiterLength = 1;
				boolean lastRelation = false;
				if (group.endsWith("}}")) {
					delimiterLength = 2;
					lastRelation = true;
				}

				// Note the relation, if possible
				group = group.substring(0, group.length() - delimiterLength)
						.trim();
				int bracketIndex = group.indexOf('{');
				// Removing special syntax
				if (bracketIndex > 0)
					group = group.substring(0, bracketIndex);

				// Noting the group
				if (leftSide) {
					String[] split = group.split("\\s+");
					leftRelation = split[split.length - 1];
				} else
					createRelation(leftRelation, group, infoboxData);
				leftSide = !leftSide;

				if (lastRelation)
					break;
			}
			newMarkup.append(markup.substring(currIndex));
			return newMarkup.toString();
		}

		@Override
		public String parseResult(String result, String source) {
			return result;
		}

		@Override
		public Map<String, Object> parseSubResults(String result, String source) {
			Map<String, Object> subResults = new HashMap<>();
			String cleanMarkup = WikiParser.cleanupUselessMarkup(result);

			// First parse the infobox (if any exists)
			cleanMarkup = parseInfobox(cleanMarkup, source, subResults);

			// Then find the first sentence and parse that.
			String cleanerMarkup = WikiParser.cleanupExternalLinksAndStyling(
					cleanMarkup).trim();
			if (cleanerMarkup.isEmpty()) {
				int infoEndIndex = cleanMarkup.indexOf("}}");
				if (infoEndIndex != -1)
					cleanerMarkup = WikiParser.cleanupExternalLinksAndStyling(
							cleanMarkup.substring(infoEndIndex + 2)).trim();
			}

			subResults.put("first", parseFirstSentence(cleanerMarkup, source));
			subResults.put("paragraph",
					parseFirstParagraph(cleanerMarkup, source));

			subResults.put(DEFAULT, result);
			return subResults;
		}
	}

	/**
	 * Parses the page details into subinformation.
	 * 
	 * @author Sam Sarjant
	 */
	private final class PageDetailsParser extends WikipediaMethod<String> {
		@Override
		public String parseResult(String result, String source) {
			return result;
		}

		@Override
		public Map<String, Object> parseSubResults(String result, String source) {
			Map<String, Object> subResults = new HashMap<>();
			String[] pageSplit = result.split("\\|");
			if (pageSplit[0].equals("null")) {
				subResults.put("fulltitle", null);
				subResults.put("shorttitle", null);
				subResults.put("titlecontext", null);
				subResults.put("pagetype", null);
			}

			// Parse the article title
			String title = pageSplit[2];
			subResults.put("fulltitle", title);
			Matcher m = CONTEXT_PATTERN.matcher(title);
			if (m.matches()) {
				subResults.put("shorttitle", m.group(1));
				subResults.put("titlecontext", m.group(2));
			} else {
				subResults.put("shorttitle", title);
				subResults.put("titlecontext", "");
			}

			// Parse the page type
			subResults.put("pagetype", pageSplit[3]);
			subResults.put(DEFAULT, result);
			return subResults;
		}
	}

	/**
	 * Parses a double value from a String.
	 * 
	 * @author Sam Sarjant
	 */
	private final class SensesParser extends
			WikipediaMethod<WeightedSet<Integer>> {
		@Override
		public WeightedSet<Integer> cloneResult(WeightedSet<Integer> result) {
			WeightedSet<Integer> clonedSenses = new WeightedSet<>();
			clonedSenses.addAll(result);
			return clonedSenses;
		}

		@Override
		public WeightedSet<Integer> parseResult(String result, String source) {
			// Returns a variably sized list of elements, where each
			// element includes an integer id, a weight, and a title
			String[] split = result.split("\\|");
			WeightedSet<Integer> articles = new WeightedSet<Integer>();
			if (split.length == 1)
				return articles;
			int numResults = Math.max(0, Integer.parseInt(split[0]));
			for (int i = 1; i <= numResults; i++) {
				Matcher m = SENSES_PATTERN.matcher(split[i]);
				if (m.find()) {
					int id = Integer.parseInt(m.group(1));
					double weight = Double.parseDouble(m.group(2));
					articles.add(id, weight);
				}
			}

			return articles;
		}
	}

	/**
	 * Parses topics from the output of topics/dc command.
	 *
	 * @author Sam Sarjant
	 */
	private final class TopicParser extends
			WikipediaMethod<WeightedSet<Integer>> {
		@Override
		public WeightedSet<Integer> cloneResult(WeightedSet<Integer> result) {
			return new WeightedSet<>(result);
		}

		@Override
		public WeightedSet<Integer> parseResult(String result, String source) {
			WeightedSet<Integer> topics = new WeightedSet<>();

			String[] topicSplit = result.split("\n");
			for (String topic : topicSplit) {
				topic = topic.trim();
				if (topic.isEmpty())
					continue;
				String[] split = topic.split("\\|");

				try {
					topics.add(getArticleByTitle(split[1]),
							Double.parseDouble(split[2]));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			return topics;
		}
	}
}
