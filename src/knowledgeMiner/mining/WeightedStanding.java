/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import knowledgeMiner.TermStanding;
import util.Mergeable;

/**
 * A class representing the evidence of standing gathered.
 * 
 * @author Sam Sarjant
 */
public class WeightedStanding implements Mergeable<WeightedStanding>,
		Serializable {
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
		if (totalWeight_ == 0) {
			// Check if new
			weights_ = Arrays.copyOf(otherInfo.weights_, weights_.length);
			totalWeight_ = otherInfo.totalWeight_;
			heuristicProvenance_ = Arrays
					.copyOf(otherInfo.heuristicProvenance_,
							heuristicProvenance_.length);
			return true;
		}
		
		// Normalise and merge.
		normaliseViaGlobal(null);
		WeightedStanding copy = new WeightedStanding(otherInfo);
		copy.normaliseViaGlobal(null);
		for (int i = 0; i < weights_.length; i++) {
			weights_[i] += copy.weights_[i];
			if (copy.heuristicProvenance_[i] != null) {
				if (heuristicProvenance_[i] == null)
					heuristicProvenance_[i] = new HashSet<>();
				heuristicProvenance_[i].addAll(copy.heuristicProvenance_[i]);
			}
		}
		totalWeight_ += copy.totalWeight_;
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

	@Override
	public String toString() {
		float indvWeight = weights_[TermStanding.INDIVIDUAL.ordinal()] + 1;
		float collWeight = weights_[TermStanding.COLLECTION.ordinal()] + 1;
		float totalWeight = totalWeight_ + 2;
		return "IND:" + (indvWeight / totalWeight) + ", COLL:"
				+ (collWeight / totalWeight);
	}

	public float getActualWeight(TermStanding termStanding) {
		return weights_[termStanding.ordinal()];
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

	public float getRawWeight(TermStanding type) {
		return weights_[type.ordinal()];
	}

	public float getNormalisedWeight(TermStanding type) {
		return (weights_[type.ordinal()] + 1) / (totalWeight_ + 2);
	}

	public void normaliseViaGlobal(WeightedStanding global) {
		if (totalWeight_ == 0 || (totalWeight_ == 1 && global == null))
			return;

		if (global == null || global.totalWeight_ > 0) {
			// Divide the normalised weight by the global normalised weight
			float sum = 0;
			float globalIndv = (global != null) ? global
					.getNormalisedWeight(TermStanding.INDIVIDUAL) : 1;
			weights_[TermStanding.INDIVIDUAL.ordinal()] = getNormalisedWeight(TermStanding.INDIVIDUAL)
					/ globalIndv;
			sum += weights_[TermStanding.INDIVIDUAL.ordinal()];
			float globalColl = (global != null) ? global
					.getNormalisedWeight(TermStanding.COLLECTION) : 1;
			weights_[TermStanding.COLLECTION.ordinal()] = getNormalisedWeight(TermStanding.COLLECTION)
					/ globalColl;
			sum += weights_[TermStanding.COLLECTION.ordinal()];

			// Normalise the sums
			weights_[TermStanding.INDIVIDUAL.ordinal()] /= sum;
			weights_[TermStanding.COLLECTION.ordinal()] /= sum;
			totalWeight_ = 1;
		}
	}
}
