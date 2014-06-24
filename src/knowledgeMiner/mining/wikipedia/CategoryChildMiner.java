/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
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
		super(mapper, miner);
	}

	/**
	 * Finds the child articles of a category.
	 * 
	 * @param articleID
	 *            Article ID.
	 * @param wmi
	 *            WMI access.
	 * @param categoryID
	 *            The category article.
	 * 
	 * @return The child articles.
	 * @throws IOException
	 *             Should something go awry...
	 */
	private Collection<Integer> findChildArticles(String articleTitle,
			int articleID, WMISocket wmi) throws IOException {
		Collection<Integer> childArts = new HashSet<>();
		// Already a category, use this
		if (wmi.getPageType(articleID).equals("category"))
			childArts.addAll(WMISocket.union(wmi.getChildArticles(articleID)));
		else {
			Collection<Integer> categories = findRelevantCategories(
					articleTitle, articleID, wmi);

			childArts.addAll(WMISocket.union(wmi.getChildArticles(categories
					.toArray(new Integer[categories.size()]))));
		}
		return childArts;
	}

	/**
	 * Finds the relevant categories for a given Cyc term and article, baed on
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
			Collection<Integer> categories, String articleTitle, WMISocket wmi)
			throws IOException {
		Collection<Integer> relevant = new ArrayList<>();
		Integer[] categoryArray = categories.toArray(new Integer[categories
				.size()]);
		List<String> categoryNames = wmi.getPageTitle(true, categoryArray);
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
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		int article = info.getArticle();
		// Change everything to a single case
		String articleTitle = wmi.getPageTitle(article, false).toLowerCase();
		if (informationRequested(informationRequested,
				InformationType.CHILD_ARTICLES)) {
			info.addChildArticles(findChildArticles(articleTitle, article, wmi));
		}
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.CHILD_ARTICLES.ordinal()] = true;
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
			int articleID, WMISocket wmi) throws IOException {
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
