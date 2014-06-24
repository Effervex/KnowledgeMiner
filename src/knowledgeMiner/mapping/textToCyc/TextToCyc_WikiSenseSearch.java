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

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * Searches Wikipedia for all articles linked by the string used for searching
 * and uses those results as the seed for WikiToCyc mappings.
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_WikiSenseSearch extends
		MappingHeuristic<String, OntologyConcept> {
	public TextToCyc_WikiSenseSearch(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(String term,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(term);
		WeightedSet<Integer> arts = wmi.getWeightedArticles(term);
		WeightedSet<OntologyConcept> concepts = new WeightedSet<>();
		for (Integer art : arts)
			concepts.addAll(
					mapper_.getVerifiedMappings(art, null, wmi, ontology),
					arts.getWeight(art));
		return concepts;
	}

}
