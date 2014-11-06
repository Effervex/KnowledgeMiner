/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.io.Serializable;

import util.Weighted;

/**
 * A class which represents a single mined information thing with a weight and a
 * mining source.
 * 
 * @author Sam Sarjant
 */
public abstract class WeightedInformation implements Weighted,
		Serializable {
	private static final long serialVersionUID = -4325745875771472944L;

	/** The post-assertion status of the asserted information. */
	private int status_;

	/** The source of this information. */
	protected HeuristicProvenance heuristic_;

	/**
	 * Gets the type of information this object represents.
	 * 
	 * @return The type of information represented by this class.
	 */
	protected abstract InformationType getInfoType();

	public WeightedInformation(HeuristicProvenance provenance) {
		heuristic_ = provenance;
	}

	public final HeuristicProvenance getProvenance() {
		return heuristic_;
	}

	public int getStatus() {
		return status_;
	}

	public void setStatus(int status) {
		status_ = status;
	}

	@Override
	public void setWeight(double weight) {
		// Do nothing - default
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
