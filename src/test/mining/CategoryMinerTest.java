/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.CategoryChildMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class CategoryMinerTest {
	private static CategoryChildMiner miner_;
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
		miner_ = new CategoryChildMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.CategoryChildMiner#mineArticleInternal(MinedInformation, int, WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IOException
	 */
	@Test
	public void testMineArticle() throws IOException {
		// Simple
		MinedInformation info = miner_.mineArticle(new ConceptModule(
				new OntologyConcept("Dog"), wmi_.getArticleByTitle("Dog"), 1,
				true), MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getConcreteAssertions().isEmpty());
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		Collection<Integer> children = info.getChildArticles();
		assertEquals(children.size(), 40);

		info = miner_.mineArticle(new ConceptModule(new OntologyConcept("Dog"),
				wmi_.getArticleByTitle("Bruce Willis"), 1, true),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		children = info.getChildArticles();
		assertTrue(children.isEmpty());

		info = miner_.mineArticle(new ConceptModule(new OntologyConcept("Dog"),
				wmi_.getArticleByTitle("Teacher"), 1, true),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		children = info.getChildArticles();
		assertEquals(children.size(), 67);
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.CategoryChildMiner#findCategoryPage(java.lang.String, int)}
	 * .
	 * 
	 * @throws IOException
	 */
	@Test
	public void testFindCategoryPage() throws IOException {
		// Simple test
		Collection<Integer> categoryPage = miner_.findRelevantCategories("Dog",
				wmi_.getArticleByTitle("Dog"), wmi_);
		assertEquals(categoryPage.size(), 1);
		assertTrue(categoryPage.contains(wmi_.getCategoryByTitle("Dogs")));

		// Slight text manipulation
		categoryPage = miner_.findRelevantCategories("Cactus", wmi_.getArticleByTitle("Cactus"),
				wmi_);
		assertEquals(categoryPage.size(), 1);
		assertTrue(categoryPage.contains(wmi_.getCategoryByTitle("Cacti")));

		// Impossible
		categoryPage = miner_.findRelevantCategories("Bruce Willis",
				wmi_.getArticleByTitle("Bruce Willis"), wmi_);
		assertEquals(categoryPage.size(), 0);

		// Country
		categoryPage = miner_.findRelevantCategories("New Zealand",
				wmi_.getArticleByTitle("New Zealand"), wmi_);
		assertEquals(categoryPage.size(), 1);
		assertTrue(categoryPage
				.contains(wmi_.getCategoryByTitle("New Zealand")));
	}

}
