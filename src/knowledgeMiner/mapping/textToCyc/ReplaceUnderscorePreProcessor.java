/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.MappingPreProcessor;

/**
 * 
 * @author Sam Sarjant
 */
public class ReplaceUnderscorePreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WMISocket wmi, OntologySocket cyc) {
		return input.replaceAll("_+", " ").trim();
	}

}
