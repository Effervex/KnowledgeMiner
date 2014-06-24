/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.core.CommonConcepts;
import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Iterator;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_IntervalParse extends
		MappingHeuristic<String, OntologyConcept> {
	/**
	 * Constructor for a new IntervalValueMiner
	 * 
	 * @param mapper
	 */
	public TextToCyc_IntervalParse(CycMapper mapper) {
		super(mapper);
	}

	@Override
	public WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket cyc) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(source);
		source = NLPToSyntaxModule.convertToAscii(source);
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		if (!source.contains("-"))
			return results;

		String[] split = source.split("-");
		if (split.length != 2)
			return results;

		// Parse each side, and resolve using the existing resolution info.
		// Looking for Temporal Things
		WeightedSet<OntologyConcept> past = mapper_.mapTextToCyc(split[0], false, false,
				true, wmi, cyc);
		WeightedSet<OntologyConcept> future = mapper_.mapTextToCyc(split[1], false,
				false, true, wmi, cyc);
		resolveIntervals(past, future, results, cyc);

		return results;
	}

	/**
	 * Resolves the interval, creating functions that represent all possible
	 * intervals.
	 * 
	 * @param past
	 *            The past (left-side) values.
	 * @param future
	 *            The future (right-side) values.
	 * @param results
	 *            The results to add to.
	 * @param cyc
	 *            The Cyc access.
	 * @throws Exception
	 */
	private void resolveIntervals(WeightedSet<OntologyConcept> past,
			WeightedSet<OntologyConcept> future, WeightedSet<OntologyConcept> results,
			OntologySocket cyc) throws Exception {
		for (OntologyConcept pastFact : past) {
			// If fact is a TemporalThing
			if (cyc.isa(pastFact.getIdentifier(), CommonConcepts.DATE.getID())) {
				Iterator<OntologyConcept> futureIter = future.iterator();
				while (futureIter.hasNext()) {
					OntologyConcept futureFact = futureIter.next();
					if (cyc.isa(futureFact.getIdentifier(),
							CommonConcepts.DATE.getID())) {
						// If future is after past
						if (cyc.evaluate(null,
								CommonConcepts.LATER_PREDICATE.getID(),
								futureFact.getIdentifier(),
								pastFact.getIdentifier())) {
							OntologyConcept interval = new OntologyConcept("("
									+ CommonConcepts.INTERVAL_FUNCTION.getID()
									+ " " + pastFact + " " + futureFact + ")");
							double weight = past.getWeight(pastFact)
									* future.getWeight(futureFact);
							results.add(interval, weight);
						}
					} else {
						futureIter.remove();
					}
				}
			}
		}
	}
}
