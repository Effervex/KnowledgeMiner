/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic;
import util.Mergeable;
import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;

/**
 * An object representing the information extracted from an article from one or
 * more heuristics.
 * 
 * @author Sam Sarjant
 */
public class MinedInformation implements Mergeable<MinedInformation>,
		Serializable {
	private static final long serialVersionUID = 1L;

	/** The bitwise int for all info types being true. */
	public static final int ALL_TYPES = (1 << InformationType.values().length) - 1;

	/** The non-parentage assertions found for this concept */
	private Collection<PartialAssertion> assertions_ = new HashSet<>();

	/**
	 * The assertions that are non-refutable and the selected disambiguated
	 * assertions.
	 */
	private Collection<DefiniteAssertion> concreteAssertions_ = new HashSet<>();

	/** The assertions to make during disjointness disambiguation. */
	private Collection<DefiniteAssertion> concreteParentageAssertions_ = new HashSet<>();

	/**
	 * If there are any assertions that COULD be resolved as parentage
	 * assertions.
	 */
	private boolean hasParentageAssertions_ = false;

	/** The bitwise representation of the mined information. */
	private int minedTypes_ = -1;

	/** The self ref to the mappable article. */
	private WikipediaMappedConcept selfRef_;

	/** The article being processed. */
	protected int articleID_;

	/** The type of infobox this article contains (if any). */
	protected List<String> infoboxType_ = null;

	/** The extracted standing for articles. */
	protected Map<Integer, WeightedStanding> standing_ = new HashedMap<>();

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
		selfRef_ = WikipediaArticleMiningHeuristic
				.createSelfRefConcept(articleID_);
	}

	/**
	 * Adds an assertion to this information.
	 * 
	 * @param singleAssertion
	 *            The assertion to add.
	 */
	public void addAssertion(MinedAssertion singleAssertion) {
		if (singleAssertion == null)
			return;
		if (singleAssertion instanceof DefiniteAssertion) {
			// A definite assertion - add to concretes.
			concreteAssertions_.add((DefiniteAssertion) singleAssertion);
			if (singleAssertion.isHierarchical()) {
				concreteParentageAssertions_
						.add((DefiniteAssertion) singleAssertion);
				hasParentageAssertions_ = true;
			}
		} else {
			// Only partially complete - add to disambiguatable.
			assertions_.add((PartialAssertion) singleAssertion);
			if (singleAssertion.isHierarchical())
				hasParentageAssertions_ = true;
		}
	}

	/**
	 * Adds an assertion to this information from the predicate and args.
	 * 
	 * @param predicate
	 *            The predicate of the assertion.
	 * @param provenance
	 *            The source of the assertion.
	 * @param args
	 *            The arguments of the assertion - can be
	 *            {@link MappableConcept}.
	 */
	public void addAssertion(OntologyConcept predicate,
			HeuristicProvenance provenance, AssertionArgument... args) {
		if (predicate == null || args == null || args.length == 0)
			return;
		boolean isMappable = false;
		for (AssertionArgument aa : args) {
			if (aa instanceof MappableConcept) {
				isMappable = true;
				break;
			}
		}

		MinedAssertion assertion = null;
		if (isMappable)
			assertion = new PartialAssertion(predicate, provenance, args);
		else
			assertion = new DefiniteAssertion(predicate, provenance,
					(OntologyConcept[]) args);
		addAssertion(assertion);
	}

	/**
	 * Adds a child article of this article to the information.
	 * 
	 * @param childArt
	 *            The child article to add.
	 * @param provenance
	 *            The source of the assertion.
	 */
	public void addChild(MappableConcept childArt,
			HeuristicProvenance provenance) {
		addAssertion(CycConstants.ISA_GENLS.getConcept(), provenance, childArt,
				selfRef_);
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

	/**
	 * Adds a parent article of this article to the information.
	 * 
	 * @param parentArt
	 *            The parent article to add.
	 * @param provenance
	 *            The source of the assertion.
	 */
	public void addParent(MappableConcept parentArt,
			HeuristicProvenance provenance) {
		addAssertion(CycConstants.ISA_GENLS.getConcept(), provenance, selfRef_,
				parentArt);
		hasParentageAssertions_ = true;
	}

	public void addStandingInformation(TermStanding standing, int article,
			double weight, HeuristicProvenance provenance) {
		WeightedStanding ws = getArticleStanding(article);
		ws.addStanding(provenance, standing, weight);
	}

	public void addStandingInformation(TermStanding standing, double weight,
			HeuristicProvenance provenance) {
		addStandingInformation(standing, articleID_, weight, provenance);
	}

	public WeightedStanding getArticleStanding(int article) {
		WeightedStanding ws = standing_.get(article);
		if (ws == null) {
			ws = new WeightedStanding();
			standing_.put(article, ws);
		}
		return ws;
	}

	public void addStandingInformation(WeightedStanding standing, int article)
			throws Exception {
		WeightedStanding ws = getArticleStanding(article);
		ws.mergeInformation(standing);
	}

	public void clearInformation() {
		assertions_.clear();
		standing_ = new HashedMap<>();
		infoboxType_ = null;
		concreteAssertions_.clear();
		concreteParentageAssertions_.clear();
		minedTypes_ = -1;
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

	/**
	 * Gets the unresolved assertion queues.
	 * 
	 * @return The unresolved assertion queues.
	 */
	public Collection<PartialAssertion> getAssertions() {
		return assertions_;
	}

	public Collection<DefiniteAssertion> getConcreteAssertions() {
		return concreteAssertions_;
	}

	public Collection<DefiniteAssertion> getConcreteParentageAssertions() {
		return concreteParentageAssertions_;
	}

	public List<String> getInfoboxTypes() {
		return infoboxType_;
	}

	public MappableConcept getMappableSelfRef() {
		return selfRef_;
	}

	public int getMinedInformation() {
		if (minedTypes_ == -1)
			return 0;
		return minedTypes_;
	}

	public Map<Integer, WeightedStanding> getAllMinedStanding() {
		return standing_;
	}

	public WeightedStanding getStanding() {
		return getArticleStanding(articleID_);
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + articleID_;
		result = prime * result
				+ ((assertions_ == null) ? 0 : assertions_.hashCode());
		result = prime
				* result
				+ ((concreteAssertions_ == null) ? 0 : concreteAssertions_
						.hashCode());
		result = prime * result
				+ ((infoboxType_ == null) ? 0 : infoboxType_.hashCode());
		result = prime * result + minedTypes_;
		result = prime * result
				+ ((standing_ == null) ? 0 : standing_.hashCode());
		return result;
	}

	public boolean hasParentageAssertions() {
		return hasParentageAssertions_;
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

		for (PartialAssertion assertionQueue : otherInfo.assertions_) {
			if (recreateInternals)
				addAssertion(assertionQueue.clone());
			else
				addAssertion(assertionQueue);
		}
		for (DefiniteAssertion concrete : otherInfo.concreteAssertions_) {
			if (recreateInternals)
				addAssertion(concrete.clone());
			else
				addAssertion(concrete);
		}
		if (otherInfo.infoboxType_ != null)
			infoboxType_ = otherInfo.infoboxType_;
		if (minedTypes_ == -1)
			minedTypes_ = otherInfo.minedTypes_;
		else
			minedTypes_ &= otherInfo.minedTypes_;

		// Resolve standing
		for (Integer art : otherInfo.standing_.keySet())
			addStandingInformation(otherInfo.getArticleStanding(art), art);
		return true;
	}

	public void setInfoboxTypes(List<String> infoboxTypes) {
		infoboxType_ = infoboxTypes;
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
		// Parentage
		if (!concreteAssertions_.isEmpty())
			buffer.append("\nConcrete parentage assertions: "
					+ concreteParentageAssertions_);
		return buffer.toString();
	}
}
