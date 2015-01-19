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

import graph.core.CommonConcepts;
import graph.core.DirectedAcyclicGraph;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.WeightedSet;
import util.serialisation.DefaultSerialisationMechanism;
import util.serialisation.FSTSerialisationMechanism;
import util.serialisation.SerialisationMechanism;
import cyc.OntologyConcept;

/**
 * Precomputes mappings and minings for concepts/articles to be used later. Also
 * has the capability to read the precomputed data.
 * 
 * @author Sam Sarjant
 */
public class KnowledgeMinerPreprocessor {
	private static final File DIR_PATH = new File("precomputedHeuristics");

	private static KnowledgeMinerPreprocessor instance_;

	public static boolean ENABLE_PREPROCESSING = true;

	public static final int NUM_MAPPINGS = (int) Math.pow(2, 13);

	/**
	 * The data being precomputed. Heuristic name => sortedmap of
	 * concept/article and the precomputed data.
	 */
	private Map<String, HeuristicResult> heuristicResults_;

	/** The current heuristics being processed. */
	private Collection<? extends WeightedHeuristic> heuristics_;

	/** The mapper link. */
	private CycMapper mapper_;

	/** The miner link. */
	private CycMiner miner_;

	/** The number of preprocessed concepts/articles. */
	private int numWritten_;

	private boolean overwriteMode_ = false;

	/** The start time of the processing. */
	private long startTime_;

	@SuppressWarnings("rawtypes")
	private KnowledgeMinerPreprocessor() {
		miner_ = KnowledgeMiner.getInstance().getMiner();
		mapper_ = KnowledgeMiner.getInstance().getMapper();

		// Initialise the heuristic map
		heuristicResults_ = new HashMap<String, HeuristicResult>();
		for (MappingHeuristic cycWikiMapper : mapper_
				.getCycToWikiMappingSuite().getHeuristics())
			heuristicResults_.put(cycWikiMapper.getHeuristicName(),
					new HeuristicResult(cycWikiMapper.getHeuristicName()));
		for (MappingHeuristic wikiCycMapper : mapper_
				.getWikiToCycMappingSuite().getHeuristics())
			heuristicResults_.put(wikiCycMapper.getHeuristicName(),
					new HeuristicResult(wikiCycMapper.getHeuristicName()));
		for (MiningHeuristic miningHeuristic : miner_.getMiningHeuristics())
			heuristicResults_.put(miningHeuristic.getHeuristicName(),
					new HeuristicResult(miningHeuristic.getHeuristicName()));
	}

