/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

import graph.core.CommonConcepts;
import io.IOManager;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.debugInterface.ConceptThreadInterface;
import knowledgeMiner.debugInterface.MappingChainInterface;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;
import knowledgeMiner.preprocessing.CycPreprocessor;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.HierarchicalWeightedSet;
import util.serialisation.FSTSerialisationMechanism;
import util.serialisation.SerialisationMechanism;
import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * The core class for running the KnowledgeMiner algorithm.
 * 
 * @author Sam Sarjant
 */
public class KnowledgeMiner {
	private static final String ENWIKI_DEFAULT = "Enwiki_default";
	/** The instance of KnowledgeMiner. */
	private static KnowledgeMiner instance_;

	public static final int CACHE_SIZES = 5000;

	public static final float CPU_USAGE = 0.9f;

	/**
	 * Values with weight less than this (relative to 1.0) will not be included
	 * in results.
	 */
	public static final double CUTOFF_THRESHOLD = 0.01;

	public static final String RUN_ID = "runID";
	private static final String RESOURCE_WIKIPEDIA = "Wikipedia";
	private static final String RESOURCE_ONTOLOGY = "Ontology";
	private static final int REFINE_EVIDENCE = 100;
	private static final Pattern XY_SUB_PATTERN = Pattern
			.compile("\\?X/(\\d+),\\?Y/\"(\\d+)\"");

	/** If new concepts are being created. */
	public static boolean mappingRun_ = false;

	public static boolean onlineWeightUpdating_ = false;
	
	public static boolean onlyMineLeaf_ = true;

	/** The current version of Wikipedia being used. */
	public static String wikiVersion_ = ENWIKI_DEFAULT;

	/** The current run ID. */
	private int runID_ = -1;

	/** The last index to seed with. */
	private int endCount_ = -1;

	/** The thread executor. */
	private ThreadPoolExecutor executor_;

	/** A map linking heuristic strings to their heuristics. */
	private Map<String, Object> heuristicStringMap_;

	/** The current seed index. */
	private int seedIndex_ = 0;

	/** The number of completed seeds. */
	private int numComplete_;

	private ConceptThreadInterface interface_;

	/** The preprocessor access. */
	private KnowledgeMinerPreprocessor kmp_;

	/** The mapping aspect of KnowledgeMiner. */
	private CycMapper mapper_;

	/** The mining aspect of KnowledgeMiner. */
	private CycMiner miner_;

	private DAGSocket ontology_;

	private CycPreprocessor preprocessor_;

	private int seededCount_ = 0;

	/** If the KnowledgeMiner is running in threaded mode. */
	private boolean singleThread_;

	private long startTime_;

	private WMISocket wiki_;

	private BufferedReader fileInput_;

	private int numLines_;

	/**
	 * Constructor for a new KnowledgeMiner with no IO.
	 */
	private KnowledgeMiner(String wikiVersion) {
		wikiVersion_ = Character.toUpperCase(wikiVersion.charAt(0))
				+ wikiVersion.substring(1);
		singleThread_ = wikiVersion.equals(ENWIKI_DEFAULT);
		if (!singleThread_)
			IOManager.newInstance();
		// TODO Temporary throttling threads
		int numThreads = (singleThread_) ? 1 : getNumThreads();

		ResourceAccess.newInstance();

		heuristicStringMap_ = new HashMap<>();
		mapper_ = new CycMapper(this);
		mapper_.initialise();
		miner_ = new CycMiner(this, mapper_);
		preprocessor_ = new CycPreprocessor();
		
		InteractiveMode.getInstance();

		// Start the executor
		executor_ = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(
				1, numThreads));

		interface_ = new MappingChainInterface(); // new SimpleListInterface();
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		wiki_ = ResourceAccess.requestWMISocket();

		if (!singleThread_) {
			FirstSentenceMiner.wikifyText_ = true;
			File outputFile = new File("knowledgeMiner.log");
			try {
				outputFile.createNewFile();
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			FirstSentenceMiner.wikifyText_ = false;
		}
	}

