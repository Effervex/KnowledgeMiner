/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

import graph.inference.CommonQuery;
import io.ontology.OntologySocket;

import java.util.Collection;
import java.util.HashSet;

import util.DistanceFunction;

/**
 * 
 * @author Sam Sarjant
 */
public class CycSemanticDistanceFunction extends DistanceFunction {
	/** The normalising constant. */
	private static float MAX_COLLECTION = -1;

	@Override
	public float distance(OntologyConcept termA, OntologyConcept collectionB,
			OntologySocket cyc) throws Exception {
		if (!cyc.isaCollection(collectionB))
			return -1;
		if (termA.equals(collectionB))
			return 0;
		if (MAX_COLLECTION == -1)
			MAX_COLLECTION = cyc.getNumConstants();

		// Use the Google distance measure to figure out how similar to concepts
		// are.
		Collection<OntologyConcept> broaderA = cyc.quickQuery(CommonQuery.ALLGENLS,
				termA.getIdentifier());
		broaderA.addAll(cyc.quickQuery(CommonQuery.ALLISA,
				termA.getIdentifier()));
		int a = broaderA.size();
		Collection<OntologyConcept> broaderB = cyc.quickQuery(CommonQuery.ALLGENLS,
				collectionB.getIdentifier());
		broaderB.addAll(cyc.quickQuery(CommonQuery.ALLISA,
				collectionB.getIdentifier()));
		int b = broaderB.size();
		Collection<OntologyConcept> intersect = new HashSet<>(broaderA);
		intersect.retainAll(broaderB);
		int aAndB = intersect.size();

		float max = Math.max(a, b);
		float min = Math.min(a, b);
		return (max - aAndB) / (MAX_COLLECTION - min);
	}

	@Override
	public float getMaxDistance() {
		return 1;
	}

}