	/**
	 * Loops through all Wikipedia/Ontology indices.
	 * 
	 * @param taskType
	 *            The type of task to process.
	 * @param heuristics
	 *            The heuristics to process with.
	 * @param reverseOrder
	 */
	private void precomputeAll(PrecomputationTaskType taskType,
			Collection<? extends WeightedHeuristic> heuristics,
			boolean reverseOrder) {
		boolean loopOntology = taskType == PrecomputationTaskType.CYC_TO_WIKI;

		// Set up the iterator
		OntologySocket ontology = (loopOntology) ? ResourceAccess
				.requestOntologySocket() : null;
		WMISocket wmi = (loopOntology) ? null : ResourceAccess
				.requestWMISocket();

		// Set up an executor and add all concepts to the execution queue.
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors
				.newFixedThreadPool(Math.max(1, KnowledgeMiner.getNumThreads()));
		int id = (reverseOrder) ? 35000000 : 0;

		while (true) {
			try {
				// Get next thing
				int nextID = 0;
				if (loopOntology) {
					if (reverseOrder)
						nextID = ontology.getPrevNode(id);
					else
						nextID = ontology.getNextNode(id);
				} else {
					if (reverseOrder)
						nextID = wmi.getPrevArticle(id);
					else
						nextID = wmi.getNextArticle(id);
				}
				if (nextID < 0)
					break;

				id = nextID;
				if (loopOntology) {
					// If the concept is not a predicate or ephemeral, process
					// it.
					String constant = ontology.findConceptByID(id);
					if (constant != null
							&& ontology.getProperty(id, true,
									DirectedAcyclicGraph.EPHEMERAL_MARK) == null
							&& !ontology.evaluate(null,
									CommonConcepts.ISA.getID(), constant,
									CommonConcepts.PREDICATE.getID())) {
						PrecomputationTask preTask = new PrecomputationTask(
								new ConceptModule(new OntologyConcept(constant,
										id)), heuristics, taskType, this);
						executor.execute(preTask);
					}
				} else {
					String type = wmi.getPageType(nextID);
					// If it's an article or disambiguation, process it.
					if (type.equals(WMISocket.TYPE_ARTICLE)
							|| type.equals(WMISocket.TYPE_DISAMBIGUATION)) {
						PrecomputationTask preTask = new PrecomputationTask(
								new ConceptModule(id), heuristics, taskType,
								this);
						executor.execute(preTask);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Wait for completion
		executor.shutdown();
		try {
			if (executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS))
				return;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.err.println("Error precomputing tasks.");
	}

	/**
	 * Writes the ontology mapped data to file for the given heuristic.
	 * 
	 * @param ontologyConcept
	 * 
	 * @param mappings
	 *            The mapping data to write.
	 * @param heuristic
	 *            The heuristic to write for.
	 */
	protected void writeCycMappedData(ConceptModule concept,
			WeightedSet<Integer> mappings,
			MappingHeuristic<OntologyConcept, Integer> heuristic) {
		recordData(heuristic.getHeuristicName(), concept.getConcept().getID(),
				mappings);
	}

	/**
	 * Writes the mined data to file for the given heuristic.
	 * 
	 * @param articleID
	 * 
	 * @param info
	 *            The mined concept module.
	 * @param heuristic
	 *            The heuristic to write the data for.
	 */
	protected void writeMinedData(ConceptModule article, MinedInformation info,
			MiningHeuristic heuristic) {
		recordData(heuristic.getHeuristicName(), article.getArticle(), info);
	}

	/**
	 * Writes the Wikipedia mapped data to file for the given heuristic.
	 * 
	 * @param article
	 *            The article to write about.
	 * @param mappings
	 *            The mapping data to write.
	 * @param heuristic
	 *            The heuristic to write for.
	 */
	protected void writeWikiMappedData(ConceptModule article,
			WeightedSet<OntologyConcept> mappings,
			MappingHeuristic<Integer, OntologyConcept> heuristic) {
		recordData(heuristic.getHeuristicName(), article.getArticle(), mappings);
	}

	/**
	 * Gets/Loads the map for the given heuristic. If the heuristic is
	 * serialised, it is deserialised for mapping data to be added to.
	 * 
	 * @param heuristic
	 *            The heuristic map to get.
	 * @param index
	 *            The index of the data to be recorded.
	 */
	public Object getLoadHeuristicResult(String heuristicName, int index) {
		if (!ENABLE_PREPROCESSING)
			return null;

		return heuristicResults_.get(heuristicName).getLoadHeuristicResults(
				index);
	}

	public void incrementProcessed() {
		if (!ENABLE_PREPROCESSING)
			return;
		numWritten_++;
		if (numWritten_ % ConceptMiningTask.UPDATE_INTERVAL == 0) {
			long elapsedTime = System.currentTimeMillis() - startTime_;
			String status = numWritten_ + " processed. "
					+ UtilityMethods.toTimeFormat(elapsedTime) + " runtime.";
			System.out.println("\n" + status);
			LoggerFactory.getLogger("STATUS").info(status);
		}

		if (numWritten_ % NUM_MAPPINGS == 0) {
			System.out.println("Writing heuristics to file.");
			writeHeuristics();
		}
	}

	/**
	 * Checks if a thing is already processed by a heuristic.
	 * 
	 * @param heuristic
	 *            The heuristic to check for.
	 * @param id
	 *            The id of the thing being checked.
	 * @return True if the thing has already been processed by the heuristic.
	 */
	public boolean isProcessed(String heuristicName, int id) {
		if (overwriteMode_ || !ENABLE_PREPROCESSING)
			return false;
		return getLoadHeuristicResult(heuristicName, id) != null;
	}

	/**
	 * Precompute a mapping/mining for a given input with a given set of
	 * heuristics.
	 * 
	 * @param cm
	 *            The input to map/mine.
	 * @param heuristics
	 *            The heuristics to mine with (if null, use all).
	 * @param reverseOrder
	 *            If the data should be precomputed in reverse order.
	 * @param The
	 *            task type to perform.
	 */
	public void precomputeData(ConceptModule cm,
			Collection<? extends WeightedHeuristic> heuristics,
			PrecomputationTaskType taskType, boolean reverseOrder) {
		ENABLE_PREPROCESSING = true;
		// If articleID is -1, run through every article via threads
		if (cm == null) {
			precomputeAll(taskType, heuristics, reverseOrder);
		} else {
			PrecomputationTask preTask = new PrecomputationTask(cm, heuristics,
					taskType, this);
			preTask.run();
		}
	}

	/**
	 * A general function for recording data. Type information is deliberately
	 * general to allow flexibility.
	 * 
	 * @param heuristic
	 *            The heuristic to record the data for.
	 * @param index
	 *            The index (article/concept) that wsa processed.
	 * @param data
	 *            The data linked to the index.
	 */
	public void recordData(String heuristicName, int index, Object data) {
		if (heuristicName == null || index < 0 || data == null
				|| !ENABLE_PREPROCESSING)
			return;
		heuristicResults_.get(heuristicName).recordResult(index, data);
	}

	public void run(CommandLine parse) {
		// Sort out article and heuristics
		ENABLE_PREPROCESSING = true;
		startTime_ = System.currentTimeMillis();
		overwriteMode_ = parse.hasOption("f");
		numWritten_ = 0;
		ConceptModule cm = null;
		boolean reverseOrder = false;
		if (parse.getOptionValue("i") != null) {
			int i = Integer.parseInt(parse.getOptionValue("i"));
			if (parse.hasOption("o"))
				cm = new ConceptModule(new OntologyConcept(i));
			else
				cm = new ConceptModule(i);
		}
		if (parse.hasOption("R"))
			reverseOrder = true;

		PrecomputationTaskType taskType = null;
		if (parse.hasOption("m")) {
			// Mining article
			heuristics_ = new ArrayList<>(miner_.getMiningHeuristics());
			taskType = PrecomputationTaskType.MINE;
		} else if (parse.hasOption("w")) {
			// Mapping article to concept
			heuristics_ = new ArrayList<>(mapper_.getWikiToCycMappingSuite()
					.getHeuristics());
			taskType = PrecomputationTaskType.WIKI_TO_CYC;
		} else if (parse.hasOption("o")) {
			// Mapping concept to article
			heuristics_ = new ArrayList<>(mapper_.getCycToWikiMappingSuite()
					.getHeuristics());
			taskType = PrecomputationTaskType.CYC_TO_WIKI;
		}

		// Parse the heuristics
		if (parse.hasOption("h")) {
			String[] heuristicArgs = parse.getOptionValues("h");
			Collection<WeightedHeuristic> filteredHeuristics = new ArrayList<>();
			// Find the heuristic by name
			for (String arg : heuristicArgs) {
				boolean found = false;
				for (WeightedHeuristic obj : heuristics_) {
					if (arg.equals(obj.getHeuristicName())) {
						filteredHeuristics.add(obj);
						found = true;
						break;
					}
				}

				if (!found) {
					System.err.println("Could not find heuristic: " + arg);
					System.err.println("Available heuristics: "
							+ heuristics_.toString());
					System.exit(1);
				}
			}

			heuristics_ = filteredHeuristics;
		}
		System.out.println("Beginning precomputation with heuristics: "
				+ heuristics_);
		precomputeData(cm, heuristics_, taskType, reverseOrder);

		// Write files
		writeHeuristics();

		long elapsedTime = System.currentTimeMillis() - startTime_;
		System.out.println("\n\nProcessing complete. "
				+ UtilityMethods.toTimeFormat(elapsedTime) + " runtime.");
	}

	/**
	 * Writes the heuristic-processed data to file.
	 */
	public void writeHeuristics() {
		if (!ENABLE_PREPROCESSING)
			return;

		// Write every heuristic
		for (HeuristicResult heuristic : heuristicResults_.values())
			heuristic.writeHeuristic();

		// Clear the serialisation cache
		((FSTSerialisationMechanism) SerialisationMechanism.FST.getSerialiser())
				.reset();
	}

	/**
	 * Gets the {@link KnowledgeMiner} instance.
	 * 
	 * @return The {@link KnowledgeMiner} instance or null if it hasn't been
	 *         created yet.
	 */
	public static KnowledgeMinerPreprocessor getInstance() {
		if (instance_ == null) {
			instance_ = new KnowledgeMinerPreprocessor();
			SerialisationMechanism.FST.getSerialiser();
		}
		return instance_;
	}

	/**
	 * Main method for running the CycMiner in precomputation or verbose mode.
	 * 
	 * @param args
	 *            The args to determine which mode to run in.
	 */
	public static void main(String[] args) {
		ENABLE_PREPROCESSING = true;
		Options options = new Options();
		options.addOption("m", false, "If precomputing mined data.");
		options.addOption("w", false, "If mapping from article to ontology.");
		options.addOption("o", false, "If mapping from ontology to article.");
		options.addOption("f", false,
				"Force all heuristics to be run, even if they have stored results.");
		options.addOption("i", true,
				"The article/concept to mine. Defaults to all.");
		options.addOption("R", false,
				"If the process should run in reverse order.");
		Option heurOption = new Option("h", true,
				"The heuristic(s) to use. Defaults to all.");
		heurOption.setArgs(20);
		options.addOption(heurOption);

		CommandLineParser parser = new BasicParser();
		try {
			CommandLine parse = parser.parse(options, args);
			KnowledgeMinerPreprocessor kmp = getInstance();
			kmp.run(parse);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Basic helper method for consistent file naming.
	 */
	public static File makeFilename(String heuristic, int index) {
		// Organise into folders
		int chunkIndex = (index / NUM_MAPPINGS) * NUM_MAPPINGS;
		return new File(DIR_PATH, heuristic + File.separator + chunkIndex
				+ File.separator + index + ".dat");
	}

	private class HeuristicResult {
		/** The indices that have changed and need to be serialised. */
		private Collection<Integer> changed_;

		/** The name of the heuristic this result set is for. */
		private String heuristicName_;

		/** HashMap results by ID. */
		private Map<Integer, Object> resultMap_;

		private Lock writeLock_;

		/**
		 * Constructor for a new HeuristicResult
		 */
		public HeuristicResult(String heuristicName) {
			heuristicName_ = heuristicName;
			resultMap_ = new HashMap<>();
			writeLock_ = new ReentrantLock();
			changed_ = new HashSet<>();
		}

		private Object loadResult(int index) {
			File location = makeFilename(heuristicName_, index);
			Object deser = null;
			try {
				deser = SerialisationMechanism.FST.getSerialiser().deserialize(
						location);
			} catch (Exception e) {
				e.printStackTrace();
				// Remove the file and treat as unprocessed
				location.delete();
				return null;
			}
			if (deser != null) {
				try {
					writeLock_.lock();
					resultMap_.put(index, deser);
				} finally {
					writeLock_.unlock();
				}
			}
			return deser;
		}

		/**
		 * Clears the results from memory.
		 */
		public void clear() {
			resultMap_.clear();
			changed_.clear();
		}

		/**
		 * Gets or loads the mapping containing the preprocessed results.
		 * 
		 * @param index
		 *            The index to get the map for.
		 * @return The mapping that would contain the index.
		 */
		public Object getLoadHeuristicResults(int index) {
			// First attempt to load it
			if (resultMap_.containsKey(index))
				return resultMap_.get(index);
			return loadResult(index);
		}

		/**
		 * Records a result for the index and returns the old value (if any).
		 * 
		 * @param index
		 *            The index to record the value for.
		 * @param data
		 *            The value to record.
		 * @return The old value (if any).
		 */
		public void recordResult(int index, Object data) {
			try {
				writeLock_.lock();
				Object oldVal = resultMap_.put(index, data);
				if (oldVal == null || !oldVal.equals(data))
					changed_.add(index);
			} finally {
				writeLock_.unlock();
			}
		}

		/**
		 * Writes the current results to file.
		 */
		public void writeHeuristic() {
			// If there's no data to serialise, continue.
			if (resultMap_.isEmpty())
				return;

			try {
				writeLock_.lock();
				for (Integer index : changed_) {
					// Set up file details
					File location = makeFilename(heuristicName_, index);
					// Serialise it.
					try {
						SerialisationMechanism.FST.getSerialiser().serialize(
								resultMap_.get(index), location,
								DefaultSerialisationMechanism.NORMAL);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				clear();
			} finally {
				writeLock_.unlock();
			}
		}
	}
}
