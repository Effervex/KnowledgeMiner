/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.cycToWiki;

import graph.module.NLPToStringModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;
import util.UtilityMethods;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * A simple heuristic that just checks a direct title mapping, with some
 * manipulation of the capitalisation and floating words.
 * 
 * @author Sam Sarjant
 * 
 */
public class CycToWiki_TitleMatching extends
		MappingHeuristic<OntologyConcept, Integer> {

	public CycToWiki_TitleMatching(CycMapper mappingRoot) {
		super(mappingRoot);
	}

	@Override
	protected WeightedSet<Integer> mapSourceInternal(OntologyConcept cycTerm,
			WMISocket wmi, OntologySocket cyc) throws IOException {
		// Cannot map functions
		WeightedSet<Integer> mappings = new WeightedSet<>();
		if (cycTerm.isFunction())
			return mappings;

		Set<String> possibleStrings = UtilityMethods
				.manipulateStringCapitalisation(NLPToStringModule
						.conceptToPlainText(cycTerm.getConceptName()));
		// Check every possible title. If any duplicates, return null.
		// mappings.addAll(wmi.batchCommand("art", possibleStrings,
		// Integer.class));
		List<Integer> target = wmi.getArticleByTitle(possibleStrings
				.toArray(new String[possibleStrings.size()]));
		UtilityMethods.removeNegOnes(target);
		mappings.setAll(target, 1);

		return mappings;
	}
}
