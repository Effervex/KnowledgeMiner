/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import io.resources.WMISocket;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import util.Mergeable;
import util.Weighted;

/**
 * A class which represents a single mined information thing with a weight and a
 * mining source.
 * 
 * @author Sam Sarjant
 */
public abstract class WeightedInformation implements Weighted,
		Mergeable<WeightedInformation>, Serializable {
	private static final long serialVersionUID = -4325745875771472944L;

	/** The post-assertion status of the asserted information. */
	private int status_;

	/** The source of this information. */
	protected List<HeuristicProvenance> heuristics_ = new ArrayList<>();

	/**
	 * Gets the type of information this object represents.
	 * 
	 * @return The type of information represented by this class.
	 */
	protected abstract InformationType getInfoType();

	public WeightedInformation(HeuristicProvenance provenance) {
		if (provenance != null && !heuristics_.contains(provenance))
			heuristics_.add(provenance);
	}

	public final Collection<HeuristicProvenance> getSources() {
		return heuristics_;
	}

	public int getStatus() {
		return status_;
	}

	@Override
	public double getWeight() {
		double weight = 0;
		for (HeuristicProvenance source : getSources())
			weight += source.getHeuristic().getInfoTypeWeight(getInfoType());

		return weight;
	}

	/**
	 * Merges other information with this information.
	 * 
	 * @param otherInfo
	 *            The other information being merged.
	 * @return True if the information was merged.
	 */
	@Override
	public boolean mergeInformation(WeightedInformation otherInfo) {
		if (equals(otherInfo))
			return heuristics_.addAll(((MinedAssertion) otherInfo).heuristics_);
		return false;
	}

	public void setStatus(int status) {
		status_ = status;
	}

	@Override
	public final void setWeight(double weight) {
		// Do nothing
	}

	/**
	 * Updates the heuristics that produced this assertion.
	 * 
	 * @param wmi
	 *            The WMI access.
	 */
	public final void updateHeuristics(WMISocket wmi) {
		if (needToUpdate()) {
			for (HeuristicProvenance source : getSources()) {
				Double weight = determineStatusWeight((MiningHeuristic) source
						.getHeuristic());
				if (weight == null)
					weight = 0d;
				source.getHeuristic().updateViaAssertion(this,
						source.getDetails(), weight, getInfoType(), wmi);
			}
		}
	}

	protected abstract boolean needToUpdate();

	/**
	 * Determines what weight the status has for the heuristic update procedure.
	 * 
	 * @param source
	 *            The source being updated, if necessary.
	 * @return The weight to use for the update, or null if no update necessary.
	 */
	protected abstract Double determineStatusWeight(MiningHeuristic source);
}
