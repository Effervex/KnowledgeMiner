package knowledgeMiner.mining.dbpedia;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MiningHeuristic;

public abstract class DBPediaMiningHeuristic extends MiningHeuristic {
	public DBPediaMiningHeuristic(boolean usePrecomputed, CycMapper mapper,
			CycMiner miner) {
		super(usePrecomputed, mapper, miner);
		partitionInformation_ = false;
	}
}
