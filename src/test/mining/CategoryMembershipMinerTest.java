/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertFalse;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.SortedSet;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.CategoryMembershipMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.CycConstants;

public class CategoryMembershipMinerTest {
	private static CategoryMembershipMiner miner_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper(null);
		mapper.initialise();
		CycMiner miner = new CycMiner(null, mapper);
		miner_ = new CategoryMembershipMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic#mineArticle(ConceptModule, int, io.resources.WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IOException
	 */
	@Test
	public void testMineArticle() throws IOException {
		int article;
		MinedInformation info;
		SortedSet<AssertionQueue> parentage;
		article = wmi_.getArticleByTitle("Uma Thurman");
		info = miner_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		parentage = info.getParentageAssertions();
		assertFalse(parentage.isEmpty());

		article = wmi_.getArticleByTitle("Jaguar");
		info = miner_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		parentage = info.getParentageAssertions();
		assertFalse(parentage.isEmpty());
	}

}
