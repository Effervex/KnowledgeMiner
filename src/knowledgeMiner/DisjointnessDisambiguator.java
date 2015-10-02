/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package knowledgeMiner;

import graph.core.CommonConcepts;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.WeightedStanding;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;

/**
 * A class explicitly for disambiguating groups of assertions such that a
 * maximal set of consistent assertions is found.
 * 
 * @author Sam Sarjant
 */
public class DisjointnessDisambiguator {

	public final static Logger logger_ = LoggerFactory
			.getLogger(AssertionGrid.class);

	private static final boolean ASSERTION_REMOVAL = false;

	private Collection<DefiniteAssertion> consistentAssertions_;

	/** The assertion grid composed of the extracted assertions. */
	private AssertionGrid coreAssertionGrid_;

	/**
	 * The assertion grid composed of the extracted assertions and concept
	 * assertions.
	 */
	private AssertionGrid currentAssertionGrid_;

	private Collection<DefiniteAssertion> removedAssertions_;

	private int caseNumber_ = 0;

	public DisjointnessDisambiguator(Collection<PartialAssertion> assertions,
			MappableConcept coreConcept, OntologySocket ontology, WMISocket wmi) {
		coreAssertionGrid_ = new AssertionGrid(assertions, coreConcept,
				ontology, wmi);
		// System.out.println(coreAssertionGrid_);
	}

