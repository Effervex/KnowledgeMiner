/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

import io.IOManager;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;
import knowledgeMiner.mining.wikipedia.ListMiner;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * The concept mining task controls the concept mapping and mining flow of the
 * algorithm by iteratively updating the concepts before finally settling on a
 * result and asserting the information found.
 * 
 * The intent of this task is to only process as much as required and no more to
 * find the best possible. This is achieved by iteratively updating result
 * weights.
 * 
 * @author Sam Sarjant
 */
public class ConceptMiningTask implements Runnable {
	private static final int BIG_ENOUGH = 40000000;

	/** The chance that a child is created. */
	private static final float CHILD_CREATION_CHANCE = .5f;

	private final static Logger logger_ = LoggerFactory
			.getLogger(ConceptMiningTask.class);

	/** The frequency at which the output files are updated. */
	public static final int UPDATE_INTERVAL = 100;

	static final byte MAPPED_CURRENT = 2;
	static final byte PENDING = 1;
	static final byte UNKNOWN = 0;
	static final byte UNMAPPABLE_PRIOR = -1;
	static final byte UNMAPPABLE_CURRENT = -2;

	/** Mappings from an indexed article to a given ontology (ID). */
	private static byte[] artStates_ = new byte[BIG_ENOUGH];

	/** Mappings from an indexed concept (ID) to a given article. */
	private static byte[] ontologyStates_ = new byte[BIG_ENOUGH];

	/** The number of asserted concepts. */
	public static int assertedCount_ = 0;

	/** The interactive interface for interactive mode. */
	public static InteractiveMode interactiveInterface_ = new InteractiveMode();

	/** A collection for keeping track of all asserted conceptModules. */
	private Collection<ConceptModule> assertedConcepts_;

	/** The KnowledgeMiner core. */
	private KnowledgeMiner km_;

	private OntologySocket ontology_;

	/** The data to process, in weighted order. */
	private SortedSet<ConceptModule> processables_;

	/** The WMI access for this threaded task. */
	private WMISocket wmi_;

	private boolean trackAsserted_ = false;

	/**
	 * Constructor for a new ConceptMiningTask
	 * 
	 */
	private ConceptMiningTask() {
		processables_ = new TreeSet<>();
		km_ = KnowledgeMiner.getInstance();
	}

	/**
	 * Constructor for a new ConceptMiningTask with a single Cyc Term to begin
	 * with.
	 * 
	 * @param conceptModule
	 *            The initial concept module to begin with. Should only be a
	 *            term/article to map.
	 */
	public ConceptMiningTask(ConceptModule conceptModule) {
		this();
		processables_.add(conceptModule);
	}

	/**
	 * Constructor for a new ConceptMiningTask with a single Cyc Term to begin
	 * with.
	 * 
	 * @param conceptModule
	 *            The initial concept module to begin with. Should only be a
	 *            term/article to map.
	 * @param trackAssertedConcepts
	 *            If the asserted concepts should be tracked.
	 */
	public ConceptMiningTask(ConceptModule conceptModule,
			boolean trackAssertedConcepts) {
		this(conceptModule);
		trackAsserted_ = trackAssertedConcepts;
	}

	/**
	 * Constructor for a new ConceptMiningTask.
	 * 
	 * @param mapped
	 *            The {@link ConceptModule}s to process.
	 * @param cyc
	 *            The Cyc access.
	 * @param wikipedia
	 *            The WMI access.
	 */
	public ConceptMiningTask(SortedSet<ConceptModule> mapped) {
		this();
		processables_ = mapped;
	}

	/**
	 * Adds mappings to processables, merging them with existing concepts when
	 * applicable. Mappings may also not be added if they include a completed
	 * concept/article.
	 * 
	 * @param mappings
	 *            The mappings to add/merge.
	 * @param removeCompletedMappings
	 *            If mappings containing completed terms should not be added.
	 * @param commonConcept
	 *            If the common element is the concept. False implies the common
	 *            element is the article.
	 * @param onlyKeepReversed
	 *            If only mappings that are reversed should be kept.
	 */
	private void addProcessables(Collection<ConceptModule> mappings,
			boolean removeCompletedMappings, boolean commonConcept,
			boolean onlyKeepReversed) {
		// Store mappings in map for quick access
		// All mappings are either all the same article, or the same concept.
		Map<Object, ConceptModule> mappedMappings = new HashMap<>(
				mappings.size() * 2);
		Object commonID = null;
		for (ConceptModule cm : mappings) {
			if (!removeCompletedMappings || !containsCompleted(cm)) {
				if (commonConcept) {
					mappedMappings.put(cm.getArticle(), cm);
					commonID = cm.getConcept();
				} else {
					mappedMappings.put(cm.getConcept(), cm);
					commonID = cm.getArticle();
				}
			}
		}

		// Fuse mapping together where appropriate
		Collection<ConceptModule> added = new ArrayList<>();
		for (Iterator<ConceptModule> iter = processables_.iterator(); iter
				.hasNext();) {
			ConceptModule cm = iter.next();
			// Look up cm article and concept. If there is an element, merge it,
			// keeping mined/disambiguated info
			Object source = cm.getConcept();
			Object target = cm.getArticle();
			if (!commonConcept) {
				Object temp = source;
				source = target;
				target = temp;
			}

			// If same common element
			if (source.equals(commonID)) {
				// Same commonID. Now check the mapped object
				if (mappedMappings.containsKey(target)) {
					// Merge by removing cm and adding after looping
					ConceptModule otherCM = mappedMappings.get(target);
					mappedMappings.remove(target);
					if (cm.getState() != MiningState.MAPPED)
						continue;

					iter.remove();
					try {
						cm.mergeInformation(otherCM);
					} catch (Exception e) {
						e.printStackTrace();
					}
					added.add(cm);
					continue;
				} else if (onlyKeepReversed)
					iter.remove();
			}
		}

		processables_.addAll(added);
		if (!onlyKeepReversed)
			processables_.addAll(mappedMappings.values());
	}

