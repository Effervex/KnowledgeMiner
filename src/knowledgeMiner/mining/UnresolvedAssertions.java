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
package knowledgeMiner.mining;

import graph.core.CommonConcepts;
import io.ontology.OntologySocket;

import java.util.Collection;
import java.util.HashSet;

import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * This class is for storing potential assertions that are composed of unlinked
 * mappings. It periodically attempts to map them into assertions.
 * 
 * @author Sam Sarjant
 */
public class UnresolvedAssertions {
	private static final int CONSTRAINT_THRESHOLD = 100;
	private Collection<OntologyConcept> infoboxRelations_;

	public UnresolvedAssertions() {
		infoboxRelations_ = new HashSet<>();
	}

	public OntologyConcept createNewRelation(String relation, String comment,
			OntologySocket ontology) throws Exception {
		if (ontology.inOntology(relation))
			return null;

		// Create the new predicate
		int id = ontology.createAndAssert(relation,
				CommonConcepts.BINARY_PREDICATE.getID(), comment);
		OntologyConcept newRelation = new OntologyConcept(id);
		infoboxRelations_.add(newRelation);

		// Add quotedIsa tagging information
		ontology.assertToOntology(
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.QUOTED_ISA.getID(), id,
				CycConstants.UNCLASSIFIED_CONCEPT.getID());

		// TODO Add argNotIsa info

		return newRelation;
	}

	public OntologyConcept createOnTheFlyCollection(String collection,
			String comment, OntologySocket ontology) throws Exception {
		if (ontology.inOntology(collection))
			return null;

		// Create the new predicate
		int id = ontology.createAndAssert(collection,
				CommonConcepts.COLLECTION.getID(), comment);
		OntologyConcept newCollection = new OntologyConcept(id);

		// Add quotedIsa tagging information
		ontology.assertToOntology(
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.QUOTED_ISA.getID(), id,
				CycConstants.UNCLASSIFIED_CONCEPT.getID());

		// TODO Add argNotIsa info

		return newCollection;
	}
	
	public void calculateArgConstraints() {
		for (OntologyConcept relation : infoboxRelations_) {
			// Find the number of uses
			int numAssertions = 0;
			
			if (numAssertions >= CONSTRAINT_THRESHOLD) {
				// Find the LGG of the parents, allowing some errors (to be removed)
				
				// Define the arg constraint and unassert those that do not meet.
				
				
			}
			
		}
	}
}
