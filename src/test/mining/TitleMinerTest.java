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

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.TitleMiner;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;
import cyc.CycConstants;
import cyc.StringConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TitleMinerTest {
	private static TitleMiner miner_;
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
		miner_ = new TitleMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@AfterClass
	public static void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic#mineArticle(java.lang.Integer, boolean[], WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IOException
	 * @throws IllegalAccessException
	 */
	@Test
	public void testMineArticle() throws IOException, IllegalAccessException {
		// Simple
		MinedInformation info = miner_.mineArticle(
				wmi_.getArticleByTitle("Dog"), MinedInformation.ALL_TYPES,
				wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertTrue(info.getChildArticles().isEmpty());
		Collection<MinedAssertion> assertions = info.getConcreteAssertions();
		assertEquals(assertions.size(), 1);
		assertTrue(assertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION_CANONICAL.getConcept(),
				OntologyConcept.PLACEHOLDER, new StringConcept("Dog"), null,
				new HeuristicProvenance(miner_, null))));

		// Standing (individual)
		info = miner_.mineArticle(wmi_.getArticleByTitle("Bruce Willis"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.INDIVIDUAL);
		assertTrue(info.getChildArticles().isEmpty());
		assertions = info.getConcreteAssertions();
		assertEquals(assertions.size(), 1);
		assertTrue(assertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION_CANONICAL.getConcept(),
				OntologyConcept.PLACEHOLDER, new StringConcept("Bruce Willis"),
				null, new HeuristicProvenance(miner_, null))));

		// Standing (collection)
		info = miner_.mineArticle(wmi_.getArticleByTitle("Comedy film"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.COLLECTION);
		assertTrue(info.getChildArticles().isEmpty());
		assertions = info.getConcreteAssertions();
		assertEquals(assertions.size(), 1);
		assertTrue(assertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION_CANONICAL.getConcept(),
				OntologyConcept.PLACEHOLDER, new StringConcept("Comedy film"), null,
				new HeuristicProvenance(miner_, null))));
	}

}
