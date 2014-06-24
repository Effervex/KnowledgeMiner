/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import knowledgeMiner.TermStanding;

/**
 * 
 * @author Sam Sarjant
 */
public class WeightedStanding extends WeightedInformation {
	private static final long serialVersionUID = 1L;

	/**
	 * The amount of confidence a standing must have to classify it one way or
	 * another.
	 */
	public static final float VOTING_CONFIDENCE = 0.95f;

	/** The actual found standing after testing this assertion. */
	private TermStanding actualStanding_;

	/** The calculated sum of the standings. */
	private double[] calculatedSum_;

	/**
	 * The sources of this {@link WeightedInformation}, with given standing
	 * weights.
	 */
	private Map<String, double[]> sourceMappings_;

	/**
	 * Constructor for a new WeightedStanding
	 * 
	 */
	public WeightedStanding() {
		super(null);
		sourceMappings_ = new HashMap<>();
	}

	/**
	 * Constructor for the creating the actual {@link WeightedStanding}.
	 * 
	 * @param actual
	 *            The actual standing.
	 */
	public WeightedStanding(TermStanding actual) {
		super(null);
		sourceMappings_ = new HashMap<>();
		actualStanding_ = actual;
	}

	/**
	 * Constructor for a new WeightedStanding
	 * 
	 * @param source
	 */
	public WeightedStanding(TermStanding standing, HeuristicProvenance source) {
		super(null);
		sourceMappings_ = new HashMap<>();
		addStanding(source, standing);
	}

	/**
	 * Calculates the sum of the weights making up this standing.
	 */
	private void calculateSum() {
		if (calculatedSum_ == null) {
			calculatedSum_ = new double[TermStanding.values().length];
			for (double[] sourceWeights : sourceMappings_.values()) {
				for (int i = 0; i < calculatedSum_.length; i++)
					calculatedSum_[i] += sourceWeights[i];
			}
		}
	}

	private TermStanding getStanding(double[] standingArray) {
		double weight = getWeight();
		if (standingArray[TermStanding.INDIVIDUAL.ordinal()] == standingArray[TermStanding.COLLECTION
				.ordinal()] || weight < VOTING_CONFIDENCE)
			return TermStanding.UNKNOWN;

		if (standingArray[TermStanding.INDIVIDUAL.ordinal()] > standingArray[TermStanding.COLLECTION
				.ordinal()])
			return TermStanding.INDIVIDUAL;
		else
			return TermStanding.COLLECTION;
	}

	@Override
	protected Double determineStatusWeight(MiningHeuristic source) {
		TermStanding standing = getStanding(sourceMappings_.get(source
				.getHeuristicName()));
		if (standing == TermStanding.UNKNOWN)
			return null;

		// If this heuristic predicted standing, update it based whether
		// it was correct or not.
		if (standing == actualStanding_)
			return 1d;
		else
			return 0d;
	}

	@Override
	protected InformationType getInfoType() {
		return InformationType.STANDING;
	}

	@Override
	protected boolean needToUpdate() {
		return actualStanding_ != TermStanding.UNKNOWN;
	}

	/**
	 * Adds standing information to the source-mapped map.
	 * 
	 * @param source
	 *            The source of the information.
	 * @param standing
	 *            The standing of the information.
	 */
	public void addStanding(HeuristicProvenance source, TermStanding standing) {
		if (source == null)
			return;

		if (!heuristics_.contains(source))
			heuristics_.add(source);

		double[] standingArray = getStandingArray(source.getHeuristic()
				.getHeuristicName());
		standingArray[standing.ordinal()] += source.getHeuristic()
				.getInfoTypeWeight(getInfoType());
		calculatedSum_ = null;
	}

	private double[] getStandingArray(String mh) {
		double[] standingArray = sourceMappings_.get(mh);
		if (standingArray == null) {
			standingArray = new double[3];
			sourceMappings_.put(mh, standingArray);
		}
		return standingArray;
	}

	/**
	 * Returns the sources that predicted this standing.
	 * 
	 * @return The predicting sources.
	 */
	public Collection<HeuristicProvenance> getPositiveSources() {
		TermStanding standing = getStanding();
		Collection<HeuristicProvenance> positiveSource = new HashSet<>();
		for (HeuristicProvenance mh : heuristics_) {
			if (getStanding(sourceMappings_.get(mh.getHeuristic()
					.getHeuristicName())) == standing)
				positiveSource.add(mh);
		}
		return positiveSource;
	}

	/**
	 * Gets the term standing based on whichever standing has the larger weight.
	 * 
	 * @return The majority TermStanding.
	 */
	public TermStanding getStanding() {
		if (actualStanding_ != null)
			return actualStanding_;
		calculateSum();
		return getStanding(calculatedSum_);
	}

	@Override
	public double getWeight() {
		calculateSum();
		if (calculatedSum_[TermStanding.INDIVIDUAL.ordinal()] == calculatedSum_[TermStanding.COLLECTION
				.ordinal()])
			return 1;

		double sum = calculatedSum_[TermStanding.INDIVIDUAL.ordinal()]
				+ calculatedSum_[TermStanding.COLLECTION.ordinal()];
		if (calculatedSum_[TermStanding.INDIVIDUAL.ordinal()] > calculatedSum_[TermStanding.COLLECTION
				.ordinal()])
			return calculatedSum_[TermStanding.INDIVIDUAL.ordinal()] / sum;
		else
			return calculatedSum_[TermStanding.COLLECTION.ordinal()] / sum;
	}

	/**
	 * If no standing information has been recorded.
	 * 
	 * @return True if no standing information has been recorded yet.
	 */
	public boolean isEmpty() {
		return calculatedSum_ == null && actualStanding_ == null;
	}

	/**
	 * Adds standing information to this WeightedStanding, from the given
	 * source.
	 * 
	 * @param standing
	 *            The standing to add.
	 * @param source
	 *            The source of the information. If null, then the standing is
	 *            considered absolute.
	 */
	public boolean mergeInformation(TermStanding standing,
			HeuristicProvenance source) {
		addStanding(source, standing);
		return true;
	}

	@Override
	public boolean mergeInformation(WeightedInformation standing) {
		if (standing != null && standing instanceof WeightedStanding) {
			Map<String, double[]> otherMappings = ((WeightedStanding) standing).sourceMappings_;
			for (String mh : otherMappings.keySet()) {
				double[] doubleStanding = getStandingArray(mh);
				double[] otherWeights = otherMappings.get(mh);
				for (int i = 0; i < doubleStanding.length; i++)
					doubleStanding[i] += otherWeights[i];
			}
			return true;
		}
		return false;
	}

	public void setActualStanding(TermStanding actual) {
		actualStanding_ = actual;
	}

	public String toParsableString() {
		StringBuilder buffer = new StringBuilder(getStanding().toString());
		calculateSum();
		for (int i = 0; i < calculatedSum_.length; i++) {
			buffer.append("\t" + calculatedSum_[i]);
		}
		return buffer.toString();
	}

	@Override
	public String toString() {
		int status = getStatus();
		return status + " " + getStanding() + " (" + getWeight() + ")";
	}
}
