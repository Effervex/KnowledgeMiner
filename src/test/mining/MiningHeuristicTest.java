/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import io.KMSocket;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.wikipedia.TitleMiner;
import knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic;

import org.junit.After;
import org.junit.BeforeClass;

import cyc.CycConstants;


/**
 * 
 * @author Sam Sarjant
 */
public class MiningHeuristicTest {
	private static WikipediaArticleMiningHeuristic miner_;
	private static KMSocket wmi_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper(null);
		mapper.initialise();
		CycMiner miner = new CycMiner(null, mapper);
		miner_ = new TitleMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

}
