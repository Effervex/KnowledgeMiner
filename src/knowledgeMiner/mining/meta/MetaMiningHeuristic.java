/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.meta;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class MetaMiningHeuristic extends MiningHeuristic {

	/**
	 * Constructor for a new MetaMiningHeuristic
	 * 
	 * @param mapper
	 */
	public MetaMiningHeuristic(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner);
	}

	@Override
	public final MinedInformation mineArticle(ConceptModule minedInformation,
			int informationRequested, WMISocket wmi, OntologySocket cyc) {
		return mineArticle(minedInformation, wmi, cyc);
	}

	/**
	 * The more specific mineArticle method to use.
	 * 
	 * @param conceptModule
	 *            The information to meta-mine.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The Ontology access.
	 * @return The meta-mined information.
	 */
	public MinedInformation mineArticle(ConceptModule conceptModule,
			WMISocket wmi, OntologySocket ontology) {
		return super.mineArticle(conceptModule, MinedInformation.ALL_TYPES,
				wmi, ontology);
	}

	@Override
	protected abstract void setInformationTypes(boolean[] infoTypes);

}
