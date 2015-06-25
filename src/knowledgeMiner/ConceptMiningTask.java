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
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.CycConstants;
import cyc.OntologyConcept;
import cyc.StringConcept;

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
	private static final int BIG_ENOUGH = 10000000;

	/** The chance that a child is created. */
	private static final float CHILD_CREATION_THRESHOLD = .1f;

	private final static Logger logger_ = LoggerFactory
			.getLogger(ConceptMiningTask.class);

	/** Mappings from an indexed article to a given ontology (ID). */
	private static int[] artStates_ = new int[BIG_ENOUGH];

	/** Mappings from an indexed concept (ID) to a given article. */
	private static int[] ontologyStates_ = new int[BIG_ENOUGH];

	static final byte UNMAPPABLE_PRIOR = -1;

	/** The number of asserted concepts. */
	public static int assertedCount_ = 0;

	/** The interactive interface for interactive mode. */
	public static InteractiveMode interactiveInterface_ = new InteractiveMode();

	/** The frequency at which the output files are updated. */
	public static final int UPDATE_INTERVAL = 100;

	public static boolean usingMinedProperty_ = false;

	/** A collection for keeping track of all asserted conceptModules. */
	private Collection<ConceptModule> assertedConcepts_;

	/** The iteration flag to run this task with. */
	private int iteration_;

	/** The KnowledgeMiner core. */
	private KnowledgeMiner km_;

	private OntologySocket ontology_;

	/** The data to process, in weighted order. */
	private SortedSet<ConceptModule> processables_;

	private boolean trackAsserted_ = false;

	/** The WMI access for this threaded task. */
	private WMISocket wmi_;

	/**
	 * Constructor for a new ConceptMiningTask
	 * 
	 */
	private ConceptMiningTask() {
		processables_ = new TreeSet<>();
		km_ = KnowledgeMiner.getInstance();
		iteration_ = KnowledgeMiner.runID_;
	}

	/**
	 * Constructor for a new ConceptMiningTask with a single Cyc Term to begin
	 * with.
	 * 
	 * @param conceptModule
	 *            The initial concept module to begin with. Should only be a
	 *            term/article to map.
	 * @param runIteration
	 *            The iteration to run the mappings in.
	 */
	public ConceptMiningTask(ConceptModule conceptModule, int runIteration) {
		this();
		processables_.add(conceptModule);
		iteration_ = runIteration;
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

	private boolean containsCompleted(ConceptModule concept) {
		if (concept.getArticle() != -1
				&& isArticleProcessed(concept.getArticle()))
			return true;
		if (concept.getConcept() != null && concept.getConcept().getID() >= 0
				&& isConceptProcessed(concept.getConcept()))
			return true;
		return false;
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
			// Use the possible parentage, perhaps? Might have to be after
			// disambiguation though.
			OntologyConcept newConcept = createNewCycTermName(
					wmi_.getPageTitle(article, true), null, ontology_);
			if (newConcept != null) {
				ConceptModule newCM = new ConceptModule(newConcept, article,
						CHILD_CREATION_THRESHOLD, false);
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
	 * Checks if an article is mapped to a concept that does not need to be
	 * reprocessed.
	 *
	 * @param article
	 *            The article to check.
	 * @return True if the article does not need to be reprocessed.
	 */
	private boolean isArticleProcessed(Integer article) {
		if (article < 0 || iteration_ < 0)
			return false;
		int iter = getState(article, artStates_);
		if (iter != 0)
			return iter >= iteration_;
		OntologyConcept concept = KnowledgeMiner.getConceptMapping(article,
				ontology_);
		if (concept != null) {
			boolean result = isConceptProcessed(concept);
			setArticleState(article, getState(concept.getID(), ontologyStates_));
			return result;
		}
		return false;
	}

	/**
	 * Checks the concept properties to see if it does not need to currently be
	 * reprocessed.
	 *
	 * @param concept
	 *            The concept to check.
	 * @return True if the concept does not need to be reprocessed.
	 */
	private boolean isConceptProcessed(OntologyConcept concept) {
		int conceptID = concept.getID();
		if (conceptID < 0 || iteration_ < 0)
			return false;
		int iter = getState(conceptID, ontologyStates_);
		if (iter == 0) {
			String strIteration = ontology_.getProperty(concept, true,
					KnowledgeMiner.RUN_ID);
			if (strIteration == null)
				return false;
			else {
				iter = Short.parseShort(strIteration);
				setConceptState(conceptID, iter);
			}
		}
		return iter >= iteration_;
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
	private void runInternal(ConceptModule cm, boolean singleMapping) {
		try {
			logger_.info(cm.toFlatString());
			km_.getInterface().update(cm, processables_);
			MiningState state = cm.getState();
			switch (state) {
			case UNMINED:
				// If unmined: Mine article (except children)
				mineConcept(cm, MinedInformation.ALL_TYPES);
				processables_.add(cm);
				break;
			case UNMAPPED:
				// If unmapped: Map article to cm
				Collection<ConceptModule> mappings = mapConcept(cm, false);
				addProcessables(mappings, singleMapping, cm.isCycToWiki(),
						false);
				break;
			case MAPPED:
				// If mapped: Map in reverse
				Collection<ConceptModule> revMappings = mapConcept(cm, true);
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
				assertConcept(cm);
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
					&& WikiParser.isAListOf(wmi_.getPageTitle(
							concept.getArticle(), true)))
				return true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Stop on completed articles/concepts (from this run)
		if (concept.getConcept() != null) {
			if (isConceptProcessed(concept.getConcept()))
				return true;
		}
		if (concept.getArticle() != -1) {
			if (isArticleProcessed(concept.getArticle()))
				return true;
		}

		// If neither article nor concept, remove this
		if (concept.getArticle() == -1
				&& (concept.getConcept() == null || concept.isCreatedConcept()))
			return true;

		return false;
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
	protected boolean assertConcept(ConceptModule concept) {
		try {
			synchronized (this) {
				if (containsCompleted(concept))
					return false;
				// Get concept ID and record processed
				setArticleState(concept.getArticle(), iteration_);

				// If a created concept
				if (concept.isCreatedConcept()) {
					// If a no new concepts allowed, or there are no parentage
					// assertions.
					if (KnowledgeMiner.mappingRun_
							|| concept.getConcreteParentageAssertions()
									.isEmpty()) {
						// TODO Don't bother with only Individual/Collection
						return false;
					}
				} else if (concept.getConcept().getID() < 0) {
					// No unreifiable mappings
					return false;
				}

				// Create any new concepts
				concept.findCreateConcept(ontology_);
				setConceptState(concept.getConcept().getID(), iteration_);

				// Note mining details on node
				if (iteration_ != -1) {
					int newIter = 0;
					String oldIter = ontology_.getProperty(
							concept.getConcept(), true, KnowledgeMiner.RUN_ID);
					if (oldIter != null)
						newIter = Integer.parseInt(oldIter);
					ontology_.setProperty(concept.getConcept(), true,
							KnowledgeMiner.RUN_ID, "" + (newIter + 1));
				}
			}
			// Firstly, record the mapping
			String articleTitle = wmi_.getPageTitle(concept.getArticle(), true);
			IOManager.getInstance().writeMapping(concept, articleTitle);

			// Interactive - manual evaluation if correct mapping
			interactiveInterface_.evaluateMapping(concept);

			// TODO Remove all KM assertions no longer produced by KM

			// Then, make the mining assertions
			concept.makeAssertions(articleTitle, ontology_);
			if (trackAsserted_) {
				if (assertedConcepts_ == null)
					assertedConcepts_ = new ArrayList<>();
				assertedConcepts_.add(concept);
			}

			concept.setState(1.0f, MiningState.ASSERTED);

			// Update the mining heuristics
			for (MiningHeuristic mh : KnowledgeMiner.getInstance().getMiner()
					.getMiningHeuristics())
				mh.updateGlobal(concept, wmi_);

			assertedCount_++;
			if (assertedCount_ % UPDATE_INTERVAL == 0)
				km_.statusUpdate();

			km_.getInterface().update(concept, processables_);

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
			// If the concept is newly created or has -1 ID, just automap.
			if (concept.isCreatedConcept()
					|| (concept.getConcept() != null && concept.getConcept()
							.getID() == -1)) {
				// If there is an article
				if (concept.getArticle() == -1)
					return mapped;
				// Term is new, just automatically return reverse mapping.
				concept.setState(1.0f, MiningState.REVERSE_MAPPED);
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
							(float) articles.getWeight(article), true);
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
							(float) terms.getWeight(term), false);
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
			ConceptMiningTask childTask = new ConceptMiningTask(cm,
					KnowledgeMiner.runID_);
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

		// Get iteration for concept
		OntologyConcept concept = cm.getConcept();
		String origIteration = null;
		if (concept != null) {
			origIteration = ontology_.getProperty(concept, true,
					KnowledgeMiner.RUN_ID);
		}

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
				runInternal(cm, tempSingleMapping);

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

		// Mark the input as completed.
		if (concept != null) {
			String strIteration = ontology_.getProperty(concept, true,
					KnowledgeMiner.RUN_ID);
			if (strIteration == null || strIteration.equals(origIteration)) {
				int iteration = (strIteration == null) ? 0 : Integer
						.parseInt(strIteration);
				ontology_.setProperty(concept, true, KnowledgeMiner.RUN_ID, ""
						+ (iteration + 1));
			}
		}

		// Flush the interface
		if (InteractiveMode.interactiveMode_)
			interactiveInterface_.saveEvaluations();
		km_.getInterface().flush();
	}

	public void setTrackAsserted(boolean trackAsserted) {
		trackAsserted_ = trackAsserted;
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
			System.out.println("Enter article to mine '<article>':");
			String map = in.readLine().trim();
			staticMine(cmt, in, map);
		} else if (input.equals("5")) {
			System.out.println("Enter article to create mapping for:");
			String art = in.readLine().trim();
			System.out.println("Enter concept to create mapping for:");
			String concept = in.readLine().trim();
			OntologyConcept concept2 = new OntologyConcept(concept);
			addMapping(cmt.wmi_.getArticleByTitle(art), concept2, cmt.ontology_);
		} else if (InteractiveMode.interactiveMode_ || input.equals("4")
				|| input.equals("6")) {
			InteractiveMode.interactiveMode_ |= input.equals("7");
			System.out
					.println("Enter term/article to process '#$<termname>' or '<article>'.");
			String map = in.readLine().trim();
			ConceptModule cm = parseConceptModule(map, cmt.wmi_, cmt.ontology_);
			if (cm != null) {
				cmt = new ConceptMiningTask(cm, KnowledgeMiner.runID_);
				cmt.setTrackAsserted(true);
				cmt.run(true);
				System.out.println(cmt.getAssertedConcepts());
				// cmt.run();
			} else {
				System.err.println("Could not parse term.");
			}
		} else if (input.contains("=>"))
			staticReverseMap(cmt, input);
		else if (!input.equals("exit"))
			staticMap(cmt, input);

		if (InteractiveMode.interactiveMode_)
			interactiveInterface_.saveEvaluations();

		IOManager.getInstance().flush();
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
			cm.setState(1.0f, MiningState.REVERSE_MAPPED);
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
			cm = new ConceptModule(cycTerm, articleID, 1.0f, cycIndex == 0);
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
	 * Adds a mapping between an article and concept, such that lookup calls
	 * will find the mapping for the given article.
	 *
	 * @param article
	 *            The article to map.
	 * @param concept
	 *            The concept to map the article to.
	 * @return The ID of the assertion or -1.
	 */
	public static int addMapping(int article, OntologyConcept concept,
			OntologySocket ontology) {
		try {
			// No concept or no article
			if (concept == null || !ontology.inOntology(concept)
					|| article == -1)
				return -1;

			DefiniteAssertion assertion = new DefiniteAssertion(
					CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getConcept(),
					CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(),
					null, concept, CycConstants.WIKI_VERSION,
					new StringConcept(article + ""));
			return assertion.makeAssertion(concept, ontology);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
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
		if (ontology.validConstantName(ontName)) {
			if (ontology.findConceptByName(ontName, false, true, false)
					.isEmpty())
				return new OntologyConcept(ontName);
		} else {
			// If the name isn't valid for whatever reason
			ontName = "Concept_" + ontName;
			if (!ontology.validConstantName(ontName))
				ontName = "Concept";
		}

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

	public static int getArticleState(int article) {
		// TODO Read iter state from concept
		return getState(article, artStates_);
	}

	public static int getConceptState(OntologyConcept concept) {
		// TODO Read iter state from concept
		return getState(concept.getID(), ontologyStates_);
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
	public static int getState(int index, int[] array) {
		if (index < 0)
			return -1;
		if (index >= array.length)
			return -1;
		return array[index];
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
		StringBuilder article = null;
		try {
			// Process the args
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-i")) {
					System.out.println("Interactive mode enabled.");
					InteractiveMode.interactiveMode_ = true;
					input = "interactive";
				} else if (args[i].equals("-r")) {
					i++;
					KnowledgeMiner.runID_ = Integer.parseInt(args[i]);
					KnowledgeMiner.readInOntologyMappings();
				} else {
					if (article == null)
						article = new StringBuilder(args[i]);
					else
						article.append(" " + args[i]);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		do {
			try {
				if (article != null) {
					int artID = ResourceAccess.requestWMISocket()
							.getArticleByTitle(article.toString());
					ConceptModule cm = new ConceptModule(artID);
					ConceptMiningTask cmt = new ConceptMiningTask(cm,
							KnowledgeMiner.runID_);
					cmt.run();
					break;
				} else {
					System.out
							.println("Select an option:\n\t'1' (map), '2' (reverseMap), "
									+ "'3' (mine), '4' (full process), "
									+ "'5' (add mapping), "
									+ "'6' (interactive mode)\n"
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
		} else if (term.startsWith("(") && term.endsWith(")")) {
			OntologyConcept cycTerm = new OntologyConcept(term);
			if (ontology.inOntology(cycTerm))
				cm = new ConceptModule(cycTerm);
			else {
				System.err.println("No such constant!");
				return null;
			}
		} else {
			// If an ID
			try {
				int articleID = Integer.parseInt(term);
				cm = new ConceptModule(articleID);
				return cm;
			} catch (Exception e) {
			}

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
	public static void setArticleState(int article, int state) {
		if (article == -1)
			return;
		if (article >= artStates_.length)
			artStates_ = Arrays.copyOf(artStates_, artStates_.length * 2);

		artStates_[article] = state;
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
	public static void setConceptState(Integer concept, int state) {
		if (concept.intValue() < 0)
			return;
		if (concept >= ontologyStates_.length)
			ontologyStates_ = Arrays.copyOf(ontologyStates_,
					ontologyStates_.length * 2);

		ontologyStates_[concept.intValue()] = state;
		logger_.trace("Set concept state to {} for {}.", state, concept);
	}
}
