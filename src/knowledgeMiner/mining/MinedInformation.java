/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import graph.core.CommonConcepts;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.TermStanding;
import util.Mergeable;
import util.UtilityMethods;
import util.WeightedSetComparator;
import cyc.OntologyConcept;

/**
 * An object representing the information extracted from an article from one or
 * more heuristics.
 * 
 * @author Sam Sarjant
 */
public class MinedInformation implements Mergeable<MinedInformation>, Serializable {
	private static final long serialVersionUID = 1L;

	/** The bitwise int for all info types being true. */
	public static final int ALL_TYPES = (1 << InformationType.values().length) - 1;

	/** The bitwise representation of the mined information. */
	private int minedTypes_ = -1;

	/** The parentage assertions found for this concept. */
	private SortedSet<AssertionQueue> parentageAssertions_ = new TreeSet<>(
			new WeightedSetComparator());

	/** The article being processed. */
	protected int articleID_;

	/** The non-parentage assertions found for this concept */
	private SortedSet<AssertionQueue> assertions_ = new TreeSet<>(
			new WeightedSetComparator());

	/** The potential child articles for this term. */
	protected Collection<Integer> childArticles_ = new HashSet<>();

	/**
	 * The assertions that are non-refutable and the selected disambiguated
	 * assertions.
	 */
	private Collection<MinedAssertion> concreteAssertions_ = new HashSet<>();

	/** The assertions to make during disjointness disambiguation. */
	private Collection<MinedAssertion> concreteParentageAssertions_ = new HashSet<>();

	/** The type of infobox this article contains (if any). */
	protected List<String> infoboxType_ = null;

	/** The weight of the mapping between the concept and article [0-1] */
	protected double miningWeight_ = 1;

	/** The inferred standing of the article. */
	protected WeightedStanding standing_ = new WeightedStanding();

	/**
	 * Constructor for a new MinedInformation
	 * 
	 * @param cycTerm
	 *            The term this information represents.
	 * @param article
	 *            The article to extract information from.
	 */
	public MinedInformation(int article) {
		articleID_ = article;
	}

	/**
	 * Gets the unresolved assertion queues.
	 * 
	 * @return The unresolved assertion queues.
	 */
	public SortedSet<AssertionQueue> getAmbiguousAssertions() {
		return assertions_;
	}

	/**
	 * Adds an AssertionQueue to this module, such that the assertions are later
	 * used during disambiguation to calculate the level of confidence in the
	 * mapping.
	 * 
	 * @param assertion
	 *            The assertion(s) being added.
	 */
	public void addAssertion(AssertionQueue assertion) {
		if (assertion.size() == 0)
			if (assertion.hasSubSets())
				for (AssertionQueue aq : assertion.getSubAssertionQueues())
					addAssertion(aq);
			else
				return;
		else {
			if (assertion.isHierarchical())
				parentageAssertions_.add(assertion);

			// Add all assertions to assertions_
			assertions_.add(assertion);
		}
	}

	public void addChildArticles(Collection<Integer> articles) {
		UtilityMethods.removeNegOnes(articles);
		childArticles_.addAll(articles);
	}

	/**
	 * Adds a concrete, non-disambiguatable assertion to the mined information.
	 * 
	 * @param minedAssertion
	 *            The concrete assertion to add.
	 */
	public void addConcreteAssertion(MinedAssertion minedAssertion) {
		if (minedAssertion.isHierarchical())
			concreteParentageAssertions_.add(minedAssertion);
		concreteAssertions_.add(minedAssertion);
	}

	/**
	 * Adds a mined information type to the mined types this object has used.
	 * 
	 * @param infoType
	 *            The information types mined.
	 */
	public void addMinedInfoType(InformationType infoType) {
		int val = 1 << infoType.ordinal();
		if (minedTypes_ == -1)
			minedTypes_ = val;
		minedTypes_ |= val;
	}

	/**
	 * Adds a mined information type to the mined types this object has used.
	 * 
	 * @param infoType
	 *            The information types mined.
	 */
	public void addMinedInfoType(int infoType) {
		if (minedTypes_ == -1)
			minedTypes_ = infoType;
		minedTypes_ |= infoType;
	}

