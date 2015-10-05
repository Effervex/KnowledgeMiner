/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

import graph.core.CommonConcepts;
import io.IOManager;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import cyc.CycConstants;
import cyc.OntologyConcept;
import cyc.PrimitiveConcept;
import cyc.StringConcept;

/**
 * A concept module contains both mapping and mining information for a concept
 * to assertable information. It also contains a weighted element such that the
 * weight corresponds to the confidence of the information found about the
 * concept.
 * 
 * @author Sam Sarjant
 */
public class ConceptModule extends MinedInformation implements
		Comparable<ConceptModule> {
	private static final long serialVersionUID = 1L;

	private static final float UNMINED_CONFIDENCE = 0.5f;

	/**
	 * A set of assertions that take effect if the concept meets the parent
	 * reqs.
	 */
	private Collection<MinedAssertion> autoAssertions_;

	/** The concept for which this information is about. */
	private OntologyConcept concept_;

	/** If the concept is (to be) newly created. */
	private boolean createdConcept_ = false;

	/** If mapping started with a Cyc term and maps to Wiki. */
	private boolean cycToWiki_;

	private transient DisjointnessDisambiguator dd_;

	private transient Collection<DefiniteAssertion> deletedAssertions_;

	private transient boolean disambiguated_ = false;

	/** The weight of the mapping between the concept and article [0-1] */
	private float mappingWeight_ = -1;

	/**
	 * A set of parents that, if this concept is a member of, will trigger the
	 * auto assertions.
	 */
	private Collection<OntologyConcept> parents_;

	/** The state of this Concept Module. */
	private MiningState state_ = MiningState.UNMINED;

	/** The type of concept this module represents. */
	private OntologyConcept type_ = null;

	/** The weight of the mapping between the concept and article [0-1] */
	protected float miningWeight_ = 1;

	/** If, after processing, this concept module imposed significant changes. */
	private boolean significantChange_ = false;

	/**
	 * Constructor for a new ConceptModule using just an article ID.
	 * 
	 * @param articleID
	 *            The article ID.
	 */
	public ConceptModule(int articleID) {
		super(articleID);
		state_ = MiningState.UNMAPPED;
		cycToWiki_ = false;
		mappingWeight_ = 1.0f;
	}

	/**
	 * Constructor for a new ConceptModule that begins with an article. The
	 * article may also use a parent term for guidance and can also potentially
	 * create a new term.
	 * 
	 * @param articleID
	 *            The article to investigate.
	 * @param parents
	 *            The parent concepts that must be met to perform the
	 *            assertions.
	 * @param autoAssertions
	 *            The assertions to apply if the concept meets the parent
	 *            collections.
	 */
	public ConceptModule(int articleID, Collection<OntologyConcept> parents,
			Collection<MinedAssertion> autoAssertions) {
		this(articleID);
		state_ = MiningState.UNMAPPED;
		addParentDetails(parents, autoAssertions);
		mappingWeight_ = 1.0f;
	}

	/**
	 * Constructor for a new ConceptModule that maps and mines a single term.
	 * 
	 * @param term
	 *            The term to map and mine.
	 */
	public ConceptModule(OntologyConcept term) {
		super(-1);
		concept_ = term;
		if (term != null)
			createdConcept_ = term.getID() == OntologySocket.NON_EXISTENT_ID;
		state_ = MiningState.UNMAPPED;
		cycToWiki_ = true;
		mappingWeight_ = 1.0f;
		deletedAssertions_ = new ArrayList<>();
	}

	/**
	 * Constructor for a new ConceptModule
	 * 
	 * @param concept
	 *            The concept this {@link ConceptModule} represents.
	 * @param article
	 *            The article the concept has been linked to.
	 * @param weight
	 *            The weight of the mapping.
	 * @param cycToWiki
	 *            The direction of the mapping.
	 */
	public ConceptModule(OntologyConcept concept, Integer article,
			float weight, boolean cycToWiki) {
		super(article);
		concept_ = concept;
		mappingWeight_ = weight;
		cycToWiki_ = cycToWiki;
		state_ = MiningState.MAPPED;
		if (concept == null) {
			cycToWiki_ = false;
			state_ = MiningState.UNMAPPED;
		} else
			createdConcept_ = concept.getID() == OntologySocket.NON_EXISTENT_ID;
		if (article == -1) {
			cycToWiki_ = true;
			state_ = MiningState.UNMAPPED;
		}
		deletedAssertions_ = new ArrayList<>();
	}

	private void addDeletedAssertion(DefiniteAssertion assertion) {
		if (deletedAssertions_ == null)
			deletedAssertions_ = new ArrayList<DefiniteAssertion>();
		deletedAssertions_.add(assertion);
	}

	private String articleToString() {
		String article = null;
		if (articleID_ != -1) {
			try {
				article = "'"
						+ ResourceAccess.requestWMISocket().getPageTitle(
								articleID_, true) + "'";

				// Appending mining/disambiguation prefixes
				if (isDisambiguated())
					article += ":[CM]";
				else if (isMined())
					article += ":[M]";
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return article;
	}

	private String conceptToString() {
		String concept = null;
		if (concept_ != null) {
			concept = concept_.toPrettyString();
			if (createdConcept_)
				concept += "[NEW]";
		}
		return concept;
	}

	private String createString(String article, String concept,
			boolean withWeight) {
		// Only a concept
		if (article == null)
			return concept;

		// Only an article
		if (concept == null)
			return article;

		// Mapped
		String output = null;
		switch (state_) {
		case MAPPED:
			if (cycToWiki_)
				output = concept + " => " + article;
			else
				output = article + " => " + concept;
			break;
		case REVERSE_MAPPED:
		case CONSISTENT:
		case ASSERTED:
			output = concept + " <=> " + article;
			break;
		default:
			break;
		}
		if (withWeight)
			return output + " (w=" + getModuleWeight() + ")";
		return output;
	}

	private OntologyConcept determineType(OntologySocket ontology) {
		if (ontology.evaluate(null, CommonConcepts.ISA.getID(),
				concept_.getID(), CommonConcepts.FUNCTION.getID()))
			return CycConstants.FUNCTION.getConcept();
		if (ontology.evaluate(null, CommonConcepts.ISA.getID(),
				concept_.getID(), CommonConcepts.PREDICATE.getID()))
			return CycConstants.PREDICATE.getConcept();
		if (ontology.evaluate(null, CommonConcepts.ISA.getID(),
				concept_.getID(), CommonConcepts.COLLECTION.getID()))
			return CycConstants.COLLECTION.getConcept();
		return CycConstants.INDIVIDUAL.getConcept();
	}

	private boolean isChildOfParents(OntologySocket ontology) {
		if (concept_.getID() < 0)
			return true;
		if (parents_ == null)
			return true;
		for (OntologyConcept parent : parents_) {
			if (!ontology.isa(concept_.getID(), parent.getID())
					&& !ontology.genls(concept_.getID(), parent.getID()))
				return false;
		}
		return true;
	}

	/**
	 * Make the standard assertions regarding the mapping contained by this
	 * MinedInformation.
	 * 
	 * @param articleTitle
	 *            The article title
	 * @param runIter
	 *            The current run iteration
	 * @param ontology
	 *            The ontology access.
	 * 
	 * @throws Exception
	 *             Should something go awry...
	 */
	private void makeWikiMappingAssertions(String articleTitle, int runIter,
			DAGSocket ontology) throws Exception {
		String strURL = WMISocket.getArticleURL(articleTitle,
				WMISocket.WIKIPEDIA_URL);

		// Wiki URL
		int newWiki = new DefiniteAssertion(
				CycConstants.WIKIPEDIA_URL.getConcept(),
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(), null,
				concept_, new StringConcept(strURL)).makeAssertion(runIter,
				concept_, ontology);
		unassertOldWikiAssertions(newWiki, ontology,
				CycConstants.WIKIPEDIA_URL.getID(), concept_.getID());

		// Synonymous External Concept
		int newSynConcept = new DefiniteAssertion(
				CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getConcept(),
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(), null,
				concept_, CycConstants.WIKI_VERSION, new StringConcept(
						articleID_ + "")).makeAssertion(runIter, concept_,
				ontology);
		unassertOldWikiAssertions(newSynConcept, ontology,
				CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID(),
				concept_.getID(),
				CycConstants.WIKI_VERSION.getID());
	}

	/**
	 * Unasserts any edges using the same predicate. Sort of like
	 * StrictlyFunctional, but works for any edge.
	 * 
	 * @param newConcept
	 *            The newly asserted assertion not to remove.
	 * @param ontology
	 *            The ontology access.
	 * @param args
	 *            The args to search with (in order).
	 */
	private void unassertOldWikiAssertions(int newConcept, DAGSocket ontology,
			int... args) {
		String[] indexArgs = new String[args.length * 2];
		for (int i = 0; i < args.length; i++) {
			indexArgs[i * 2] = args[i] + "";
			indexArgs[i * 2 + 1] = "(" + (i + 1) + ")";
		}

		Collection<Integer> edges = ontology.findEdgeIDs(indexArgs);
		for (Integer edgeID : edges) {
			if (newConcept != edgeID)
				ontology.unassert(null, edgeID, true);
		}
	}

	/**
	 * Perform assertion addition, being careful not to change the type of the
	 * concept.
	 * 
	 * @param runIter
	 *            The run iteration in which the assertions are being made.
	 * @param ontology
	 *            The ontology access.
	 * @param noSemantic
	 *            If the concept should assert semantic assertions (opposed to
	 *            no constraint, or primitive assertions).
	 * @throws Exception
	 *             Should something go awry...
	 */
	protected void performAssertionAdding(int runIter, OntologySocket ontology,
			boolean noSemantic) throws Exception {
		Collection<Integer> assertionIDs = new ArrayList<>();
		int oldSize = 0;
		Collection<DefiniteAssertion> assertions = getConcreteAssertions();
		// Repeat assertions until sure nothing changes
		while (assertions.size() > 0 && assertions.size() != oldSize) {
			Collection<DefiniteAssertion> failed = new ArrayList<>();
			oldSize = assertions.size();
			for (DefiniteAssertion assertion : assertions) {
				if (noSemantic
						&& !ontology.isInfoless(assertion.getRelation(), true,
								true))
					continue;

				// Ignore self-referential genls edges
				if (assertion.getRelation().equals(
						CycConstants.GENLS.getConcept())
						&& assertion.getArgs()[0]
								.equals(assertion.getArgs()[1]))
					continue;
				int id = assertion.makeAssertion(runIter, concept_, ontology);
				if (id != -1)
					assertionIDs.add(id);
				else
					failed.add(assertion);
			}
			assertions = failed;
		}
		//
		// // Check type has not changed. If so, start again, checking after
		// every
		// // step
		// if (determineType(ontology) != type_) {
		// for (Integer id : assertionIDs)
		// ontology.unassert(null, id, true);
		//
		// for (DefiniteAssertion assertion : getConcreteAssertions()) {
		// int id = assertion.makeAssertion(concept_, ontology);
		// if (id != -1) {
		// if (determineType(ontology) != type_)
		// ontology.unassert(null, id, true);
		// }
		// }
		// }
	}

	/**
	 * Perform assertion removal.
	 * 
	 * @param ontology
	 *            The ontology access.
	 */
	protected void performAssertionRemoval(OntologySocket ontology) {
		for (DefiniteAssertion assertion : deletedAssertions_) {
			significantChange_ = true;
			int assertionID = ontology.findEdgeIDByArgs((Object[]) assertion
					.asArgs());
			if (ontology.unassert(null, assertionID, false)) {
				try {
					IOManager.getInstance().writeRemovedAssertion(assertion);
				} catch (IOException e) {
					e.printStackTrace();
				}
				LoggerFactory.getLogger(getClass()).info("UNASSERTED:\t{}",
						assertion);
			}
		}
	}

	/**
	 * Perform any auto-assertions defined by external factors.
	 * 
	 * @param runIter
	 *            The run iteration in which the assertions are being made.
	 * @param ontology
	 *            The ontology access.
	 * @param noSemantic
	 *            If the concept should assert semantic assertions (opposed to
	 *            no constraint, or primitive assertions).
	 * @throws Exception
	 *             Should something go awry...
	 */
	protected void performAutoAssertions(int runIter, OntologySocket ontology,
			boolean noSemantic) throws Exception {
		if (autoAssertions_ != null && parents_ != null
				&& isChildOfParents(ontology)) {
			for (MinedAssertion assertion : autoAssertions_) {
				if (noSemantic
						&& !ontology.isInfoless(
								(OntologyConcept) assertion.getRelation(),
								true, true))
					continue;
				if (assertion instanceof PartialAssertion)
					assertion = ((PartialAssertion) assertion).instantiate(
							getMappableSelfRef(), concept_);
				((DefiniteAssertion) assertion).makeAssertion(runIter,
						concept_, ontology);
			}
		}
	}

	public void addParentDetails(Collection<OntologyConcept> parents,
			Collection<MinedAssertion> autoAssertions) {
		if (parents != null && !parents.isEmpty())
			parents_ = parents;
		if (autoAssertions != null && !autoAssertions.isEmpty())
			autoAssertions_ = autoAssertions;
	}

	public void buildDisambiguationGrid(OntologySocket ontology, WMISocket wmi) {
		Collection<PartialAssertion> assertions = getAssertions();
		dd_ = new DisjointnessDisambiguator(assertions, getMappableSelfRef(),
				ontology, wmi);
	}

	@Override
	public void clearInformation() {
		super.clearInformation();
		state_ = MiningState.UNMAPPED;
		dd_ = null;
		disambiguated_ = false;
		mappingWeight_ = 1.0f;
		miningWeight_ = 1;
		significantChange_ = false;
		if (deletedAssertions_ != null)
			deletedAssertions_.clear();
	}

	public ConceptModule clone() {
		ConceptModule newCM = new ConceptModule(concept_, getArticle(),
				mappingWeight_, cycToWiki_);
		return newCM;
	}

	@Override
	public int compareTo(ConceptModule o) {
		// Compare by weight (largest is better)
		int result = -Double.compare(getModuleWeight(), o.getModuleWeight());
		if (result != 0)
			return result;

		// Compare by state
		result = state_.compareTo(o.state_);
		if (result != 0)
			return -result;

		result = Double.compare(miningWeight_, o.miningWeight_);
		if (result != 0)
			return -result;

		// Compare by name
		if (concept_ == null) {
			if (o.concept_ != null)
				return -1;
		} else {
			if (o.concept_ == null)
				return 1;
			else
				result = concept_.toString().compareTo(o.concept_.toString());
		}

		if (result != 0)
			return result;
		return Double.compare(getArticle(), o.getArticle());
	}

	/**
	 * Disambiguates the mining assertions for this {@link ConceptModule} and
	 * returns the new module weight (always <= the old one).
	 * 
	 * @return The new module weight, as per getModuleWeight().
	 * @throws Exception
	 *             Should something go awry...
	 */
	public double disambiguateAssertions(OntologySocket ontology)
			throws Exception {
		state_ = MiningState.CONSISTENT;
		disambiguated_ = true;
		Collection<PartialAssertion> assertions = getAssertions();
		if (assertions.isEmpty()) {
			// Mining is only somewhat confident
			// TODO Figure out an appropriate value here. Perhaps something to
			// do with the heuristics.
			miningWeight_ = UNMINED_CONFIDENCE;
			return 1;
		}
		// Mining weight is never 0.
		miningWeight_ = 1;

		// Disambiguate the assertions.
		dd_.findMaximalConjoint(this, ontology);
		miningWeight_ = dd_.getConjointWeight();
		if (miningWeight_ > 1) {
			LoggerFactory.getLogger(this.getClass()).error(
					"Mining Weight exceeded 1.0 for {}", this.toFlatString());
			miningWeight_ = 1;
		}
		for (DefiniteAssertion assertion : dd_.getConsistentAssertions())
			addAssertion(assertion);
		for (DefiniteAssertion assertion : dd_.getRemovedAssertions())
			addDeletedAssertion(assertion);
		try {
			if (createdConcept_) {
				if (dd_.isCollection())
					type_ = CycConstants.COLLECTION.getConcept();
				else
					type_ = CycConstants.INDIVIDUAL.getConcept();
			} else {
				type_ = determineType(ontology);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Perform evaluation if interactive mode on
		if (InteractiveMode.interactiveMode_) {
			for (DefiniteAssertion assertion : getConcreteAssertions())
				InteractiveMode.getInstance().evaluateAddition(assertion,
						ontology);
			for (DefiniteAssertion removed : deletedAssertions_)
				InteractiveMode.getInstance()
						.evaluateRemoval(removed, ontology);
		}

		return miningWeight_;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConceptModule other = (ConceptModule) obj;
		if (concept_ == null) {
			if (other.concept_ != null)
				return false;
		} else if (!concept_.equals(other.concept_))
			return false;
		if (cycToWiki_ != other.cycToWiki_)
			return false;
		if (state_ != other.state_)
			return false;
		return true;
	}

	public OntologyConcept findCreateConcept(OntologySocket ontology)
			throws Exception {
		if (!ontology.inOntology(concept_)) {
			int id = ontology.createConcept(concept_.getConceptName());
			concept_.setID(id);
			LoggerFactory.getLogger(this.getClass()).info("NEW_CONSTANT:\t{}",
					concept_.getConceptName());
		}
		return concept_;
	}

	public Collection<MinedAssertion> getAutoAssertions() {
		return autoAssertions_;
	}

	public OntologyConcept getConcept() {
		return concept_;
	}

	public TermStanding getConceptStanding() {
		if (type_ == CycConstants.COLLECTION.getConcept())
			return TermStanding.COLLECTION;
		if (type_ == CycConstants.INDIVIDUAL.getConcept())
			return TermStanding.INDIVIDUAL;
		return TermStanding.UNKNOWN;
	}

	/**
	 * Gets the weight (between 0 and 1 inclusive) of this information, where 1
	 * is highly likely information and 0 is absolutely incorrect. Note that the
	 * weight may only represent the accuracy of the mapping if no mining has
	 * been performed.
	 * 
	 * @return The weight of the information.
	 */
	public float getModuleWeight() {
		if (mappingWeight_ == -1)
			return 0;
		else if (miningWeight_ == -1)
			return mappingWeight_;
		else
			return mappingWeight_ * miningWeight_;
	}

	public Collection<OntologyConcept> getParents() {
		return parents_;
	}

	public MiningState getState() {
		if (!isMined() && articleID_ != -1)
			return MiningState.UNMINED;
		else if (!isMapped())
			return MiningState.UNMAPPED;
		else if (state_ == MiningState.MAPPED)
			return MiningState.MAPPED;
		else if (!isDisambiguated())
			return MiningState.REVERSE_MAPPED;
		else
			return state_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((concept_ == null) ? 0 : concept_.hashCode());
		result = prime * result + (cycToWiki_ ? 1231 : 1237);
		result = prime * result + ((state_ == null) ? 0 : state_.hashCode());
		return result;
	}

	public boolean isCreatedConcept() {
		return createdConcept_;
	}

	/**
	 * If the concept module started with a Cyc term to map.
	 * 
	 * @return True if the module was initialised with a cyc module to an
	 *         article.
	 */
	public boolean isCycToWiki() {
		return cycToWiki_;
	}

	public boolean isDisambiguated() {
		return disambiguated_;
	}

	public boolean isMapped() {
		return articleID_ != -1 && concept_ != null;
	}

	public boolean isSignificantlyChanged() {
		return significantChange_;
	}

	/**
	 * Make the assertions contained within this ConceptModule.
	 * 
	 * @param runIter
	 *            The run iteration in which the assertions are being made.
	 * @param articleTitle
	 *            The article title.
	 * 
	 * @throws Exception
	 */
	public void makeAssertions(int runIter, String articleTitle,
			OntologySocket ontology) throws Exception {
		// Assert type
		int numAssertions = 0;
		if (createdConcept_) {
			new DefiniteAssertion(CycConstants.ISA.getConcept(),
					CycConstants.BASE_MICROTHEORY.getConceptName(), null,
					concept_, type_).makeAssertion(runIter, concept_, ontology);
			significantChange_ = true;
		} else {
			// Count the number of assertions
			numAssertions = ontology.getAllAssertions(concept_, 2,
					CommonConcepts.REMOVED).size();
		}

		if (!KnowledgeMiner.mappingRun_) {
			boolean noSemantic = KnowledgeMiner.onlyMineLeaf_
					&& !isCreatedConcept()
					&& ontology.conceptHasChildren(concept_.getIdentifier());

			// Perform the removals
			if (!noSemantic)
				performAssertionRemoval(ontology);

			// Perform the assertions
			performAssertionAdding(runIter, ontology, noSemantic);

			// Perform auto-assertions
			performAutoAssertions(runIter, ontology, noSemantic);
		}
		if (!significantChange_) {
			int newNumAssertions = ontology.getAllAssertions(concept_, 2,
					CommonConcepts.REMOVED).size();
			significantChange_ = newNumAssertions != numAssertions;
		}

		makeWikiMappingAssertions(articleTitle, runIter, (DAGSocket) ontology);
		DefiniteAssertion weightAssertion = new DefiniteAssertion(
				CycConstants.MAPPING_CONFIDENCE.getConcept(),
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(), null,
				concept_, new PrimitiveConcept(getModuleWeight()));
		weightAssertion.makeAssertion(runIter, concept_, ontology);
	}

	@Override
	public boolean mergeInformation(MinedInformation otherInfo)
			throws Exception {
		// No info, do nothing.
		if (otherInfo == null)
			return true;

		if (articleID_ == otherInfo.getArticle()) {
			super.mergeInformation(otherInfo);
			if (otherInfo instanceof ConceptModule) {
				ConceptModule cm = (ConceptModule) otherInfo;
				// Check for reverse-mapping
				if (concept_ != null && concept_.equals(cm.concept_)
						&& state_ == MiningState.MAPPED
						&& cm.state_ == MiningState.MAPPED
						&& cm.cycToWiki_ != cycToWiki_)
					setState(cm.getModuleWeight(), MiningState.REVERSE_MAPPED);
				disambiguated_ |= cm.disambiguated_;

				if (cm.parents_ != null)
					parents_ = cm.parents_;
				if (cm.autoAssertions_ != null)
					autoAssertions_ = cm.autoAssertions_;
				if (cm.dd_ != null)
					dd_ = cm.dd_;
				return true;
			}
		}
		return false;
	}

	public void removeArticle() {
		articleID_ = -1;
		cycToWiki_ = true;
		mappingWeight_ = 1;
		clearInformation();
	}

	public void removeConcept() {
		concept_ = null;
		cycToWiki_ = false;
		mappingWeight_ = 1;
		disambiguated_ = false;
		getConcreteAssertions().clear();
		getConcreteParentageAssertions().clear();
	}

	public void setState(float weight, MiningState state) {
		state_ = state;
		if (state.equals(MiningState.ASSERTED))
			return;
		if (state.equals(MiningState.MAPPED)
				|| state.equals(MiningState.REVERSE_MAPPED))
			mappingWeight_ *= weight;
		else
			miningWeight_ = weight;
	}

	public String toPrettyString(boolean withWeight) {
		String article = articleToString();
		String concept = conceptToString();
		return createString(article, concept, withWeight);
	}

	/**
	 * A simple few words about this module.
	 * 
	 * @return A String describing this module.
	 */
	public String toSimpleString() {
		String output = null;
		String article = articleToString();
		String concept = conceptToString();
		try {
			switch (getState()) {
			case UNMINED:
				if (concept == null)
					output = "MINING: " + article;
				else
					output = "MINING: " + article + " (<= " + concept + ")";
				break;
			case UNMAPPED:
				if (cycToWiki_)
					output = "MAPPING: " + concept;
				else
					output = "MAPPING: " + article;
				break;
			case MAPPED:
				if (cycToWiki_)
					output = "REVERSE_MAPPING: " + concept + " => " + article;
				else
					output = "REVERSE_MAPPING: " + article + " => " + concept;
				break;
			case REVERSE_MAPPED:
				output = "DISAMBIGUATING: " + concept + " <=> " + article;
				break;
			case CONSISTENT:
				output = "ASSERTING: " + concept + " <=> " + article;
				break;
			case ASSERTED:
				output = "COMPLETE: " + concept + " <=> " + article;
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		output += " (w=" + getModuleWeight() + ")";
		return output;
	}

	@Override
	public String toString() {
		String article = null;
		if (articleID_ != -1)
			article = articleID_ + "A";
		String concept = null;
		if (concept_ != null)
			if (concept_.getID() > 0)
				concept = concept_.getID() + "C";
			else
				concept = concept_.toString();
		return createString(article, concept, true);
	}

	public Collection<DefiniteAssertion> getDeletedAssertions() {
		return deletedAssertions_;
	}
}
