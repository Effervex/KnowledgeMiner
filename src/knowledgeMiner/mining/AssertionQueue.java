/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;

/**
 * An assertion queue is a specialised {@link WeightedSet} that contains only
 * mined assertions represented in an ordered collection. Generally, each
 * element in the assertion queue comes from the exact same information source,
 * but can be interpreted in multiple ways.
 * 
 * @author Sam Sarjant
 */
public class AssertionQueue extends HierarchicalWeightedSet<MinedAssertion>
		implements Queue<MinedAssertion> {
	private static final long serialVersionUID = 1L;
	/** The source of the assertions in this queue. */
	private HeuristicProvenance source_;

	/**
	 * Constructor for a new AssertionQueue
	 * 
	 */
	public AssertionQueue() {
		super();
	}

	/**
	 * Constructor for a new AssertionQueue
	 * 
	 */
	public AssertionQueue(HeuristicProvenance provenance) {
		super();
		source_ = provenance;
	}

	@SuppressWarnings("unchecked")
	@Override
	public WeightedSet<MinedAssertion> cleanEmptyParents() {
		// Remove duplicate children
		if (lowerQueues_ != null) {
			Set<WeightedSet<MinedAssertion>> newLower = new HashSet<>();
			for (WeightedSet<MinedAssertion> lower : lowerQueues_
					.toArray(new WeightedSet[lowerQueues_.size()])) {
				if (lower instanceof AssertionQueue)
					lower.removeAll(this);
				if (!lower.isEmpty())
					newLower.add(lower);
			}
			lowerQueues_ = newLower;
		}
		return super.cleanEmptyParents();
	}

	@Override
	public AssertionQueue clone() {
		AssertionQueue clone = new AssertionQueue(source_);
		clone.addAll(this);
		return clone;
	}

	@Override
	public MinedAssertion element() {
		return getOrdered().first();
	}

	public Set<AssertionQueue> getSubAssertionQueues() {
		Set<AssertionQueue> subAQs = new HashSet<>();
		for (WeightedSet<? extends MinedAssertion> subSet : getSubSets()) {
			AssertionQueue subAQ = new AssertionQueue(this.source_);
			subAQ.addAll(subSet);
			subAQs.add(subAQ);
		}
		return subAQs;
	}

	public HeuristicProvenance getProvenance() {
		return source_;
	}

	/**
	 * If the assertions in this queue are hierarchical.
	 * 
	 * @return True if at least the first assertion is hierarchical.
	 */
	public boolean isHierarchical() {
		if (isEmpty())
			return false;
		Iterator<MinedAssertion> iter = iterator();
		if (iter.hasNext())
			return iterator().next().isHierarchical();
		else {
			for (AssertionQueue subQueue : getSubAssertionQueues()) {
				if (subQueue.isHierarchical())
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean offer(MinedAssertion e) {
		return add(e);
	}

	@Override
	public MinedAssertion peek() {
		if (size() == 0)
			return null;
		return element();
	}

	@Override
	public MinedAssertion poll() {
		if (size() == 0)
			return null;
		return remove();
	}

	@Override
	public MinedAssertion remove() {
		if (size() == 0)
			return null;
		MinedAssertion assertion = element();
		remove(assertion);
		return assertion;
	}
}
