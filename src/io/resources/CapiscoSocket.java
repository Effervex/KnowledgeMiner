/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;

/**
 * This class connects to and is a wrapper for various Capisco methods.
 * 
 * @author Sam Sarjant
 */
public class CapiscoSocket extends WikipediaSocket {
	private static final Pattern LABELS_PATTERN = Pattern
			.compile("(\\d+),(.+)");

	private static final Pattern REVERSE_ABBREVIATION_PATTERN = Pattern
			.compile("\\.[a-z]{0,4}[A-Z].*");

	private static final Pattern SENTENCE_END = Pattern
			.compile("\\.((')|\")?\\s*(?=[^a-z]|$).*");

	protected static final String PARSER_ID_COLLECTION = "IDCollection";

	protected static final String PARSER_SENSES = "Senses";

	protected static final String PARSER_SENTENCE = "Sentence";

	protected static final String PARSER_SYNONYMS = "Synonyms";

	protected static final String PARSER_TOPICS = "Topics";

	/** The port number to connect to for Capisco. */
	public static final int CAPISCO_PORT = 3434;

	public CapiscoSocket(CapiscoAccess access) {
		super(access);
		try {
			command("set /env/singleline true", true);
			command("set /env/endmessage ", true);
			command("set /env/prompt ", true);
			command("set /env/time false", true);
			command("set /env/database wikipedia", true);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Register the parsing methods
		registerParsingMethod(PARSER_SENTENCE, new SentenceParser());
		registerParsingMethod(PARSER_ID_COLLECTION, new IDCollectionParser());
		registerParsingMethod(PARSER_SYNONYMS, new SynCountsParser());
		registerParsingMethod(PARSER_SENSES, new SensesCountsParser());
		registerParsingMethod(PARSER_TOPICS, new TopicsParser());
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
			String commandResult = command(command + " " + argument, true);
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
			String commandResult = command(command + " " + argument, true);
			result = (A) cacheResult(command, argument, commandResult,
					parsingMethod, subCommand);
		}
		return result;
	}

	@Override
	protected int getPort() {
		return CAPISCO_PORT;
	}

