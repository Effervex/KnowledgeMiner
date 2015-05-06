/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.SentenceParserHeuristic;

import org.apache.commons.lang3.StringUtils;

import util.UtilityMethods;
import util.collection.MultiMap;
import util.text.OpenNLP;
import util.wikipedia.BulletListParser;
import util.wikipedia.TableMiner;
import util.wikipedia.WikiParser;
import util.wikipedia.WikiTable;
import cyc.AssertionArgument;
import cyc.CycConstants;

/**
 * A mining heuristic purely for processing 'List of...' articles. This
 * heuristic produces standing and taxonomic assertions for the target of the
 * list article.
 * 
 * @author Sam Sarjant
 */
public class ListMiner extends WikipediaArticleMiningHeuristic {
	private static final AssertionArgument LIST_ELEMENT = new TextMappedConcept(
			"_LIST_ELEMENT_", false, false);
	private static final String LIST_OF = "List of ";
	private static final String LIST_OF_REGEX = "Lists? of ";

	/**
	 * Constructor for a new ListMiner.java
	 * 
	 * @param miner
	 */
	public ListMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
	}

	private void assignDataToAssertions(String focusString, String context,
			String listTitle, Collection<PartialAssertion> listAssertions,
			MinedInformation info, WMISocket wmi) throws IOException {
		if (focusString.isEmpty())
			return;
		Matcher m = WikiParser.ANCHOR_PARSER_ROUGH.matcher(focusString);
		// TODO Just parsing anchors at the moment - not plain text
		if (m.find()) {
			String artTitle = m.group(1);
			int artID = wmi.getArticleByTitle(artTitle);
			if (artID == -1)
				return;

			WikipediaMappedConcept wmc = new WikipediaMappedConcept(artID);
			HeuristicProvenance provenance = new HeuristicProvenance(this,
					listTitle + ": " + artTitle + " (" + context + ")");
			// Replace LIST_ELEMENT with the article
			for (PartialAssertion pa : listAssertions) {
				PartialAssertion newPA = pa.replaceArg(LIST_ELEMENT, wmc);
				newPA.setProvenance(provenance);
				info.addAssertion(newPA);
			}
		}
	}

	/**
	 * Creates a single assertion for a known article, using the List Element
	 * constant as the subject and the article as the object.
	 *
	 * @param art
	 *            The article to set as the object of the taxonomic assertion.
	 * @param provenance
	 *            The provenance to use.
	 * @return The created taxonomic assertion
	 */
	private PartialAssertion createTaxonomicArticleAssertion(int art,
			HeuristicProvenance provenance) {
		PartialAssertion pa = new PartialAssertion(
				CycConstants.ISA_GENLS.getConcept(), provenance,
				ListMiner.LIST_ELEMENT, new WikipediaMappedConcept(art));
		return pa;
	}

	/**
	 * Searches for an appropriate focus article for the list (i.e. the article
	 * which the list is about).
	 *
	 * @param article
	 *            The list article.
	 * @param title
	 *            The title of the list article.
	 * @param provenance
	 *            The provenance of the assertion.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return A focus article mappable concept for the list article or a text
	 *         mappable concept.
	 */
	private Collection<PartialAssertion> searchFocusArticle(int article,
			String title, HeuristicProvenance provenance, WMISocket wmi,
			OntologySocket ontology) {
		String plural = title.replaceAll(LIST_OF_REGEX, "");
		Collection<PartialAssertion> results = new ArrayList<>();
		try {
			int art = wmi.getArticleByTitle(plural);
			String type = wmi.getPageType(art);
			if (art != -1 && art != article && type != null
					&& type.equals(WMISocket.TYPE_ARTICLE)) {
				results.add(createTaxonomicArticleAssertion(art, provenance));
				return results;
			}

			// Stem the plural
			String stemmed = OpenNLP.stem(plural);
			art = wmi.getArticleByTitle(stemmed);
			type = wmi.getPageType(art);
			if (art != -1 && art != article && type != null
					&& type != null && type.equals(WMISocket.TYPE_ARTICLE)) {
				results.add(createTaxonomicArticleAssertion(art, provenance));
				return results;
			}
		} catch (Exception e) {
			System.err
					.println("Exception while searching for focus article for: "
							+ article);
			e.printStackTrace();
		}

		// Sentence parse the list title
		MinedInformation tempInfo = new MinedInformation(article);
		String sentence = SentenceParserHeuristic.SENTENCE_PREFIX + plural
				+ ".";
		miner_.mineSentence(sentence, false, tempInfo, this, ontology, wmi);
		for (PartialAssertion pa : tempInfo.getAssertions())
			results.add(pa.replaceArg(tempInfo.getMappableSelfRef(),
					LIST_ELEMENT));

		return results;
	}

	/**
	 * Search for a list for a given article. For example, article X will search
	 * for 'List of Xs'.
	 *
	 * @param article
	 *            The article to find a list for.
	 * @param title
	 *            The title of the article.
	 * @param wmi
	 *            The WMI access.
	 * @return A list article for the input article or -1.
	 */
	private int searchListArticle(int article, String title, WMISocket wmi) {
		Set<String> potentialTitles = new HashSet<>();
		for (String permutation : UtilityMethods
				.manipulateStringCapitalisation(title))
			potentialTitles.add(LIST_OF + permutation);
		for (String permutation : UtilityMethods
				.manipulateStringCapitalisation(title + "s"))
			potentialTitles.add(LIST_OF + permutation);
		try {
			List<Integer> arts = wmi.getArticleByTitle(potentialTitles
					.toArray(new String[potentialTitles.size()]));
			UtilityMethods.removeNegOnes(arts);
			HashSet<Integer> artSet = new HashSet<>(arts);
			if (artSet.size() == 1)
				return arts.get(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	/**
	 * Parses any bulleted lists in the list article, extract the child
	 * articles, and create as assertions to the parent mappable concept.
	 *
	 * @param listArticle
	 *            The article to parse.
	 * @param listAssertions
	 * @param info
	 *            The info to add to.
	 * @param wmi
	 *            The WMI access.
	 * @param listTitle
	 *            The list title.
	 * @throws Exception
	 */
	protected void extractBulletInformation(int listArticle, String listTitle,
			String markup, Collection<PartialAssertion> listAssertions,
			MinedInformation info, WMISocket wmi) throws Exception {
		MultiMap<String, String> listItems = BulletListParser
				.parseBulletList(markup);
		// Iterate through the points
		for (Map.Entry<String, Collection<String>> entry : listItems.entrySet()) {
			if (entry.getKey().equalsIgnoreCase("references"))
				continue;
			for (String point : entry.getValue()) {
				assignDataToAssertions(point, entry.getKey(), listTitle,
						listAssertions, info, wmi);
			}
		}
	}

	/**
	 * Parses any tables in the article, identifies the focus column of the
	 * table, adds it to the list assertions, and extracts additional assertions
	 * from the table.
	 *
	 * @param listArticle
	 *            The list article to parse.
	 * @param markup
	 * @param listAssertions
	 *            The assertions to add the focus elements to.
	 * @param info
	 *            The info to add the information to.
	 * @param wmi
	 *            The WMI access.
	 * @throws IOException
	 */
	protected void extractTableInformation(int listArticle, String title,
			String markup, Collection<PartialAssertion> listAssertions,
			MinedInformation info, WMISocket wmi) throws IOException {
		Collection<WikiTable> tables = TableMiner.parseTable(markup);
		for (WikiTable table : tables) {
			MultiMap<String, String> colData = table.getTableData();

			// Identify which column is the focus column
			String focusColumn = findFocusColumn(colData, title);
			Collection<String> focusConcepts = colData.get(focusColumn);
			for (String focusConcept : focusConcepts) {
				assignDataToAssertions(focusConcept, focusColumn, title,
						listAssertions, info, wmi);
			}
		}
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		int article = info.getArticle();
		String title = wmi.getPageTitle(article, false);
		HeuristicProvenance provenance = new HeuristicProvenance(this, title);

		Collection<PartialAssertion> listAssertions = null;
		int listArticle = -1;
		// If a list,
		if (WikiParser.isAListOf(title)) {
			// Attempt to find the corresponding article
			listAssertions = searchFocusArticle(article, title, provenance,
					wmi, cyc);

			listArticle = article;
		} else {
			// Search for list
			listArticle = searchListArticle(article, title, wmi);

			listAssertions = new ArrayList<>();
			listAssertions.add(createTaxonomicArticleAssertion(article,
					provenance));
		}

		// Cannot find list, cannot mine
		if (listArticle == -1)
			return;

		// If standing is requested, and we know the focus article, set as
		// collection for the appropriate focus article.
		if (listAssertions.size() == 1
				&& (listAssertions.iterator().next().getArgs()[1] instanceof WikipediaMappedConcept)
				&& informationRequested(informationRequested,
						InformationType.STANDING)) {
			int focusArticle = ((WikipediaMappedConcept) listAssertions
					.iterator().next().getArgs()[1]).getArticle();
			try {

				MinedInformation focusInfo = getInfo(focusArticle);
				focusInfo.addStandingInformation(TermStanding.COLLECTION, 1,
						provenance);
				focusInfo.addMinedInfoType(InformationType.STANDING);
				writeInfo(focusInfo);
			} catch (Exception e) {
				System.err
						.println("Exception recording standing information for "
								+ focusArticle + " for list " + listArticle);
				e.printStackTrace();
			}
		}

		// Parse the list items
		if (informationRequested(informationRequested,
				InformationType.TAXONOMIC)
				|| informationRequested(informationRequested,
						InformationType.NON_TAXONOMIC)) {
			String markup = wmi.getMarkup(listArticle);
			extractBulletInformation(listArticle, title, markup,
					listAssertions, info, wmi);
			// extractTableInformation(listArticle, title, markup,
			// listAssertions,
			// info, wmi);
		}
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		informationProduced[InformationType.STANDING.ordinal()] = true;
		informationProduced[InformationType.TAXONOMIC.ordinal()] = true;
		informationProduced[InformationType.NON_TAXONOMIC.ordinal()] = true;
	}

	/**
	 * Finds the focus column (the column containing the data of the list).
	 * 
	 * TODO This could be more hypothesis driven (i.e. test the data before
	 * committing).
	 *
	 * @param colData
	 *            The column data.
	 * @return The most likely focus column.
	 */
	public String findFocusColumn(MultiMap<String, String> colData,
			String listTitle) {
		String stem = OpenNLP.stem(listTitle.replaceAll(LIST_OF_REGEX, ""))
				.toLowerCase();

		// Search for columns with the word 'name' in it.
		// Alternatively search for mentions of the word in the title.
		// Finally, use Levenshtein dist
		int singleNamed = -1;
		int singleContained = -1;
		int bestDist = Integer.MAX_VALUE;
		int distIndex = 0;
		String[] keys = colData.keySet().toArray(new String[colData.size()]);
		for (int i = 0; i < keys.length; i++) {
			String colTitle = WikiParser.cleanAllMarkup(keys[i]).toLowerCase();
			if (keys[i].toLowerCase().contains("name")) {
				if (singleNamed == -1)
					singleNamed = i;
				else
					singleNamed = -2;
			}
			String stemCol = OpenNLP.stem(colTitle);
			if (stem.contains(stemCol)) {
				if (singleContained == -1)
					singleContained = i;
				else
					singleContained = -2;
			}
			int dist = StringUtils.getLevenshteinDistance(stemCol, stem);
			if (dist < bestDist) {
				bestDist = dist;
				distIndex = i;
			}
		}

		if (singleNamed > 0)
			return keys[singleNamed];
		else if (singleContained > 0)
			return keys[singleContained];
		else
			return null;
	}

	public static void main(String[] args) throws IOException {
		ResourceAccess.newInstance();
		WMISocket wmi = ResourceAccess.requestWMISocket();

		BufferedWriter out = new BufferedWriter(new FileWriter(new File(
				"listfile.txt")));

		int artId = -1;
		int listCount = 0;
		while (true) {
			try {
				artId = wmi.getNextArticle((int) artId);
				if (artId == -1) {
					out.close();
					System.out.println(listCount + " lists found.");
					System.exit(0);
				}

				String title = wmi.getPageTitle(artId, true);
				String type = wmi.getPageType(artId);
				if (WikiParser.isAListOf(title) && type != null
						&& type.equals(WMISocket.TYPE_ARTICLE)) {
					out.write(artId + "\t" + title + "\n");
					System.out.println(title);
					listCount++;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
