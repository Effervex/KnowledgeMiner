/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.wikiToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * This heuristic uses the labels for a Wikipedia article as synonyms to search
 * for a Cyc term.
 * 
 * @author Sam Sarjant
 */
public class WikiToCyc_VoteSynonyms extends
		MappingHeuristic<Integer, OntologyConcept> {
	public WikiToCyc_VoteSynonyms(CycMapper mappingRoot) {
		super(mappingRoot);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(Integer articleID,
			WMISocket wmi, OntologySocket cyc) throws Exception {
		WeightedSet<OntologyConcept> possibleTerms = new WeightedSet<>();

		// If the id represents a non-article
		// TODO Too restrictive
		// if (!wmi.getPageType(articleID).equals("article"))
		// return possibleTerms;

		// Find the anchor synonyms
		WeightedSet<String> weightedLabels = wmi.getLabels(articleID);
		// Clone, but in all lowercase with summed counts
		WeightedSet<String> lowercaseLabels = new WeightedSet<String>(
				weightedLabels.size());
		for (String label : weightedLabels)
			lowercaseLabels.add(label.toLowerCase(),
					weightedLabels.getWeight(label));

		// For every synonym, find the possible Cyc terms. If a term occurs
		// in every synonym, it is used as the mapped term.
		lowercaseLabels.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
		for (String weightedLabel : lowercaseLabels) {
			Collection<OntologyConcept> cycTerms = cyc.findConceptByName(
					weightedLabel, false, true, true);
			double count = lowercaseLabels.getWeight(weightedLabel);
			// If the synonym produces at least one result
			if (count > 0) {
				// Check if the string hasn't already been mapped
				possibleTerms.addAll(cycTerms, count);
			}
		}

		// Check normalisation
		if (!possibleTerms.isEmpty() && possibleTerms.getWeight(possibleTerms.getOrdered().first()) > 1)
			possibleTerms.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);

		LoggerFactory.getLogger(CycMapper.class).trace("W-CSynonym: {} {}",
				articleID, possibleTerms);
		return possibleTerms;
	}
}
