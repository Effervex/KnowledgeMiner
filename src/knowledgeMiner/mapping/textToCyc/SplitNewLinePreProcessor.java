/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;

import knowledgeMiner.mapping.MappingPreProcessor;
import util.wikipedia.WikiParser;

/**
 * 
 * @author Sam Sarjant
 */
public class SplitNewLinePreProcessor extends MappingPreProcessor<String> {

	@Override
	public Collection<String> process(String input, WMISocket wmi,
			OntologySocket cyc) {
		return WikiParser.split(input, "\n");
	}

	@Override
	public String processSingle(String input, WMISocket wmi,
			OntologySocket ontology) {
		return input;
	}

}