	private Collection<DefiniteAssertion> getExistingAssertions(
			ConceptModule conceptModule, OntologySocket ontology) {
		Collection<DefiniteAssertion> existingAssertions = new ArrayList<>();

		// TODO Get ALL type-bound assertions!
		OntologyConcept concept = conceptModule.getConcept();

		// Get isa and genls truths (including removed)
		Collection<OntologyConcept> isaTruths = null;
		Collection<OntologyConcept> genlTruths = null;
		if (!conceptModule.isCreatedConcept()) {
			try {
				isaTruths = parseTaxonomic(concept.getIdentifier(),
						CommonConcepts.ISA, (DAGSocket) ontology);
				genlTruths = parseTaxonomic(concept.getIdentifier(),
						CommonConcepts.GENLS, (DAGSocket) ontology);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Add any concretes
		Collection<DefiniteAssertion> concreteTaxonomics = new ArrayList<>();
		for (DefiniteAssertion concrete : conceptModule
				.getConcreteParentageAssertions()) {
			concreteTaxonomics.add(concrete);
		}

		// Add the information.
		try {
			if (!conceptModule.isCreatedConcept()) {
				for (OntologyConcept isaTruth : isaTruths)
					existingAssertions.add(new DefiniteAssertion(
							CycConstants.ISA.getConcept(), null, concept,
							isaTruth));
				for (OntologyConcept genlTruth : genlTruths)
					existingAssertions.add(new DefiniteAssertion(
							CycConstants.GENLS.getConcept(), null, concept,
							genlTruth));
			}
			for (DefiniteAssertion ma : concreteTaxonomics)
				existingAssertions.add(ma);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return existingAssertions;
	}

	/**
	 * Parse all taxonomic collections from a concept, removed and non-removed.
	 *
	 * @param conceptID
	 *            The concept being parsed
	 * @param predicate
	 *            The isa/genls to parse
	 * @param ontology
	 *            The ontology access.
	 * @return The taxonomic assertions about the concept.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private Collection<OntologyConcept> parseTaxonomic(String conceptID,
			CommonConcepts predicate, DAGSocket ontology) throws Exception {
		Collection<OntologyConcept> result = new ArrayList<>();
		String queryResult = ontology.command("findedges", predicate.getID()
				+ " (1) " + conceptID + " (2)", false);
		String[] split = queryResult.split("\\|");
		for (int i = 1; i < split.length; i++) {
			int id = Integer.parseInt(split[i]);
			String[] edge = ontology.findEdgeByID(id);
			// Parse predicate and removed
			int edgeID = Integer.parseInt(edge[0]);
			if (edgeID == predicate.getID())
				result.add(OntologyConcept.parseArgument(edge[2]));
			else if (edgeID == CommonConcepts.REMOVED.getID()) {
				edge = UtilityMethods.splitToArray(
						UtilityMethods.shrinkString(edge[1], 1), ' ');
				result.add(OntologyConcept.parseArgument(edge[2]));
			}
		}

		return result;
	}

	/**
	 * Appends the assertion grid and all associated grids with the concept's
	 * existing assertions.
	 * 
	 * @param conceptModule
	 *            The concept to add
	 * @param existingAssertions
	 *            The existing assertions to integrate.
	 * @param assertionRemoval
	 *            If assertions can be removed.
	 */
	private AssertionGrid integrateGroundTruths(ConceptModule conceptModule,
			Collection<DefiniteAssertion> existingAssertions,
			boolean assertionRemoval, OntologySocket ontology) {
		if (conceptModule.isCreatedConcept())
			return new AssertionGrid(coreAssertionGrid_,
					conceptModule.getConcept(), conceptModule.getStanding(),
					new ArrayList<DefiniteAssertion>(0), false);
		else {
			WeightedStanding standing = new WeightedStanding(
					conceptModule.getStanding());
			if (ontology.isaCollection(conceptModule.getConcept()))
				standing.addStanding(null, TermStanding.COLLECTION, 1);
			else
				standing.addStanding(null, TermStanding.INDIVIDUAL, 1);
			return new AssertionGrid(coreAssertionGrid_,
					conceptModule.getConcept(), standing, existingAssertions,
					assertionRemoval);
		}
	}

	/**
	 * Finds the maximally conjoint set of assertions that are consistent with
	 * one-another. This process also mixes in the existing assertions, treating
	 * them as assertions to be added.
	 * 
	 * @param conceptModule
	 *            The concept to be consistent with.
	 * @param ontology
	 *            The ontology access.
	 * @return The maximally conjoint set of assertions.
	 */
	@SuppressWarnings("unchecked")
	public void findMaximalConjoint(ConceptModule conceptModule,
			OntologySocket ontology) {
		// Null grid check
		if (coreAssertionGrid_.isEmpty()) {
			caseNumber_ = -1;
			return;
		}
		boolean assertionRemoval = ASSERTION_REMOVAL
				&& !conceptHasChildren(conceptModule, (DAGSocket) ontology);

		Collection<DefiniteAssertion> existingAssertions = getExistingAssertions(
				conceptModule, ontology);
		currentAssertionGrid_ = integrateGroundTruths(conceptModule,
				existingAssertions, assertionRemoval, ontology);
		consistentAssertions_ = currentAssertionGrid_
				.findMaximalConjoint(ontology);
		caseNumber_ = 0;

		// Note the removed assertions
		logger_.trace("Added " + consistentAssertions_.size());
		if (assertionRemoval) {
			existingAssertions.removeAll(consistentAssertions_);
			removedAssertions_ = existingAssertions;
			logger_.trace("Removed " + removedAssertions_.size());
		} else
			removedAssertions_ = CollectionUtils.EMPTY_COLLECTION;
	}

	/**
	 * Checks if the concept has any children. If so, does not allow assertion
	 * removal.
	 *
	 * @param conceptModule
	 *            The concept to check.
	 * @param ontology
	 *            The ontology access.
	 * @return True if the concept has children.
	 */
	private boolean conceptHasChildren(ConceptModule conceptModule,
			DAGSocket ontology) {
		if (conceptModule.isCreatedConcept())
			return false;
		try {
			// Isa
			OntologyConcept concept = conceptModule.getConcept();
			String result = ontology.command(
					"findedges",
					CommonConcepts.ISA.getID() + " (1) "
							+ concept.getIdentifier() + " (3) [0,1)", false);
			if (result.startsWith("1"))
				return true;
			// Genls
			result = ontology.command("findedges", CommonConcepts.GENLS.getID()
					+ " (1) " + concept.getIdentifier() + " (3) [0,1)", false);
			if (result.startsWith("1"))
				return true;
		} catch (Exception e) {
			return true;
		}
		return false;
	}

	public float getConjointWeight() {
		if (caseNumber_ == -1)
			return 0;
		return currentAssertionGrid_.getCaseWeight(caseNumber_);
	}

	public boolean isCollection() {
		if (caseNumber_ == -1)
			return false;
		return currentAssertionGrid_.isCollection(caseNumber_);
	}

	public Collection<DefiniteAssertion> getConsistentAssertions() {
		if (caseNumber_ == -1)
			return Collections.EMPTY_LIST;
		return consistentAssertions_;
	}

	public Collection<DefiniteAssertion> getRemovedAssertions() {
		if (caseNumber_ == -1)
			return Collections.EMPTY_LIST;
		return removedAssertions_;
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder("Disjointness Disambiguation");
		return buffer.toString();
	}
}
