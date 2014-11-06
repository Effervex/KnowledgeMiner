/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.cycToWiki;

import graph.module.NLPToStringModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * This heuristic searches for a Wikipedia article mapping by searching for
 * articles using the synonyms of the Cyc term.
 * 
 * @author Sam Sarjant
 */
public class CycToWiki_VoteSynonyms extends
		MappingHeuristic<OntologyConcept, Integer> {
	public CycToWiki_VoteSynonyms(CycMapper mappingRoot) {
		super(mappingRoot);
	}

	@Override
	protected WeightedSet<Integer> mapSourceInternal(OntologyConcept cycTerm,
			WMISocket wmi, OntologySocket cyc) throws IOException {
		return mapSourceInternal(cycTerm, wmi, cyc, null);
	}

	/**
	 * Uses related articles to alter the result weights based on how similar
	 * the results are to the related articles.
	 * 
	 * @param cycTerm
	 *            The term being mapped.
	 * @param wmi
	 *            The WMI access.
	 * @param cyc
	 *            The Cyc access.
	 * @param relatedArticles
	 *            The related articles (can be null).
	 * @return A {@link WeightedSet} of articles, weighted by synonym relevance
	 *         and context weight.
	 * @throws IOException
	 *             Should something go awry...
	 */
	protected WeightedSet<Integer> mapSourceInternal(OntologyConcept cycTerm,
			WMISocket wmi, OntologySocket cyc,
			Collection<Integer> relatedArticles) throws IOException {
		WeightedSet<Integer> mappings = new WeightedSet<>();
		// Compile the synonyms
		Collection<String> synonyms = new HashSet<>(cyc.getSynonyms(cycTerm
				.getIdentifier()));
		if (!cycTerm.isFunction()) {
			String termName = NLPToStringModule.conceptToPlainText(cycTerm
					.getConceptName());
			for (String name : UtilityMethods
					.manipulateStringCapitalisation(NLPToStringModule
							.conceptToPlainText(cycTerm.getConceptName()))) {
				if (!name.trim().isEmpty())
					synonyms.add(name.trim());
			}
			// Add contextless version too
			if (termName.contains("(") && termName.indexOf("(") > 0) {
				String synonym = termName.substring(0,
						termName.lastIndexOf("(")).trim();
				if (!synonym.isEmpty())
					synonyms.add(synonym);
			}
		}

		String[] synonymArray = synonyms.toArray(new String[synonyms.size()]);

		List<WeightedSet<Integer>> synonymMappings = null;
		if (relatedArticles != null)
			// With related terms.
			synonymMappings = wmi.getWeightedArticles(relatedArticles,
					KnowledgeMiner.CUTOFF_THRESHOLD, synonymArray);
		else
			synonymMappings = wmi.getWeightedArticles(synonymArray);
		for (WeightedSet<Integer> synonymMapping : synonymMappings)
			mappings.addAll(synonymMapping);
		mappings.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);

		// Check the mappings for the most frequently occurring term.
		LoggerFactory.getLogger(CycMapper.class).trace("C-WSynonym: {} {}",
				cycTerm.getID(), mappings);
		return mappings;
	}
}
