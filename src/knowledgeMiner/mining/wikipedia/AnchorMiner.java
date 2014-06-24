/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import util.collection.WeightedSet;
import cyc.CycConstants;
import cyc.StringConcept;

/**
 * A class that mines information purely using anchor data for articles.
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
		super(mapper, miner);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		WeightedSet<String> labels = wmi.getLabels(info.getArticle());
		for (String label : labels) {
			if (labels.getWeight(label) < LABEL_THRESHOLD)
				break;
			info.addConcreteAssertion(createAssertion(
					CycConstants.SYNONYM_RELATION.getConcept(),
					new StringConcept(label)));
		}
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.RELATIONS.ordinal()] = true;
	}

}
