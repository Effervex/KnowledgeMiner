/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.cycToWiki;

import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * This heuristic is similar to {@link CycToWiki_VoteSynonyms}, but it uses
 * contextually related articles to aid in the decision process.
 * 
 * @author Sam Sarjant
 */
public class CycToWiki_ContextRelatedSynonyms extends
		MappingHeuristic<OntologyConcept, Integer> {
	/** The number of related terms used in the related list. */
	public static final int NUM_RELATED_TERMS = 10;

	private static final int MAX_LEVELS = 4;

	/** The synonym mapper used to map context-related terms. */
	private CycToWiki_VoteSynonyms synonymMapper_;

	private Collection<Class<? extends MappingHeuristic<OntologyConcept, Integer>>> thisClass_;

	public CycToWiki_ContextRelatedSynonyms(CycMapper mappingRoot) {
		super(mappingRoot);
		synonymMapper_ = new CycToWiki_VoteSynonyms(mappingRoot);
		thisClass_ = new ArrayList<>(1);
		thisClass_.add(this.getClass());
	}

	@Override
	protected WeightedSet<Integer> mapSourceInternal(OntologyConcept cycTerm,
			WikipediaSocket wmi, OntologySocket cyc) throws IOException {
		// Progressively gather context information, starting with upper level
		// stuff, then working down.
		WeightedSet<Integer> mappings = synonymMapper_.mapSourceToTarget(
				cycTerm, wmi, cyc);
		if (mappings.isEmpty())
			// Context can't be used if no articles found.
			return mappings;

		if (!isUsefulTerm(cycTerm))
			return mappings;

		int level = 0;
		WeightedSet<Integer> relatedArticles = new WeightedSet<>();
		while (relatedArticles.size() < NUM_RELATED_TERMS && level < MAX_LEVELS) {
			Collection<OntologyConcept> relatedCycTerms = null;
			String strID = cycTerm.getIdentifier() + "";
			relatedCycTerms = null;
			switch (level) {
			// TODO Unit test all of this.
			case 0:
				// Only get immediate isa/genls
				relatedCycTerms = cyc.quickQuery(CommonQuery.DIRECTISA, strID);
				relatedCycTerms.addAll(cyc.quickQuery(CommonQuery.DIRECTGENLS,
						strID));
				break;
			case 1:
				// Get all non-narrower predicates
				Collection<String[]> otherAssertions = cyc.getAllAssertions(
						strID, 2, CommonConcepts.ISA.getID(),
						CommonConcepts.GENLS.getID(),
						CommonConcepts.DISJOINTWITH.getID());
				relatedCycTerms = new ArrayList<>();
				for (String[] assertion : otherAssertions) {
					if (assertion.length >= 3) {
						OntologyConcept concept = OntologyConcept
								.parseArgument(assertion[2]);
						if (concept != null && concept.isOntologyConcept())
							relatedCycTerms.add(concept);
					}
				}
				break;
			case -1:
				// Siblings
				if (cyc.isaCollection(cycTerm))
					relatedCycTerms = cyc.quickQuery(CommonQuery.GENLSIBLINGS,
							strID);
				else
					relatedCycTerms = cyc.quickQuery(CommonQuery.ISASIBLINGS,
							strID);
				break;
			case 3:
				// Lower terms
				// TODO Should be MAXINSTANCE and MAXSPECS
				relatedCycTerms = cyc.quickQuery(CommonQuery.DIRECTINSTANCE,
						strID);
				relatedCycTerms.addAll(cyc.quickQuery(CommonQuery.DIRECTSPECS,
						strID));
				break;
			}
			level++;

			// Map the related terms
			if (relatedCycTerms != null && !relatedCycTerms.isEmpty()) {
				for (OntologyConcept term : relatedCycTerms) {
					if (isUsefulTerm(term)) {
						if (term instanceof OntologyConcept)
							relatedArticles.setAll(
									mapper_.mapCycToWikipedia(term, thisClass_,
											wmi, cyc).getMostLikely(), 1);
					}

					if (relatedArticles.size() >= NUM_RELATED_TERMS)
						break;
				}
			}
		}
		
		// If related articles is empty, just return mappings
		if (relatedArticles.isEmpty())
			return mappings;

		// Recalculate weights based on context
		Integer[] relatedArray = relatedArticles
				.toArray(new Integer[relatedArticles.size()]);
		for (Integer art : mappings) {
			double sumRel = 0;
			for (Double d : wmi.getRelatednessList(art, relatedArray))
				sumRel += d;
			mappings.scaleElement(art, sumRel);	
		}
		mappings.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
		LoggerFactory.getLogger(CycMapper.class).trace("C-WSynonym: {} {}",
				cycTerm.getID(), mappings);
		return mappings;
	}

	/**
	 * Checks if a term is useful (not Collection, Individual, or Predicate).
	 * 
	 * @param term
	 *            The term to check.
	 * @return True if the term is not Collection, Individual or Predicate.
	 */
	public boolean isUsefulTerm(OntologyConcept concept) {
		String id = concept.getIdentifier();
		if (id.equals(CommonConcepts.COLLECTION.getID() + "")
				|| id.equals(CommonConcepts.INDIVIDUAL.getID() + "")
				|| id.equals(CommonConcepts.PREDICATE.getID() + ""))
			return false;
		return true;
	}
}
