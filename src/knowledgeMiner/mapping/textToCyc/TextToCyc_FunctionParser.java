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
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * The function parser attempts to parse wrapping functions from a string. The
 * string must have more than one tokens for the function parser to be able to
 * produce a result.
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_FunctionParser extends
		MappingHeuristic<String, OntologyConcept> {
	public TextToCyc_FunctionParser(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
