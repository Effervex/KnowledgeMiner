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
package knowledgeMiner;

import io.ResourceAccess;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import knowledgeMiner.mining.DefiniteAssertion;
import cyc.OntologyConcept;

public class RippleTask implements Callable<Collection<ConceptModule>> {
	/** The concept module to process. */
	private ConceptModule cm_;
	private Set<Integer> completedArticles_;
	private Set<OntologyConcept> completedConcepts_;
	private boolean returnResults_;

	public RippleTask(ConceptModule cm, boolean returnResults,
			Set<Integer> completedArticles,
			Set<OntologyConcept> completedConcepts) {
		cm_ = cm;
		completedArticles_ = completedArticles;
		completedConcepts_ = completedConcepts;
		returnResults_ = returnResults;
	}

	@Override
	public Collection<ConceptModule> call() throws Exception {
		// Run the concept mapping
		ConceptMiningTask cmt = new ConceptMiningTask(cm_, true);
		cmt.run();

		// Add new ripples if this is not at the limit
		Collection<ConceptModule> nextRipple = new HashSet<>();
		Collection<ConceptModule> assertedConcepts = cmt.getAssertedConcepts();
		for (ConceptModule cm : assertedConcepts) {
			// Note completed
			completedArticles_.add(cm.getArticle());
			completedConcepts_.add(cm.getConcept());

			if (returnResults_) {
				// Add linked articles
				addLinkedArticles(cm, nextRipple);

				// Add linked concepts
				addLinkedConcepts(cm, nextRipple);
			}
		}
		return nextRipple;
	}

	/**
	 * Adds all linked articles (from mapped article) to the queue.
	 * 
	 * @param cm
	 *            The concept module containing the article mapping.
	 * @param currentRipple
	 *            The current ripple of the processed thing.
	 * @param rippleQueue
	 *            The ripple queue to add to.
	 * @param completedConcepts
	 *            All completed concepts (for avoiding repeat processing).
	 */
	private void addLinkedArticles(ConceptModule cm,
			Collection<ConceptModule> result) {
		try {
			WMISocket wmi = ResourceAccess.requestWMISocket();
			Collection<Integer> outlinks = wmi.getOutLinks(cm.getArticle());
			// Add every outlink to the queue
			for (Integer outArt : outlinks)
				result.add(new ConceptModule(outArt));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds all linked concepts (from discovered assertions) to the queue.
	 * 
	 * @param cm
	 *            The concept module containing the assertions.
	 * @param currentRipple
	 *            The current ripple of the processed thing.
	 * @param rippleQueue
	 *            The ripple queue to add to.
	 * @param completedConcepts
	 *            All completed concepts (for avoiding repeat processing).
	 */
	private void addLinkedConcepts(ConceptModule cm,
			Collection<ConceptModule> result) {
		Collection<DefiniteAssertion> assertions = cm.getConcreteAssertions();
		for (DefiniteAssertion ma : assertions) {
			OntologyConcept[] args = ma.getArgs();

			// Add the concept to the queue
			for (OntologyConcept concept : args) {
				if (concept.getID() > 0)
					result.add(new ConceptModule(concept));
			}
		}
	}
}
