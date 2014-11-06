package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;

public class NeuralNetworkPredictor extends WikipediaArticleMiningHeuristic {
	public NeuralNetworkPredictor(boolean usePrecomputed, CycMapper mapper,
			CycMiner miner) {
		super(usePrecomputed, mapper, miner);
//		classifier_ = loadModel();
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
		// Get all words (and freqs) of the article and send them through
		wmi.getMarkup(info.getArticle());
		
		//classifier_.classify();
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.PARENTAGE.ordinal()] = true;
		infoTypes[InformationType.RELATIONS.ordinal()] = true;
		infoTypes[InformationType.STANDING.ordinal()] = true;
	}

}
