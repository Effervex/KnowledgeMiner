/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * Mines any bracketed context by locating and mining the text within a value's
 * bracket and setting it as a Temporal interval for the base relation.
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_TimeContextParse extends
		MappingHeuristic<String, OntologyConcept> {
	private static final Pattern CONTEXT_PATTERN = Pattern
			.compile("(.+?)\\((.+?[\\-a ,].+?)\\)");
	private static final OntologyConcept TEMPORAL_THING = new OntologyConcept(
			"TemporalThing");
	private static final OntologyConcept TIME_INTERVAL = new OntologyConcept(
			"TimeInterval");

	/**
	 * Constructor for a new TimeContextMiner.
	 * 
	 * @param mapper
	 *            The Cyc mapping access.
	 */
	public TextToCyc_TimeContextParse(CycMapper mapper) {
		super(mapper);
	}

	@Override
	public WeightedSet<OntologyConcept> mapSourceInternal(String value,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(value);
		value = NLPToSyntaxModule.convertToAscii(value);
		Matcher m = CONTEXT_PATTERN.matcher(value);
		if (m.matches()) {
			// Match left side
			WeightedSet<OntologyConcept> arguments = mapper_.mapTextToCyc(
					m.group(1), false, false, false, wmi, ontology);
			if (arguments.isEmpty())
				return new WeightedSet<>();

			WeightedSet<OntologyConcept> context = mapper_.mapTextToCyc(m.group(2),
					false, false, false, wmi, ontology);
			if (context.isEmpty())
				return new WeightedSet<>();

			return resolveTimeContext(arguments, context, ontology);
		}
		return new WeightedSet<>();
	}

	/**
	 * Resolves the assertions with the context.
	 * 
	 * @param arguments
	 *            The possible assertions.
	 * @param contexts
	 *            The possible contexts.
	 * @param ontology
	 * @return The time-contextual arguments.
	 */
	private WeightedSet<OntologyConcept> resolveTimeContext(
			WeightedSet<OntologyConcept> arguments,
			WeightedSet<OntologyConcept> contexts, OntologySocket ontology) {
		WeightedSet<OntologyConcept> results = new WeightedSet<>(arguments.size()
				* contexts.size());

		// Only accept TEMPORAL assertions
		for (OntologyConcept context : contexts) {
			if (!context.isOntologyConcept()
					|| !ontology.isa(context.getIdentifier(),
							TIME_INTERVAL.getID()))
				continue;
			double contextWeight = contexts.getWeight(context);
			for (OntologyConcept arg : arguments) {
				if (!context.isOntologyConcept()
						|| !ontology.isa(context.getIdentifier(),
								TEMPORAL_THING.getID()))
					continue;
				double argWeight = arguments.getWeight(arg);
				OntologyConcept cloneArg = arg.clone();
				cloneArg.setTemporalContext(context);
				results.add(cloneArg, contextWeight * argWeight);
			}
		}
		return results;
	}
}
