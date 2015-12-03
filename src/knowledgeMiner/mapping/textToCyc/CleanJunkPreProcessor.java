/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.MappingPreProcessor;
import util.wikipedia.WikiParser;

/**
 * 
 * @author Sam Sarjant
 */
public class CleanJunkPreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WikipediaSocket wmi, OntologySocket cyc) {
		return WikiParser.cleanupUselessMarkup(input);
	}

	@Override
	public boolean requiresRecurse() {
		return false;
	}

}
