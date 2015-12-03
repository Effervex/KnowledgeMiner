/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import util.UtilityMethods;

/**
 * A mining heuristic for discovering new children using Wikipedia's category
 * hierarchy. Produces terms and possibly standing.
 * 
 * @author Sam Sarjant
 */
public class CategoryChildMiner extends WikipediaArticleMiningHeuristic {
	/**
	 * The number of ancestors to go up in the 'tree' by when looking for new
	 * children.
	 */
	public static final int LEAF_PARENT_VAL = 2;

	/**
	 * Constructor for a new CategoryMiner.java
	 * 
	 * @param miner
	 */
	public CategoryChildMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
	}

	/**
	 * Finds the child articles of a category.
	 * 
	 * @param articleID
	 *            Article ID.
	 * @param wmi
	 *            WMI access.
	 * @param info
	 * @param categoryID
	 *            The category article.
	 * 
	 * @return The child articles.
	 * @throws IOException
	 *             Should something go awry...
	 */
	private void findChildArticles(String articleTitle,
			int articleID, WikipediaSocket wmi, MinedInformation info)
			throws IOException {
		// Already a category, use this
		String type = wmi.getPageType(articleID);
		if (type == null)
			return;
		if (type.equals("category"))
			addChildrenFromCategory(articleID, info, wmi);
		else {
			Collection<Integer> categories = findRelevantCategories(
					articleTitle, articleID, wmi);
			for (Integer categoryID : categories)
				addChildrenFromCategory(categoryID, info, wmi);
		}
	}

	/**
	 * Adds children to the mined information from the category.
	 * 
	 * @param categoryID
	 *            The category to add children from.
	 * @param info
	 *            The info to add to.
	 * @param wmi
	 *            The WMI access.
	 * @throws IOException
	 *             Should something go awry...
	 */
	protected void addChildrenFromCategory(int categoryID,
			MinedInformation info, WikipediaSocket wmi) throws IOException {
		Collection<Integer> childArts = WikipediaSocket.union(wmi
				.getChildArticles(categoryID));
		HeuristicProvenance provenance = new HeuristicProvenance(this,
				categoryID + "");
		// Add every child as an assertion, with the category as provenance
		// TODO Modify this to add children through SentenceParser
//		for (Integer childArt : childArts)
//			info.addChild(new WikipediaMappedConcept(childArt), provenance);
	}

	/**
	 * Finds the relevant categories for a given Cyc term and article, based on
	 * whether the term/title is present in the category name.
	 * 
	 * @param categories
	 *            The categories to check.
	 * @param articleTitle
	 *            The article title.
	 * @param wmi
	 *            WMI access.
	 * @return A collection of relevant categories.
	 * @throws IOException
	 */
	protected Collection<Integer> getTextRelevantCategories(
			Collection<Integer> categories, String articleTitle, WikipediaSocket wmi)
			throws IOException {
		Collection<Integer> relevant = new ArrayList<>();
		Integer[] categoryArray = categories.toArray(new Integer[categories
				.size()]);
		List<String> categoryNames = wmi.getArtTitle(true, categoryArray);
		int i = 0;
		for (String categoryName : categoryNames) {
			boolean foundString = UtilityMethods.findSubString(articleTitle,
					categoryName);

			// Ambiguous category
			if (foundString)
				relevant.add(categoryArray[i]);
			i++;
		}
		return relevant;
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WikipediaSocket wmi, OntologySocket cyc)
			throws Exception {
		int article = info.getArticle();
		// Change everything to a single case
		String articleTitle = wmi.getArtTitle(article, false).toLowerCase();
		if (informationRequested(informationRequested,
				InformationType.TAXONOMIC)) {
			findChildArticles(articleTitle, article, wmi, info);
		}
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.TAXONOMIC.ordinal()] = true;
	}

	/**
	 * Finds the relevant categories for a given term and article.
	 * 
	 * @param articleTitle
	 *            The article title.
	 * @param articleID
	 *            The article ID.
	 * @param wmi
	 *            WMI access.
	 * 
	 * @return A collection of categories that are all suited as relevant
	 *         categories.
	 * @throws IOException
	 */
	public Collection<Integer> findRelevantCategories(String articleTitle,
			int articleID, WikipediaSocket wmi) throws IOException {
		// Get categories for article.
		Collection<Integer> categories = new ArrayList<>();

		// Optionally, if possible, use equivalent category when possible
		int equivCategory = wmi.getEquivalentCategory(articleID);
		if (equivCategory != -1) {
			categories.add(wmi.getEquivalentCategory(articleID));
		} else {
			// Check if categories match any permutations of article
			categories = wmi.getArticleCategories(articleID);
			categories = getTextRelevantCategories(categories, articleTitle,
					wmi);
		}
		return categories;
	}

}
