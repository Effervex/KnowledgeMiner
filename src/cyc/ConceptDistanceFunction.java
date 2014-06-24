/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

import graph.core.CommonConcepts;
import io.ontology.OntologySocket;

import java.util.Iterator;
import java.util.List;

import util.DistanceFunction;

/**
 * 
 * @author Sam Sarjant
 */
public class ConceptDistanceFunction extends DistanceFunction {
	private static final String TRANSITIVE_PRED = "(isa genls TransitiveBinaryPredicate)";

	/**
	 * Removes special Cyc justifications from the list of terms.
	 * 
	 * @param listTerms
	 *            The terms to remove justifications from.
	 */
	private void removeSpecialTerms(List<String> listTerms) {
		listTerms.remove(TRANSITIVE_PRED);
		for (Iterator<String> iter = listTerms.iterator(); iter.hasNext();) {
			String str = iter.next();
			if (str.startsWith("(termOfUnit")) {
				iter.remove();
				continue;
			}
		}
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
	@Override
	public float distance(OntologyConcept termA, OntologyConcept collectionB,
			OntologySocket cyc) throws Exception {
		if (!cyc.isaCollection(termA))
			return -1;
		if (termA.equals(collectionB))
			return 0;

		// Isa distance
		int minDist = -1;
		List<String> whyIsa = cyc.justify(CommonConcepts.ISA.getID(),
				termA.getIdentifier(), collectionB.getIdentifier());
		removeSpecialTerms(whyIsa);
		if (!whyIsa.isEmpty()) {
			minDist = whyIsa.size();
		}

		// Genls distance
		List<String> whyGenls = cyc.justify(CommonConcepts.GENLS.getID(),
				termA.getIdentifier(), collectionB.getIdentifier());
		removeSpecialTerms(whyGenls);
		if (!whyGenls.isEmpty()) {
			int genlsDist = whyGenls.size();
			if (minDist == -1)
				minDist = genlsDist;
			else
				minDist = Math.min(minDist, genlsDist);
		}

		return minDist;
	}

	@Override
	public float getMaxDistance() {
		return Byte.MAX_VALUE;
	}
}
