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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.WeightedSet;
import util.wikipedia.PopularityComparator;

import com.google.common.base.Predicate;

import cyc.CycConstants;
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
	private static final int BIG_ENOUGH = 50000000;

	/** The chance that a child is created. */
	private static final float CHILD_CREATION_CHANCE = .5f;

	private final static Logger logger_ = LoggerFactory
			.getLogger(ConceptMiningTask.class);

	/** The maximum set of items to show. */
	private static final int MAX_CHILD_CHUNK = 20;

	/** The frequency at which the output files are updated. */
	public static final int UPDATE_INTERVAL = 100;

	static final byte AVAILABLE = 0;

	static final byte COMPLETED = 2;

	static final byte PENDING = 1;

	static final byte UNAVAILABLE = 3;

	/** The current article states. */
	public static byte[] artStates_ = new byte[BIG_ENOUGH];

	/** The number of asserted concepts. */
	public static int assertedCount_ = 0;

	/** The interactive interface for interactive mode. */
	public static InteractiveMode interactiveInterface_ = new InteractiveMode();

	/** Keeps track of mapped concepts. */
	public static byte[] ontologyStates_ = new byte[BIG_ENOUGH];

	/** A collection for keeping track of all asserted conceptModules. */
	private Collection<ConceptModule> assertedConcepts_ = new ArrayList<>();

	private boolean ephemeral_ = true;

	/** The KnowledgeMiner core. */
	private KnowledgeMiner km_;

	private OntologySocket ontology_;

	/** The data to process, in weighted order. */
	private SortedSet<ConceptModule> processables_;

	private boolean sortingArticles_ = false;

	/** The WMI access for this threaded task. */
	private WMISocket wmi_;

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
		this(conceptModule, false);
	}

	/**
	 * Constructor for a new ConceptMiningTask with a single Cyc Term to begin
	 * with.
	 * 
	 * @param conceptModule
	 *            The initial concept module to begin with. Should only be a
	 *            term/article to map.
	 */
	public ConceptMiningTask(ConceptModule conceptModule, boolean ephemeral) {
		this();
		processables_.add(conceptModule);
		ephemeral_ = ephemeral;
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
			if (getState(cm.getArticle()) != AVAILABLE) {
				logger_.trace("Article " + cm + " currently/already mapped!");
				return false;
			}
			setArticleState(cm.getArticle(), PENDING, pendingArts);
		}
		if (cm.getConcept() != null) {
			if (getState(cm.getConcept()) != AVAILABLE) {
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
			setArticleState(pendingArt, AVAILABLE, pendingArts);
		}
		for (Integer pendingConcept : pendingConcepts
				.toArray(new Integer[pendingConcepts.size()])) {
			logger_.trace("Loose pending concept " + pendingConcept + ".");
			setConceptState(pendingConcept, AVAILABLE, pendingConcepts);
		}
	}

	private boolean containsCompleted(ConceptModule concept) {
		if (concept.getArticle() != -1
				&& getState(concept.getArticle()) == COMPLETED) {
			return true;
		}
		if (concept.getConcept() != null
				&& getState(concept.getConcept()) == COMPLETED)
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
			// TODO Identify some collection sense
			// TODO Use the possible parentage, perhaps? Might have to be after
			// disambiguation though.
			OntologyConcept newConcept = createNewCycTermName(article, -1, wmi_);
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
	 * @return True if either the article or the concept have already been
	 *         processed.
	 */
	private boolean removeCompleted(ConceptModule concept,
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

		// Remove completed articles/concepts
		if (concept.getArticle() != -1
				&& getState(concept.getArticle()) == COMPLETED)
			concept.removeArticle();
		if (concept.getConcept() != null
				&& getState(concept.getConcept()) == COMPLETED)
			concept.removeConcept();
		// Remove if no article & concept remains.
		return concept.getArticle() == -1
				&& (concept.getConcept() == null || concept.isCreatedConcept());
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
			switch (cm.getState()) {
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
			if (concept.isCreatedConcept()
					&& concept.getParentageAssertions().isEmpty())
				return false;
			synchronized (this) {
				if (containsCompleted(concept))
					return false;
				// Get concept ID and record processed
				concept.findCreateConcept(ontology_);
				setArticleState(concept.getArticle(), COMPLETED, pendingArts);
				setConceptState(concept.getConcept().getID(), COMPLETED,
						pendingConcepts);
			}
			// Firstly, record the mapping
			String articleTitle = wmi_.getPageTitle(concept.getArticle(), true);
			IOManager.getInstance().writeMapping(concept, articleTitle);

			// TODO Remove all KM assertions no longer produced by KM

			// Then, make the mining assertions
			concept.makeAssertions(articleTitle, ontology_);
			assertedConcepts_.add(concept);

			concept.setState(1.0, MiningState.ASSERTED);

			// Use the mined info as training data
			concept.recordTrainingInfo(wmi_);

			ConceptMiningTask.interactiveInterface_.interactiveAssertion(
					concept, wmi_, ontology_);

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
			if (concept.isCreatedConcept()) {
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

				// Add a mapping
				if (!concept.getParentageAssertions().isEmpty()) {
					ConceptModule newConcept = createConcept(concept);
					if (newConcept != null)
						mapped.add(newConcept);
				}
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
		cm.buildDisambiguationGrid(ontology_);
	}

	/**
	 * Finds the child articles for a given concept and sorts them.
	 * 
	 * @param concept
	 *            The concept being checked.
	 * @return The child articles in sorted order.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public Collection<Integer> findChildArticles(ConceptModule concept)
			throws Exception {
		// Mine children
		km_.getMiner().mineArticle(concept,
				1 << InformationType.CHILD_ARTICLES.ordinal(), wmi_, ontology_);
		// Create the new children terms
		Collection<Integer> childArticles = concept.getChildArticles();

		// Sort the articles
		if (sortingArticles_) {
			List<Integer> sortedChildren = new ArrayList<>(childArticles);
			Collections.sort(sortedChildren, new PopularityComparator(
					childArticles, wmi_));
			int cutoff = (int) Math.ceil(sortedChildren.size()
					* CHILD_CREATION_CHANCE);
			childArticles = sortedChildren.subList(0, cutoff);
		}

		if (!childArticles.isEmpty())
			System.out.println("Mining " + (CHILD_CREATION_CHANCE * 100)
					+ "% of children for " + concept.getConcept() + " ("
					+ childArticles.size() + " in total)");
		return childArticles;
	}

	public Collection<ConceptModule> getAssertedConcepts() {
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
	public SortedSet<ConceptModule> getVerifiedMappings(
			Predicate<ConceptModule> mappingRestriction, WMISocket wmi,
			OntologySocket ontology) {
		// return runAlternative(false);
		wmi_ = wmi;
		ontology_ = ontology;
		ConceptModule concept = processables_.first();

		// Map the concept.
		processables_ = mapConcept(concept, false);
		ConceptModule[] mappings = processables_
				.toArray(new ConceptModule[processables_.size()]);
		processables_.clear();

		// Reverse map every mapping.
		for (ConceptModule mapping : mappings) {
			// Don't worry about mappings below the threshold.
			if (mapping.getModuleWeight() < KnowledgeMiner.CUTOFF_THRESHOLD)
				break;
			if (mappingRestriction == null || mappingRestriction.apply(mapping)) {
				processables_.add(mapping);
				addProcessables(mapConcept(mapping, true), false,
						!mapping.isCycToWiki(), true);
			}
		}

		return processables_;
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
			ConceptMiningTask childTask = new ConceptMiningTask(cm, false);
			km_.processConcept(childTask);
		}
	}

	@Override
	public void run() {
		run(true);
	}

	public SortedSet<ConceptModule> run(boolean singleMapping) {
		SortedSet<ConceptModule> allResults = null;

		ConceptModule cm = processables_.first();

		if (!singleMapping && cm.getArticle() != -1) {
			allResults = km_.getCached(cm.getArticle());
			if (allResults != null)
				return allResults;
		}
		allResults = new TreeSet<ConceptModule>();

		wmi_ = ResourceAccess.requestWMISocket();
		ontology_ = ResourceAccess.requestOntologySocket();
		if (ephemeral_)
			ontology_.setEphemeral(true);
		KnowledgeMiner.miningChildren_ = false;

		Set<Integer> pendingArts = new HashSet<>();
		Set<Integer> pendingConcepts = new HashSet<>();
		// If the concept/article is unavailable, exit
		if (!checkAvailability(allResults, cm, pendingArts, pendingConcepts))
			return allResults;

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
				if (cm.getModuleWeight() == 0) {
					completed = true;
					break;
				}
				processables_.remove(cm);

			} while (removeCompleted(cm, !tempSingleMapping, original));
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

		// Cache results
		if (!singleMapping && original.getArticle() != -1)
			km_.addCached(original.getArticle(), allResults);
		return allResults;
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
		KnowledgeMinerPreprocessor.ENABLE_PREPROCESSING = false;
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
				cmt = new ConceptMiningTask(cm);
				System.out.println(cmt.run(false));
				// cmt.run();
			} else {
				System.err.println("Could not parse term.");
			}
		} else if (input.equals("5")) {
			System.out
					.println("Enter full mapping for children '#$<termname> <=> <article>'.");
			String map = in.readLine().trim();
			staticChildList(cmt, map);
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
	 * Lists a sorted subset of potential child articles for a given
	 * term/article mapping.
	 * 
	 * @param cmt
	 *            The task to perform the listing in.
	 * @param mapping
	 *            The mapping to check for children.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private static void staticChildList(ConceptMiningTask cmt, String mapping)
			throws Exception {
		ConceptModule cm = null;
		String[] split = mapping.split("<=>");
		if (split.length != 2)
			return;

		OntologyConcept cycTerm = new OntologyConcept(split[0].trim());
		if (!cmt.ontology_.inOntology(cycTerm)) {
			System.err.println("No such constant!");
			return;
		}

		try {
			int articleID = cmt.wmi_.getArticleByTitle(split[1].trim());
			cm = new ConceptModule(cycTerm, articleID, 1.0, true);
			cm.setState(1.0, MiningState.REVERSE_MAPPED);
		} catch (IOException e) {
			System.err.println("No article by that title!");
			return;
		}

		System.out.println("Listing children " + mapping + "...");
		Collection<Integer> sortedChildren = cmt.findChildArticles(cm);
		if (sortedChildren.isEmpty())
			System.out.println("<No child candidates found>");

		// Output chunks of results
		int remaining = sortedChildren.size();
		Iterator<Integer> iter = sortedChildren.iterator();
		while (remaining > 0) {
			int chunkSize = Math.min(MAX_CHILD_CHUNK, remaining);
			// Create the chunk of ids.
			Integer[] idChunk = new Integer[chunkSize];
			for (int i = 0; i < chunkSize; i++)
				idChunk[i] = iter.next();

			List<String> titles = cmt.wmi_.getPageTitle(true, idChunk);
			for (String artTitle : titles)
				System.out.println(artTitle);

			remaining -= chunkSize;
			if (remaining > 0) {
				System.out.println("\tPress Enter to continue...");
				System.in.read();
			}
		}
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
		System.out.println(cm.getAmbiguousAssertions());

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
		ConceptModule parentCM = new ConceptModule(
				cmt.wmi_.getArticleByTitle(parentArticle));
		cmt.mineConcept(parentCM,
				(1 << InformationType.CHILD_ARTICLES.ordinal()));
		Collection<Integer> children = parentCM.getChildArticles();

		// Ask for parent
		Collection<OntologyConcept> parents = new ArrayList<>();
		System.out.println("Input comma separated parent(s)");
		String parentsStr = in.readLine();
		for (String parent : parentsStr.split(","))
			parents.addAll(cmt.ontology_.findConceptByName(parent.trim(), true,
					true, false));

		// Ask for assertion
		Collection<MinedAssertion> autoAssertions = new ArrayList<>();
		System.out
				.println("Input binary auto-assertions in bracketed format, one-per-line, "
						+ "using '?X' to denote the mined concept. Use "
						+ "non-bracketed to stop assertion input.");
		String input = null;
		while ((input = in.readLine()).startsWith("(")) {
			if (!input.endsWith(")"))
				break;
			ArrayList<String> args = UtilityMethods.split(
					UtilityMethods.shrinkString(input, 1), ' ');
			if (args.size() != 3) {
				System.out.println("Non-binary predicate! Try again.");
				continue;
			}
			OntologyConcept[] concepts = new OntologyConcept[3];
			for (int i = 0; i < args.size(); i++) {
				String arg = args.get(i);
				if (arg.equals("?X"))
					concepts[i] = OntologyConcept.PLACEHOLDER;
				else {
					concepts[i] = cmt.ontology_
							.findConceptByName(arg, true, true, false)
							.iterator().next();
					if (concepts[i] == null)
						concepts[i] = new OntologyConcept(arg, -1);
				}
			}
			MinedAssertion ma = new MinedAssertion(concepts[0], concepts[1],
					concepts[2],
					CycConstants.DATA_MICROTHEORY.getConceptName(), null);
			autoAssertions.add(ma);
		}

		cmt.processCollection(children, parents, autoAssertions);
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
	 * Creates a new Cyc term name from an article using the Article title as
	 * the constant name. If required, the collection encompassing the article
	 * will be added as extra sense.
	 * 
	 * @param articleID
	 *            The article used to create a new Cyc constant name.
	 * @param collectionSenseID
	 *            The article representing the collection of the base article.
	 * @param wmi
	 *            The WMI access.
	 * @return The newly created Cyc constant name.
	 */
	public static OntologyConcept createNewCycTermName(int articleID,
			int collectionSenseID, WMISocket wmi) throws Exception {
		OntologySocket ontology = ResourceAccess.requestOntologySocket();

		String cycTerm = ontology.toOntologyFormat(wmi.getPageTitle(articleID,
				true));
		if (cycTerm.equals(""))
			return null;

		// If the term is less than 2 letters or if the term already
		// exists add collectionSense to the term.
		if (!ontology.validConstantName(cycTerm)
				|| !ontology.findConceptByName(cycTerm, false, true, false)
						.isEmpty()) {
			if (collectionSenseID != -1) {
				// If there is no existing scope, simply add the collectionSense

				if (wmi.hasTitleContext(articleID)) {
					cycTerm += "-"
							+ ontology.toOntologyFormat(wmi.getPageTitle(
									collectionSenseID, false));
				} else {
					// Have to remove the existing scope and try with the
					// collectionSense scope
					cycTerm = ontology.toOntologyFormat(wmi.getPageTitle(
							articleID, false))
							+ "-"
							+ ontology.toOntologyFormat(wmi.getPageTitle(
									collectionSenseID, false));
				}
			}

			// Check that the term isn't already in Cyc
			if (!ontology.findConceptByName(cycTerm, false, true, false)
					.isEmpty()) {
				// Loop with number suffix until non-existant node found
				int i = 0;
				String name = cycTerm;
				do {
					i++;
					name = cycTerm + "-Concept_" + i;
				} while (!ontology.findConceptByName(name, false, true, false)
						.isEmpty());
				cycTerm = name;
			}
		}

		return new OntologyConcept(cycTerm);
	}

	public static byte getState(OntologyConcept concept) {
		int id = concept.getID();
		if (id == -1)
			return UNAVAILABLE;
		if (id >= ontologyStates_.length)
			return AVAILABLE;
		return ontologyStates_[concept.getID()];
	}

	public static byte getState(int article) {
		if (article >= artStates_.length)
			return AVAILABLE;
		return artStates_[article];
	}

	/**
	 * A simple interface to produce on call mapping/mining of a term.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		KnowledgeMiner.getInstance();
		FirstSentenceMiner.wikifyText_ = true;
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		String input = null;
		do {
			try {
				if (args.length >= 1 && args[0].equals("-i")) {
					System.out.println("Interactive mode enabled.");
					InteractiveMode.interactiveMode_ = true;
					input = "interactive";
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

	public static void setArticleState(int article, byte state,
			Set<Integer> pendingArts) {
		if (article == -1)
			return;
		if (article >= artStates_.length)
			artStates_ = Arrays.copyOf(artStates_, artStates_.length * 2);

		artStates_[article] = state;
		if (state == PENDING)
			pendingArts.add(article);
		else
			pendingArts.remove(article);
		logger_.trace("Set article state to {} for {}.", state, article);
	}

	public static void setConceptState(Integer concept, byte state,
			Set<Integer> pendingArts) {
		if (concept.intValue() == -1)
			return;
		if (concept >= ontologyStates_.length)
			ontologyStates_ = Arrays.copyOf(ontologyStates_,
					ontologyStates_.length * 2);

		ontologyStates_[concept.intValue()] = state;
		if (state == PENDING)
			pendingArts.add(concept);
		else
			pendingArts.remove(concept);
		logger_.trace("Set concept state to {} for {}.", state, concept);
	}
}
