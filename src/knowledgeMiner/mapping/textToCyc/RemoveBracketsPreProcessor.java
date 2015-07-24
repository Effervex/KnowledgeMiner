/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.MappingPreProcessor;
import util.wikipedia.WikiParser;

/**
 * 
 * @author Sam Sarjant
 */
public class RemoveBracketsPreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WMISocket wmi, OntologySocket cyc) {
		return WikiParser.cleanBrackets(input, 0, '(', ')', false, true).trim();
	}

}
