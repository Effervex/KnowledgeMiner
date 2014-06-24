/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.wikiToCyc;

import graph.core.CommonConcepts;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.MappingPostProcessor;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * post processor that
 * 
 * @author Sam Sarjant
 */
public class WikiToCycPostProcessor extends MappingPostProcessor<OntologyConcept> {

	/**
	 * Constructor for a new WikiToCycPostProcessor
	 */
	public WikiToCycPostProcessor() {
		super();
	}

	@Override
	public WeightedSet<OntologyConcept> process(WeightedSet<OntologyConcept> collection,
			WMISocket wmi, OntologySocket ontology) {
		// Only keep Constants (no Predicates)
		WeightedSet<OntologyConcept> newSet = new WeightedSet<>();
		for (OntologyConcept s : collection) {
			try {
				if (!ontology.isa(s.getIdentifier(),
						CommonConcepts.PREDICATE.getID()))
					newSet.add(s, collection.getWeight(s));

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return newSet;
	}
}
