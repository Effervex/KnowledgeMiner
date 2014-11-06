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
package knowledgeMiner.preprocessing;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;

import util.collection.WeightedSet;

import cyc.OntologyConcept;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.mapping.MappingHeuristic;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic;

/**
 * A threadable precomputation task worker.
 * 
 * @author Sam Sarjant
 */
public class PrecomputationTask implements Runnable {
	/** The WMI access. */
	private WMISocket wmi_;

	/** The ontology worker. */
	private OntologySocket ontology_;

	/** The type of task. */
	private PrecomputationTaskType taskType_;

	/** The input to process. */
	private ConceptModule input_;

	/** The heuristics to process the input with. */
	private Collection<? extends WeightedHeuristic> heuristics_;

	private KnowledgeMinerPreprocessor kmp_;

	/**
	 * Constructor for a new PrecomputationTask
	 * 
	 * @param cm
	 *            The input to process.
	 * @param heuristics
	 * @param taskType
	 *            The type of process.
	 */
	public PrecomputationTask(ConceptModule cm,
			Collection<? extends WeightedHeuristic> heuristics,
			PrecomputationTaskType taskType, KnowledgeMinerPreprocessor kmp) {
		input_ = cm;
		heuristics_ = heuristics;
		taskType_ = taskType;
		kmp_ = kmp;
	}

	@Override
	public void run() {
		wmi_ = ResourceAccess.requestWMISocket();
		ontology_ = ResourceAccess.requestOntologySocket();

		switch (taskType_) {
		case CYC_TO_WIKI:
			mapConcept(input_, heuristics_);
			break;
		case WIKI_TO_CYC:
			mapArticle(input_, heuristics_);
			break;
		case MINE:
			mineArticle(input_, heuristics_);
			break;
		}
		kmp_.incrementProcessed();
	}

	/**
	 * Maps a concept to a set of articles for the given heuristics.
	 * 
	 * @param cm
	 *            The concept module to mine.
	 * @param heuristics
	 *            The heuristics to mine the article with (individually).
	 */
	public void mapConcept(ConceptModule cm,
			Collection<? extends WeightedHeuristic> heuristics) {
		// Perform a mining task for the article
		for (WeightedHeuristic heuristic : heuristics) {
			if (kmp_.isProcessed(heuristic.toString(), cm.getConcept().getID())
					|| !heuristic.isPrecomputed())
				continue;

			@SuppressWarnings("unchecked")
			MappingHeuristic<OntologyConcept, Integer> mh = (MappingHeuristic<OntologyConcept, Integer>) heuristic;
			WeightedSet<Integer> mappings = mh.mapSourceToTarget(
					cm.getConcept(), wmi_, ontology_);
			kmp_.writeCycMappedData(cm, mappings, mh);
		}
	}

	/**
	 * Maps an article to a set of concepts for the given heuristics.
	 * 
	 * @param cm
	 *            The concept module to mine.
	 * @param heuristics
	 *            The heuristics to mine the article with (individually).
	 */
	public void mapArticle(ConceptModule cm,
			Collection<? extends WeightedHeuristic> heuristics) {
		// Perform a mining task for the article
		for (WeightedHeuristic heuristic : heuristics) {
			if (kmp_.isProcessed(heuristic.toString(), cm.getArticle())
					|| !heuristic.isPrecomputed())
				continue;

			@SuppressWarnings("unchecked")
			MappingHeuristic<Integer, OntologyConcept> mh = (MappingHeuristic<Integer, OntologyConcept>) heuristic;
			WeightedSet<OntologyConcept> mappings = mh.mapSourceToTarget(
					cm.getArticle(), wmi_, ontology_);
			kmp_.writeWikiMappedData(cm, mappings, mh);
		}
	}

	/**
	 * Mines an article with the given heuristics.
	 * 
	 * @param cm
	 *            The concept module to mine.
	 * @param heuristics
	 *            The heuristics to mine the article with (individually).
	 */
	public void mineArticle(ConceptModule cm,
			Collection<? extends WeightedHeuristic> heuristics) {
		// Perform a mining task for the article
		for (WeightedHeuristic heuristic : heuristics) {
			if (kmp_.isProcessed(heuristic.toString(), cm.getArticle())
					|| !heuristic.isPrecomputed())
				continue;

			WikipediaArticleMiningHeuristic wamh = (WikipediaArticleMiningHeuristic) heuristic;
			MinedInformation info = wamh.mineArticle(cm,
					MinedInformation.ALL_TYPES, wmi_, ontology_);
			kmp_.writeMinedData(cm, info, wamh);
		}
	}
}
