/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

import graph.core.CommonConcepts;
import graph.module.NLPToSyntaxModule;
import io.KMSocket;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.SortedSet;

import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;

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

	private static final double UNMINED_CONFIDENCE = 0.5;

	/** The concept for which this information is about. */
	private OntologyConcept concept_;

	/** If the concept is (to be) newly created. */
	private boolean createdConcept_ = false;

	/** If mapping started with a Cyc term and maps to Wiki. */
	private boolean cycToWiki_;

	private boolean disambiguated_ = false;

	/** The weight of the mapping between the concept and article [0-1] */
	private double mappingWeight_ = -1;

	/** The state of this Concept Module. */
	private MiningState state_ = MiningState.UNMINED;

	/**
	 * A set of parents that, if this concept is a member of, will trigger the
	 * auto assertions.
	 */
	private Collection<OntologyConcept> parents_;

	/**
	 * A set of assertions that take effect if the concept meets the parent
	 * reqs.
	 */
	private Collection<MinedAssertion> autoAssertions_;

	private transient DisjointnessDisambiguator dd_;

	private transient Collection<MinedAssertion> deletedAssertions_;

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
			createdConcept_ = term.getID() == -1;
		state_ = MiningState.UNMAPPED;
		cycToWiki_ = true;
		mappingWeight_ = 1.0;
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
			double weight, boolean cycToWiki) {
		super(article);
		concept_ = concept;
		mappingWeight_ = weight;
		cycToWiki_ = cycToWiki;
		state_ = MiningState.MAPPED;
		if (concept == null) {
			cycToWiki_ = false;
			state_ = MiningState.UNMAPPED;
		} else
			createdConcept_ = concept.getID() == -1;
		if (article == -1) {
			cycToWiki_ = true;
			state_ = MiningState.UNMAPPED;
		}
		deletedAssertions_ = new ArrayList<>();
	}

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
		mappingWeight_ = 1.0;
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
		mappingWeight_ = 1.0;
	}

	/**
	 * Make the standard assertions regarding the mapping contained by this
	 * MinedInformation.
	 * 
	 * @param ontology
	 * 
	 * @throws Exception
	 *             Should something go awry...
	 */
	private void makeWikiMappingAssertions(String articleTitle,
			OntologySocket ontology) throws Exception {
		String title = NLPToSyntaxModule.convertToAscii(articleTitle)
				.replaceAll(" ", "_");
		String strURL = "http://en.wikipedia.org/wiki/" + title;
		// Unassert any old Wiki mappings (unless they are the same as this)

		new MinedAssertion(CycConstants.WIKIPEDIA_URL.getConcept(), concept_,
				new StringConcept(strURL), null, null).makeAssertion(concept_,
				null, ontology);

		// Synonymous
		int id = ontology.createConcept("(" + CycConstants.URLFN.getID()
				+ " \"" + strURL + "\")");
		if (id != -1) {
			OntologyConcept url = new OntologyConcept(id);
			StringConcept resource = new StringConcept(
					KnowledgeMiner.wikiVersion_);
			new MinedAssertion(
					CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getConcept(),
					CycConstants.DATA_MICROTHEORY.getConceptName(), null,
					concept_, url, resource).makeAssertion(concept_, null,
					ontology);
		}
	}

	public void addParentDetails(Collection<OntologyConcept> parents,
			Collection<MinedAssertion> autoAssertions) {
		if (parents != null && !parents.isEmpty())
			parents_ = parents;
		if (autoAssertions != null && !autoAssertions.isEmpty())
			autoAssertions_ = autoAssertions;
	}

	@Override
	public void clearInformation() {
		super.clearInformation();
		state_ = MiningState.UNMAPPED;
		dd_ = null;
		disambiguated_ = false;
		mappingWeight_ = 1.0;
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

	public void buildDisambiguationGrid(OntologySocket ontology) {
		SortedSet<AssertionQueue> assertions = getAmbiguousAssertions();
		dd_ = new DisjointnessDisambiguator(assertions, ontology);
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
		SortedSet<AssertionQueue> assertions = getAmbiguousAssertions();
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
		dd_.findMaximalConjoint(this, standing_.getStanding(), ontology);
		for (MinedAssertion assertion : dd_.getConsistentAssertions())
			addConcreteAssertion(assertion);
		for (MinedAssertion assertion : dd_.getRemovedAssertions())
			addDeletedAssertion(assertion);
		miningWeight_ = dd_.getConjointWeight();

		return miningWeight_;
	}

	private void addDeletedAssertion(MinedAssertion assertion) {
		if (deletedAssertions_ == null)
			deletedAssertions_ = new ArrayList<MinedAssertion>();
		deletedAssertions_.add(assertion);
	}

	public OntologyConcept findCreateConcept(OntologySocket ontology)
			throws Exception {
		if (!ontology.inOntology(concept_)) {
			int id = ontology.createConcept(concept_.toString());
			concept_.setID(id);
			LoggerFactory.getLogger(this.getClass()).info("NEW_CONSTANT:\t{}",
					concept_);

			// Assert the standing of the new term.
			boolean priorEph = ontology.getEphemeral();
			ontology.setEphemeral(false);
			switch (standing_.getStanding()) {
			case COLLECTION:
				ontology.assertToOntology(
						CycConstants.DATA_MICROTHEORY.getConceptName(),
						CommonConcepts.ISA.getID(), concept_.toString(),
						CommonConcepts.COLLECTION.getID() + "");
				LoggerFactory.getLogger(
						standing_.getPositiveSources().toString()).info(
						"STANDING:\t(isa {} Collection)", concept_);
				standing_.setActualStanding(TermStanding.COLLECTION);
				break;
			case INDIVIDUAL:
				ontology.assertToOntology(
						CycConstants.DATA_MICROTHEORY.getConceptName(),
						CommonConcepts.ISA.getID(), concept_.toString(),
						CommonConcepts.INDIVIDUAL.getID() + "");
				LoggerFactory.getLogger(
						standing_.getPositiveSources().toString()).info(
						"STANDING:\t(isa {} Individual)", concept_);
				standing_.setActualStanding(TermStanding.INDIVIDUAL);
				break;
			case UNKNOWN:
				ontology.assertToOntology(
						CycConstants.DATA_MICROTHEORY.getConceptName(),
						"quotedIsa", concept_.toString(),
						CycConstants.DEFAULTED_INDIVIDUAL);
				LoggerFactory
						.getLogger(standing_.getSources().toString())
						.info("STANDING:\t(quotedIsa {} "
								+ CycConstants.DEFAULTED_INDIVIDUAL.getConceptName()
								+ ")", concept_);
				standing_.setActualStanding(TermStanding.INDIVIDUAL);
				break;
			}
			ontology.setEphemeral(priorEph);
			standing_.setStatus(1);
		}
		return concept_;
	}

	public OntologyConcept getConcept() {
		return concept_;
	}

	/**
	 * Gets the weight (between 0 and 1 inclusive) of this information, where 1
	 * is highly likely information and 0 is absolutely incorrect. Note that the
	 * weight may only represent the accuracy of the mapping if no mining has
	 * been performed.
	 * 
	 * @return The weight of the information.
	 */
	public double getModuleWeight() {
		if (mappingWeight_ == -1)
			return 0;
		else if (miningWeight_ == -1)
			return mappingWeight_;
		else
			return mappingWeight_ * miningWeight_;
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

	/**
	 * Make the assertions contained within this ConceptModule.
	 * 
	 * @param articleTitle
	 *            The article title.
	 * @throws Exception
	 */
	public void makeAssertions(String articleTitle, OntologySocket ontology)
			throws Exception {
		// Create the child term if necessary.
		TermStanding actualStanding = null;
		// Determine the actual standing
		if (ontology.isaCollection(concept_))
			actualStanding = TermStanding.COLLECTION;
		else
			actualStanding = TermStanding.INDIVIDUAL;

		// Check if it clashes.
		if (standing_.getStanding() != TermStanding.UNKNOWN
				&& standing_.getStanding() != actualStanding) {
			standing_.setStatus(-1);
			LoggerFactory.getLogger(getClass()).info(
					"WRONG_STANDING:\t{} ({})", concept_, articleID_);
		} else
			standing_.setStatus(0);

		standing_.setActualStanding(actualStanding);

		// Perform the removals
		for (MinedAssertion assertion : deletedAssertions_) {
			int assertionID = ontology.findEdgeIDByArgs((Object[]) assertion
					.asArgs());
			if (ontology.unassert(null, assertionID)) {
				LoggerFactory.getLogger(getClass()).info("UNASSERTED:\t{}",
						assertion);
			}
		}

		// Perform the assertions
		for (MinedAssertion assertion : getConcreteAssertions())
			assertion.makeAssertion(concept_, actualStanding, ontology);
		if (autoAssertions_ != null && parents_ != null
				&& isChildOfParents(ontology))
			for (MinedAssertion assertion : autoAssertions_)
				assertion.makeAssertion(concept_, actualStanding, ontology);

		makeWikiMappingAssertions(articleTitle, ontology);
		MinedAssertion weightAssertion = new MinedAssertion(
				CycConstants.MAPPING_CONFIDENCE.getConcept(), concept_,
				new PrimitiveConcept(getModuleWeight()),
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(), null);
		weightAssertion.makeAssertion(concept_, actualStanding, ontology);
	}

	private boolean isChildOfParents(OntologySocket ontology) {
		if (parents_ == null)
			return true;
		for (OntologyConcept parent : parents_) {
			if (!ontology.isa(concept_.getID(), parent.getID())
					&& !ontology.genls(concept_.getID(), parent.getID()))
				return false;
		}
		return true;
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
		getConcreteAssertions().removeAll(getConcreteParentageAssertions());
		getConcreteParentageAssertions().clear();
	}

	public void setState(double weight, MiningState state) {
		state_ = state;
		if (state.equals(MiningState.ASSERTED))
			return;
		if (state.equals(MiningState.MAPPED)
				|| state.equals(MiningState.REVERSE_MAPPED))
			mappingWeight_ *= weight;
		else
			miningWeight_ = weight;
	}

	/**
	 * A simple few words about this module.
	 * 
	 * @param wmi
	 *            The WMI access.
	 * 
	 * @return A String describing this module.
	 */
	public String toSimpleString(KMSocket wmi) {
		String output = null;
		String article = articleToString();
		String concept = conceptToString();
		try {
			switch (getState()) {
			case UNMINED:
				output = "MINING: " + article;
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
		try {
			// Creating strings of each
			String article = articleToString();
			String concept = conceptToString();

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
			return output + " (w=" + getModuleWeight() + ")";
		} catch (Exception e) {
		}
		return "Error forming string.";
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

	public boolean isCreatedConcept() {
		return createdConcept_;
	}

	public Collection<MinedAssertion> getAutoAssertions() {
		return autoAssertions_;
	}

	public Collection<OntologyConcept> getParents() {
		return parents_;
	}
}
