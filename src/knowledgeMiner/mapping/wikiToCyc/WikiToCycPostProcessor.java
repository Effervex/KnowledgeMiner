/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.wikiToCyc;

import graph.core.CommonConcepts;
import graph.core.DirectedAcyclicGraph;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.MappingPostProcessor;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * post processor that
 * 
 * @author Sam Sarjant
 */
public class WikiToCycPostProcessor extends
		MappingPostProcessor<OntologyConcept> {

	@Override
	public WeightedSet<OntologyConcept> process(
			WeightedSet<OntologyConcept> collection, WikipediaSocket wmi,
			OntologySocket ontology) {
		// Only keep Constants (no Predicates)
		WeightedSet<OntologyConcept> newSet = new WeightedSet<>();
		for (OntologyConcept s : collection) {
			try {
				// If ephemeral, skip
				if (ontology.getProperty(s.getIdentifier(), true,
						DirectedAcyclicGraph.EPHEMERAL_MARK) != null)
					continue;
				// If a predicate, skip
				if (ontology.isa(s.getIdentifier(),
						CommonConcepts.PREDICATE.getID()))
					continue;
				newSet.add(s, collection.getWeight(s));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return newSet;
	}
}