	private boolean checkAvailability(SortedSet<ConceptModule> allResults,
			ConceptModule cm, Set<Integer> pendingArts,
			Set<Integer> pendingConcepts) {
		if (cm.getArticle() != -1) {
			if (getArticleState(cm.getArticle()) != UNKNOWN
					&& getArticleState(cm.getArticle()) != UNMAPPABLE_PRIOR) {
				logger_.trace("Article " + cm + " currently/already mapped!");
				return false;
			}
			setArticleState(cm.getArticle(), PENDING, pendingArts);
		}
		if (cm.getConcept() != null) {
			if (getConceptState(cm.getConcept()) != UNKNOWN
					&& getConceptState(cm.getConcept()) != UNMAPPABLE_PRIOR) {
				logger_.trace("Concept " + cm + " currently/already mapped!");
				return false;
			}
			setConceptState(cm.getConcept().getID(), PENDING, pendingConcepts);
		}
		return true;
	}

	private void cleanupPending(Set<Integer> pendingArts,
			Set<Integer> pendingConcepts) {
		for (Integer pendingArt : pendingArts.toArray(new Integer[pendingArts
				.size()])) {
			logger_.trace("Loose pending article " + pendingArt + ".");
			setArticleState(pendingArt, UNKNOWN, pendingArts);
		}
		for (Integer pendingConcept : pendingConcepts
				.toArray(new Integer[pendingConcepts.size()])) {
			logger_.trace("Loose pending concept " + pendingConcept + ".");
			setConceptState(pendingConcept, UNKNOWN, pendingConcepts);
		}
	}

	private boolean containsCompleted(ConceptModule concept) {
		if (concept.getArticle() != -1
				&& isProcessed(concept.getArticle(), artStates_))
			return true;
		if (concept.getConcept() != null && concept.getConcept().getID() >= 0
				&& isProcessed(concept.getConcept().getID(), ontologyStates_))
			return true;
		return false;
	}

	private boolean isProcessed(Integer index, byte[] stateArray) {
		return getState(index, stateArray) == MAPPED_CURRENT
				|| getState(index, stateArray) == UNMAPPABLE_CURRENT;
	}

