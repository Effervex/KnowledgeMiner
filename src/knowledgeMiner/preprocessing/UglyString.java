/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.preprocessing;

import graph.core.CommonConcepts;
import graph.module.NLPToStringModule;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.util.Collection;

import knowledgeMiner.mining.DefiniteAssertion;
import cyc.CycConstants;
import cyc.OntologyConcept;
import cyc.StringConcept;

/**
 * This preprocessor creates 'ugly strings' for terms by decomposing their
 * names.
 * 
 * @author Sam Sarjant
 */
public class UglyString implements Preprocessor {
	@Override
	public void processTerm(OntologyConcept term) throws Exception {
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		if (!cyc.isa(term, CommonConcepts.PREDICATE.getID())
				|| cyc.isa(term, "BookkeepingPredicate") || term.isFunction())
			return;
		String cleanTerm = NLPToStringModule.conceptToPlainText(term
				.getConceptName());
		int bracketIndex = cleanTerm.indexOf("(");
		if (bracketIndex != -1)
			cleanTerm = cleanTerm.substring(0, bracketIndex).trim();

		Collection<String> synonyms = cyc.getSynonyms(term.getIdentifier());

		// Collection<StringFragment> nGrams = CycDeriver.nGrams(cleanTerm, 2);
		String[] split = cleanTerm.split("\\s");
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < split.length; i++) {
			if (i != 0)
				buffer.append(" ");
			buffer.append(split[i].toLowerCase());
			if (!synonyms.contains(buffer.toString())) {
				DefiniteAssertion assertion = new DefiniteAssertion(
						CycConstants.UGLY_PRED.getConcept(), null, term,
						new StringConcept(buffer.toString()));
				assertion.makeAssertion(null, cyc);
			}
		}
	}
}
