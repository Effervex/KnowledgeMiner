/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.mapping.CycMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.Weighted;

/**
 * An abstract instantiation of the Heuristic implementation. This class uses
 * weights for its predictions.
 * 
 * @author Sam Sarjant
 */
public abstract class WeightedHeuristic implements Heuristic, Weighted {
	/** The default update rate. */
	public static final double DEFAULT_ALPHA = 0.1;

	/** The initial weight of the heuristic. */
	public static final double INITIAL_WEIGHT = 1.0;

	/** The name of this heuristic. */
	private final String heuristicName_;

	/** The instances being noted. */
	private Collection<String> instances_;

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/** The weight update step-size. */
	protected double alpha_;

	/** The mapping class for KnowledgeMiner. */
	protected CycMapper mapper_;

	/** If this heuristic should be precomputed. */
	protected final boolean usingPrecomputed_;

	/** The weight of the heuristic. */
	protected double weight_;

	public WeightedHeuristic(boolean usePrecomputed, CycMapper mapper) {
		usingPrecomputed_ = usePrecomputed;
		weight_ = INITIAL_WEIGHT;
		mapper_ = mapper;
		alpha_ = DEFAULT_ALPHA;

		// Form the heuristic name
		heuristicName_ = generateHeuristicName(this.getClass());
	}
	
	/**
	 * Initialises (if necessary) and gets the instances collection.
	 */
	private Collection<String> getInstanceCollection() {
		if (instances_ == null)
			instances_ = new ArrayList<>();
		return instances_;
	}

	@Override
	public String asTrainingInstance(Object instance)
			throws IllegalArgumentException {
		return instance.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WeightedHeuristic other = (WeightedHeuristic) obj;
		if (heuristicName_ == null) {
			if (other.heuristicName_ != null)
				return false;
		} else if (!heuristicName_.equals(other.heuristicName_))
			return false;
		return true;
	}

	@Override
	public String getARFFHeader(String file) {
		return "";
	}

	public String getHeuristicName() {
		return heuristicName_;
	}

	@Override
	public final double getWeight() {
		return weight_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((heuristicName_ == null) ? 0 : heuristicName_.hashCode());
		return result;
	}

	public final boolean isPrecomputed() {
		return usingPrecomputed_;
	}

	/**
	 * Prints the current heuristic's information, if necessary.
	 */
	@Override
	public void printHeuristicState() throws Exception {
		// Print nothing by default
	}

	@Override
	public final void recordInstance(Object instance) {
		Collection<String> instanceColl = getInstanceCollection();
		String trainingInstance = asTrainingInstance(instance);
		instanceColl.add(trainingInstance);
		logger.info("INSTANCE_RECORDED:\t{}", trainingInstance);
	}

	@Override
	public final void setWeight(double weight) {
		weight_ = weight;
	}

	@Override
	public String toString() {
		return getHeuristicName();
	}

	/**
	 * Updates the weight towards the new value.
	 * 
	 * @param updateValue
	 *            The update value.
	 */
	public void updateWeight(double updateValue) {
		weight_ = updateWeight(weight_, updateValue, alpha_);
	}

	public static String generateHeuristicName(Class<? extends Object> clazz) {
		return clazz.getSimpleName().replaceAll("(?<=[A-Z][^A-Z]{3})[^A-Z]+",
				"");
	}

	public static double updateWeight(double oldWeight, double newWeight,
			double stepSize) {
		if (newWeight < 0)
			newWeight = 0;
		else if (newWeight > 1)
			newWeight = 1;
		oldWeight = stepSize * newWeight + (1 - stepSize) * oldWeight;
		return oldWeight;
	}
}