	/**
	 * Creates a new concept module linking to a newly created concept.
	 * 
	 * @param cm
	 *            The concept module to create a concept for.
	 * @return The newly created concept module or null if impossible.
	 */
	private ConceptModule createConcept(ConceptModule cm) {
		Integer article = cm.getArticle();
		try {
			// TODO Identify some collection sense
			// TODO Use the possible parentage, perhaps? Might have to be after
			// disambiguation though.
			OntologyConcept newConcept = createNewCycTermName(
					wmi_.getPageTitle(article, true), null, ontology_);
			if (newConcept != null) {
				ConceptModule newCM = new ConceptModule(newConcept, article,
						CHILD_CREATION_CHANCE, false);
				logger_.debug("New concept created: " + newCM + ".");
				newCM.mergeInformation(cm);
				return newCM;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * If this asserted concept is for the original concept.
	 * 
	 * @param concept
	 *            The asserted concept.
	 * @param original
	 *            The original concept/article.
	 * @return True if the asserted concept includes the original
	 *         concept/article.
	 */
	private boolean originalConcept(ConceptModule concept,
			ConceptModule original) {
		return concept.getArticle().equals(original.getArticle())
				|| concept.getConcept().equals(original.getConcept());
	}

	/**
	 * Checks if a mapping has already been processed.
	 * 
	 * @param concept
	 *            The concept to check.
	 * @param onlyKeepOriginal
	 *            If only mappings containing the original concept are to be
	 *            used.
	 * @param original
	 *            The original concept that started this.
	 * @return True if the concept module should be skipped.
	 */
	private boolean shouldSkipConceptModule(ConceptModule concept,
			boolean onlyKeepOriginal, ConceptModule original) {
		// If only original concepts allowed, remove any non-originals
		if (onlyKeepOriginal) {
			try {
				if (concept.getConcept() != null
						&& !ontology_.inOntology(concept.getConcept()))
					return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (concept.isCreatedConcept())
				return true;
			return !originalConcept(concept, original);
		}

		// Remove list articles
		try {
			if (concept.getArticle() != -1
					&& wmi_.getPageTitle(concept.getArticle(), true)
							.startsWith(ListMiner.LIST_OF))
				return true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Stop on completed articles/concepts (from this run)
		if (concept.getArticle() != -1
				&& isProcessed(concept.getArticle(), artStates_)) {
			// If the concept produced the article, it has already mapped and
			// should be skipped
			// if (concept.isCycToWiki() && concept.isMapped())
			return true;
			// concept.removeArticle();
		}
		if (concept.getConcept() != null
				&& isProcessed(concept.getConcept().getID(), ontologyStates_)) {
			// If the article produced the concept, it has already mapped and
			// should be skipped
			// if (!concept.isCycToWiki() && concept.isMapped())
			return true;
			// concept.removeConcept();
		}

		// If neither article nor concept, remove this
		if (concept.getArticle() == -1
				&& (concept.getConcept() == null || concept.isCreatedConcept()))
			return true;

		return false;
	}

	/**
	 * Performs a mapping/mining/dismabiguation/asserting step for the given
	 * ConceptModule, depending on its current state.
	 * 
	 * @param cm
	 *            The concept module to process.
	 * @param singleMapping
	 *            If only a single mapping is being created.
	 * @param pendingArts
	 *            The pending articles to notify if the state of the article
	 *            changes.
	 * @param pendingConcepts
	 *            The pending concepts to notify if the state of the concept
	 *            changes.
	 */
	private void runInternal(ConceptModule cm, boolean singleMapping,
			Set<Integer> pendingArts, Set<Integer> pendingConcepts) {
		try {
			logger_.info(cm.toFlatString());
			km_.updateConcept(Thread.currentThread(), cm, processables_, wmi_);
			MiningState state = cm.getState();
			switch (state) {
			case UNMINED:
				// If unmined: Mine article (except children)
				int allButChildren = MinedInformation.ALL_TYPES
						- (1 << InformationType.CHILD_ARTICLES.ordinal());
				mineConcept(cm, allButChildren);
				processables_.add(cm);
				break;
			case UNMAPPED:
				// If unmapped: Map article to cm
				Collection<ConceptModule> mappings = mapConcept(cm, false);
				interactiveInterface_.interactiveMap(cm, mappings, wmi_,
						ontology_);
				addProcessables(mappings, singleMapping, cm.isCycToWiki(),
						false);
				break;
			case MAPPED:
				// If mapped: Map in reverse
				Collection<ConceptModule> revMappings = mapConcept(cm, true);
				interactiveInterface_.interactiveMap(cm, revMappings, wmi_,
						ontology_);
				addProcessables(revMappings, singleMapping, !cm.isCycToWiki(),
						false);
				break;
			case REVERSE_MAPPED:
				// If mapped: Immediately test all concepts through
				// disambiguation
				cm.disambiguateAssertions(ontology_);
				processables_.add(cm);
				break;
			case CONSISTENT:
				// If reverse-mapped: Assert & initiate sibling search
				assertConcept(cm, pendingArts, pendingConcepts);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Source: " + cm);
			logger_.error(e.getMessage() + " Source: " + cm);
			ResourceAccess.recreateWMISocket(wmi_);
			ResourceAccess.recreateOntologySocket(ontology_);
			wmi_ = ResourceAccess.requestWMISocket();
			ontology_ = ResourceAccess.requestOntologySocket();
		}

	}

	/**
	 * Asserts the information in a concept module to Cyc.
	 * 
	 * @param concept
	 *            The concept to be asserted.
	 * @param pendingArts
	 *            The pending articles set to remove the assertion from.
	 * @param pendingConcepts
	 *            The pending concepts set to remove the assertion from.
	 * @return If the assertion was successful.
	 */
	protected boolean assertConcept(ConceptModule concept,
			Set<Integer> pendingArts, Set<Integer> pendingConcepts) {
		try {
			synchronized (this) {
				if (containsCompleted(concept))
					return false;
				// Get concept ID and record processed
				setArticleState(concept.getArticle(), MAPPED_CURRENT,
						pendingArts);

				// If a created concept
				if (concept.isCreatedConcept()) {
					// If a no new concepts allowed, or there are no parentage
					// assertions.
					if (KnowledgeMiner.mappingRun_
							|| concept.getConcreteParentageAssertions()
									.isEmpty()) {
						setArticleState(concept.getArticle(),
								UNMAPPABLE_CURRENT, pendingArts);
						return false;
					}
				} else if (concept.getConcept().getID() < 0) {
					// No unreifiable mappings
					setArticleState(concept.getArticle(),
							UNMAPPABLE_CURRENT, pendingArts);
					return false;
				}

				// Create any new concepts
				concept.findCreateConcept(ontology_);
				setConceptState(concept.getConcept().getID(), MAPPED_CURRENT,
						pendingConcepts);
			}
			// Firstly, record the mapping
			String articleTitle = wmi_.getPageTitle(concept.getArticle(), true);
			IOManager.getInstance().writeMapping(concept, articleTitle);

			// TODO Remove all KM assertions no longer produced by KM

			// Then, make the mining assertions
			concept.makeAssertions(articleTitle, ontology_);
			if (trackAsserted_) {
				if (assertedConcepts_ == null)
					assertedConcepts_ = new ArrayList<>();
				assertedConcepts_.add(concept);
			}

			concept.setState(1.0, MiningState.ASSERTED);

			ConceptMiningTask.interactiveInterface_.interactiveAssertion(
					concept, wmi_, ontology_);

			// Update the mining heuristics
			for (MiningHeuristic mh : KnowledgeMiner.getInstance().getMiner()
					.getMiningHeuristics())
				mh.updateGlobal(concept, wmi_);

			assertedCount_++;
			if (assertedCount_ % UPDATE_INTERVAL == 0)
				km_.statusUpdate();

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			logger_.error("EXCEPTION:\t" + concept.toFlatString() + "\t"
					+ Arrays.toString(e.getStackTrace()));
		}
		return false;
	}

	/**
	 * Maps a single concept or article to a SortedSet of mappings.
	 * 
	 * @param concept
	 *            The concept being mapped.
	 * @param reversedOrder
	 *            If mapping should go in the opposite direction.
	 * @return A sortedset of mappings.
	 */
	protected SortedSet<ConceptModule> mapConcept(ConceptModule concept,
			boolean reversedOrder) {
		SortedSet<ConceptModule> mapped = new TreeSet<>();
		try {
			// TODO Deal with this. If ID == -1,
			// If the concept is newly created or has -1 ID, just automap.
			if (concept.isCreatedConcept()
					|| (concept.getConcept() != null && concept.getConcept()
							.getID() == -1)) {
				// If there is an article
				if (concept.getArticle() == -1)
					return mapped;
				// Term is new, just automatically return reverse mapping.
				concept.setState(1.0, MiningState.REVERSE_MAPPED);
				mapped.add(concept);
				return mapped;
			}

			boolean cycToWiki = concept.isCycToWiki();
			if (reversedOrder)
				cycToWiki = !cycToWiki;
			if (cycToWiki) {
				// Cyc term to Wiki article.
				OntologyConcept cycTerm = concept.getConcept();
				WeightedSet<Integer> articles = km_.getMapper()
						.mapCycToWikipedia(cycTerm, null, wmi_, ontology_);
				for (Integer article : articles) {
					ConceptModule cm = new ConceptModule(cycTerm, article,
							articles.getWeight(article), true);
					cm.mergeInformation(concept);
					mapped.add(cm);
				}
			} else {
				// Wiki article to Cyc term
				Integer articleID = concept.getArticle();
				WeightedSet<OntologyConcept> terms = km_.getMapper()
						.mapWikipediaToCyc(articleID, wmi_, ontology_);
				for (OntologyConcept term : terms) {
					ConceptModule cm = new ConceptModule(term, articleID,
							terms.getWeight(term), false);
					cm.mergeInformation(concept);
					mapped.add(cm);
				}

				// Create a new concept mapping
				ConceptModule newConcept = createConcept(concept);
				if (newConcept != null)
					mapped.add(newConcept);
			}
		} catch (Exception e) {
			logger_.error("EXCEPTION:\t" + concept.toFlatString() + "\t"
					+ Arrays.toString(e.getStackTrace()));
		}

		return mapped;
	}

	/**
	 * Mines the child articles in a thread-based manner.
	 * 
	 * @param parentTerm
	 *            The parent term for the children.
	 * @param parentArticle
	 *            The parent article for the children.
	 * @param childArticles
	 *            The potential child articles.
	 * @return
	 */
	// private void mineChildren(ConceptModule concept) {
	// try {
	// CycConcept parentTerm = concept.getConcept();
	// if (!ontology_.isaCollection(parentTerm))
	// return;
	// int parentArticle = concept.getArticle();
	//
	// // Find the child articles.
	// Collection<Integer> childArticles = findChildArticles(concept);
	//
	// Random random = new Random();
	// for (Integer childArt : childArticles) {
	// // Do not make the parent term a child
	// if (childArt == parentArticle
	// || (!sortingArticles_ && random.nextFloat() >= CHILD_CREATION_CHANCE))
	// continue;
	//
	// logger_.trace("MINING_CHILD:\t" + childArt + " (Parent "
	// + parentTerm + ")");
	// ConceptModule childModule = new ConceptModule(childArt,
	// parentTerm, parentArticle);
	// KnowledgeMiner.getInstance().processConcept(
	// new ConceptMiningTask(childModule));
	// }
	// } catch (Exception e) {
	// e.printStackTrace();
	// logger_.error("EXCEPTION:\t" + concept.toFlatString() + "\t"
	// + Arrays.toString(e.getStackTrace()));
	// }
	// }

	protected void mineConcept(ConceptModule cm, int minedTypes)
			throws Exception {
		km_.getMiner().mineArticle(cm, minedTypes, wmi_, ontology_);
		cm.buildDisambiguationGrid(ontology_, wmi_);
	}

	@SuppressWarnings("unchecked")
	public Collection<ConceptModule> getAssertedConcepts() {
		if (assertedConcepts_ == null)
			return CollectionUtils.EMPTY_COLLECTION;
		return assertedConcepts_;
	}

	/**
	 * Gets the verified mappings for the input to this
	 * {@link ConceptMiningTask}. That is, get every double-checked, but not
	 * mined, mapping with the learned weights.
	 * 
	 * @param mappingRestriction
	 *            A restriction for the mapping process, such that EVERY mapping
	 *            need not be examined.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            The Cyc access.
	 * @return A sorted set of double-checked and weight mappings.
	 */
	// public SortedSet<ConceptModule> getVerifiedMappings(
	// Predicate<ConceptModule> mappingRestriction, WMISocket wmi,
	// OntologySocket ontology) {
	// // return runAlternative(false);
	// wmi_ = wmi;
	// ontology_ = ontology;
	// ConceptModule concept = processables_.first();
	//
	// // Map the concept.
	// processables_ = mapConcept(concept, false);
	// ConceptModule[] mappings = processables_
	// .toArray(new ConceptModule[processables_.size()]);
	// processables_.clear();
	//
	// // Reverse map every mapping.
	// for (ConceptModule mapping : mappings) {
	// // Don't worry about mappings below the threshold.
	// if (mapping.getModuleWeight() < KnowledgeMiner.CUTOFF_THRESHOLD)
	// break;
	// if (mappingRestriction == null || mappingRestriction.apply(mapping)) {
	// processables_.add(mapping);
	// addProcessables(mapConcept(mapping, true), false,
	// !mapping.isCycToWiki(), true);
	// }
	// }
	//
	// return processables_;
	// }

	/**
	 * Processes a collection of articles, assigning assertions to them if they
	 * meet the parent collection requirements.
	 * 
	 * @param children
	 *            The children to process.
	 * @param parents
	 *            The parent collection(s) to meet.
	 * @param autoAssertions
	 *            The assertion(s) to assign if the parent reqs. are met.
	 */
	public void processCollection(Collection<Integer> children,
			Collection<OntologyConcept> parents,
			Collection<MinedAssertion> autoAssertions) {
		// Process each child normally
		for (Integer childArt : children) {
			ConceptModule cm = new ConceptModule(childArt, parents,
					autoAssertions);
			ConceptMiningTask childTask = new ConceptMiningTask(cm);
			km_.processConcept(childTask);
		}
	}

	@Override
	public void run() {
		run(true);
	}

	public void run(boolean singleMapping) {
		SortedSet<ConceptModule> allResults = null;

		ConceptModule cm = processables_.first();
		allResults = new TreeSet<ConceptModule>();

		wmi_ = ResourceAccess.requestWMISocket();
		ontology_ = ResourceAccess.requestOntologySocket();

		Set<Integer> pendingArts = new HashSet<>();
		Set<Integer> pendingConcepts = new HashSet<>();
		// If the concept/article is unavailable, exit
		if (!checkAvailability(allResults, cm, pendingArts, pendingConcepts))
			return;

		ConceptModule original = cm;
		do {
			boolean tempSingleMapping = (allResults.isEmpty()) ? true
					: singleMapping;

			// Find next concept module
			boolean completed = false;
			do {
				if (processables_.isEmpty()) {
					completed = true;
					break;
				}
				cm = processables_.first();
				// Stop if the weight gets too low.
				if (cm.getModuleWeight() <= 0) {
					completed = true;
					break;
				}
				processables_.remove(cm);

			} while (shouldSkipConceptModule(cm, !tempSingleMapping, original));
			if (completed)
				break;

			// If about to assert when we already have an answer, skip it
			if (cm.getState() != MiningState.CONSISTENT || allResults.isEmpty()) {
				// Selects a particular process to run, advancing the state.
				runInternal(cm, tempSingleMapping, pendingArts, pendingConcepts);
				// System.out.println(processables_.size());

				// If the original concept, add it to the results.
				if (cm.getState().equals(MiningState.ASSERTED)
						&& originalConcept(cm, original))
					allResults.add(cm);
			} else
				allResults.add(cm);
			// Continue until either there are no processables left, or the
			// original concept has been mapped (and we only want the one
			// mapping).
		} while (!processables_.isEmpty()
				&& !(singleMapping && !allResults.isEmpty()));

		// Clean up any loose pending articles (theoretically shouldn't
		// happen).
		cleanupPending(pendingArts, pendingConcepts);
	}

	@Override
	public String toString() {
		if (processables_ == null || processables_.isEmpty())
			return "No processables.";
		ConceptModule first = processables_.first();
		return first.toString() + "\n + " + (processables_.size() - 1)
				+ " others.";
	}

	private static void processInput(BufferedReader in, String input)
			throws Exception {
		ConceptMiningTask cmt = new ConceptMiningTask();
		// KnowledgeMinerPreprocessor.ENABLE_PREPROCESSING = false;
		cmt.wmi_ = ResourceAccess.requestWMISocket();
		cmt.ontology_ = ResourceAccess.requestOntologySocket();
		if (input.startsWith("wmi")) {
			String command = input.substring(3).trim();
			System.out.println(cmt.wmi_.command(command, true));
		} else if (input.equals("1")) {
			System.out
					.println("Enter term '#$<termname>' or article '<article>' to map.");
			String map = in.readLine().trim();
			staticMap(cmt, map);
		} else if (input.equals("2")) {
			System.out
					.println("Enter mapping to reverse '#$<termname> => <article>' or vice-versa.");
			String map = in.readLine().trim();
			staticReverseMap(cmt, map);
		} else if (input.equals("3")) {
			System.out.println("Enter article to mine '<article>'.");
			String map = in.readLine().trim();
			staticMine(cmt, in, map);
		} else if (InteractiveMode.interactiveMode_ || input.equals("4")
				|| input.equals("7")) {
			InteractiveMode.interactiveMode_ |= input.equals("7");
			System.out
					.println("Enter term/article to process '#$<termname>' or '<article>'.");
			String map = in.readLine().trim();
			ConceptModule cm = parseConceptModule(map, cmt.wmi_, cmt.ontology_);
			if (cm != null) {
				cmt = new ConceptMiningTask(cm, true);
				cmt.run(false);
				System.out.println(cmt.getAssertedConcepts());
				// cmt.run();
			} else {
				System.err.println("Could not parse term.");
			}
		} else if (input.equals("6")) {
			System.out.println("Enter collection article.");
			String map = in.readLine().trim();
			staticProcessCollection(cmt, map, in);
		} else if (input.contains("=>"))
			staticReverseMap(cmt, input);
		else if (!input.equals("exit"))
			staticMap(cmt, input);
	}

	/**
	 * Performs mapping on a single term/article.
	 * 
	 * @param cmt
	 *            The task to perform it in.
	 * @param term
	 *            The term/article to map.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private static void staticMap(ConceptMiningTask cmt, String term)
			throws Exception {
		ConceptModule cm = parseConceptModule(term, cmt.wmi_, cmt.ontology_);
		if (cm != null) {
			System.out.println("Mapping " + term + "...");
			cmt.processables_.add(cm);
			cmt.run(true);
			System.out.println(cmt.processables_);
		}
	}

	/**
	 * Performs mining on a reverse mapped pair.
	 * 
	 * @param cmt
	 *            The task to perform it in.
	 * @param in
	 *            The System input.
	 * @param mapping
	 *            The mapping to mine.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private static void staticMine(ConceptMiningTask cmt, BufferedReader in,
			String article) throws Exception {
		ConceptModule cm = null;
		try {
			int articleID = cmt.wmi_.getArticleByTitle(article.trim());
			cm = new ConceptModule(articleID);
			cm.setState(1.0, MiningState.REVERSE_MAPPED);
		} catch (IOException e) {
			System.err.println("No article by that title!");
			return;
		}

		System.out.println("Mining " + article + "...");
		cmt.processables_.add(cm);
		cmt.mineConcept(cm, MinedInformation.ALL_TYPES);
		System.out.println(cm.getAssertions());
		System.out.println(cm.getStanding());

		// Disambiguate?
		do {
			System.out.println("Disambiguate for concept? Enter a concept "
					+ "to disambiguate, otherwise leave empty to exit.");
			String concept = in.readLine().trim();
			if (concept.isEmpty())
				return;

			OntologyConcept cycTerm = new OntologyConcept(concept);
			if (!cmt.ontology_.inOntology(cycTerm)) {
				System.err.println("No such constant!");
				return;
			}
			ConceptModule cm2 = new ConceptModule(cycTerm, cm.getArticle(), 1,
					true);
			cm2.mergeInformation(cm);
			double weight = cm2.disambiguateAssertions(cmt.ontology_);
			System.out.println(weight + " "
					+ cm2.getConcreteAssertions().size() + ": "
					+ cm2.getConcreteAssertions());
		} while (true);
	}

	/**
	 * Statically processes an article for its children, applying assertions to
	 * the valid children found.
	 * 
	 * @param cmt
	 *            The concept mining task.
	 * @param parentArticle
	 *            The parent article.
	 * @param in
	 *            The input stream for prompting queries.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private static void staticProcessCollection(ConceptMiningTask cmt,
			String parentArticle, BufferedReader in) throws Exception {
		// First mine the children
		// TODO
		// ConceptModule parentCM = new ConceptModule(
		// cmt.wmi_.getArticleByTitle(parentArticle));
		// cmt.mineConcept(parentCM,
		// (1 << InformationType.CHILD_ARTICLES.ordinal()));
		// Collection<Integer> children = null;// parentCM.getChildArticles();
		//
		// // Ask for parent
		// Collection<OntologyConcept> parents = new ArrayList<>();
		// System.out.println("Input comma separated parent(s)");
		// String parentsStr = in.readLine();
		// for (String parent : parentsStr.split(","))
		// parents.addAll(cmt.ontology_.findConceptByName(parent.trim(), true,
		// true, false));
		//
		// // Ask for assertion
		// Collection<MinedAssertion> autoAssertions = new ArrayList<>();
		// System.out
		// .println("Input binary auto-assertions in bracketed format, one-per-line, "
		// + "using '?X' to denote the mined concept. Use "
		// + "non-bracketed to stop assertion input.");
		// String input = null;
		// while ((input = in.readLine()).startsWith("(")) {
		// if (!input.endsWith(")"))
		// break;
		// ArrayList<String> args = UtilityMethods.split(
		// UtilityMethods.shrinkString(input, 1), ' ');
		// if (args.size() != 3) {
		// System.out.println("Non-binary predicate! Try again.");
		// continue;
		// }
		// OntologyConcept[] concepts = new OntologyConcept[3];
		// for (int i = 0; i < args.size(); i++) {
		// String arg = args.get(i);
		// if (arg.equals("?X"))
		// concepts[i] = new WikipediaMappedConcept(article);
		// else {
		// concepts[i] = cmt.ontology_
		// .findConceptByName(arg, true, true, false)
		// .iterator().next();
		// if (concepts[i] == null)
		// concepts[i] = new OntologyConcept(arg, -1);
		// }
		// }
		// MinedAssertion ma = new DefiniteAssertion(concepts[0],
		// CycConstants.DATA_MICROTHEORY.getConceptName(), null,
		// concepts[1], concepts[2]);
		// autoAssertions.add(ma);
		// }
		//
		// cmt.processCollection(children, parents, autoAssertions);
	}

	/**
	 * Reverse maps an existing mapping.
	 * 
	 * @param cmt
	 *            The task to perform it in.
	 * @param mapping
	 *            The mapping to reverse.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private static void staticReverseMap(ConceptMiningTask cmt, String mapping)
			throws Exception {
		ConceptModule cm = null;
		String[] split = mapping.split("=>");
		if (split.length != 2)
			return;

		int cycIndex = (mapping.startsWith("#$")) ? 0 : 1;
		OntologyConcept cycTerm = new OntologyConcept(split[cycIndex].trim()
				.substring(2));
		if (!cmt.ontology_.inOntology(cycTerm)) {
			System.err.println("No such constant!");
			return;
		}

		try {
			int articleID = cmt.wmi_
					.getArticleByTitle(split[(cycIndex + 1) % 2].trim());
			cm = new ConceptModule(cycTerm, articleID, 1.0, cycIndex == 0);
		} catch (IOException e) {
			System.err.println("No article by that title!");
			return;
		}

		System.out.println("Reverse mapping " + mapping + "...");
		cmt.processables_.add(cm);
		cmt.run(true);
		System.out.println(cmt.processables_);
	}

	/**
	 * Creates a new Cyc name from a string. If required, optional context can
	 * be used to ensure the name is valid & unique.
	 *
	 * @param text
	 *            The string to create a concept from.
	 * @param context
	 *            The optional context.
	 * @param ontology
	 *            The ontology access.
	 * @return The newly created Cyc constant name.
	 */
	public static OntologyConcept createNewCycTermName(String text,
			String context, OntologySocket ontology) {
		if (text.equals(""))
			return null;
		// If the name is valid and non-existing, return
		String ontName = ontology.toOntologyFormat(text);
		if (ontology.validConstantName(ontName)
				&& ontology.findConceptByName(ontName, false, true, false)
						.isEmpty())
			return new OntologyConcept(ontName);

		// If we have context, use it
		if (context != null) {
			context = ontology.toOntologyFormat(context);
			String contextName = ontName;
			if (text.matches(".+?\\(.+?\\)"))
				contextName = ontology.toOntologyFormat(text.substring(0,
						text.lastIndexOf("(")));
			contextName = contextName + "-" + context;
			if (ontology.validConstantName(contextName)
					&& ontology.findConceptByName(contextName, false, true,
							false).isEmpty())
				return new OntologyConcept(contextName);
		}

		// Otherwise, add standard suffix until we have a unique concept
		int i = 0;
		String name = ontName;
		do {
			i++;
			name = ontName + "-Concept_" + i;
		} while (!ontology.validConstantName(name)
				|| !ontology.findConceptByName(name, false, true, false)
						.isEmpty());
		return new OntologyConcept(name);
	}

	/**
	 * Gets the state of a indexed thing from an array of states. Requires a
	 * little more than basic array access, as the value returned must be
	 * converted to the appropriate state value.
	 * 
	 * @param index
	 *            The indexed thing to get.
	 * @param array
	 *            The array of indexed things.
	 * @param currentOnly
	 *            If we only check current states.
	 * @return The state of the indexed thing.
	 */
	public static byte getState(int index, byte[] array) {
		if (index < 0)
			return UNKNOWN;
		if (index >= array.length)
			return UNKNOWN;
		return array[index];
	}

	public static byte getArticleState(int article) {
		return getState(article, artStates_);
	}

	public static byte getConceptState(OntologyConcept concept) {
		return getState(concept.getID(), ontologyStates_);
	}

	/**
	 * A simple interface to produce on call mapping/mining of a term.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		FirstSentenceMiner.wikifyText_ = true;
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		String input = null;
		do {
			try {
				// Process the args
				StringBuilder article = null;
				for (int i = 0; i < args.length; i++) {
					if (args[i].equals("-i")) {
						System.out.println("Interactive mode enabled.");
						InteractiveMode.interactiveMode_ = true;
						input = "interactive";
					} else if (args[i].equals("-r")) {
						i++;
						KnowledgeMiner.runID_ = Integer.parseInt(args[i]);
					} else {
						if (article == null)
							article = new StringBuilder(args[i]);
						else
							article.append(" " + args[i]);
					}
				}
				if (article != null) {
					int artID = ResourceAccess.requestWMISocket()
							.getArticleByTitle(article.toString());
					ConceptModule cm = new ConceptModule(artID);
					ConceptMiningTask cmt = new ConceptMiningTask(cm);
					cmt.run();
					break;
				} else {
					System.out
							.println("Select an option:\n\t'1' (map), '2' (reverseMap), "
									+ "'3' (mine), '4' (full process), "
									+ "'5' (sorted children subset), "
									+ "'6' (process children), "
									+ "'7' (interactive mode)\n"
									+ "\tEnter mapping ('X', '#$Y', or 'X => Y'),\n"
									+ "\t'wmi <command> for WMI commands,\n"
									+ "\tOr 'exit'");
					input = in.readLine().trim();
				}
				processInput(in, input);
				System.out.println();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} while (!input.equals("exit"));
		System.exit(0);
	}

	/**
	 * Parses a concept module from either a Cyc term, or an article title.
	 * 
	 * @param term
	 *            The term/article.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology_2
	 * @return A Concept Module representing that term/article.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public static ConceptModule parseConceptModule(String term, WMISocket wmi,
			OntologySocket ontology) throws Exception {
		ConceptModule cm = null;
		if (term.startsWith("#$")) {
			OntologyConcept cycTerm = new OntologyConcept(term.substring(2));
			if (ontology.inOntology(cycTerm))
				cm = new ConceptModule(cycTerm);
			else {
				System.err.println("No such constant!");
				return null;
			}
		} else if (term.startsWith("(")) {
			OntologyConcept cycTerm = new OntologyConcept(term);
			if (ontology.inOntology(cycTerm))
				cm = new ConceptModule(cycTerm);
			else {
				System.err.println("No such constant!");
				return null;
			}
		} else {
			try {
				int articleID = wmi.getArticleByTitle(term);
				cm = new ConceptModule(articleID);
			} catch (IOException e) {
				System.err.println("No article by that title!");
				return null;
			}
		}
		return cm;
	}

	/**
	 * Sets the state of an article to a given state (e.g. completed, pending,
	 * etc). This also takes the runID for which this state is being set, to
	 * correspond with bootstrapping checks.
	 * 
	 * @param article
	 *            The article to set the state for.
	 * @param mapping
	 *            The mapping for the article or current state being set.
	 * @param runID
	 *            The run ID for which the state is being set (use
	 *            KnowledgeMiner.runID_ for current run).
	 * @param pendingArts
	 *            The optional set of pending articles to modify. Use this if
	 *            keeping track of articles currently being mapped.
	 */
	public static void setArticleState(int article, byte state,
			Set<Integer> pendingArts) {
		if (article == -1)
			return;
		if (article >= artStates_.length)
			artStates_ = Arrays.copyOf(artStates_, artStates_.length * 2);

		artStates_[article] = state;
		if (pendingArts != null) {
			if (state == PENDING)
				pendingArts.add(article);
			else
				pendingArts.remove(article);
		}
		logger_.trace("Set article state to {} for {}.", state, article);
	}

	/**
	 * Sets the state of a concept to a given state (e.g. completed, pending,
	 * etc). This also takes the runID for which this state is being set, to
	 * correspond with bootstrapping checks.
	 * 
	 * @param concept
	 *            The concept to set the state for.
	 * @param state
	 *            The state being set.
	 * @param runID
	 *            The run ID for which the state is being set (use
	 *            KnowledgeMiner.runID_ for current run).
	 * @param pendingConcepts
	 *            The optional set of pending concepts to modify. Use this if
	 *            keeping track of concepts currently being mapped.
	 */
	public static void setConceptState(Integer concept, byte state,
			Set<Integer> pendingConcepts) {
		if (concept.intValue() < 0)
			return;
		if (concept >= ontologyStates_.length)
			ontologyStates_ = Arrays.copyOf(ontologyStates_,
					ontologyStates_.length * 2);

		ontologyStates_[concept.intValue()] = state;
		if (pendingConcepts != null) {
			if (state == PENDING)
				pendingConcepts.add(concept);
			else
				pendingConcepts.remove(concept);
		}
		logger_.trace("Set concept state to {} for {}.", state, concept);
	}
}
