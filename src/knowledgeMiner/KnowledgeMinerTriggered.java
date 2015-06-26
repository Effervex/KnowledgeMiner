package knowledgeMiner;

import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import knowledgeMiner.mining.DefiniteAssertion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cyc.OntologyConcept;

/**
 * This class is a triggered version of KnowledgeMiner. While KnowledgeMiner
 * processes all articles in a list, KnowledgeMiner triggered instead waits for
 * a triggered article to be added to the CycDAG. Once it finds one, it
 * schedules it for processing, performing several ripple loops until it the
 * information no longer changes.
 * 
 * Following this methodology, the ontology only really updates concepts it is
 * asked about, rather than processing many irrelevant concepts.
 * 
 * This class only calls on the KnowledgeMiner methods, rather than
 * reimplmenting it.
 *
 * @author Sam Sarjant
 */
public class KnowledgeMinerTriggered {
	private static final long CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(1);

	private final static Logger logger_ = LoggerFactory
			.getLogger(ConceptMiningTask.class);

	private static final String TRIGGER_QUEUED = "queued";

	private static final String TRIGGER_PROPERTY = "trigger";

	private KnowledgeMiner km_;

	public KnowledgeMinerTriggered(String[] args) {
		km_ = KnowledgeMiner.newInstance("enwiki_20110722");
		KnowledgeMiner.runID_ = -1;
		// KnowledgeMiner.readInOntologyMappings(KnowledgeMiner.runID_);
	}

	public static void main(String[] args) {
		KnowledgeMinerTriggered kmt = new KnowledgeMinerTriggered(args);
		kmt.beginTriggerWaiting();
		System.exit(0);
	}

	/**
	 * Begins a cycle of waiting for a trigger to be found in the CycDAG
	 */
	public void beginTriggerWaiting() {
		System.out.println("Starting triggered waiting cycle.");
		logger_.info("Starting triggered waiting cycle.");
		DAGSocket ontology = (DAGSocket) ResourceAccess.requestOntologySocket();

		ConceptMiningTask.usingMinedProperty_ = true;
		// Forever, continue re-checking for a trigger every interval.
		while (true) {
			try {
				// Search for a trigger
				Collection<OntologyConcept> triggers = ontology
						.searchNodeProperty(TRIGGER_PROPERTY, TRIGGER_QUEUED);

				if (!triggers.isEmpty()) {
					// If found, add it to the executor
					logger_.info("{} triggers found, beginning process",
							triggers.size());
					for (OntologyConcept concept : triggers) {
						// Set the concept/article as pending
						ontology.removeProperty(concept, true, TRIGGER_PROPERTY);

						processConcept(new ConceptModule(concept), true);
					}
				}

				// Wait for N seconds before checking again
				Thread.sleep(CHECK_INTERVAL);
			} catch (Exception e) {
				e.printStackTrace();
				logger_.error(Arrays.toString(e.getStackTrace()));
			}
		}
	}

	/**
	 * Begins the cycle for processing a single seed concept. This cycle
	 * involves repeatedly examining the concept and its ripples.
	 *
	 * @param concept
	 *            The concept to process.
	 * @param createRipples
	 *            If the trigger concept should create ripples.
	 */
	public synchronized void processConcept(ConceptModule concept,
			boolean createRipples) {
		// Queue up the triggered processing tasks
		RepeatedTriggerTask triggerTask = new RepeatedTriggerTask(concept,
				createRipples);
		km_.getExecutor().execute(triggerTask);
	}

	private class RepeatedTriggerTask implements Runnable {
		private ConceptModule concept_;
		private boolean createRipples_;

		public RepeatedTriggerTask(ConceptModule concept, boolean createRipples) {
			concept_ = concept;
			createRipples_ = createRipples;
		}

		@Override
		public void run() {
			OntologySocket ontology = ResourceAccess.requestOntologySocket();
			WMISocket wmi = ResourceAccess.requestWMISocket();

			// If the concept is mined, skip it
			int iteration = 0;
			if (concept_.getConcept() != null)
				iteration = ConceptMiningTask.getConceptState(concept_
						.getConcept());
			else
				iteration = ConceptMiningTask.getArticleState(concept_
						.getArticle());
			iteration++;

			// Map the core concept
			ConceptMiningTask cmt = new ConceptMiningTask(concept_, iteration);
			cmt.setTrackAsserted(true);
			cmt.run();

			// Create ripples
			if (createRipples_) {
				Collection<ConceptModule> assertedConcepts = cmt
						.getAssertedConcepts();
				Collection<ConceptModule> rippleConcepts = new HashSet<>();
				// Identify changed concepts
				for (ConceptModule cm : assertedConcepts) {
					if (cm.isSignificantlyChanged()) {
						Collection<ConceptModule> linked = notifyLinkedConcepts(
								cm, ontology, wmi);
						rippleConcepts.addAll(linked);
					}
				}

				for (ConceptModule link : rippleConcepts) {
					processConcept(link, false);
				}

				// Add original concept to the end of the queue
				processConcept(concept_, true);
			}
		}

		/**
		 * Sets all linked concepts mining state to false, as well as preparing
		 * them for processing and returning. Linked concepts could be both
		 * ontologically linked and article linked.
		 *
		 * @param cm
		 *            The concept module to draw links from.
		 * @param ontology
		 *            The ontology access.
		 * @param wmi
		 * @return The linked modules prepped for processing.
		 */
		private Collection<ConceptModule> notifyLinkedConcepts(
				ConceptModule cm, OntologySocket ontology, WMISocket wmi) {
			Collection<ConceptModule> linked = new HashSet<>();
			// Get linked concepts
			for (DefiniteAssertion da : cm.getConcreteAssertions()) {
				OntologyConcept[] args = da.getArgs();

				// Add the concept to the queue
				for (OntologyConcept concept : args) {
					if (concept.getID() > 0 && !concept.equals(cm.getConcept())) {
						linked.add(new ConceptModule(concept));
					}
				}
			}

			// Get linked articles (and their linked concepts)
			try {
				Collection<Integer> outlinks = wmi.getOutLinks(cm.getArticle());
				// Add every outlink to the queue
				for (Integer outArt : outlinks)
					linked.add(new ConceptModule(outArt));
			} catch (IOException e) {
				e.printStackTrace();
			}
			return linked;
		}
	}
}
