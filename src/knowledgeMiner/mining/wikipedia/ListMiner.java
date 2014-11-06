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
import java.util.Set;
import java.util.regex.Matcher;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import util.UtilityMethods;
import util.collection.MultiMap;
import util.wikipedia.BulletListParser;
import util.wikipedia.WikiParser;

/**
 * A mining heuristic purely for processing 'List of...' articles. This
 * heuristic produces standing and terms.
 * 
 * @author Sam Sarjant
 */
// TODO This entire heuristic needs to be modified to just directly process list
// articles.
public class ListMiner extends WikipediaArticleMiningHeuristic {
	/** The List prefix. */
	public static final String LIST_OF = "List of ";

	/**
	 * Constructor for a new ListMiner.java
	 * 
	 * @param miner
	 */
	public ListMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
	}

	/**
	 * Harvests children from a List.
	 * 
	 * @param info
	 *            The info to add to.
	 * @param informationRequested
	 *            The information requested.
	 * @param wmi
	 *            The WMI access point.
	 * @param title
	 *            The title to add to "List of "
	 * @param searched
	 *            The searched Strings.
	 * 
	 * @throws Exception
	 */
	private void harvestChildren(int listArticle, MinedInformation info,
			int informationRequested, WMISocket wmi) throws Exception {
		// Add standing
		if (informationRequested(informationRequested, InformationType.STANDING))
			info.addStandingInformation(TermStanding.COLLECTION, getWeight(),
					basicProvenance_);
		// Add children
		if (informationRequested(informationRequested,
				InformationType.CHILD_ARTICLES)) {
			String markup = wmi.getMarkup(listArticle);

			// Parse the bullets from the list
			MultiMap<String, String> bulletPoints = BulletListParser
					.parseBulletList(markup);
			Collection<String> items = new ArrayList<>(bulletPoints.sizeTotal());
			for (String point : bulletPoints.values()) {
				Matcher m = WikiParser.ANCHOR_PARSER.matcher(point);
				if (m.find())
					items.add(m.group(1));
			}

			Collection<Integer> childArts = wmi.getArticleByTitle(items
					.toArray(new String[items.size()]));
			HeuristicProvenance provenance = new HeuristicProvenance(this,
					listArticle + "");
			for (Integer childArt : childArts)
				info.addChild(new WikipediaMappedConcept(childArt), provenance);

			// TODO Parse the table elements from the list
		}
	}

	/**
	 * Creates a collection of possible article titles for lists if the target
	 * article.
	 * 
	 * @param title
	 *            The title of the target article.
	 * @param synonyms
	 *            Synonyms for the target.
	 * @return A Collection of possible 'list of' article titles.
	 */
	private Collection<String> permutateSynonyms(String title,
			Collection<String> synonyms) {
		Set<String> potentialTitles = new HashSet<>();
		// Title permutations
		// TODO Perform better pluralisation of words
		for (String permutation : UtilityMethods
				.manipulateStringCapitalisation(title))
			potentialTitles.add(LIST_OF + permutation);
		for (String permutation : UtilityMethods
				.manipulateStringCapitalisation(title + "s"))
			potentialTitles.add(LIST_OF + permutation);

		// Synonym permutations
		for (String synonym : synonyms) {
			for (String permutation : UtilityMethods
					.manipulateStringCapitalisation(synonym))
				potentialTitles.add(LIST_OF + permutation);
			for (String permutation : UtilityMethods
					.manipulateStringCapitalisation(synonym + "s"))
				potentialTitles.add(LIST_OF + permutation);
		}
		return potentialTitles;
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		int article = info.getArticle();

		// Attempt to find a list version of the Cyc term
		String title = wmi.getPageTitle(article, false);
		// Already a list, use this
		if (title.startsWith(LIST_OF)) {
			harvestChildren(article, info, informationRequested, wmi);
			return;
		}
		Collection<String> synonyms = new HashSet<>();
		synonyms.add(title);

		Collection<String> potentialLists = permutateSynonyms(title, synonyms);
		Collection<Integer> listArticles = wmi.getArticleByTitle(potentialLists
				.toArray(new String[potentialLists.size()]));
		UtilityMethods.removeNegOnes(listArticles);
		// For each list article
		for (Integer listArt : listArticles)
			harvestChildren(listArt, info, informationRequested, wmi);
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		informationProduced[InformationType.STANDING.ordinal()] = true;
		informationProduced[InformationType.CHILD_ARTICLES.ordinal()] = true;
		// informationProduced[InformationType.PARENTAGE.ordinal()] = true;
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
				if (title.startsWith(LIST_OF)) {
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
