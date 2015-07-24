/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;

/**
 * Like a category miner, but instead gathers children from sub-categories of
 * the category (but not the category itself, as that causes unnecessary
 * overlap).
 * 
 * @author Sam Sarjant
 */
public class SubCategoryMiner extends CategoryChildMiner {

	/**
	 * Constructor for a new SubCategoryMiner
	 * 
	 * @param mapper
	 * @param miner 
	 */
	public SubCategoryMiner(CycMapper mapper, CycMiner miner) {
		super(mapper, miner);
	}

	@Override
	public Collection<Integer> findRelevantCategories(String articleTitle,
			int articleID, WMISocket wmi)
			throws IOException {
		Collection<Integer> rootCategories = super.findRelevantCategories(
				articleTitle, articleID, wmi);

		// Get all subcategories
		Collection<Integer> foundCategories = new HashSet<>();
		Collection<Integer> prevCategories = rootCategories;
		while (!prevCategories.isEmpty()) {
			prevCategories = WMISocket.union(wmi
					.getChildCategories(prevCategories
							.toArray(new Integer[prevCategories.size()])));
			prevCategories = getTextRelevantCategories(prevCategories,
					articleTitle, wmi);
			prevCategories.removeAll(foundCategories);
			foundCategories.addAll(prevCategories);
		}
		return foundCategories;
	}
}
