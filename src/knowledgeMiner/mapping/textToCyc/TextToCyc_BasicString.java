/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.util.regex.Matcher;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.OntologyConcept;
import cyc.StringConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_BasicString extends MappingHeuristic<String, OntologyConcept> {

	/**
	 * Constructor for a new StringValueMiner
	 * 
	 * @param mapper
	 */
	public TextToCyc_BasicString(CycMapper mapper) {
		super(mapper);
	}

	@Override
	public WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WikipediaSocket wmi, OntologySocket cyc) {
		LoggerFactory.getLogger(getClass()).trace(source);
		WeightedSet<OntologyConcept> result = new WeightedSet<>();
		if (source == null || source.isEmpty())
			return result;
		// Remove functions
		Matcher m = WikiParser.FUNCTION_PARSER.matcher(source);
		source = m.replaceAll("");

		source = WikiParser.cleanAllMarkup(source).trim();
		if (source.isEmpty())
			return result;
		source = source.replaceAll("\n", " ");
		source = source.replaceAll("  +", " ");

		result.add(new StringConcept(source));
		return result;
	}
}
