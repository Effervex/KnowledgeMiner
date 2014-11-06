/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.mapping.CycMapper;

import org.slf4j.LoggerFactory;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class MiningHeuristic extends WeightedHeuristic {

	/** The information this heuristic produces. */
	private final int informationProduced_;

	/** The weights of each infoType held by this heuristic. */
	private final double[] infoTypeWeights_;

	protected final HeuristicProvenance basicProvenance_;

	protected final CycMiner miner_;

	/**
	 * Constructor for a new MiningHeuristic
	 * 
	 * @param mapper
	 *            The mapping class.
	 * @param miner
	 *            The mining class.
	 */
	public MiningHeuristic(boolean usePrecomputed, CycMapper mapper,
			CycMiner miner) {
		super(usePrecomputed, mapper);
		miner_ = miner;
		basicProvenance_ = new HeuristicProvenance(this, null);

		boolean[] infoTypes = new boolean[InformationType.values().length];
		infoTypeWeights_ = new double[infoTypes.length];
		setInformationTypes(infoTypes);
		int bitwise = 0;
		for (int i = 0; i < infoTypes.length; i++) {
			if (infoTypes[i]) {
				bitwise += 1 << i;
				infoTypeWeights_[i] = INITIAL_WEIGHT;
			}
		}

		informationProduced_ = bitwise;
	}

	/**
	 * If the given infoType is requested.
	 * 
	 * @param infoRequested
	 *            The total information requested.
	 * @param infoType
	 *            The type of information being checked.
	 * @return True if the information was requested.
	 */
	protected final boolean informationRequested(int infoRequested,
			InformationType infoType) {
		// If this has what is wanted.
		if ((infoRequested & (1 << infoType.ordinal())) > 0)
			return true;
		return false;
	}

	/**
	 * If this heuristic produces the requested information.
	 * 
	 * @param informationRequested
	 *            The information requested (bitwise).
	 * @return True if this heuristic is able to produce that information.
	 */
	protected final boolean producesRequestedInformation(
			int informationRequested) {
		if ((informationProduced_ & informationRequested) > 0)
			return true;
		return false;
	}

	/**
	 * Set the information types that this mining heuristic produces.
	 * 
	 * @param infoTypes
	 *            The array to set.
	 */
	protected abstract void setInformationTypes(boolean[] infoTypes);

	public final int getInformationProduced() {
		return informationProduced_;
	}

	/**
	 * Gets the weight of a specific information type produced by this
	 * heuristic. Always <= the heuristic's weight.
	 * 
	 * @param type
	 *            The information type.
	 * @return The weight of this heuristic's specific information type.
	 */
	public final double getInfoTypeWeight(InformationType type) {
		return weight_ * infoTypeWeights_[type.ordinal()];
	}

	/**
	 * Mines an article for information. This can be adding relations, adding
	 * new terms, or assisting in mapping.
	 * 
	 * @param minedInformation
	 *            The {@link MinedInformation} specifying the target mining.
	 * @param informationRequested
	 *            The information requested for this mining operation.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The information that was able to be mined.
	 */
	public MinedInformation mineArticle(ConceptModule minedInformation,
			int informationRequested, WMISocket wmi, OntologySocket ontology) {
		// If this doesn't produce the required information, return empty
		// information.
		if (!producesRequestedInformation(informationRequested))
			return null;
		LoggerFactory.getLogger(getHeuristicName()).info("MINING:\t{}",
				minedInformation.getArticle());
		return minedInformation;
	}

	/**
	 * Updates the heuristic using entire collections of assertions about a
	 * concept and article. Note that updates on an assertion level should use
	 * the updateViaAssertion method.
	 * 
	 * @param info
	 *            The information to update with.
	 * @param wmi
	 *            The WMI access.
	 */
	public void updateGlobal(MinedInformation info, WMISocket wmi) {
		// Do nothing by default.
	}

	/**
	 * Updates the weight of both this heuristic and the sub-information type
	 * that this heuristic produces.
	 * 
	 * @param assertion
	 *            The assertion to update the heuristic with.
	 * @param details
	 *            The details of the provenance.
	 * @param weight
	 *            The weight to update towards.
	 * @param infoType
	 *            The information type.
	 * @param wmi
	 *            The WMI access.
	 */
	public void updateViaAssertion(WeightedInformation assertion,
			String details, double weight, InformationType infoType,
			WMISocket wmi) {
		// Perform online weight updating.
		if (KnowledgeMiner.onlineWeightUpdating_) {
			infoTypeWeights_[infoType.ordinal()] = WeightedHeuristic
					.updateWeight(infoTypeWeights_[infoType.ordinal()], weight,
							DEFAULT_ALPHA);
			updateWeight(weight);
		}
	}
}