	public void clearInformation() {
		assertions_.clear();
		childArticles_.clear();
		parentageAssertions_.clear();
		standing_ = new WeightedStanding();
		infoboxType_ = null;
		concreteAssertions_.clear();
		concreteParentageAssertions_.clear();
		minedTypes_ = -1;
		miningWeight_ = 1;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MinedInformation other = (MinedInformation) obj;
		if (articleID_ != other.articleID_)
			return false;
		if (assertions_ == null) {
			if (other.assertions_ != null)
				return false;
		} else if (!assertions_.equals(other.assertions_))
			return false;
		if (childArticles_ == null) {
			if (other.childArticles_ != null)
				return false;
		} else if (!childArticles_.equals(other.childArticles_))
			return false;
		if (concreteAssertions_ == null) {
			if (other.concreteAssertions_ != null)
				return false;
		} else if (!concreteAssertions_.equals(other.concreteAssertions_))
			return false;
		if (infoboxType_ == null) {
			if (other.infoboxType_ != null)
				return false;
		} else if (!infoboxType_.equals(other.infoboxType_))
			return false;
		if (minedTypes_ != other.minedTypes_)
			return false;
		if (Double.doubleToLongBits(miningWeight_) != Double
				.doubleToLongBits(other.miningWeight_))
			return false;
		if (parentageAssertions_ == null) {
			if (other.parentageAssertions_ != null)
				return false;
		} else if (!parentageAssertions_.equals(other.parentageAssertions_))
			return false;
		if (standing_ == null) {
			if (other.standing_ != null)
				return false;
		} else if (!standing_.equals(other.standing_))
			return false;
		return true;
	}

	public Integer getArticle() {
		return articleID_;
	}

	public Collection<Integer> getChildArticles() {
		return childArticles_;
	}

	public Collection<MinedAssertion> getConcreteAssertions() {
		return concreteAssertions_;
	}

	public Collection<MinedAssertion> getConcreteParentageAssertions() {
		return concreteParentageAssertions_;
	}

	/**
	 * Grounds a floating parentage assertion to either isa or genls.
	 * 
	 * @param floating
	 *            The assertion to ground.
	 * @param standing
	 *            The standing to ground to.
	 * @throws Exception
	 */
	protected void groundAssertionStanding(MinedAssertion floating,
			TermStanding standing) throws Exception {
		MinedAssertion groundedAssertion = floating.clone();
		groundedAssertion.makeParentageAssertion(standing);
		concreteParentageAssertions_.remove(floating);
		concreteAssertions_.remove(floating);
		addConcreteAssertion(groundedAssertion);
	}

	public List<String> getInfoboxTypes() {
		return infoboxType_;
	}

	public int getMinedInformation() {
		if (minedTypes_ == -1)
			return 0;
		return minedTypes_;
	}

	public SortedSet<AssertionQueue> getParentageAssertions() {
		return parentageAssertions_;
	}

	public TermStanding getStanding() {
		return standing_.getStanding();
	}

	/**
	 * Returns a bitwise int of the information that has not been mined by this
	 * {@link MinedInformation} yet.
	 * 
	 * @return A bitwise int of the unmined information.
	 */
	public int getUnminedInformation() {
		if (minedTypes_ == -1)
			return ALL_TYPES;
		return minedTypes_ ^ ALL_TYPES;
	}

	public WeightedStanding getWeightedStanding() {
		if (standing_ == null)
			return new WeightedStanding(TermStanding.UNKNOWN);
		return standing_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + articleID_;
		result = prime * result
				+ ((assertions_ == null) ? 0 : assertions_.hashCode());
		result = prime * result
				+ ((childArticles_ == null) ? 0 : childArticles_.hashCode());
		result = prime
				* result
				+ ((concreteAssertions_ == null) ? 0 : concreteAssertions_
						.hashCode());
		result = prime * result
				+ ((infoboxType_ == null) ? 0 : infoboxType_.hashCode());
		result = prime * result + minedTypes_;
		long temp;
		temp = Double.doubleToLongBits(miningWeight_);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime
				* result
				+ ((parentageAssertions_ == null) ? 0 : parentageAssertions_
						.hashCode());
		result = prime * result
				+ ((standing_ == null) ? 0 : standing_.hashCode());
		return result;
	}