	protected ConceptModule readArticle() {
		int artId = -1;
		while (artId == -1) {
			try {
				if (fileInput_ != null) {
					String in = fileInput_.readLine();
					if (in == null) {
						return null;
					} else
						in = in.split("\t")[0];
					artId = Integer.parseInt(in);
					seedIndex_++;
				} else {
					artId = wiki_.getNextArticle((int) seedIndex_);

					if (artId == -1
							|| (seededCount_ >= endCount_ && endCount_ != -1)) {
						return null;
					}

					seedIndex_ = artId;
				}
				String type = wiki_.getPageType(artId);
				if (type != null && !type.equals(WMISocket.TYPE_ARTICLE)
						&& !type.equals(WMISocket.TYPE_DISAMBIGUATION))
					artId = -1;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return new ConceptModule(artId);
	}

	protected ConceptModule readConcept() {
		OntologyConcept concept = null;
		int id = -1;
		while (concept == null) {
			concept = null;
			try {
				if (fileInput_ != null) {
					String in = fileInput_.readLine();
					if (in == null) {
						return null;
					} else
						in = in.split("\t")[0];
					id = Integer.parseInt(in);
					seedIndex_++;
				} else {
					id = ontology_.getNextNode(seedIndex_);
					if (id == -1
							|| (seededCount_ >= endCount_ && endCount_ != -1)) {
						return null;
					}

					seedIndex_ = id;
				}

				// Check if the concept is valid (according to post processors)
				concept = new OntologyConcept(id);
				HierarchicalWeightedSet<OntologyConcept> constantSet = new HierarchicalWeightedSet<>();
				constantSet.add(concept);
				constantSet = getMapper().getWikiToCycMappingSuite()
						.postProcess(constantSet, wiki_, ontology_);
				// Processed out - skip this concept.
				if (constantSet.isEmpty())
					concept = null;
			} catch (Exception e) {
				e.printStackTrace();
				concept = null;
			}
		}

		return new ConceptModule(concept);
	}

	/**
	 * Gets a heuristic (mapping or mining) by string name.
	 * 
	 * @param heuristicStr
	 *            The string to retrieve a heuristic with.
	 * @return A heuristic with a matching string.
	 */
	public Object getHeuristicByString(String heuristicStr) {
		return heuristicStringMap_.get(heuristicStr);
	}

	/**
	 * Retrieves precomputed results (if they exist) for a given input and task.
	 * 
	 * @param inputID
	 *            The ID of the input term.
	 * @param heuristic
	 *            The name of the heuristic results to retrieve.
	 * @param taskType
	 *            The type of task to check.
	 * @return The precomputed results or null if no results.
	 */
	public Object getHeuristicResult(int inputID, WeightedHeuristic heuristic) {
		if (inputID < 0 || heuristic == null)
			return null;
		if (!heuristic.isPrecomputed())
			return null;
		if (kmp_ == null)
			kmp_ = KnowledgeMinerPreprocessor.getInstance();
		return kmp_.getLoadHeuristicResult(heuristic.getHeuristicName(),
				inputID);
	}

	public CycMapper getMapper() {
		return mapper_;
	}

	public CycMiner getMiner() {
		return miner_;
	}

	public void mapAllResource(String resourceName, int startIndex, int end,
			String filename) throws Exception {
		if (filename != null)
			readInputFile(filename);
		seedIndex_ = startIndex;
		endCount_ = end;
		startTime_ = System.currentTimeMillis();

		// Load up the executor with a list of article/concept IDs
		ConceptModule cm = null;
		do {
			if (resourceName.equals(RESOURCE_WIKIPEDIA))
				cm = readArticle();
			else if (resourceName.equals(RESOURCE_ONTOLOGY))
				cm = readConcept();
			if (cm != null)
				processConcept(new ConceptMiningTask(cm, runID_));
		} while (cm != null);

		executor_.shutdown();
		executor_.awaitTermination(30, TimeUnit.DAYS);
		System.out.println("Done!");
		statusUpdate();
	}

	private void readInputFile(String filename) throws FileNotFoundException,
			IOException {
		if (filename != null) {
			File file = new File(filename);
			if (file.exists()) {
				fileInput_ = new BufferedReader(new FileReader(file));
				LineNumberReader lnr = new LineNumberReader(
						new FileReader(file));
				lnr.skip(Long.MAX_VALUE);
				numLines_ = lnr.getLineNumber();
				// Finally, the LineNumberReader object should be closed to
				// prevent resource leak
				lnr.close();
			}
		} else {
			System.out.println("File not found! " + filename);
			System.exit(0);
		}
	}

	public void preprocess() throws Exception {
		// Perform preprocessing first
		preprocessor_.preprocess(executor_);
	}

	/**
	 * Adds a processable concept to the queue.
	 * 
	 * @param concept
	 *            The task to process.
	 */
	public synchronized void processConcept(ConceptMiningTask task) {
		seededCount_++;
		if (singleThread_)
			task.run();
		else {
			ProcessConceptTask<Integer> futureTask = new ProcessConceptTask<Integer>(
					task, 0);
			executor_.execute(futureTask);
		}
	}

	public void registerHeuristic(Object heuristic) {
		heuristicStringMap_.put(heuristic.toString(), heuristic);
	}

	public void statusUpdate() {
		long elapsedTime = System.currentTimeMillis() - startTime_;
		StringBuilder builder = new StringBuilder(
				UtilityMethods.toTimeFormat(elapsedTime) + " runtime. Mapped "
						+ ConceptMiningTask.assertedCount_ + ". Completed "
						+ numComplete_ + " seeds");
		if (fileInput_ != null) {
			float percent = (1f * numComplete_) / numLines_;
			long remaining = (long) (elapsedTime / percent) - elapsedTime;
			DecimalFormat format = new DecimalFormat("##0.00");
			builder.append(" (" + format.format(percent * 100) + "%). ETA "
					+ UtilityMethods.toTimeFormat(remaining));
		}

		// Concept Mining Task Times
		String runTimes = ConceptMiningTask.printRuntimes();
		System.out.println("\n\n\n\n" + builder + "\n" + executor_ + "\n"
				+ runTimes);
		LoggerFactory.getLogger("STATUS").info(
				builder.toString() + "\n" + runTimes);
		System.out.println("\n\n\n\n");
		((FSTSerialisationMechanism) SerialisationMechanism.FST.getSerialiser())
				.reset();

		// Begin predicate refinement
		// ontology_.refinePredicate(REFINE_EVIDENCE);

		try {
			IOManager.getInstance().flush();
			miner_.printCurrentHeuristicStates();
			kmp_.writeHeuristics();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void stop() {
		executor_.shutdown();
		while (!executor_.isShutdown()) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		try {
			IOManager.getInstance().close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ConceptThreadInterface getInterface() {
		return interface_;
	}

	/**
	 * Gets the {@link KnowledgeMiner} instance.
	 * 
	 * @return The {@link KnowledgeMiner} instance or null if it hasn't been
	 *         created yet.
	 */
	public static KnowledgeMiner getInstance() {
		if (instance_ == null)
			instance_ = new KnowledgeMiner(ENWIKI_DEFAULT);
		return instance_;
	}

	/**
	 * Gets a known mapping using the article as lookup.
	 * 
	 * @param article
	 *            The article to find the mapping to.
	 * @param ontology
	 *            The ontology access.
	 * @return The mapped concept or null. If no mapping found, sets the state
	 *         array appropriately.
	 */
	public static OntologyConcept getConceptMapping(int article,
			OntologySocket ontology) {
		// Query the ontology and find the mapping (if any)
		int edgeID = ontology.findEdgeIDByArgs(
				CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID(), null,
				CycConstants.WIKI_VERSION.getID(), "\"" + article + "\"");
		if (edgeID < 0) {
			return null;
		}

		// Parse the concept out
		String[] edgeArgs = ontology.findEdgeByID(edgeID);
		OntologyConcept concept = OntologyConcept.parseArgument(edgeArgs[1]);
		return concept;
	}

	/**
	 * Gets a known mapping using the concept as lookup.
	 *
	 * @param concept
	 *            The concept to search with.
	 * @param ontology
	 *            The ontology access.
	 * @return The article ID, or -1 if none found.
	 */
	public static int getArtMapping(OntologyConcept concept,
			OntologySocket ontology) {
		// Query the ontology and find the mapping (if any)
		int edgeID = ontology.findEdgeIDByArgs(
				CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID(),
				concept.getIdentifier(), CycConstants.WIKI_VERSION.getID(),
				null);
		if (edgeID < 0) {
			return -1;
		}

		// Parse the concept out
		String[] edgeArgs = ontology.findEdgeByID(edgeID);
		return Integer.parseInt(UtilityMethods.shrinkString(edgeArgs[3], 1));
	}

	/**
	 * Get the number of threads to use.
	 * 
	 * @return The number of threads the system should use.
	 */
	public static int getNumThreads() {
		return (int) Math.ceil(Runtime.getRuntime().availableProcessors()
				* CPU_USAGE);
	}

	/**
	 * The main method.
	 * 
	 * @param args
	 *            Five arguments: The input file for accepted Cyc concepts, the
	 *            input file for completed Cyc concepts, the output file, the
	 *            output for the new input file, child output file, caught
	 *            mappings output.
	 * @throws Exception
	 *             If something goes awry...
	 */
	public static void main(String[] args) throws Exception {
		KnowledgeMiner km = newInstance("enwiki_20110722");
		boolean preprocess = false;
		int start = 0;
		int end = -1;
		String filename = null;
		boolean mappingCyc = false;
		int runID = -1;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("preprocess"))
				preprocess = true;
			else if (args[i].equals("-s")) {
				i++;
				start = Integer.parseInt(args[i]);
			} else if (args[i].equals("-e")) {
				i++;
				end = Integer.parseInt(args[i]);
			} else if (args[i].equals("-f")) {
				i++;
				filename = args[i];
			} else if (args[i].equals("-i")) {
				i++;
				runID = Integer.parseInt(args[i]);
			} else if (args[i].equals("-m")) {
				mappingRun_ = true;
			} else if (args[i].equals("-c")) {
				mappingCyc = true;
			} else if (args[i].equals("-M")) {
				ConceptMiningTask.onlyMining = true;
			} else if (args[i].equals("-I")) {
				InteractiveMode.interactiveMode_ = true;
				km.singleThread_ = true;
			}
		}

		if (preprocess)
			km.preprocess();

		if (runID != -1) {
			readInOntologyMappings();
			km.runID_ = runID;
		}

		// Link an input file to KM
		String resourceName = RESOURCE_WIKIPEDIA;
		if (mappingCyc)
			resourceName = RESOURCE_ONTOLOGY;
		km.mapAllResource(resourceName, start, end, filename);

		System.exit(0);
	}

	/**
	 * Reads in existing run information for both concepts and articles such
	 * that any future mapping processes with a lower/equal iteration are
	 * skipped and later ones are run.
	 */
	public static void readInOntologyMappings() {
		System.out.println("Beginning preloading.");
		DAGSocket ontology = (DAGSocket) ResourceAccess.requestOntologySocket();

		// TODO Rewrite to look at the node run, not the edges on the node
		// q (assertedSentence (synonymousExternalConcept ?X Enwiki_20110722
		// ?Y))

		// Search for all runIDs using mapping edges
		String delimiter = "#";
		String mapArgs = "getprop N $1 " + RUN_ID + " " + delimiter + " T";
		String mappedNodes = "query F ("
				+ CommonConcepts.ASSERTED_SENTENCE.getID() + " ("
				+ CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID() + " ?X "
				+ CycConstants.WIKI_VERSION.getConceptName() + " ?Y))";
		String regEx = XY_SUB_PATTERN.pattern();
		try {
			String result = ontology.command("map", mapArgs + "\n"
					+ mappedNodes + "\n" + regEx, false);
			String[] split = result.split(delimiter);
			if (split.length > 0)
				System.out.println("Preloading " + (split.length / 2)
						+ " existing mappings.");
			// Reading in each runID
			for (int i = 0; i < split.length; i++) {
				if (split[i].isEmpty())
					continue;
				String inputSubs = split[i++].trim();
				String[] nodeRunSplit = split[i].trim().split("\\|");
				if (nodeRunSplit[0].equals("1")) {
					int runID = Integer.parseInt(nodeRunSplit[1]);

					// Parse the subs
					Matcher m = XY_SUB_PATTERN.matcher(inputSubs);
					m.find();
					int conceptID = Integer.parseInt(m.group(1));
					int articleID = Integer.parseInt(m.group(2));

					// Set the states
					ConceptMiningTask.setConceptState(conceptID, runID);
					ConceptMiningTask.setArticleState(articleID, runID);
				}
			}
			if (split.length > 0)
				System.out.println("Done!");
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Preloading complete.");
	}

	/**
	 * Creates the KnowledgeMiner instance. Only one instance may be around at
	 * once.
	 * 
	 * @param databaseName
	 *            The name of the database being loaded.
	 * @return The instance.
	 */
	public static KnowledgeMiner newInstance(String databaseName) {
		if (instance_ == null) {
			instance_ = new KnowledgeMiner(databaseName);
		} else
			throw new IllegalAccessError("Instance already exists.");
		return instance_;
	}

	private class ProcessConceptTask<V> extends FutureTask<V> {

		public ProcessConceptTask(Runnable runnable, V result) {
			super(runnable, result);
		}

		@Override
		protected void done() {
			numComplete_++;
		}

	}

	protected ThreadPoolExecutor getExecutor() {
		return executor_;
	}
}
