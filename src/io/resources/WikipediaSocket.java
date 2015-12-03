/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.resources;

import gnu.trove.map.TMap;
import gnu.trove.map.hash.THashMap;
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
import java.util.regex.Pattern;

import knowledgeMiner.mining.wikipedia.InfoboxTypeMiner;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.InfoboxData;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;

/**
 * This abstract class connects to and is a wrapper for various
 * Wikipedia-interface methods.
 * 
 * @author Sam Sarjant
 */
public abstract class WikipediaSocket extends KMSocket {
	/** The name of the machine that WMI is stored on. */
	private static final String MACHINE_NAME = "wmi";

	/** The delimiter string to cease topic parsing. */
	protected static final String DELIMITER = "!Y^e#";

	protected static final String DELIMITER_PATTERN = Pattern.quote(DELIMITER);

	protected static final String PARSER_DOUBLE = "Double";

	protected static final String PARSER_INTEGER = "Integer";

	protected static final String PARSER_STRING = "String";
	/** The code for creating DBPedia URLs. */
	public static final int DBPEDIA_URL = 1;

	/** The message end delimiter. */
	public static final String MESSAGE_END = "-END-";

	public static final String TYPE_ARTICLE = "article";

	public static final String TYPE_CATEGORY = "category";

	public static final String TYPE_DISAMBIGUATION = "disambiguation";

	public static final String TYPE_REDIRECT = "redirect";

	/** The code for creating Wikipedia URLs. */
	public static final int WIKIPEDIA_URL = 0;

	/** The collection of parsers used to interpret results. */
	private TMap<String, WikipediaMethod<?>> parsers_;

	public WikipediaSocket(WikipediaAccess access) {
		super(access);
		parsers_ = new THashMap<>();
		registerParsingMethod(PARSER_STRING, new StringParser());
		registerParsingMethod(PARSER_INTEGER, new IntegerParser());
		registerParsingMethod(PARSER_DOUBLE, new DoubleParser());
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
			Object[] arguments, List<T> results,
			WikipediaMethod<T> parsingMethod) {
		StringBuilder batchCommand = new StringBuilder("batch " + command + " "
				+ DELIMITER + "\n");

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
		batchCommand.append(DELIMITER);

		if (allCached)
			return null;
		else
			return batchCommand.toString();
	}

