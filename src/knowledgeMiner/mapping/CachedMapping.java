/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping;

import util.collection.WeightedSet;

/**
 * A basic class that notes down a mapping.
 * 
 * @author Sam Sarjant
 */
public class CachedMapping<Source, Target> {
	/** The target mappings found. */
	private WeightedSet<Target> mappings_;
	/** The source of the mapping. */
	private Source source_;
	/** An additional distinguishing object. */
	private Object distinguishingObject_;

	/**
	 * Constructor for a new CachedMapping
	 * 
	 * @param s
	 *            The source to map for.
	 */
	public CachedMapping(Source s) {
		source_ = s;
	}

	public void setDistinguishingObject(Object obj) {
		distinguishingObject_ = obj;
	}

	public WeightedSet<Target> getMappings() {
		return mappings_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((distinguishingObject_ == null) ? 0 : distinguishingObject_
						.hashCode());
		result = prime * result + ((source_ == null) ? 0 : source_.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("rawtypes")
		CachedMapping other = (CachedMapping) obj;
		if (distinguishingObject_ == null) {
			if (other.distinguishingObject_ != null)
				return false;
		} else if (!distinguishingObject_.equals(other.distinguishingObject_))
			return false;
		if (source_ == null) {
			if (other.source_ != null)
				return false;
		} else if (!source_.equals(other.source_))
			return false;
		return true;
	}

	public void setMapping(WeightedSet<Target> mappedTarget) {
		if (mappedTarget == null)
			mappings_ = new WeightedSet<>(0);
		else
			mappings_ = mappedTarget;
	}

	@Override
	public String toString() {
		return source_.toString() + " (" + mappings_.size() + " results)";
	}
}
