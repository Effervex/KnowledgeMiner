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

import graph.inference.CommonQuery;
import io.ontology.OntologySocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.SortedSet;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.MinedAssertion;
import cyc.OntologyConcept;
import cyc.CycConstants;

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

	private Collection<MinedAssertion> consistentAssertions_;

	/** The assertion grid composed of the extracted assertions. */
	private AssertionGrid coreAssertionGrid_;

	/**
	 * The assertion grid composed of the extracted assertions and concept
	 * assertions.
	 */
	private AssertionGrid currentAssertionGrid_;

	private Collection<MinedAssertion> removedAssertions_;

	private int caseNumber_ = 0;

	public DisjointnessDisambiguator(SortedSet<AssertionQueue> assertions,
			OntologySocket ontology) {
		coreAssertionGrid_ = new AssertionGrid(assertions);
	}

	private Collection<MinedAssertion> getExistingAssertions(
			ConceptModule conceptModule, OntologySocket ontology) {
		Collection<MinedAssertion> existingAssertions = new ArrayList<>();

		// For every ISA
		OntologyConcept concept = conceptModule.getConcept();
		Collection<OntologyConcept> isaTruths = null;
		Collection<OntologyConcept> genlTruths = null;
		if (!conceptModule.isCreatedConcept()) {
			isaTruths = ontology.quickQuery(CommonQuery.DIRECTISA,
					concept.getIdentifier());
			// For every GENLS
			genlTruths = ontology.quickQuery(CommonQuery.DIRECTGENLS,
					concept.getIdentifier());
		}
		// Add any concretes
		Collection<MinedAssertion> concreteTaxonomics = new ArrayList<>();
		for (MinedAssertion concrete : conceptModule
				.getConcreteParentageAssertions()) {
			if (concrete.isHierarchical())
				concreteTaxonomics.add(concrete);
		}

		// Add the information.
		try {
			if (!conceptModule.isCreatedConcept()) {
				for (OntologyConcept isaTruth : isaTruths)
					existingAssertions.add(new MinedAssertion(CycConstants.ISA
							.getConcept(), concept, isaTruth, null, null));
				for (OntologyConcept genlTruth : genlTruths)
					existingAssertions.add(new MinedAssertion(
							CycConstants.GENLS.getConcept(), concept,
							genlTruth, null, null));
			}
			for (MinedAssertion ma : concreteTaxonomics)
				existingAssertions.add(ma);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return existingAssertions;
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
			Collection<MinedAssertion> existingAssertions,
			boolean assertionRemoval) {
		if (conceptModule.isCreatedConcept())
			return new AssertionGrid(coreAssertionGrid_,
					OntologyConcept.PLACEHOLDER, new ArrayList<MinedAssertion>(0),
					ASSERTION_REMOVAL);

		// TODO Need to test assertion removal.
		return new AssertionGrid(coreAssertionGrid_,
				conceptModule.getConcept(), existingAssertions,
				assertionRemoval);
	}

	/**
	 * Finds the maximally conjoint set of assertions that are consistent with
	 * one-another. This process also mixes in the existing assertions, treating
	 * them as assertions to be added.
	 * 
	 * @param conceptModule
	 *            The concept to be consistent with.
	 * @param standing
	 *            The standing of the concept.
	 * @param ontology
	 *            The ontology access.
	 * @return The maximally conjoint set of assertions.
	 */
	@SuppressWarnings("unchecked")
	public void findMaximalConjoint(ConceptModule conceptModule,
			TermStanding standing, OntologySocket ontology) {
		Collection<MinedAssertion> existingAssertions = getExistingAssertions(
				conceptModule, ontology);
		currentAssertionGrid_ = integrateGroundTruths(conceptModule,
				existingAssertions, ASSERTION_REMOVAL);
		if (InteractiveMode.interactiveMode_) {
			currentAssertionGrid_.findNConjoint(
					InteractiveMode.NUM_DISAMBIGUATED,
					standing == TermStanding.COLLECTION, ontology);
			caseNumber_ = ConceptMiningTask.interactiveInterface_
					.interactiveDisambiguation(conceptModule,
							currentAssertionGrid_, ontology);
			consistentAssertions_ = currentAssertionGrid_.getAssertions(caseNumber_);
		} else {
			consistentAssertions_ = currentAssertionGrid_.findMaximalConjoint(
					standing == TermStanding.COLLECTION, ontology);
			caseNumber_ = 0;
		}

		// Note the removed assertions
		if (ASSERTION_REMOVAL) {
			existingAssertions.removeAll(consistentAssertions_);
			removedAssertions_ = existingAssertions;
		} else
			removedAssertions_ = CollectionUtils.EMPTY_COLLECTION;
	}

	public double getConjointWeight() {
		return currentAssertionGrid_.getCaseWeight(caseNumber_);
	}

	public Collection<MinedAssertion> getConsistentAssertions() {
		return consistentAssertions_;
	}

	public Collection<MinedAssertion> getRemovedAssertions() {
		return removedAssertions_;
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder("Disjointness Disambiguation");
		return buffer.toString();
	}
}
