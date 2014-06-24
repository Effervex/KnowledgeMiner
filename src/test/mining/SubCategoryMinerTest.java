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
import knowledgeMiner.mining.wikipedia.SubCategoryMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class SubCategoryMinerTest {
	private static SubCategoryMiner miner_;
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
		miner_ = new SubCategoryMiner(mapper, miner);
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
		assertEquals(info.getArticle(), wmi_.getArticleByTitle("Dog"), 0);
		Collection<Integer> children = info.getChildArticles();
		assertEquals(children.size(), 2049);

		info = miner_.mineArticle(
				new ConceptModule(new OntologyConcept("BruceWillis"), wmi_
						.getArticleByTitle("Bruce Willis"), 1, true),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		children = info.getChildArticles();
		assertTrue(children.isEmpty());

		info = miner_.mineArticle(new ConceptModule(new OntologyConcept(
				"Peninsula"), wmi_.getArticleByTitle("Peninsula"), 1, true),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getConcreteAssertions().isEmpty());
		children = info.getChildArticles();
		assertEquals(children.size(), 1652);
	}
}
