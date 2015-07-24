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
public class CleanJunkPreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WMISocket wmi, OntologySocket cyc) {
		return WikiParser.cleanupUselessMarkup(input);
	}

	@Override
	public boolean requiresRecurse() {
		return false;
	}

}
