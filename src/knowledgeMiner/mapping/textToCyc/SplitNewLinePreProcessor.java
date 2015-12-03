/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.mapping.MappingPreProcessor;
import util.UtilityMethods;
import util.wikipedia.WikiParser;

/**
 * 
 * @author Sam Sarjant
 */
public class SplitNewLinePreProcessor extends MappingPreProcessor<String> {

	@Override
	public Collection<String> process(String input, WikipediaSocket wmi,
			OntologySocket cyc) {
		// Split on commas and trim
		ArrayList<String> split = UtilityMethods.split(input, '\n');
		for (int i = 0; i < split.size(); i++) {
			split.set(i, split.get(i).trim());
		}
		return split;
	}

	@Override
	public String processSingle(String input, WikipediaSocket wmi,
			OntologySocket ontology) {
		return input;
	}

}
