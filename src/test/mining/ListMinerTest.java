/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertEquals;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.ListMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.CycConstants;


/**
 * 
 * @author Sam Sarjant
 */
public class ListMinerTest {
	private static ListMiner miner_;
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
		miner_ = new ListMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	@Test
	public void testMineArticle() throws Exception {
		// No (obvious) list
		MinedInformation info = miner_.mineArticle(
				wmi_.getArticleByTitle("Pacifier"), MinedInformation.ALL_TYPES,
				wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		Collection<Integer> children = info.getChildArticles();
		assertEquals(children.size(), 0);

		// Easy list
		info = miner_.mineArticle(wmi_.getArticleByTitle("Chemist"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.COLLECTION);
		children = info.getChildArticles();
		assertEquals(children.size(), 420);
	}

}
