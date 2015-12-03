/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.MappingPreProcessor;

/**
 * 
 * @author Sam Sarjant
 */
public class ReplaceUnderscorePreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WikipediaSocket wmi, OntologySocket cyc) {
		return input.replaceAll("_+", " ").trim();
	}

}
