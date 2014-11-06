/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util;

import graph.inference.CommonQuery;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.util.Collection;

import knowledgeMiner.mining.DefiniteAssertion;
import cyc.OntologyConcept;

/**
 * A distance function for assertions and terms in the Knowledge Miner ontology.
 * 
 * @author Sam Sarjant
 */
public abstract class DistanceFunction {
	private float argDistance(OntologyConcept argument, OntologySocket cyc,
			OntologyConcept relation, int argNum) throws Exception {
		float minDist = getMaxDistance();
		// No argument, assumed success.
		if (argument == null)
			return 1;
		// Unknown relation, assumed success.
		if (!cyc.inOntology(relation))
			return 1;

		Collection<OntologyConcept> minArgReqs = cyc.quickQuery(
				CommonQuery.MINARGNISA, relation + " '" + argNum);
		minArgReqs.addAll(cyc.quickQuery(CommonQuery.MINARGNGENL, relation
				+ " '" + argNum));
		for (OntologyConcept argNIsa : minArgReqs)
			minDist = Math.min(minDist, distance(argument, argNIsa, cyc));
		return minDist;
	}

	/**
	 * The maximum distance for a given distance measure.
	 * 
	 * @return The maximum distance.
	 */
	public abstract float getMaxDistance();

	/**
	 * Gets the distance the given arguments for an assertion are from the
	 * predicate's type constraints.
	 * 
	 * @param assertion
	 *            The assertion to get distance for.
	 * @return The distance between the predicate's arguments and the required
	 *         arguments.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public float assertionDistance(DefiniteAssertion assertion) throws Exception {
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		OntologyConcept relation = assertion.getRelation();
		float distanceSum = 0;

		// First arg
		float minDist = argDistance(assertion.getArgs()[0], cyc, relation, 1);
		distanceSum += minDist;

		// Second arg
		minDist = argDistance(assertion.getArgs()[1], cyc, relation, 2);
		distanceSum += minDist;

		return distanceSum / 2;
	}

	/**
	 * Computes the distance between two concepts.
	 * 
	 * @param termA
	 *            The first concept. May be a collection.
	 * @param termB
	 *            The second concept. Must be a collection.
	 * @return The taxonomic distance between two concepts, or -1 if they cannot
	 *         be measured.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public abstract float distance(OntologyConcept termA, OntologyConcept collectionB,
			OntologySocket cyc) throws Exception;
}