	/**
	 * Checks if the assertions made include a given parentage assertion and if
	 * so, adds that assertion as a concrete assertion. This forces asserted
	 * information to comply with the parentage assertion during disjointness
	 * disambiguation.
	 * 
	 * @param parentTerm
	 *            The parent term to search for in the mined information.
	 * @param ontology
	 *            The Cyc access.
	 * @return True if this mined information asserts that the term is a parent.
	 * @throws IllegalAccessException
	 *             If the assertions haven't been initialised.
	 */
	public boolean hasParent(OntologyConcept directParent, OntologySocket ontology)
			throws IllegalAccessException {
		for (AssertionQueue parentAssertion : parentageAssertions_) {
			// TODO Hierarchical
			for (MinedAssertion minedAssertion : parentAssertion
					.flattenHierarchy()) {
				// Equals parent term
				OntologyConcept parentTerm = minedAssertion.getArgs()[1];
				// Genls parent term
				if (directParent.toString().equals(parentTerm.toString())
						|| ontology.evaluate(null,
								CommonConcepts.ASSERTED_SENTENCE.getID(), "("
										+ CommonConcepts.GENLS.getID() + " "
										+ directParent.getIdentifier() + " "
										+ parentTerm.getIdentifier() + ")")) {
					// Reshape the parentage
					addConcreteAssertion(new MinedAssertion(minedAssertion,
							directParent, 2));
					return true;
				}
			}
		}
		return false;
	}

	public boolean isMined() {
		return minedTypes_ >= 0;
	}

	@Override
	public boolean mergeInformation(MinedInformation otherInfo)
			throws Exception {
		return mergeInformation(otherInfo, false);
	}

	/**
	 * If, during the merging, internal data should be recreated (cloned).
	 * 
	 * @param otherInfo
	 *            The other info to merge.
	 * @param recreateInternals
	 *            If internal data should be cloned.
	 * @return True if the merging was successful.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public boolean mergeInformation(MinedInformation otherInfo,
			boolean recreateInternals) throws Exception {
		// No info, do nothing.
		if (otherInfo == null)
			return true;

		// Non-matching information!
		if (articleID_ != otherInfo.articleID_)
			throw new Exception("Information does not match!");

		for (AssertionQueue assertionQueue : otherInfo.assertions_) {
			if (recreateInternals)
				addAssertion(assertionQueue.clone());
			else
				addAssertion(assertionQueue);
		}
		childArticles_.addAll(otherInfo.childArticles_);
		for (MinedAssertion concrete : otherInfo.concreteAssertions_) {
			if (recreateInternals)
				addConcreteAssertion(concrete.clone());
			else
				addConcreteAssertion(concrete);
		}
		if (otherInfo.infoboxType_ != null)
			infoboxType_ = otherInfo.infoboxType_;
		if (minedTypes_ == -1)
			minedTypes_ = otherInfo.minedTypes_;
		else
			minedTypes_ &= otherInfo.minedTypes_;

		// Resolve standing
		standing_.mergeInformation(otherInfo.standing_);
		return true;
	}

	/**
	 * Uses the grouped and tested information contained within this
	 * {@link MinedInformation} as training data for the heuristics that
	 * produced the information.
	 * 
	 * @param wmi
	 *            The WMI access.
	 */
	public void recordTrainingInfo(WMISocket wmi) {
		// Update every heuristic with global information
		for (MiningHeuristic heuristic : KnowledgeMiner.getInstance()
				.getMiner().getMiningHeuristics())
			heuristic.updateGlobal(this, wmi);

		// Update the heuristics that predicted the standing.
		standing_.updateHeuristics(wmi);

		// Update the heuristics that produced the assertions.
		for (WeightedInformation assertion : concreteAssertions_)
			assertion.updateHeuristics(wmi);
	}

	public void setInfoboxTypes(List<String> infoboxTypes) {
		infoboxType_ = infoboxTypes;
	}

	public void setStanding(WeightedStanding standing) {
		standing_ = standing;
	}

	public void setStandingStatus(int status, TermStanding actual) {
		standing_.setActualStanding(actual);
		standing_.setStatus(status);
	}

	/**
	 * Prints out any new info found in this mined information.
	 * 
	 * @return The new info string.
	 */
	public String toFlatString() {
		return toString().replaceAll("\\n", ", ");
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder("Mined info for " + articleID_);
		// Standing
		if (!standing_.isEmpty())
			buffer.append("\nStanding: " + standing_);
		if (infoboxType_ != null)
			buffer.append("\nInfobox type: " + infoboxType_);
		// Child Articles
		if (!childArticles_.isEmpty())
			buffer.append("\nChild articles: " + childArticles_);
		// Parentage
		if (!concreteAssertions_.isEmpty())
			buffer.append("\nConcrete parentage assertions: "
					+ concreteParentageAssertions_);
		if (miningWeight_ == -1) {
			// Relations
			if (!assertions_.isEmpty())
				buffer.append("\nDisambiguatable assertions: " + assertions_);
		}
		return buffer.toString();
	}
}
