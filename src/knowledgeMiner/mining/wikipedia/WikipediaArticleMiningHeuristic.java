/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MiningHeuristic;

/**
 * An abstract class representing a mining technique for extracting new
 * information from an information source. Information can add relations to
 * existing Cyc terms, create new Cyc terms, or simple assist in the mapping
 * process.
 * 
 * @author Sam Sarjant
 */
public abstract class WikipediaArticleMiningHeuristic extends MiningHeuristic {
	/**
	 * Constructor for a new WikipediaArticleMiningHeuristic
	 * 
	 * @param mapper
	 *            The mapper.
	 * @param miner
	 *            The miner
	 */
	public WikipediaArticleMiningHeuristic(boolean usePrecomputed,
			CycMapper mapper, CycMiner miner) {
		super(usePrecomputed, mapper, miner);
		partitionInformation_ = true;
	}
}
