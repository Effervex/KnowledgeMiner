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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.activity.InvalidActivityException;

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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.WeightedSet;
import util.serialisation.DefaultSerialisationMechanism;
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

	/** If any new data has been found. */
	private Set<String> changedHeuristics_;

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

	private Lock writeLock_;

	@SuppressWarnings("rawtypes")
	private KnowledgeMinerPreprocessor() {
		miner_ = KnowledgeMiner.getInstance().getMiner();
		mapper_ = KnowledgeMiner.getInstance().getMapper();
		writeLock_ = new ReentrantLock();

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

		changedHeuristics_ = new HashSet<>();
	}

	/**
	 * Basic helper method for consistent file naming.
	 */
	private String makeFilename(String heuristic, Integer startKey,
			Integer endKey) {
		return heuristic + startKey + "-" + endKey + ".dat";
	}

	/**
	 * Loops through all Wikipedia/Ontology indices.
	 * 
	 * @param taskType
	 *            The type of task to process.
	 * @param heuristics
	 *            The heuristics to process with.
	 */
	private void precomputeAll(PrecomputationTaskType taskType,
			Collection<? extends WeightedHeuristic> heuristics) {
		boolean loopOntology = taskType == PrecomputationTaskType.CYC_TO_WIKI;

		// Set up the iterator
		OntologySocket ontology = (loopOntology) ? ResourceAccess
				.requestOntologySocket() : null;
		WMISocket wmi = (loopOntology) ? null : ResourceAccess
				.requestWMISocket();

		// Set up an executor and add all concepts to the execution queue.
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors
				.newFixedThreadPool(Math.max(1, KnowledgeMiner.getNumThreads()));
		int id = 0;
		while (true) {
			try {
				// Get next thing
				int nextID = (loopOntology) ? ontology.getNextNode(id) : wmi
						.getNextArticle(id);
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
					if (type.equals("article") || type.equals("disambiguation")) {
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
	 * @param articleID
	 * 
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
	public Map<Integer, Object> getLoadHeuristicMap(String heuristicName,
			int index) {
		if (!ENABLE_PREPROCESSING)
			return new HashMap<>();

		return heuristicResults_.get(heuristicName).getLoadHeuristicMap(index);
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
			writeLock_.lock();
			try {
				writeClearHeuristics();
			} finally {
				writeLock_.unlock();
			}
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
		return getLoadHeuristicMap(heuristicName, id).containsKey(id);
	}

	/**
	 * Precompute a mapping/mining for a given input with a given set of
	 * heuristics.
	 * 
	 * @param cm
	 *            The input to map/mine.
	 * @param heuristics
	 *            The heuristics to mine with (if null, use all).
	 * @param The
	 *            task type to perform.
	 */
	public void precomputeData(ConceptModule cm,
			Collection<? extends WeightedHeuristic> heuristics,
			PrecomputationTaskType taskType) {
		ENABLE_PREPROCESSING = true;
		// If articleID is -1, run through every article via threads
		if (cm == null) {
			precomputeAll(taskType, heuristics);
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
		if (heuristicName == null || index == -1 || data == null
				|| !ENABLE_PREPROCESSING)
			return;
		writeLock_.lock();
		try {
			Map<Integer, Object> cachedMappings = getLoadHeuristicMap(
					heuristicName, index);
			Object oldVal = cachedMappings.put(index, data);
			if (oldVal == null)
				changedHeuristics_.add(heuristicName);
		} finally {
			writeLock_.unlock();
		}
	}

	public void run(CommandLine parse) {
		// Sort out article and heuristics
		ENABLE_PREPROCESSING = true;
		startTime_ = System.currentTimeMillis();
		overwriteMode_ = parse.hasOption("f");
		numWritten_ = 0;
		ConceptModule cm = null;
		if (parse.getOptionValue("i") != null) {
			int i = Integer.parseInt(parse.getOptionValue("i"));
			if (parse.hasOption("o"))
				cm = new ConceptModule(new OntologyConcept(i));
			else
				cm = new ConceptModule(i);
		}

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
		precomputeData(cm, heuristics_, taskType);

		// Write files
		writeClearHeuristics();

		long elapsedTime = System.currentTimeMillis() - startTime_;
		System.out.println("\n\nProcessing complete. "
				+ UtilityMethods.toTimeFormat(elapsedTime) + " runtime.");
	}

	/**
	 * Writes the heuristic-processed data to file.
	 */
	public void writeClearHeuristics() {
		if (!ENABLE_PREPROCESSING)
			return;
		// Write every heuristic
		for (String heuristic : heuristicResults_.keySet()) {
			if (changedHeuristics_.contains(heuristic))
				heuristicResults_.get(heuristic).writeHeuristic();
			changedHeuristics_.remove(heuristic);
			heuristicResults_.get(heuristic).clear();
		}
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
		try {
			migrateToSingular();
		} catch (Exception e) {
			e.printStackTrace();
		}
//		ENABLE_PREPROCESSING = true;
//		Options options = new Options();
//		options.addOption("m", false, "If precomputing mined data.");
//		options.addOption("w", false, "If mapping from article to ontology.");
//		options.addOption("o", false, "If mapping from ontology to article.");
//		options.addOption("f", false,
//				"Force all heuristics to be run, even if they have stored results.");
//		options.addOption("i", true,
//				"The article/concept to mine. Defaults to all.");
//		Option heurOption = new Option("h", true,
//				"The heuristic(s) to use. Defaults to all.");
//		heurOption.setArgs(20);
//		options.addOption(heurOption);
//
//		CommandLineParser parser = new BasicParser();
//		try {
//			CommandLine parse = parser.parse(options, args);
//			KnowledgeMinerPreprocessor kmp = getInstance();
//			kmp.run(parse);
//		} catch (Exception e) {
//			e.printStackTrace();
//			System.exit(1);
//		}
	}

	public static void migrateToSingular() throws Exception {
		Iterator<File> iterator = FileUtils.iterateFiles(DIR_PATH,
				new String[] { "dat" }, false);
		while (iterator.hasNext()) {
			File file = iterator.next();
			Map<Integer, Object> deser = (Map<Integer, Object>) SerialisationMechanism.FST
					.getSerialiser().deserialize(file);
			File folder = new File(DIR_PATH, file.getName().replaceAll(
					"\\d+-\\d+\\.dat", ""));
			folder.mkdir();
			for (Integer index : deser.keySet()) {
				File output = new File(folder, index + ".dat");
				SerialisationMechanism.FST.getSerialiser().serialize(
						deser.get(index), output,
						DefaultSerialisationMechanism.NORMAL);
			}
		}
	}

	private class HeuristicResult {
		/** Chunked HashMap results by ID. */
		private Map<Integer, HashMap<Integer, Object>> chunkedResults_;

		/** The name of the heuristic this result set is for. */
		private String heuristicName_;

		/** The loaded files for each heuristic. */
		private Set<String> loadedFiles_;

		/**
		 * Constructor for a new HeuristicResult
		 */
		public HeuristicResult(String heuristicName) {
			heuristicName_ = heuristicName;
			chunkedResults_ = new HashMap<>();
			loadedFiles_ = new HashSet<>();
		}

		/**
		 * Loads the heuristic results from file (if the file exists). If no
		 * file found, creates a new map.
		 * 
		 * @param filename
		 *            The file to load.
		 * @param startKey
		 *            The start key to save newly created maps under.
		 * @return A map of results or null if there is a synchronicity issue.
		 */
		@SuppressWarnings("unchecked")
		private HashMap<Integer, Object> loadResults(String filename,
				int startKey) {
			File location = new File(DIR_PATH, filename);
			// Load the file's data into memory
			try {
				writeLock_.lock();

				if (loadedFiles_.contains(filename))
					return null;

				// Recheck loaded files for thread synchronicity
				HashMap<Integer, Object> heuristicMap = null;
				if (location.exists()) {
					// Deserialise the file
					Object deser = SerialisationMechanism.FST.getSerialiser()
							.deserialize(location);
					if (deser != null) {
						Map<Integer, Object> loaded = (Map<Integer, Object>) deser;
						if (loaded instanceof HashMap)
							heuristicMap = (HashMap<Integer, Object>) loaded;
						else {
							heuristicMap = new HashMap<>(NUM_MAPPINGS, 1);
							heuristicMap.putAll(loaded);
							changedHeuristics_.add(heuristicName_);
						}
					}
				}

				// Adding to chunked files and loaded files
				if (heuristicMap == null)
					heuristicMap = new HashMap<>(NUM_MAPPINGS, 1);
				chunkedResults_.put(startKey, heuristicMap);
				loadedFiles_.add(filename);
				return heuristicMap;
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				writeLock_.unlock();
			}
			return null;
		}

		/**
		 * Clears the results from memory.
		 */
		public void clear() {
			chunkedResults_.clear();
			loadedFiles_.clear();
		}

		/**
		 * Gets or loads the mapping containing the preprocessed results.
		 * 
		 * @param index
		 *            The index to get the map for.
		 * @return The mapping that would contain the index.
		 */
		public Map<Integer, Object> getLoadHeuristicMap(int index) {
			// First attempt to load it
			int startKey = (index / NUM_MAPPINGS) * NUM_MAPPINGS;
			int endKey = startKey + NUM_MAPPINGS - 1;
			String filename = makeFilename(heuristicName_, startKey, endKey);
			Map<Integer, Object> heuristicMap = null;
			if (!loadedFiles_.contains(filename)) {
				// The file has been loaded, return the map
				heuristicMap = loadResults(filename, startKey);
			}

			// File already loaded (also resolves null return from loadResults)
			if (heuristicMap == null)
				heuristicMap = chunkedResults_.get(startKey);
			return heuristicMap;
		}

		/**
		 * Writes the current results to file.
		 */
		public void writeHeuristic() {
			// If there's no data to serialise, continue.
			if (chunkedResults_.isEmpty())
				return;

			for (Integer startKey : chunkedResults_.keySet()) {
				// Set up file details
				Integer endKey = startKey + NUM_MAPPINGS - 1;
				String filename = makeFilename(heuristicName_, startKey, endKey);
				File location = new File(DIR_PATH, filename);
				// Serialise it.
				try {
					SerialisationMechanism.FST.getSerialiser().serialize(
							chunkedResults_.get(startKey), location,
							DefaultSerialisationMechanism.NORMAL);
				} catch (InvalidActivityException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
