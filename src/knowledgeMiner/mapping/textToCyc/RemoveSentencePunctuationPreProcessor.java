/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.MappingPreProcessor;

public class RemoveSentencePunctuationPreProcessor extends
		MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WikipediaSocket wmi,
			OntologySocket ontology) {
		return input.replaceAll("[.!?,:;]$", "");
	}

}
