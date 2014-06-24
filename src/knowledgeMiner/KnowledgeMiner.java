/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

import graph.core.CommonConcepts;
import graph.core.DirectedAcyclicGraph;
import io.IOManager;
import io.KMSocket;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import knowledgeMiner.debugInterface.ConceptThreadInterface;
import knowledgeMiner.debugInterface.QuietListInterface;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;
import knowledgeMiner.preprocessing.CycPreprocessor;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.CacheMap;
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

	/** TODO Temp field. If children are being mined. */
	public static boolean miningChildren_ = true;

	public static boolean onlineWeightUpdating_ = false;

	/** The current version of Wikipedia being used. */
	public static String wikiVersion_ = ENWIKI_DEFAULT;

	/** Cached verified mappings. */
	private CacheMap<Integer, SortedSet<ConceptModule>> cachedMappings_;

	private CountDownLatch completedLatch_;

	/** The last index to seed with. */
	private int endCount_ = -1;

	/** The thread executor. */
	private ThreadPoolExecutor executor_;

	private int index_ = 0;

	private ConceptThreadInterface interface_;

	/** The preprocessor access. */
	private KnowledgeMinerPreprocessor kmp_;

	/** The mapping aspect of KnowledgeMiner. */
	private CycMapper mapper_;
	
	/** The mining aspect of KnowledgeMiner. */
	private CycMiner miner_;

	private OntologySocket ontology_;

	private int poolLimit_;

	private CycPreprocessor preprocessor_;

	private int seededCount_ = 0;

	/** If the KnowledgeMiner is running in threaded mode. */
	private boolean singleThread_;

	private long startTime_;
	private WMISocket wiki_;

	/** A map linking heuristic strings to their heuristics. */
	private Map<String, Object> heuristicStringMap_;

	/**
	 * Constructor for a new KnowledgeMiner with no IO.
	 */
	private KnowledgeMiner(String wikiVersion) {
		wikiVersion_ = Character.toUpperCase(wikiVersion.charAt(0))
				+ wikiVersion.substring(1);
		singleThread_ = wikiVersion.equals(ENWIKI_DEFAULT);
		if (!singleThread_)
			IOManager.newInstance();
		int numThreads = (singleThread_) ? 1 : getNumThreads();

		ResourceAccess.newInstance();

		heuristicStringMap_ = new HashMap<>();
		mapper_ = new CycMapper(this);
		mapper_.initialise();
		miner_ = new CycMiner(this, mapper_);
		preprocessor_ = new CycPreprocessor();
		cachedMappings_ = new CacheMap<>(CACHE_SIZES, true);

		// Start the executor
		executor_ = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(
				1, numThreads));

		interface_ = new QuietListInterface(); // new SimpleListInterface();
		ontology_ = ResourceAccess.requestOntologySocket();
		ontology_.findConceptByName("INITIALISED", true, true, false);
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

	protected synchronized void readArticle() {
		if (executor_.getQueue().size() >= poolLimit_)
			return;

		int artId = -1;
		while (artId == -1) {
			try {
				artId = wiki_.getNextArticle((int) index_);
				if (artId == -1
						|| (seededCount_ >= endCount_ && endCount_ != -1)) {
					completedLatch_.countDown();
					return;
				}

				String type = wiki_.getPageType(artId);
				index_ = artId;
				if (!type.equals("article") && !type.equals("disambiguation"))
					artId = -1;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		processConcept(new ConceptMiningTask(new ConceptModule(artId)));
	}

	protected synchronized void readConcept() {
		if (executor_.getQueue().size() >= poolLimit_)
			return;

		String constant = null;
		int id = -1;
		while (constant == null) {
			try {
				id = ontology_.getNextNode(index_);
				if (id == -1 || (seededCount_ >= endCount_ && endCount_ != -1)) {
					completedLatch_.countDown();
					return;
				}

				index_ = id;
				constant = ontology_.findConceptByID(id);
				// If there is an ephemeral mark, or the constant is a
				// predicate, skip the constant.
				if (constant != null
						&& (ontology_.getProperty(id, true,
								DirectedAcyclicGraph.EPHEMERAL_MARK) != null || ontology_
								.evaluate(null, CommonConcepts.ISA.getID(),
										constant,
										CommonConcepts.PREDICATE.getID())))
					constant = null;
			} catch (Exception e) {
				e.printStackTrace();
				constant = null;
			}
		}

		processConcept(new ConceptMiningTask(new ConceptModule(
				new OntologyConcept(id))));
	}

	public void addCached(Integer article, SortedSet<ConceptModule> results) {
		cachedMappings_.put(article, results);
	}

	public SortedSet<ConceptModule> getCached(Integer article) {
		return cachedMappings_.get(article);
	}

	/**
	 * Retrieves precomputed results (if they exist) for a given input and task.
	 * 
	 * @param inputID
	 *            The ID of the input term.
	 * @param heuristicName
	 *            The name of the heuristic results to retrieve.
	 * @param taskType
	 *            The type of task to check.
	 * @return The precomputed results or null if no results.
	 */
	public Object getHeuristicResult(int inputID, String heuristicName) {
		if (inputID < 0)
			return null;
		if (kmp_ == null)
			kmp_ = KnowledgeMinerPreprocessor.getInstance();
		Map<Integer, Object> resultMap = kmp_.getLoadHeuristicMap(
				heuristicName, inputID);
		if (resultMap != null)
			return resultMap.get(inputID);
		return null;
	}

	public CycMapper getMapper() {
		return mapper_;
	}

	public CycMiner getMiner() {
		return miner_;
	}

	/**
	 * Maps all of Cyc to Wikipedia, ignoring internal constants and removing
	 * useless concepts.
	 * 
	 * @throws Exception
	 *             Should something go awry...
	 */
	public void mapAllCyc() throws Exception {
		index_ = 0;
		startTime_ = System.currentTimeMillis();
		poolLimit_ = executor_.getMaximumPoolSize();

		completedLatch_ = new CountDownLatch(poolLimit_);
		poolLimit_ *= 2;
		for (int i = 0; i < poolLimit_; i++)
			readConcept();

		completedLatch_.await();
		while (!executor_.awaitTermination(10, TimeUnit.SECONDS))
			LoggerFactory.getLogger(getClass()).info(
					"Awaiting completion of threads.");
		System.out.println("Done!");
	}

	public void mapAllWikipedia(int startIndex, int end)
			throws InterruptedException {
		index_ = startIndex;
		endCount_ = end;
		startTime_ = System.currentTimeMillis();
		poolLimit_ = executor_.getMaximumPoolSize();

		completedLatch_ = new CountDownLatch(poolLimit_);
		poolLimit_ *= 2;
		for (int i = 0; i < poolLimit_; i++)
			readArticle();

		completedLatch_.await();
		while (executor_.getActiveCount() > 0) {
			Thread.sleep(10000);
			LoggerFactory.getLogger(getClass()).info(
					"Awaiting completion of threads.");
		}
		System.out.println("Done!");
		statusUpdate();
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

	public void statusUpdate() {
		long elapsedTime = System.currentTimeMillis() - startTime_;
		String output = UtilityMethods.toTimeFormat(elapsedTime)
				+ " runtime. Mapped " + ConceptMiningTask.assertedCount_
				+ ". Current index at " + index_;
		System.out.println("\n\n\n\n" + output + "\n" + executor_ + "\n\n\n\n");
		LoggerFactory.getLogger("STATUS").info(output);
		try {
			IOManager.getInstance().flush();
			miner_.printCurrentHeuristicStates();
			kmp_.writeClearHeuristics();
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

	/**
	 * Updates the thread viewing interface.
	 * 
	 * @param thread
	 *            The thread being updated.
	 * @param concept
	 *            The concept being processed in that thread.
	 * @param processables
	 *            The remaining concepts to process.
	 */
	public void updateConcept(Thread thread, ConceptModule concept,
			SortedSet<ConceptModule> processables, KMSocket wmi) {
		interface_.update(thread, concept, processables, wmi);
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
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("preprocess"))
				preprocess = true;
			if (args[i].equals("-s")) {
				i++;
				start = Integer.parseInt(args[i]);
			}
			if (args[i].equals("-e")) {
				i++;
				end = Integer.parseInt(args[i]);
			}
		}

		if (preprocess)
			km.preprocess();

		// Link an input file to KM
		km.mapAllWikipedia(start, end);// mapFile(IOManager.getInstance().getInput());

		System.exit(0);
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
			readArticle();
		}

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

	public void registerHeuristic(Object heuristic) {
		heuristicStringMap_.put(heuristic.toString(), heuristic);
	}
}
