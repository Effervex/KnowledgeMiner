/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import util.collection.WeightedSet;
import cyc.CycConstants;
import cyc.StringConcept;

/**
 * A class that mines information (synonyms) purely using anchor data for
 * articles.
 * 
 * @author Sam Sarjant
 */
public class AnchorMiner extends WikipediaArticleMiningHeuristic {

	private static final double LABEL_THRESHOLD = 10;

	/**
	 * Constructor for a new AnchorMiner.
	 * 
	 * @param mapper
	 *            The Mapping access.
	 * @param miner
	 */
	public AnchorMiner(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		WeightedSet<String> labels = wmi.getLabels(info.getArticle());
		labels.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
		for (String label : labels) {
			if (labels.getWeight(label) < LABEL_THRESHOLD)
				break;

			MinedAssertion assertion = new PartialAssertion(
					CycConstants.SYNONYM_RELATION.getConcept(),
					basicProvenance_, info.getMappableSelfRef(),
					new StringConcept(label));
			assertion.setWeight(labels.getWeight(label));
			info.addAssertion(assertion);
		}
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.RELATIONS.ordinal()] = true;
	}

}
