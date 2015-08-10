/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.core.CommonConcepts;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_DateParse extends
		MappingHeuristic<String, OntologyConcept> {
	/**
	 * Constructor for a new DateValueMiner
	 * 
	 * @param mapper
	 */
	public TextToCyc_DateParse(CycMapper mapper) {
		super(mapper);
	}

	@Override
	public WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(source);
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		try {
			// Parse function
			results.addAll(parseDateFunction(source, ontology));

			// Plain DAG search and confirm
			results.addAll(dagSearchDate(source, ontology));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return results;
	}

	private WeightedSet<OntologyConcept> dagSearchDate(String source,
			OntologySocket ontology) {
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		Collection<OntologyConcept> concepts = ontology.findConceptByName(
				source, true, true, true);
		for (OntologyConcept concept : concepts) {
			if (ontology.isa(concept.getIdentifier(),
					CommonConcepts.TIME_INTERVAL.getID()))
				results.add(concept);
		}
		return results;
	}

	protected WeightedSet<OntologyConcept> parseDateFunction(String source,
			OntologySocket ontology) {
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		if (!WikiParser.FUNCTION_PARSER.matcher(source).matches())
			return results;

		source = source.replaceAll("[{}]", "");
		ArrayList<String> split = WikiParser.split(source, "|");

		for (int i = 1; i < split.size(); i++) {
			StringBuilder buffer = new StringBuilder();
			for (int j = i; j < i + 3 && j < split.size(); j++) {
				buffer.append(split.get(j) + " ");
			}

			// Test the date
			Collection<OntologyConcept> dateTerms = ontology.findConceptByName(
					buffer.toString().trim(), false, true, true);
			if (!dateTerms.isEmpty()) {
				for (OntologyConcept term : dateTerms) {
					if (ontology.isa(term.getIdentifier(),
							CommonConcepts.DATE.getID())) {
						results.add(term);
						return results;
					}
				}
			}
		}
		return results;
	}
}