	@Override
	public String annotate(String text, double minWeight, boolean withWeight,
			Collection<Integer> context) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(DELIMITER + "|");
		if (context != null)
			sb.append(StringUtils.join(context, '|'));
		sb.append("\n" + text + "\n" + DELIMITER);
		return (String) command("annotate", sb.toString(),
				getParsingMethod(PARSER_STRING));
	}

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

	@SuppressWarnings("unchecked")
	@Override
	public List<Integer> getArticleByTitle(String... titles) throws IOException {
		return (List<Integer>) batchCommand("artid", titles,
				getParsingMethod(PARSER_INTEGER));
	}

	@Override
	public List<String> getArtTitle(boolean withScope, Integer... articleIDs)
			throws IOException {
		String subcommand = (withScope) ? "fulltitle" : "shorttitle";
		return batchCommand("artname", articleIDs, getParsingMethod("Title"),
				subcommand, String.class);
	}

	@Override
	public String getArtTitleContext(int articleID) throws IOException {
		return command("artname", articleID + "",
				getParsingMethod("PageDetails"), "titlecontext", String.class);
	}

	@Override
	public int getEquivalentArticle(int categoryID) {
		return -1;
	}

	@Override
	public int getEquivalentCategory(int articleID) {
		return -1;
	}

	@Override
	public String getFirstParagraph(int articleID) throws IOException {
		return (String) command("artdesc", "" + articleID,
				getParsingMethod("String"));
	}

	@Override
	public String getFirstSentence(int articleID) throws IOException {
		return (String) command("artdesc", "" + articleID,
				getParsingMethod(PARSER_SENTENCE));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Integer> getInLinks(int articleID) throws IOException {
		return (Collection<Integer>) command("inlinks", "" + articleID,
				getParsingMethod(PARSER_ID_COLLECTION));
	}

	@SuppressWarnings("unchecked")
	@Override
	public WeightedSet<String> getLabels(int articleID) throws IOException {
		return (WeightedSet<String>) command("syncounts", articleID + "",
				getParsingMethod(PARSER_SYNONYMS));
	}

	@Override
	public String getMarkup(int articleID) throws IOException {
		// TODO Damn. Lost functionality here. List Miner is dead
		return "";
	}

	@SuppressWarnings("unchecked")
	@Override
	public int getMostLikelyArticle(String term) throws IOException {
		Collection<Integer> ids = (Collection<Integer>) command("senCounts",
				term, getParsingMethod(PARSER_SENSES));
		if (ids.isEmpty())
			return -1;
		return ids.iterator().next();
	}

	@Override
	public int getNextArticle(int id) throws IOException {
		return (int) command("next", id + "", getParsingMethod(PARSER_INTEGER));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Integer> getOutLinks(int articleID) throws IOException {
		return (Collection<Integer>) command("outlinks", "" + articleID,
				getParsingMethod(PARSER_ID_COLLECTION));
	}

	public List<String> getPageType(Integer... pageIDs) throws IOException {
		List<String> types = new ArrayList<>();
		// No categories or redirects here
		for (Integer pageID : pageIDs) {
			if (DBPediaAccess.askQuery("?art dbo:wikiPageID " + pageID,
					"?art dbo:wikiPageDisambiguates ?x"))
				types.add(TYPE_DISAMBIGUATION);
			else
				types.add(TYPE_ARTICLE);
		}
		return types;
	}

	@Override
	public int getPrevArticle(int id) throws IOException {
		return (int) command("prev", id + "", getParsingMethod(PARSER_INTEGER));
	}

	@Override
	public int getRedirect(int articleID) throws IOException {
		// No redirect information kept
		return -1;
	}

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
	}

	@SuppressWarnings("unchecked")
	@Override
	public WeightedSet<Integer> getTopics(String text,
			Collection<Integer> context) throws IOException {
		String noBrackets = WikiParser.cleanAllMarkup(text);
		if (context == null) {
			return (WeightedSet<Integer>) command("topics", DELIMITER + "\n"
					+ noBrackets + "\n" + DELIMITER,
					getParsingMethod(PARSER_TOPICS));
		} else {
			// Run dc
			StringBuilder sb = new StringBuilder();
			sb.append(DELIMITER + "|");
			if (context != null)
				sb.append(StringUtils.join(context, '|'));
			sb.append("\n" + noBrackets + "\n" + DELIMITER);
			return (WeightedSet<Integer>) command("dc", sb.toString(),
					getParsingMethod(PARSER_TOPICS));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<WeightedSet<Integer>> getWeightedArticles(String... terms)
			throws IOException {
		return (List<WeightedSet<Integer>>) batchCommand("senCounts", terms,
				getParsingMethod(PARSER_SENSES));
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
	private final class SensesCountsParser extends
			WikipediaMethod<WeightedSet<Integer>> {
		@Override
		public WeightedSet<Integer> parseResult(String result, String source) {
			// Returns a variably sized list of elements, where each
			// element includes an integer id, a weight, and a title
			String[] split = result.split("\\|");
			Integer numResults = Integer.parseInt(split[0]);
			if (numResults <= 0)
				return new WeightedSet<Integer>(0);
			WeightedSet<Integer> articles = new WeightedSet<>(numResults);
			for (int i = 1; i <= numResults; i++) {
				Matcher m = LABELS_PATTERN.matcher(split[i]);
				if (m.find()) {
					int occurrences = Integer.parseInt(m.group(1));
					int article = Integer.parseInt(m.group(2));
					articles.add(article, occurrences);
				}
			}
			return articles;
		}
	}

	/**
	 * A parser for extracting the first sentence from a paragraph of text.
	 *
	 * @author Sam Sarjant
	 */
	private final class SentenceParser extends WikipediaMethod<String> {
		@Override
		public String parseResult(String paragraph, String source) {
			// Parse the first sentence and return.
			StringBuilder firstSentence = new StringBuilder();
			boolean satisfied = false;
			int startPoint = 0;

			try {
				while (!satisfied) {
					satisfied = false;
					String result = null;
					// Find the first occurrence of a '.'
					result = WikiParser.first(paragraph, startPoint,
							new String[] { "." }, new String[] { "\n\n" });
					firstSentence.append(result);

					// Determine if this full stop is the actual end of sentence
					int periodIndex = firstSentence.length() - 1;
					startPoint = periodIndex + 1;
					if (startPoint == paragraph.length()
							&& paragraph.contains(".")) {
						return paragraph.replaceAll(" ?\n", " ").trim();
					}
					int endPoint = Math
							.min(paragraph.length(), periodIndex + 5);
					// If no space (except punctuation), continue
					String substring = paragraph.substring(periodIndex,
							endPoint);
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
	}

	/**
	 * Parses the labels of an article and their counts into a weightedset.
	 *
	 * @author Sam Sarjant
	 */
	private final class SynCountsParser extends
			WikipediaMethod<WeightedSet<String>> {
		@Override
		public WeightedSet<String> cloneResult(WeightedSet<String> result) {
			return new WeightedSet<>(result);
		}

		@Override
		public WeightedSet<String> parseResult(String result, String source) {
			// Returns a variably sized list of elements, where each
			// element includes an integer id, a weight, and a title
			String[] split = result.split("\\|");
			Integer numResults = Integer.parseInt(split[0]);
			if (numResults <= 0)
				return new WeightedSet<String>(0);
			WeightedSet<String> labels = new WeightedSet<>(numResults);
			for (int i = 1; i <= numResults; i++) {
				Matcher m = LABELS_PATTERN.matcher(split[i]);
				if (m.find()) {
					int occurrences = Integer.parseInt(m.group(1));
					String label = m.group(2);
					labels.add(label, occurrences);
				}
			}
			return labels;
		}
	}

	/**
	 * Parses topics from the output of topics/dc command.
	 *
	 * @author Sam Sarjant
	 */
	private final class TopicsParser extends
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

				topics.add(Integer.parseInt(split[6]),
						Double.parseDouble(split[4]));
			}

			topics.normaliseWeightTo1();
			return topics;
		}
	}
}
