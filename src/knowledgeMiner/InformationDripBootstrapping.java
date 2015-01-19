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

import io.IOManager;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

import cyc.OntologyConcept;

/**
 * A class for performing ripple bootstrapping experiments. The experiment is
 * provided with two parameters: starting point and ripple size.
 * 
 * @author Sam Sarjant
 */
public class InformationDripBootstrapping {
	/** The thread executor. */
	private CompletionService<Collection<ConceptModule>> pool_;

	/** The initial seed concept. */
	private ConceptModule initial_;

	/** The maximum number of ripples (defaults to exhaustive ripples). */
	private int maxRipple_ = -1;

	/** The ontology access. */
	private OntologySocket ontology_;

	/** The number of repeats (bootstrapping passes). Defaults to 1. */
	private int repeats_ = 1;

	/** WMI access. */
	private WMISocket wmi_;

	/** The starting run number for bootstrapping purposes. */
	private int initialRunNumber_;

	/**
	 * Constructor for a new InformationDripBootstrapping
	 * 
	 * @param concept
	 *            The concept to start with (article or concept).
	 * @param ripples
	 *            The distance of 'ripples' to update (optional).
	 * @param repeats
	 *            The number of 'drips' to perform (optional).
	 * @param initialRunNumber
	 *            The starting run number for bootstrapping purposes.
	 * @throws Exception
	 */
	public InformationDripBootstrapping(String concept, String ripples,
			String repeats, String initialRunNumber) throws Exception {
		wmi_ = ResourceAccess.requestWMISocket();
		ontology_ = ResourceAccess.requestOntologySocket();
		KnowledgeMinerPreprocessor.getInstance();
		IOManager.newInstance();

		initial_ = null;
		if (concept.startsWith("#")) {
			if (!ontology_.inOntology(concept.substring(1)))
				throw new IllegalArgumentException("Concept does not exist!");
			initial_ = new ConceptModule(new OntologyConcept(
					concept.substring(1)));
		} else {
			int articleID = wmi_.getArticleByTitle(concept);
			if (articleID == -1)
				throw new IllegalArgumentException("Article does not exist!");
			initial_ = new ConceptModule(articleID);
		}

		maxRipple_ = -1;
		if (ripples != null)
			maxRipple_ = Integer.parseInt(ripples);
		repeats_ = 1;
		if (repeats != null)
			repeats_ = Integer.parseInt(repeats);
		initialRunNumber_ = 0;
		if (initialRunNumber != null)
			initialRunNumber_ = Integer.parseInt(initialRunNumber);
	}

	/**
	 * Run the experiment by starting with a seed concept/article and rippling
	 * outwards to other linked concepts/articles. When max ripple is reached,
	 * repeat for as many repeats as defined.
	 */
	private void run() {
		ResourceAccess.newInstance();
		IOManager.newInstance();
		KnowledgeMiner.readInOntologyMappings(initialRunNumber_);
		Executor executor = Executors.newFixedThreadPool(KnowledgeMiner
				.getNumThreads());
		pool_ = new ExecutorCompletionService<Collection<ConceptModule>>(
				executor);
		for (int i = 0; i < repeats_; i++) {
			KnowledgeMiner.runID_ = initialRunNumber_ + i;

			// Set up completed collections
			Set<OntologyConcept> completedConcepts = Collections
					.newSetFromMap(new ConcurrentHashMap<OntologyConcept, Boolean>());
			Set<Integer> completedArticles = Collections
					.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

			// Add the initial
			Collection<ConceptModule> rippleLayer = new HashSet<>();
			rippleLayer.add(initial_);

			int maxRipples = (maxRipple_ == -1) ? Integer.MAX_VALUE
					: maxRipple_;
			for (int r = 0; r <= maxRipples; r++) {
				System.out.println("\nRipple " + r + ": " + rippleLayer.size()
						+ " tasks to process.\n");
				int count = 0;

				// Simultaneously process every concept in the ripple layer
				System.out.print(count++ + ": ");
				for (ConceptModule cm : rippleLayer) {
					pool_.submit(new RippleTask(cm, r != maxRipples,
							completedArticles, completedConcepts));
				}

				// Wait for the tasks to finish and store results
				Collection<ConceptModule> nextLayer = new HashSet<>();
				for (int j = 0; j < rippleLayer.size(); j++) {
					try {
						// Get the results and process them.
						Collection<ConceptModule> result = pool_.take().get();
						if (count <= rippleLayer.size())
							System.out.print(count++ + ": ");
						if (r == maxRipples)
							continue;

						// Add the articles/concepts to the next ripple layer
						for (ConceptModule cm : result) {
							if (cm.getConcept() != null
									&& !completedConcepts.contains(cm
											.getConcept()))
								nextLayer.add(cm);
							else if (cm.getArticle() != -1
									&& !completedArticles.contains(cm
											.getArticle()))
								nextLayer.add(cm);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					}
				}
				rippleLayer = nextLayer;

				// TODO Record details of this run

				// Clear preprocessed data
				KnowledgeMinerPreprocessor.getInstance().writeHeuristics();

				if (rippleLayer.isEmpty())
					break;
			}
		}
	}

	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("r", true,
				"The number of ripples (-1 for unlimited).");
		options.addOption("c", true,
				"The concept to begin with (\"article\" or #concept).");
		options.addOption("N", true, "The initial hashmap size for the nodes.");
		options.addOption("i", true, "Initial run number.");

		CommandLineParser parser = new BasicParser();
		try {
			CommandLine parse = parser.parse(options, args);
			InformationDripBootstrapping rb = new InformationDripBootstrapping(
					parse.getOptionValue("c"), parse.getOptionValue("r"),
					parse.getOptionValue("N"), parse.getOptionValue("i"));
			rb.run();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
