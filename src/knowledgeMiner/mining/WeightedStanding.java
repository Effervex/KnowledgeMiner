/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import knowledgeMiner.TermStanding;
import util.Mergeable;
import cyc.CycConstants;
import cyc.MappableConcept;

/**
 * A class representing the evidence of standing gathered.
 * 
 * @author Sam Sarjant
 */
public class WeightedStanding implements Mergeable<WeightedStanding>, Serializable {
	private static final long serialVersionUID = 1L;

	/** The weights of Individual and Collection. */
	private float[] weights_ = new float[TermStanding.values().length];

	/** The provenence of learned weighting information. */
	@SuppressWarnings("unchecked")
	private Collection<HeuristicProvenance>[] heuristicProvenance_ = new Collection[TermStanding
			.values().length];

	/** The total summed weight of individuals and collections. */
	private float totalWeight_;

	public WeightedStanding() {
		totalWeight_ = 0;
	}

	public WeightedStanding(TermStanding standing) {
		weights_[standing.ordinal()] = 1;
		totalWeight_ = 1;
	}

	public WeightedStanding(WeightedStanding other) {
		weights_ = Arrays.copyOf(other.weights_, weights_.length);
		totalWeight_ = other.totalWeight_;
		heuristicProvenance_ = Arrays.copyOf(other.heuristicProvenance_,
				heuristicProvenance_.length);
	}

	@Override
	public boolean mergeInformation(WeightedStanding otherInfo)
			throws Exception {
		for (int i = 0; i < weights_.length; i++) {
			weights_[i] += otherInfo.weights_[i];
			if (i != TermStanding.UNKNOWN.ordinal())
				totalWeight_ += otherInfo.weights_[i];
		}
		return true;
	}

	public void addStanding(HeuristicProvenance provenance,
			TermStanding standing, double weight) {
		if (heuristicProvenance_[standing.ordinal()] == null)
			heuristicProvenance_[standing.ordinal()] = new HashSet<>();
		if (provenance != null)
			heuristicProvenance_[standing.ordinal()].add(provenance);
		weights_[standing.ordinal()] += weight;
		if (standing != TermStanding.UNKNOWN)
			totalWeight_ += weight;
	}

	public boolean isEmpty() {
		for (int i = 0; i < weights_.length; i++)
			if (weights_[i] != 0)
				return false;
		return true;
	}

	public Collection<PartialAssertion> asAssertions(MappableConcept coreConcept) {
		Collection<PartialAssertion> assertions = new ArrayList<>();
		double indvWeight = weights_[TermStanding.INDIVIDUAL.ordinal()] + 1;
		double collWeight = weights_[TermStanding.COLLECTION.ordinal()] + 1;
		double totalWeight = totalWeight_ + 2;

		// A weighted assertion for Individual and Collection
		if (indvWeight > 0) {
			HeuristicProvenance joinedProv = HeuristicProvenance
					.joinProvenance(heuristicProvenance_[TermStanding.INDIVIDUAL
							.ordinal()]);
			PartialAssertion indvAssertion = new PartialAssertion(
					CycConstants.ISA.getConcept(), joinedProv, coreConcept,
					CycConstants.INDIVIDUAL.getConcept());
			indvAssertion.setWeight(indvWeight / totalWeight);
			assertions.add(indvAssertion);
		}
		if (collWeight > 0) {
			HeuristicProvenance joinedProv = HeuristicProvenance
					.joinProvenance(heuristicProvenance_[TermStanding.COLLECTION
							.ordinal()]);
			PartialAssertion collAssertion = new PartialAssertion(
					CycConstants.ISA.getConcept(), joinedProv, coreConcept,
					CycConstants.COLLECTION.getConcept());
			collAssertion.setWeight(collWeight / totalWeight);
			assertions.add(collAssertion);
		}
		return assertions;
	}

	@Override
	public String toString() {
		double indvWeight = weights_[TermStanding.INDIVIDUAL.ordinal()] + 1;
		double collWeight = weights_[TermStanding.COLLECTION.ordinal()] + 1;
		double totalWeight = totalWeight_ + 2;
		return "IND:" + (indvWeight / totalWeight) + ", COLL:"
				+ (collWeight / totalWeight);
	}

	public double getActualWeight(TermStanding termStanding) {
		return weights_[termStanding.ordinal()];
	}
	
	public float getLaplaceNormalisedWeight(TermStanding termStanding) {
		return (weights_[termStanding.ordinal()] + 1) / (totalWeight_ + 2);
	}

	public boolean isIndividual() {
		if (totalWeight_ == 0)
			return true;
		return weights_[TermStanding.INDIVIDUAL.ordinal()] > 0;
	}

	public boolean isCollection() {
		if (totalWeight_ == 0)
			return true;
		return weights_[TermStanding.COLLECTION.ordinal()] > 0;
	}
}
