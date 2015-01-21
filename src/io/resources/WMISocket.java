/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.resources;

import graph.module.NLPToSyntaxModule;
import io.KMAccess;
import io.KMSocket;
import io.ResourceAccess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import util.IllegalDelimiterException;
import util.Pair;
import util.WeightComparator;
import util.collection.WeightedSet;
import util.wikipedia.InfoboxData;
import util.wikipedia.WikiAnnotation;
import util.wikipedia.WikiParser;

/**
 * This class connects to and is a wrapper for various WMI methods.
 * 
 * @author Sam Sarjant
 */
public class WMISocket extends KMSocket {
	private static final Pattern REVERSE_ABBREVIATION_PATTERN = Pattern
			.compile("\\.[a-z]{0,4}[A-Z].*");

	private static final Pattern SENTENCE_END = Pattern
			.compile("\\.((')|\")?\\s*(?=[^a-z]|$).*");

	/** The pattern for separating page title context. */
	private static final Pattern CONTEXT_PATTERN = Pattern
			.compile("(.+) \\((.+)\\)$");

	private static final Pattern FORCE_PARSE_PATTERN = Pattern
			.compile("(.+?)\\W+(\\w+)\\s*=");

	private static final Pattern LABELS_PATTERN = Pattern
			.compile("(\\d+),(\\d+),(.+)");

	/** The name of the machine that WMI is stored on. */
	private static final String MACHINE_NAME = "wmi";

	/** The pattern for parsing a senses element. */
	private static final Pattern SENSES_PATTERN = Pattern
			.compile("(\\d+),([\\d.E-]+),(.+)");

	/** The delimiter string to cease topic parsing. */
	private static final String TOPIC_DELIMITER = "!Y^e#";
	private static final String TOPIC_DELIMITER_PATTERN = Pattern
			.quote(TOPIC_DELIMITER);

	/** The port number to connect to for WMI. */
	public static final int WMI_PORT = 2424;

	/** The marker for starting a new infobox. */
	public static final Pattern INFOBOX_STARTER = Pattern.compile(
			"\\{\\{\\s*(?:(?:(?:Infobox)([\\w -]*))|(\\w+obox))",
			Pattern.CASE_INSENSITIVE);

	/** The message end delimiter. */
	public static final String MESSAGE_END = "-END-";

	/** If an infobox has no type. */
	public static final String NO_TYPE = "xnotypex";

	public static final String TYPE_DISAMBIGUATION = "disambiguation";

	public static final String TYPE_ARTICLE = "article";

	public static final String TYPE_CATEGORY = "category";

