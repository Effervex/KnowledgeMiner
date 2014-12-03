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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import util.UtilityMethods;
import util.collection.MultiMap;
import util.wikipedia.BulletListParser;
import util.wikipedia.WikiParser;
import cyc.AssertionArgument;
import cyc.CycConstants;

/**
 * A mining heuristic purely for processing 'List of...' articles. This
 * heuristic produces standing and taxonomic assertions for the target of the
 * list article.
 * 
 * @author Sam Sarjant
 */
// TODO This entire heuristic needs to be modified to just directly process list
// articles.
public class ListMiner extends WikipediaArticleMiningHeuristic {
	/** The List prefix. */
	public static final String LIST_OF = "List of ";
	private static final AssertionArgument LIST_ELEMENT = new TextMappedConcept(
			"_LIST_ELEMENT_", false, false);

	/**
	 * Constructor for a new ListMiner.java
	 * 
	 * @param miner
	 */
	public ListMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
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
		boolean isList = title.startsWith(LIST_OF);
		// If a list,
		if (isList) {
			// Attempt to find the corresponding article
			listAssertions = searchFocusArticle(article, title, provenance, wmi);

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
		// collection
		if (listAssertions.size() == 1
				&& (listAssertions.iterator().next().getArgs()[2] instanceof WikipediaMappedConcept)
				&& informationRequested(informationRequested,
						InformationType.STANDING)) {
			// TODO Add the appropriate standing information (make sure it's
			// added to the focus article, not the list
			// info.addStandingInformation(TermStanding.COLLECTION, 1,
			// provenance);
		}

		// Parse the list items
		if (informationRequested(informationRequested,
				InformationType.TAXONOMIC)
				|| informationRequested(informationRequested,
						InformationType.NON_TAXONOMIC))
			harvestChildren(listArticle, listAssertions, info, wmi);
	}

	/**
	 * Parse the list, extract the child articles, and create as assertions to
	 * the parent mappable concept.
	 *
	 * @param listArticle
	 *            The article to parse.
	 * @param listAssertions
	 * @param info
	 *            The info to add to.
	 * @param wmi
	 *            The WMI access.
	 * @throws Exception
	 */
	private void harvestChildren(int listArticle,
			Collection<PartialAssertion> listAssertions, MinedInformation info,
			WMISocket wmi) throws Exception {
		MultiMap<String, String> listItems = BulletListParser
				.parseBulletList(wmi.getMarkup(listArticle));
		String listTitle = wmi.getPageTitle(listArticle, true);
		// Iterate through the points
		for (String context : listItems.keySet()) {
			for (String point : listItems.get(context)) {
				Matcher m = WikiParser.ANCHOR_PARSER_ROUGH.matcher(point);
				// TODO Just parsing known anchors at the moment
				if (m.find()) {
					String artTitle = m.group(1);
					int artID = wmi.getArticleByTitle(artTitle);
					if (artID == -1)
						continue;

					WikipediaMappedConcept wmc = new WikipediaMappedConcept(
							artID);
					HeuristicProvenance provenance = new HeuristicProvenance(
							this, listTitle + " " + artTitle + " (" + context
									+ ")");
					// Replace LIST_ELEMENT with the article
					for (PartialAssertion pa : listAssertions) {
						PartialAssertion newPA = pa.replaceArg(LIST_ELEMENT, wmc);
						newPA.setProvenance(provenance);
					}
				}

			}
		}
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
			if (arts.size() == 1)
				return arts.get(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
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
	 * @return A focus article mappable concept for the list article or a text
	 *         mappable concept.
	 */
	private Collection<PartialAssertion> searchFocusArticle(int article,
			String title, HeuristicProvenance provenance, WMISocket wmi) {
		String plural = title.replace("List of ", "");
		Collection<PartialAssertion> results = new ArrayList<>();
		try {
			int art = wmi.getArticleByTitle(plural);
			if (art != -1
					&& wmi.getPageType(art).equals(WMISocket.TYPE_ARTICLE)) {
				results.add(createTaxonomicArticleAssertion(art, provenance));
				return results;
			}
			// Stem the plural
			// TODO Stem the plural
		} catch (Exception e) {
			e.printStackTrace();
		}

		// TODO Sentence parse the list title
		// miner_.mineSentence("X is a ", info, heuristic, cyc, wmi);
		return results;
	}

	/**
	 * Creates a single assertion for a known article, using the List Element
	 * constant as the subject and the article as the object.
	 *
	 * @param art
	 *            The article to set as the object of the taxonomic assertion.
	 * @param provenance
	 *            TODO
	 * @return The created taxonomic assertion
	 */
	private PartialAssertion createTaxonomicArticleAssertion(int art,
			HeuristicProvenance provenance) {
		PartialAssertion pa = new PartialAssertion(
				CycConstants.ISA_GENLS.getConcept(), provenance,
				ListMiner.LIST_ELEMENT, new WikipediaMappedConcept(art));
		return pa;
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		informationProduced[InformationType.STANDING.ordinal()] = true;
		informationProduced[InformationType.TAXONOMIC.ordinal()] = true;
		informationProduced[InformationType.NON_TAXONOMIC.ordinal()] = true;
	}

	public static void main(String[] args) throws IOException {
		ResourceAccess.newInstance();
		WMISocket wmi = ResourceAccess.requestWMISocket();

		BufferedWriter out = new BufferedWriter(new FileWriter(new File(
				"listfile.csv")));

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
				if (title.startsWith(LIST_OF)
						&& wmi.getPageType(artId)
								.equals(WMISocket.TYPE_ARTICLE)) {
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
