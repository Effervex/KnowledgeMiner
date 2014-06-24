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

import util.Weighted;
import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class MiningHeuristic extends WeightedHeuristic {

	/** The information this heuristic produces. */
	private final int informationProduced_;

	/** The weights of each infoType held by this heuristic. */
	private final WeightedInformationType[] infoTypeWeights_;

	protected final CycMiner miner_;

	protected final HeuristicProvenance basicProvenance_;

	/**
	 * Constructor for a new MiningHeuristic
	 * 
	 * @param mapper
	 *            The mapping class.
	 * @param miner
	 *            The mining class.
	 */
	public MiningHeuristic(CycMapper mapper, CycMiner miner) {
		super(mapper);
		miner_ = miner;
		basicProvenance_ = new HeuristicProvenance(this, null);

		boolean[] infoTypes = new boolean[InformationType.values().length];
		infoTypeWeights_ = new WeightedInformationType[infoTypes.length];
		setInformationTypes(infoTypes);
		int bitwise = 0;
		for (int i = 0; i < infoTypes.length; i++) {
			if (infoTypes[i])
				bitwise += 1 << i;
			infoTypeWeights_[i] = new WeightedInformationType(INITIAL_WEIGHT);
		}

		informationProduced_ = bitwise;
	}

	/**
	 * Creates a mined assertion using this heuristic to tag the assertion.
	 * 
	 * @param relation
	 *            The assertion relation.
	 * @param arg1
	 *            The first argument of the assertion (typically the term).
	 * @param arg2
	 *            The second argument of the assertion.
	 * @return The new assertion.
	 * @throws IllegalAccessException
	 *             If the assertions haven't been initialised yet.
	 */
	protected final MinedAssertion createAssertion(OntologyConcept relation,
			OntologyConcept arg1, OntologyConcept arg2) throws IllegalAccessException {
		return new MinedAssertion(relation, arg1, arg2, null, basicProvenance_);
	}

	/**
	 * Creates a partially-qualified assertion by leaving out a single argument
	 * to be filled in by a Cyc Term later.
	 * 
	 * @param relation
	 *            The assertion relation.
	 * @param arg2
	 *            The second argument of the assertion.
	 * @return The new partially-qualified assertion.
	 * @throws IllegalAccessException
	 *             If the assertions haven't been initialised yet.
	 */
	protected MinedAssertion createAssertion(OntologyConcept relation,
			OntologyConcept arg2) throws IllegalAccessException {
		return createAssertion(relation, OntologyConcept.PLACEHOLDER, arg2);
	}

	/**
	 * Adds a parent term to this mined information.
	 * 
	 * @param childTerm
	 *            The child term.
	 * @param parentTerm
	 *            The parent term being added.
	 * @param article
	 *            The assertion source.
	 * @return The new assertion.
	 * @throws IllegalAccessException
	 */
	protected final MinedAssertion createParentAssertion(OntologyConcept childTerm,
			OntologyConcept parentTerm, int article) throws IllegalAccessException {
		return new MinedAssertion(CycConstants.ISA_GENLS.getConcept(),
				childTerm, parentTerm, null, basicProvenance_);
	}

	/**
	 * Creates a partially-qualified parental assertion by leaving out the child
	 * argument to be filled in by a Cyc Term later.
	 * 
	 * @param parentTerm
	 *            The parent term.
	 * @param article
	 *            The article for which a parent assertion is being created.
	 * @return The new partially-qualified parental assertion.
	 * @throws IllegalAccessException
	 *             If the assertions haven't been initialised yet.
	 */
	protected MinedAssertion createParentAssertion(OntologyConcept parentTerm,
			int article) throws IllegalAccessException {
		return createParentAssertion(OntologyConcept.PLACEHOLDER, parentTerm,
				article);
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
		return weight_ * infoTypeWeights_[type.ordinal()].getWeight();
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
			infoTypeWeights_[infoType.ordinal()].updateWeight(weight);
			updateWeight(weight);
		}
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
	 * A small class for noting the weight of the individual information types
	 * produced by this heuristic.
	 * 
	 * @author Sam Sarjant
	 */
	private class WeightedInformationType implements Weighted {
		private double infoWeight_;

		/**
		 * Constructor for a new MiningHeuristic.WeightedInformationType
		 * 
		 */
		public WeightedInformationType(double weight) {
			infoWeight_ = weight;
		}

		@Override
		public double getWeight() {
			return infoWeight_;
		}

		@Override
		public void setWeight(double weight) {
			infoWeight_ = weight;
		}

		public void updateWeight(double updateValue) {
			infoWeight_ = WeightedHeuristic.updateWeight(infoWeight_,
					updateValue, DEFAULT_ALPHA);
		}

	}
}