	public static final String TYPE_REDIRECT = "redirect";

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
	}

	/**
	 * Performs a batch command of the same command with a number of arguments.
	 * Performs caching operations as well.
	 * 
	 * @param <T>
	 *            The output form of the parsing.
	 * @param command
	 *            The command to run.
	 * @param arguments
	 *            The arguments for the command.
	 * @param parsingMethod
	 *            The method to parse the result into the appropriate form.
	 * @return A list of results, in the provided class format.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	private <T> List<T> batchCommand(String command, Object[] arguments,
			WMIMethod<T> parsingMethod) throws IOException {
		List<T> results = new ArrayList<>(arguments.length);

		// Compile the batch command, only using uncached arguments
		String batchCommand = compileBatchCommand(command, null, arguments,
				results, parsingMethod);

		// Run the command
		if (batchCommand != null) {
			String batchResult = command(batchCommand.toString(), true);

			// Cache and return the results.
			int i = 0;
			for (String result : batchResult.split(TOPIC_DELIMITER_PATTERN)) {
				while (results.get(i) != null)
					i++;
				T value = (T) cacheResult(command, arguments[i].toString(),
						result.trim(), parsingMethod, null);
				results.set(i, value);
			}
		}
		return results;
	}

	/**
	 * Performs a batch command of the same command with a number of arguments
	 * and returns sub-information from those commands. Performs caching
	 * operations as well.
	 * 
	 * @param <T>
	 *            The output form of the parsing.
	 * @param <A>
	 *            The sub-information class.
	 * @param command
	 *            The command to run.
	 * @param arguments
	 *            The arguments for the command.
	 * @param parsingMethod
	 *            The method to parse the result into the appropriate form.
	 * @param subCommand
	 *            The sub-information command to cache the sub-information.
	 * @param clazz
	 *            The class of the sub-information.
	 * @return A list of results, in the provided class format.
	 * @throws IOException
	 *             Should something go awry...
	 */
	@SuppressWarnings("unchecked")
	private <T, A> List<A> batchCommand(String command, Object[] arguments,
			WMIMethod<T> parsingMethod, String subCommand, Class<A> clazz)
			throws IOException {
		List<A> results = new ArrayList<>(arguments.length);

		// Compile the batch command, only using uncached arguments
		String batchCommand = compileBatchCommand(command, subCommand,
				arguments, results, null);

		// Run the command
		if (batchCommand != null) {
			String batchResult = command(batchCommand.toString(), true);

			// Cache and return the results.
			int i = 0;
			for (String result : batchResult.split(TOPIC_DELIMITER_PATTERN)) {
				while (results.get(i) != null)
					i++;

				A value = (A) cacheResult(command, arguments[i].toString(),
						result.trim(), parsingMethod, subCommand);
				results.set(i, value);
			}
		}
		return results;
	}

	/**
	 * Parses and caches the result of a single command.
	 * 
	 * @param command
	 *            The command name that was run.
	 * @param argument
	 *            The argument of the command.
	 * @param commandResult
	 *            The result of the command.
	 * @param parsingMethod
	 *            The parsing method for converting the result.
	 * @param subCommand
	 *            An optional sub-command for a subresult.
	 */
	private <T> Object cacheResult(String command, String argument,
			String commandResult, WMIMethod<T> parsingMethod, String subCommand) {
		Object result = null;
		try {
			if (subCommand != null) {
				Map<String, Object> results = parsingMethod.parseSubResults(
						commandResult, argument);
				result = results.get(subCommand);
				for (String key : results.keySet()) {
					if (key.equals(WMIMethod.DEFAULT))
						access_.cacheCommand(command, argument,
								results.get(key));
					else
						access_.cacheCommand(key, argument, results.get(key));
				}
			} else {
				result = parsingMethod.parseResult(commandResult, argument);
				access_.cacheCommand(command, argument, result);
			}
		} catch (Exception e) {
			e.printStackTrace();
			LoggerFactory.getLogger(this.getClass()).error(
					"EXCEPTION\tPerforming command: {} {} with result {}",
					command, argument, commandResult);
		}
		return result;
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
			WMIMethod<T> parsingMethod) throws IOException {
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
			if (command.equals("markup")) {
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

			if (command.equals("markup")) {
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
			WMIMethod<T> parsingMethod, String subCommand, Class<A> clazz)
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

	/**
	 * Compiles the batch command by checking for cached results.
	 * 
	 * @param <T>
	 *            The type of the results.
	 * @param command
	 *            The command to batch.
	 * @param subCommand
	 *            The possibly null sub command for caching results.
	 * @param arguments
	 *            The arguments of the batch command.
	 * @param results
	 *            The results list.
	 * @return The batch command to use or null if all the results are already
	 *         cached.
	 */
	@SuppressWarnings("unchecked")
	private <T> String compileBatchCommand(String command, String subCommand,
			Object[] arguments, List<T> results, WMIMethod<T> parsingMethod) {
		StringBuilder batchCommand = new StringBuilder("batch " + command + " "
				+ TOPIC_DELIMITER + "\n");

		String cacheCommand = (subCommand == null) ? command : subCommand;

		boolean allCached = true;
		for (int i = 0; i < arguments.length; i++) {
			T cachedResult = null;
			if (parsingMethod != null) {
				try {
					cachedResult = (T) access_.getCachedCommand(cacheCommand,
							arguments[i].toString());
					cachedResult = parsingMethod.cloneResult(cachedResult);
				} catch (Exception e) {
					cachedResult = null;
				}
			}
			if (cachedResult != null && parsingMethod != null) {
				results.add(cachedResult);
			} else {
				batchCommand.append(arguments[i] + "\n");
				allCached = false;
				results.add(null);
			}
		}
		batchCommand.append(TOPIC_DELIMITER);

		if (allCached)
			return null;
		else
			return batchCommand.toString();
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
	public String annotate(String text, double minWeight, boolean withWeight)
			throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(minWeight + " ");
		if (withWeight)
			sb.append("T ");
		else
			sb.append("F ");
		sb.append(TOPIC_DELIMITER + "\n" + text + "\n" + TOPIC_DELIMITER);
		return command("annotate", sb.toString(), new AnnotateParser());
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
	public List<Integer> getArticleByTitle(String... titles) throws IOException {
		return batchCommand("art", titles, new IntegerParser());
	}

	/**
	 * A singular method for getArticleByTitle, as it is commonly used in
	 * singular form.
	 * 
	 * @param title
	 *            The name of the method.
	 * @return The article ID.
	 * @throws IOException
	 */
	public int getArticleByTitle(String title) throws IOException {
		return singular(getArticleByTitle(new String[] { title }));
	}

	/**
	 * Gets the categories associated with the given article.
	 * 
	 * @param articleID
	 *            The article for which we get the associated categories.
	 * @return The categories associated.
	 * @throws IOException
	 */
	public Collection<Integer> getArticleCategories(int articleID)
			throws IOException {
		return command("categories", articleID + "", new IDCollectionParser());
	}

	/**
	 * Gets a category by title (via the "category" command).
	 * 
	 * @param title
	 *            The title of the category.
	 * @return The integer identifier of the category or -1 if no match found.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public int getCategoryByTitle(String title) throws IOException {
		return command("category", title, new IntegerParser());
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
	public List<Collection<Integer>> getChildArticles(Integer... categoryIDs)
			throws IOException {
		return batchCommand("childart", categoryIDs, new IDCollectionParser());
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
	public List<Collection<Integer>> getChildCategories(Integer... categoryIDs)
			throws IOException {
		return batchCommand("children", categoryIDs, new IDCollectionParser());
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
	public List<Collection<Integer>> getParentCategories(Integer... categoryIDs)
			throws IOException {
		return batchCommand("parents", categoryIDs, new IDCollectionParser());
	}

	public int getEquivalentCategory(int articleID) {
		// This requires backtracing from categories to their equivalent
		// articles (can be more than one)
		return -1;
	}

	public int getNextArticle(int id) throws IOException {
		String result = command("next " + id, true);
		return Integer.parseInt(result.split("\\|")[0]);
	}

	public int getPrevArticle(int id) throws IOException {
		String result = command("prev " + id, true);
		return Integer.parseInt(result.split("\\|")[0]);
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
	public String getFirstSentence(int articleID) throws IOException {
		return command("markup", articleID + "", new MarkupParser(), "first",
				String.class);
	}

	public String getFirstParagraph(int articleID) throws IOException {
		return command("markup", articleID + "", new MarkupParser(),
				"paragraph", String.class);
	}

	/**
	 * Gets the fundamental root category (via the "root" command).
	 * 
	 * @return The fundamental root category id.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public int getFundamentalCategory() throws IOException {
		return command("root", "", new IntegerParser());
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
	@SuppressWarnings("unchecked")
	public List<InfoboxData> getInfoboxData(int articleID) throws IOException {
		return command("markup", articleID + "", new MarkupParser(),
				"infoboxdata", List.class);
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
	public Collection<Integer> getInLinks(int articleID) throws IOException {
		return command("inlinks", articleID + "", new IDCollectionParser());
	}

	/**
	 * Gets a batch of inlinks.
	 * 
	 * @param articles
	 *            The articles to get inlinks for.
	 * @return A list of all the collections of articles for each input article.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public List<Collection<Integer>> getInLinks(Integer... articles)
			throws IOException {
		return batchCommand("inlinks", articles, new IDCollectionParser());
	}

	/**
	 * Gets the number of incoming and outgoing links to batches of articles.
	 * 
	 * @param articles
	 *            The articles to get number of links for.
	 * @return A list of integer arrays representing the number of
	 *         distinct/total incoming/outgoing links.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public List<Integer[]> getNumLinks(Integer... articles) throws IOException {
		return batchCommand("numlinks", articles, new WMIMethod<Integer[]>() {

			@Override
			public Integer[] parseResult(String result, String source) {
				Integer[] numLinks = new Integer[4];
				String[] split = result.split(",");
				for (int i = 0; i < split.length; i++)
					numLinks[i] = Integer.parseInt(split[i]);
				return numLinks;
			}

			@Override
			public Integer[] cloneResult(Integer[] result) {
				return Arrays.copyOf(result, result.length);
			}
		});
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
	public WeightedSet<String> getLabels(int articleID) throws IOException {
		return command("labelcounts", articleID + "",
				new WMIMethod<WeightedSet<String>>() {
					@Override
					public WeightedSet<String> parseResult(String result,
							String source) {
						// Returns a variably sized list of elements, where each
						// element includes an integer id, a weight, and a title
						String[] split = result.split("\\|");
						String[] counts = split[0].split(",");
						if (counts[0].equals("-1"))
							return new WeightedSet<String>(0);
						int numResults = Integer.parseInt(counts[2]);
						WeightedSet<String> labels = new WeightedSet<>(
								numResults);
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
				});
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
	public int getMostLikelyArticle(String term) throws IOException {
		return command("mostlikely", term, new IntegerParser());
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
	public Collection<Integer> getOutLinks(int articleID) throws IOException {
		return command("outlinks", articleID + "", new IDCollectionParser());
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
	public List<String> getPageTitle(boolean withScope, Integer... articleIDs)
			throws IOException {
		String subcommand = (withScope) ? "fulltitle" : "shorttitle";
		return batchCommand("page", articleIDs, new PageDetailsParser(),
				subcommand, String.class);
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
	public String getPageTitle(Integer articleID, boolean withScope)
			throws IOException {
		return singular(getPageTitle(withScope, articleID));
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
	public String getPageTitleContext(int articleID) throws IOException {
		return command("page", articleID + "", new PageDetailsParser(),
				"titlecontext", String.class);
	}

	public String getPageType(int pageID) throws IOException {
		return singular(getPageType(new Integer[] { pageID }));
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
	public List<String> getPageType(Integer... pageIDs) throws IOException {
		return batchCommand("page", pageIDs, new PageDetailsParser(),
				"pagetype", String.class);
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
	public int getRedirect(int articleID) throws IOException {
		return command("redirect", articleID + "", new IntegerParser());
	}

	/**
	 * Singular form of getRelatednessList.
	 * 
	 * @param baseArticle
	 *            The base article to get.
	 * @param comparisonArticles
	 *            The comparison articles.
	 * @return The relatedness list for the base article to the comparison
	 *         articles.
	 * @throws IOException
	 *             Should soemthing go awry...
	 */
	public List<Double> getRelatednessList(Integer baseArticle,
			Integer... comparisonArticles) throws IOException {
		return singular(getRelatednessList(new Integer[] { baseArticle },
				comparisonArticles));
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

		List<Double> result = batchCommand("relatedness", arguments,
				new DoubleParser());

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

		return batchCommand("relatedness", arguments, new DoubleParser());
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
	 * @return A {@link WeightedSet} of topics, where each topic is represented
	 *         by a pair (text, article).
	 * @throws IOException
	 *             Should something go awry...
	 */
	public SortedSet<WikiAnnotation> getTopics(String text) throws IOException {
		String noBrackets = WikiParser.replaceBracketedByWhitespace(text, '{',
				'}');
		noBrackets = WikiParser.replaceBracketedByWhitespace(noBrackets, '<',
				'>');
		noBrackets = WikiParser.replaceBracketedByWhitespace(noBrackets, '[',
				']');
		return command("topics", TOPIC_DELIMITER + "\n" + noBrackets + "\n"
				+ TOPIC_DELIMITER, new TopicParser());
	}

	/**
	 * Gets a sorted collection of articles with corresponding weights ordered
	 * by their level of relatedness to the term provided (via the "senses"
	 * command).
	 * 
	 * @param relatedArticles
	 *            The context articles to use for computing a weighted
	 *            relatedness measure.
	 * @param cutoffThreshold
	 *            The threshold at which mappings are not added.
	 * @param term
	 *            The term to spawn the weighted articles.
	 * 
	 * @return A contextually-weighted set of of articles referenced by their
	 *         id.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public List<WeightedSet<Integer>> getWeightedArticles(
			Collection<Integer> relatedArticles, double cutoffThreshold,
			String... terms) throws IOException {
		List<WeightedSet<Integer>> contextWeightedArts = new ArrayList<>();
		List<WeightedSet<Integer>> weightedArts = getWeightedArticles(terms);

		// Compile the input array
		SortedSet<Integer> articles = new TreeSet<>();
		for (WeightedSet<Integer> weighted : weightedArts) {
			weighted.normaliseWeightTo1(cutoffThreshold);
			articles.addAll(weighted);
		}
		Integer[] inputArray = articles.toArray(new Integer[articles.size()]);
		Integer[] comparisonArray = relatedArticles
				.toArray(new Integer[relatedArticles.size()]);

		// Perform the single batch command
		List<List<Double>> relatedArts = getRelatednessList(inputArray,
				comparisonArray);

		// Update the weighted set list
		for (WeightedSet<Integer> weightedSet : weightedArts) {
			if (weightedSet.size() <= 1) {
				contextWeightedArts.add(weightedSet);
				continue;
			}

			// Create and fill the updated weights
			WeightedSet<Integer> contextWeighted = new WeightedSet<>(
					weightedSet.size());
			for (Integer artID : weightedSet) {
				double relatedness = 0;
				double obviousness = weightedSet.getWeight(artID);

				int index = Arrays.binarySearch(inputArray, artID);
				List<Double> weights = relatedArts.get(index);
				for (double r : weights)
					relatedness += r;

				double contextWeight = obviousness + relatedness;
				contextWeighted.set(artID, contextWeight);
			}
			contextWeightedArts.add(contextWeighted);
		}
		return contextWeightedArts;
	}

	/**
	 * Gets a sorted collection of articles with corresponding weights ordered
	 * by their level of relatedness to the term provided (via the "senses"
	 * command).
	 * 
	 * @param term
	 *            The term to spawn the weighted articles.
	 * @return A weighted set of of articles referenced by their id.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public WeightedSet<Integer> getWeightedArticles(String term)
			throws IOException {
		return singular(getWeightedArticles(new String[] { term }));
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
	public List<WeightedSet<Integer>> getWeightedArticles(String... terms)
			throws IOException {
		return batchCommand("senses", terms, new SensesParser());
	}

	public WeightedSet<Integer> getWeightedArticles(String term,
			double cutoffThreshold, Collection<Integer> relatedArticles)
			throws IOException {
		return singular(getWeightedArticles(relatedArticles, cutoffThreshold,
				new String[] { term }));
	}

	/**
	 * Checks if a given article has a context associated with it (contained
	 * within brackets).
	 * 
	 * @param articleID
	 *            The article to check.
	 * @return True if the article has context.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public boolean hasTitleContext(int articleID) throws IOException {
		return !getPageTitleContext(articleID).isEmpty();
	}

	/**
	 * Returns a singular value from a list of things (which should only contain
	 * a single element).
	 * 
	 * @param list
	 *            The list of things, which should only contain a single
	 *            element.
	 * @return The single element, or null if the list is empty.
	 * @throws IllegalArgumentException
	 *             If the list has more than one element.
	 */
	public static <T> T singular(List<T> list) {
		if (list.isEmpty())
			return null;
		if (list.size() > 1)
			throw new IllegalArgumentException(
					"List should only contain a single element!");
		return list.get(0);
	}

	/**
	 * Combines a list of collections of things into a single set of things.
	 * 
	 * @param collectionList
	 *            The list of collection of things.
	 * @return A set of all things present in the collectionList.
	 */
	public static <T> Set<T> union(List<Collection<T>> collectionList) {
		Set<T> union = new HashSet<>();
		for (Collection<T> collection : collectionList)
			union.addAll(collection);
		return union;
	}

	/**
	 * Parses a double value from a String.
	 * 
	 * @author Sam Sarjant
	 */
	private final class AnnotateParser extends WMIMethod<String> {
		@Override
		public String parseResult(String result, String source) {
			return result;
		}
	}

	/**
	 * Parses a double value from a String.
	 * 
	 * @author Sam Sarjant
	 */
	private final class DoubleParser extends WMIMethod<Double> {
		@Override
		public Double parseResult(String result, String source) {
			try {
				return Double.parseDouble(result);
			} catch (NumberFormatException e) {
				return 0d;
			}
		}
	}

	private final class TopicParser extends
			WMIMethod<SortedSet<WikiAnnotation>> {
		@Override
		public SortedSet<WikiAnnotation> parseResult(String result,
				String source) {
			SortedSet<WikiAnnotation> topics = new TreeSet<>(
					new WeightComparator<WikiAnnotation>());

			String[] topicSplit = result.split(TOPIC_DELIMITER_PATTERN);
			for (String topic : topicSplit) {
				topic = topic.trim();
				if (topic.isEmpty())
					continue;
				String[] split = topic.split("\\|");

				// Extract each topic
				for (int i = 3; i < split.length; i++)
					topics.add(new WikiAnnotation(split[i], split[1], Double
							.parseDouble(split[2]), WMISocket.this));
			}

			return topics;
		}

		@Override
		public SortedSet<WikiAnnotation> cloneResult(
				SortedSet<WikiAnnotation> result) {
			return new TreeSet<>(result);
		}
	}

	/**
	 * Parses a variably sized collection of IDs.
	 * 
	 * @author Sam Sarjant
	 */
	private final class IDCollectionParser extends
			WMIMethod<Collection<Integer>> {
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

		@Override
		public Collection<Integer> cloneResult(Collection<Integer> result) {
			return new ArrayList<Integer>(result);
		}
	}

	/**
	 * Parses singular ID strings (e.g. integers).
	 * 
	 * @author Sam Sarjant
	 */
	private final class IntegerParser extends WMIMethod<Integer> {
		@Override
		public Integer parseResult(String result, String source) {
			try {
				return Integer.parseInt(result.split("\\|")[0]);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
	}

	/**
	 * Parses the markup into subinformation.
	 * 
	 * @author Sam Sarjant
	 */
	private final class MarkupParser extends WMIMethod<String> {

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

		@Override
		public String parseResult(String result, String source) {
			return result;
		}
	}

	/**
	 * Parses the page details into subinformation.
	 * 
	 * @author Sam Sarjant
	 */
	private final class PageDetailsParser extends WMIMethod<String> {
		@Override
		public Map<String, Object> parseSubResults(String result, String source) {
			Map<String, Object> subResults = new HashMap<>();
			String[] pageSplit = result.split("\\|");

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

		@Override
		public String parseResult(String result, String source) {
			return result;
		}
	}

	/**
	 * Parses a double value from a String.
	 * 
	 * @author Sam Sarjant
	 */
	private final class SensesParser extends WMIMethod<WeightedSet<Integer>> {
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

		@Override
		public WeightedSet<Integer> cloneResult(WeightedSet<Integer> result) {
			WeightedSet<Integer> clonedSenses = new WeightedSet<>();
			clonedSenses.addAll(result);
			return clonedSenses;
		}
	}

	/**
	 * An abstract shell for invoking a parsing method.
	 * 
	 * @author Sam Sarjant
	 */
	private abstract class WMIMethod<T> {
		protected static final String DEFAULT = "DEFAULT";

		/**
		 * Parses the result String of a command.
		 * 
		 * @param result
		 *            The result String.
		 * @param source
		 *            The argument of the command.
		 * @return The parsed object of the result.
		 */
		public abstract T parseResult(String result, String source);

		/**
		 * Parses a collection of subresults (mapped by command) from a single
		 * result.
		 * 
		 * @param result
		 *            The result String.
		 * @param source
		 *            The argument of the command.
		 * @return The parsed sub-result mapping, where the primary result is
		 *         keyed under DEFAULT.
		 */
		public Map<String, Object> parseSubResults(String result, String source) {
			Map<String, Object> defaultResult = new HashMap<>();
			defaultResult.put(DEFAULT, parseResult(result, source));
			return defaultResult;
		}

		/**
		 * Clones the result. Used when the result has been cached and a new
		 * version is required. Defaults to returning the result. Should be
		 * overridden when result is non-primitive.
		 * 
		 * @param result
		 *            The result to clone.
		 * @return A cloned version of the result. Should be equals() to result.
		 */
		public T cloneResult(T result) {
			return result;
		}
	}

	@Override
	protected int getPort() {
		return WMI_PORT;
	}

	@Override
	protected String getMachineName() {
		if (KMAccess.isOnWMI())
			return MACHINE_NAME;
		else
			return LOCALHOST;
	}

	public static String getArticleURL(String articleTitle) {
		if (articleTitle == null)
			return null;
		String title = NLPToSyntaxModule.convertToAscii(articleTitle)
				.replaceAll(" ", "_");
		return "http://en.wikipedia.org/wiki/" + title;
	}

	public static String getArticleURL(int article) {
		try {
			return getArticleURL(ResourceAccess.requestWMISocket()
					.getPageTitle(article, true));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Gets all sub categories for this page. If the page is an article, get the
	 * subcategories of the page's categories. If an article, just get the sub
	 * categories of the category.
	 *
	 * @param pageID
	 *            The ID of the page to get sub categories for.
	 * @return All sub categories for the page.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public Collection<Integer> getPageSubCategories(int pageID)
			throws IOException {
		return getRecursiveCategories(pageID, true);
	}

	@SuppressWarnings("unchecked")
	private Collection<Integer> getRecursiveCategories(int pageID,
			boolean subCategories) throws IOException {
		if (pageID == -1)
			return Collections.EMPTY_LIST;
		// Is article or category?
		Collection<Integer> currentCategories = new HashSet<>();
		String type = getPageType(pageID);
		if (type.equals(TYPE_ARTICLE))
			currentCategories.addAll(getArticleCategories(pageID));
		else if (type.equals(TYPE_CATEGORY))
			currentCategories.add(pageID);
		else
			return Collections.EMPTY_LIST;

		Collection<Integer> totalCategories = new HashSet<>();
		// Recurse through children
		do {
			totalCategories.addAll(currentCategories);
			Collection<Integer> recurseCats = null;
			if (subCategories)
				recurseCats = union(getChildCategories(currentCategories
						.toArray(new Integer[currentCategories.size()])));
			else
				recurseCats = union(getParentCategories(currentCategories
						.toArray(new Integer[currentCategories.size()])));
			recurseCats.removeAll(totalCategories);
			currentCategories = recurseCats;
		} while (!currentCategories.isEmpty());
		return totalCategories;
	}

	/**
	 * Gets all super categories for this page. If the page is an article, get
	 * the supercategories of the page's categories. If an article, just get the
	 * super categories of the category.
	 *
	 * @param pageID
	 *            The ID of the page to get super categories for.
	 * @return All super categories for the page.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public Collection<Integer> getPageSuperCategories(int pageID)
			throws IOException {
		return getRecursiveCategories(pageID, false);
	}
}
