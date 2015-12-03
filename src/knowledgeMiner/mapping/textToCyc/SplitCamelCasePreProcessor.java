/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.module.NLPToStringModule;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.MappingPreProcessor;

/**
 * 
 * @author Sam Sarjant
 */
public class SplitCamelCasePreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WikipediaSocket wmi, OntologySocket cyc) {
		return NLPToStringModule.camelCaseToNormal(input);
	}
}
