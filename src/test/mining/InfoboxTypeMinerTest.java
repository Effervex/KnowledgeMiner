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
import java.util.List;
import java.util.SortedSet;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.InfoboxTypeMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.CycConstants;


/**
 * 
 * @author Sam Sarjant
 */
public class InfoboxTypeMinerTest {
	private static InfoboxTypeMiner miner_;
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
		miner_ = new InfoboxTypeMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic#mineArticle(java.lang.Integer, boolean[], WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IOException
	 */
	@Test
	public void testMineArticle() throws IOException {
		MinedInformation info = miner_.mineArticle(
				wmi_.getArticleByTitle("Uma Thurman"), MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getChildArticles().isEmpty());
		Collection<MinedAssertion> assertions = info.getConcreteAssertions();
		assertTrue(assertions.isEmpty());
		SortedSet<AssertionQueue> parents = info.getParentageAssertions();
		assertTrue(parents.isEmpty());
		List<String> types = info.getInfoboxTypes();
		assertEquals(types.size(), 1);
		assertEquals(types.get(0), "person");
	}

}