	@SuppressWarnings("unchecked")
	private Collection<Integer> getRecursiveCategories(int pageID,
			boolean subCategories) throws IOException {
		if (pageID == -1)
			return Collections.EMPTY_LIST;
		// Is article or category?
		Collection<Integer> currentCategories = new HashSet<>();
		String type = getPageType(pageID);
		if (type != null && type.equals(TYPE_ARTICLE))
			currentCategories.addAll(getArticleCategories(pageID));
		else if (type != null && type.equals(TYPE_CATEGORY))
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
	protected <T> List<T> batchCommand(String command, Object[] arguments,
			WikipediaMethod<T> parsingMethod) throws IOException {
		List<T> results = new ArrayList<>(arguments.length);

		// Compile the batch command, only using uncached arguments
		String batchCommand = compileBatchCommand(command, null, arguments,
				results, parsingMethod);

		// Run the command
		if (batchCommand != null) {
			String batchResult = command(batchCommand.toString(), true);

			// Cache and return the results.
			int i = 0;
			for (String result : batchResult.split(DELIMITER_PATTERN)) {
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
	protected <T, A> List<A> batchCommand(String command, Object[] arguments,
			WikipediaMethod<T> parsingMethod, String subCommand, Class<A> clazz)
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
			for (String result : batchResult.split(DELIMITER_PATTERN)) {
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
	protected <T> Object cacheResult(String command, String argument,
			String commandResult, WikipediaMethod<T> parsingMethod,
			String subCommand) {
		Object result = null;
		try {
			if (subCommand != null) {
				Map<String, Object> results = parsingMethod.parseSubResults(
						commandResult, argument);
				result = results.get(subCommand);
				for (Map.Entry<String, Object> entry : results.entrySet()) {
					if (entry.getKey().equals(WikipediaMethod.DEFAULT))
						access_.cacheCommand(command, argument,
								entry.getValue());
					else
						access_.cacheCommand(entry.getKey(), argument,
								entry.getValue());
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

	@Override
	protected String getMachineName() {
		if (KMAccess.isOnWMI())
			return MACHINE_NAME;
		else
			return LOCALHOST;
	}

	/**
	 * Returns the parsing method associated with the name.
	 *
	 * @param name
	 *            The name linked to a parsing method.
	 * @return The parsing method associated with name, or null if nothing
	 *         associated.
	 */
	protected final WikipediaMethod<?> getParsingMethod(String name) {
		return parsers_.get(name);
	}

	@Override
	protected abstract int getPort();

	/**
	 * Registers a parsing method under a given name.
	 *
	 * @param name
	 *            The name to register under.
	 * @param method
	 *            The method to register.
	 */
	protected final void registerParsingMethod(String name,
			WikipediaMethod<?> method) {
		parsers_.put(name, method);
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
	 * @param context
	 *            TODO
	 * @return The annotated text.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public abstract String annotate(String text, double minWeight,
			boolean withWeight, Collection<Integer> context) throws IOException;

	/**
	 * Sends a command string to the interface. It should be in a standard,
	 * recognisable format.
	 * 
	 * @param commandString
	 *            The string to send to the interface.
	 * @param singleline
	 *            If the command should only parse a single line (i.e.
	 *            /env/singleline is set to true).
	 * @return The result String of the command, or an empty string if the
	 *         command was not recognised.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public abstract String command(String commandString, boolean singleline)
			throws IOException;

	/**
	 * Gets an article by a title (via the "art" command).
	 * 
	 * @param title
	 *            The title of the article.
	 * @return The integer identifier of the article or -1 if no match found.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public abstract List<Integer> getArticleByTitle(String... titles)
			throws IOException;

	/**
	 * A singular method for getArticleByTitle, as it is commonly used in
	 * singular form.
	 * 
	 * @param title
	 *            The name of the method.
	 * @return The article ID.
	 * @throws IOException
	 */
	public final int getArticleByTitle(String title) throws IOException {
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
	public Collection<String> getArticleCategoriesNamed(int articleID)
			throws IOException {
		// DBPedia default
		Collection<Map<String, RDFNode>> results = DBPediaAccess.selectQuery(
				"?catID", "?art dbowl:wikiPageID " + articleID,
				"?art dct:subject ?cat", "?cat rdfs:label ?catName");

		Collection<String> names = new ArrayList<>();
		for (Map<String, RDFNode> vars : results) {
			Literal lit = vars.get("?catName").asLiteral();
			String catName = lit.getString();
			names.add(catName);
		}
		return names;
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
		// DBPedia default
		Collection<Map<String, RDFNode>> results = DBPediaAccess.selectQuery(
				"?catID", "?art dbowl:wikiPageID " + articleID,
				"?art dct:subject ?cat", "?cat dbowl:wikiPageID ?catID");

		Collection<Integer> ids = new ArrayList<>();
		for (Map<String, RDFNode> vars : results) {
			Literal lit = vars.get("?catID").asLiteral();
			int id = lit.getInt();
			ids.add(id);
		}
		return ids;
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
		List<Collection<Integer>> childArts = new ArrayList<>();
		for (int catID : categoryIDs) {
			Collection<Map<String, RDFNode>> results = DBPediaAccess
					.selectQuery("?artID", "?cat dbowl:wikiPageID " + catID,
							"?art dct:subject ?cat",
							"?art dbowl:wikiPageID ?artID");
			Collection<Integer> children = new ArrayList<>();
			for (Map<String, RDFNode> vars : results) {
				Literal lit = vars.get("?artID").asLiteral();
				int id = lit.getInt();
				children.add(id);
			}
			childArts.add(children);
		}
		return childArts;
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
		List<Collection<Integer>> childArts = new ArrayList<>();
		for (int catID : categoryIDs) {
			Collection<Map<String, RDFNode>> results = DBPediaAccess
					.selectQuery("?subCatID", "?cat dbowl:wikiPageID " + catID,
							"?subCat skos:broader ?cat",
							"?subCat dbowl:wikiPageID ?subCatID");
			Collection<Integer> children = new ArrayList<>();
			for (Map<String, RDFNode> vars : results) {
				Literal lit = vars.get("?subCatID").asLiteral();
				int id = lit.getInt();
				children.add(id);
			}
			childArts.add(children);
		}
		return childArts;
	}

	public abstract int getEquivalentCategory(int articleID);

	public abstract int getEquivalentArticle(int categoryID);

	public abstract String getFirstParagraph(int articleID) throws IOException;

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
	public abstract String getFirstSentence(int articleID) throws IOException;

	/**
	 * Gets the type of infobox associated with this page.
	 * 
	 * @param articleID
	 *            The article for which we need the first sentence.
	 * @return The infobox type used in the article, or null if no infobox.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public List<InfoboxData> getInfoboxData(int articleID) throws IOException {
		List<InfoboxData> data = new ArrayList<>();

		// Ask for all basic DBpedia relations
		Collection<Map<String, RDFNode>> results = DBPediaAccess.selectQuery(
				"?relName", "?value", "?valName", "?art dbowl:wikiPageID "
						+ articleID, "?art ?relation ?value",
				"FILTER regex(str(?relation), \""
						+ DBPediaNamespace.DBPEDIAPROP.getURI() + "\")",
				"?relation rdfs:label ?relName",
				"OPTIONAL { ?value rdfs:label ?valName",
				"FILTER langMatches(lang(?valName), \"en\") }");
		InfoboxData iData = new InfoboxData("notype");
		for (Map<String, RDFNode> vars : results) {
			String relation = vars.get("?relName").asLiteral().getString();
			String value = null;
			// Resolve the value
			RDFNode valNode = vars.get("?value");
			if (valNode.isResource() && vars.containsKey("?valName")) {
				String resName = vars.get("?valName").asLiteral().getString();
				value = "[[" + resName + "]]";
			} else if (valNode.isLiteral()) {
				value = valNode.asLiteral().getValue().toString();
			}
			iData.putRelation(relation, value);
		}
		data.add(iData);
		return data;
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
	public abstract Collection<Integer> getInLinks(int articleID)
			throws IOException;

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
	public abstract WeightedSet<String> getLabels(int articleID)
			throws IOException;

	/**
	 * Gets the markup of this article (via the "markup" command).
	 * 
	 * @param articleID
	 *            The article for which the markup is collected.
	 * @return The raw markup of the article.
	 * @throws IOException
	 */
	public abstract String getMarkup(int articleID) throws IOException;

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
	public abstract int getMostLikelyArticle(String term) throws IOException;

	public abstract int getNextArticle(int id) throws IOException;

	/**
	 * Get the list of articles that this article links to (via the "outlinks"
	 * command).
	 * 
	 * @param articleID
	 *            The article to get out links for.
	 * @return A collection of all articles this article links to.
	 * @throws IOException
	 */
	public abstract Collection<Integer> getOutLinks(int articleID)
			throws IOException;

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
	public final Collection<Integer> getPageSubCategories(int pageID)
			throws IOException {
		return getRecursiveCategories(pageID, true);
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
	public final Collection<Integer> getPageSuperCategories(int pageID)
			throws IOException {
		return getRecursiveCategories(pageID, false);
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
	public abstract List<String> getArtTitle(boolean withScope,
			Integer... articleIDs) throws IOException;

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
	public final String getArtTitle(Integer articleID, boolean withScope)
			throws IOException {
		return singular(getArtTitle(withScope, articleID));
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
	public abstract String getArtTitleContext(int articleID) throws IOException;

	public final String getPageType(int pageID) throws IOException {
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
		List<String> types = new ArrayList<>();
		for (Integer pageID : pageIDs) {
			if (DBPediaAccess.askQuery("?art dbowl:wikiPageID " + pageID,
					"?art dbowl:wikiPageDisambiguates ?x"))
				types.add(TYPE_DISAMBIGUATION);
			else if (DBPediaAccess.askQuery("?art dbowl:wikiPageID " + pageID,
					"?x dct:subject ?art"))
				types.add(TYPE_CATEGORY);
			else if (DBPediaAccess.askQuery("?art dbowl:wikiPageID " + pageID,
					"?art dbowl:wikiPageRedirects ?x"))
				types.add(TYPE_REDIRECT);
			else
				types.add(TYPE_ARTICLE);
		}
		return types;
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
	public List<Collection<Integer>> getParentCategories(
			Integer... categoryIDs) throws IOException {
		List<Collection<Integer>> parentArts = new ArrayList<>();
		for (int catID : categoryIDs) {
			Collection<Map<String, RDFNode>> results = DBPediaAccess
					.selectQuery("?superCatID", "?cat dbowl:wikiPageID " + catID,
							"?cat skos:broader ?superCat",
							"?superCat dbowl:wikiPageID ?superCatID");
			Collection<Integer> parents = new ArrayList<>();
			for (Map<String, RDFNode> vars : results) {
				Literal lit = vars.get("?superCatID").asLiteral();
				int id = lit.getInt();
				parents.add(id);
			}
			parentArts.add(parents);
		}
		return parentArts;
	}

	public abstract int getPrevArticle(int id) throws IOException;

	/**
	 * Gets the redirect target for this redirect (or -1).
	 * 
	 * @param articleID
	 *            The redirect article ID.
	 * @return The target redirect.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public abstract int getRedirect(int articleID) throws IOException;

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
	public final List<Double> getRelatednessList(Integer baseArticle,
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
	public abstract List<List<Double>> getRelatednessList(
			Integer[] baseArticles, Integer... comparisonArticles)
			throws IOException;

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
	public abstract List<Double> getRelatednessPair(int... articles)
			throws IOException;

	/**
	 * Gets the topics of a text, using the (via the "topics" command).
	 * 
	 * @param context
	 *            The optional (possibly null) context for the command to make
	 *            use of.
	 * @param term
	 *            The text for which topics are found.
	 * @return A {@link WeightedSet} of topics, where each topic is an article.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public abstract WeightedSet<Integer> getTopics(String text,
			Collection<Integer> context) throws IOException;

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
	public final List<WeightedSet<Integer>> getWeightedArticles(
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
	public final WeightedSet<Integer> getWeightedArticles(String term)
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
	public abstract List<WeightedSet<Integer>> getWeightedArticles(
			String... terms) throws IOException;

	public final WeightedSet<Integer> getWeightedArticles(String term,
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
	public final boolean hasTitleContext(int articleID) throws IOException {
		return !getArtTitleContext(articleID).isEmpty();
	}

	/**
	 * Gets the article URL for Wikipedia.
	 *
	 * @param article
	 *            The article to retrieve.
	 * @return The URL to the page or null if page not found.
	 */
	public static String getArticleURL(int article) {
		try {
			return getArticleURL(ResourceAccess.requestWikipediaSocket()
					.getArtTitle(article, true), WIKIPEDIA_URL);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Format an article title as a URL to a resource.
	 *
	 * @param articleTitle
	 *            The article title to retrieve a URL for.
	 * @param type
	 *            The type of URL resource.
	 * @return The URL as a string.
	 */
	public static String getArticleURL(String articleTitle, int type) {
		if (articleTitle == null)
			return null;
		String title = articleTitle.replaceAll(" ", "_");
		// String title = NLPToSyntaxModule.convertToAscii(articleTitle)
		// .replaceAll(" ", "_");
		if (type == DBPEDIA_URL)
			return "http://dbpedia.org/resource/" + title;
		else
			return "http://en.wikipedia.org/wiki/" + title;

	}

	/**
	 * Gets the article URL for DBpedia.
	 *
	 * @param article
	 *            The article to retrieve.
	 * @return The URL to the page or null if page not found.
	 */
	public static String getArticleURLDBpedia(int article) {
		try {
			return getArticleURL(ResourceAccess.requestWikipediaSocket()
					.getArtTitle(article, true), DBPEDIA_URL);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
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
	private final class DoubleParser extends WikipediaMethod<Double> {
		@Override
		public Double parseResult(String result, String source) {
			try {
				return Double.parseDouble(result);
			} catch (NumberFormatException e) {
				return 0d;
			}
		}
	}

	/**
	 * Parses singular ID strings (e.g. integers).
	 * 
	 * @author Sam Sarjant
	 */
	protected final class IntegerParser extends WikipediaMethod<Integer> {
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
	 * A very simple class that returns the input it receives.
	 *
	 * @author Sam Sarjant
	 */
	protected final class StringParser extends WikipediaMethod<String> {
		@Override
		public String parseResult(String result, String source) {
			return result;
		}
	}

	/**
	 * An abstract shell for invoking a parsing method.
	 * 
	 * @author Sam Sarjant
	 */
	abstract class WikipediaMethod<T> {
		protected static final String DEFAULT = "DEFAULT";

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
	}
}
