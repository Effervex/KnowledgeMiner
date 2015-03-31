package tools;

import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import graph.module.ARFFData;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.HeuristicProvenance;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.Pair;
import util.UtilityMethods;
import util.collection.MultiMap;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import cyc.OntologyConcept;

public class PairwiseDisjointExperimenter {
	public static final String EXPERIMENT_MICROTHEORY = "PairwiseDisjointExperimentMt";
	private final static Logger logger_ = LoggerFactory
			.getLogger(ConceptMiningTask.class);

	private MultiMap<String, String> nonDisjoints_;
	/** The ontology access. */
	private DAGSocket ontology_;
	private int pairedCount_ = 0;
	private ArrayList<OntologyConcept> terms_;
	private WEKAAsserter wekaGeneralisation_;

	public PairwiseDisjointExperimenter() {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		terms_ = new ArrayList<>();
	}

	/**
	 * Disambiguates a text term into one or more ontology concepts.
	 *
	 * @param term
	 *            The term to disambiguate.
	 * @return A collection of one or more Collection concepts.
	 */
	private Collection<OntologyConcept> disambiguateToCollection(String term) {
		return ontology_.findFilteredConceptByName(term, false, true, true,
				CommonConcepts.ISA.getID(), "?X",
				CommonConcepts.COLLECTION.getID());
	}

	/**
	 * Reads a list of terms to be disambiguated into collections from file and
	 * adds them as taxonomic assertions.
	 *
	 * @param termFile
	 *            The file of terms to be disambiguated.
	 * @throws IOException
	 *             Should something go awry.
	 */
	private void readTaxonomicTerms(File termFile, boolean directCollections)
			throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(termFile));

		String line = null;
		while ((line = reader.readLine()) != null) {
			Collection<OntologyConcept> colls = new ArrayList<>();
			if (directCollections)
				colls.add(new OntologyConcept(line));
			else
				colls = disambiguateToCollection(line);
			if (colls.isEmpty()) {
				System.out.println("WARNING: '" + line
						+ "' did not disambiguate into a collection(s).");
				continue;
			}
			for (OntologyConcept concept : colls) {
				terms_.add(concept);
			}
		}

		reader.close();
	}

	/**
	 * Pairs up every term and queries if they are disjoint, outputting the
	 * result to file.
	 *
	 * @param pairFile
	 *            The output file.
	 * @throws IOException
	 */
	private void runPairwiseDisjoints(File pairFile) throws IOException {
		if (!pairFile.exists() || pairFile.length() == 0) {
			System.out.println("Performing pairwise disjoint checks...");
			System.out.print("Processing");
			pairFile.createNewFile();
			// Read terms or non disjoints from earlier
			MultiMap<String, String> nonDisjoints = MultiMap
					.createSortedSetMultiMap();

			ExecutorService executor = Executors.newFixedThreadPool(Runtime
					.getRuntime().availableProcessors());
			pairedCount_ = 0;
			if (nonDisjoints_ != null) {
				runPairwiseFromPrevious(executor, nonDisjoints);
			} else {
				runPairwiseFromTerms(executor, nonDisjoints);
			}
			executor.shutdown();
			try {
				executor.awaitTermination(24, TimeUnit.HOURS);
			} catch (Exception e1) {
				e1.printStackTrace();
			}

			BufferedWriter out = new BufferedWriter(new FileWriter(pairFile));
			for (Map.Entry<String, Collection<String>> entry : nonDisjoints
					.entrySet()) {
				for (String val : entry.getValue()) {
					out.write(entry.getKey() + "\t" + val + "\n");
					out.write(val + "\t" + entry.getKey() + "\n");
				}
			}
			nonDisjoints_ = nonDisjoints;
			out.close();
			System.out.println();
		} else {
			// Read in the file as input for the next pairwise check
			if (nonDisjoints_ == null) {
				nonDisjoints_ = MultiMap.createSortedSetMultiMap();
				BufferedReader in = new BufferedReader(new FileReader(pairFile));
				String line = null;
				while ((line = in.readLine()) != null) {
					String[] split = line.split("\t");
					if (!nonDisjoints_.containsKey(split[1])
							|| !nonDisjoints_.get(split[1]).contains(split[0]))
						nonDisjoints_.put(split[0], split[1]);
				}
				in.close();
			}
			System.out.println("Skipping pairwise disjoint checks...");
		}
	}

	private void runPairwiseFromPrevious(ExecutorService executor,
			MultiMap<String, String> nonDisjoints) {
		// Read every key-value pair and test it
		for (Map.Entry<String, Collection<String>> entry : nonDisjoints_
				.entrySet()) {
			String[] arguments = new String[entry.getValue().size()];
			String[] values = entry.getValue().toArray(
					new String[arguments.length]);
			for (int i = 0; i < values.length; i++) {
				arguments[i] = "(disjointWith " + entry.getKey() + " "
						+ values[i] + ")";
			}
			executor.execute(new QueryTask(arguments, nonDisjoints));
		}
	}

	private void runPairwiseFromTerms(ExecutorService executor,
			MultiMap<String, String> nonDisjoints) {
		// For every concept
		for (int i = 0; i < terms_.size() - 1; i++) {
			OntologyConcept thisTerm = terms_.get(i);
			// Pair it with the remaining concepts
			String[] arguments = new String[terms_.size() - i - 1];
			for (int j = 0; j < arguments.length; j++) {
				// And test disjointness
				OntologyConcept thatTerm = terms_.get(i + j + 1);
				arguments[j] = "(disjointWith " + thisTerm.getIdentifier()
						+ " " + thatTerm.getIdentifier() + ")";
			}
			executor.execute(new QueryTask(arguments, nonDisjoints));
		}
	}

	public void compareClusters(File before, File after, File diffFile)
			throws IOException {
		// Read the clusters in
		MultiMap<String, String> clusterMapA = readPairFile(before);
		MultiMap<String, String> clusterMapB = readPairFile(after);

		// Deintersect
		MultiMap<String, String> diffMap = MultiMap.createSortedSetMultiMap();
		Collection<String> keys = new HashSet<>(clusterMapA.keySet());
		keys.addAll(clusterMapB.keySet());
		for (String key : keys) {
			if (clusterMapA.containsKey(key) && !clusterMapB.containsKey(key))
				diffMap.putCollection(key, clusterMapA.get(key));
			else if (!clusterMapA.containsKey(key)
					&& clusterMapB.containsKey(key))
				diffMap.putCollection(key, clusterMapB.get(key));
			else
				diffMap.putCollection(key, CollectionUtils.disjunction(
						clusterMapA.get(key), clusterMapB.get(key)));
		}

		// Output the diff
		try {
			ontology_.command("set", "/env/pretty only", false);
		} catch (Exception e) {
			e.printStackTrace();
		}
		BufferedWriter writer = new BufferedWriter(new FileWriter(diffFile));
		for (Map.Entry<String, Collection<String>> entry : diffMap.entrySet()) {
			for (String diff : entry.getValue()) {
				writer.write(entry.getKey() + "\t" + diff + "\t");

				// Damn, this isn't going to work. The disjoint relationships
				// are to the CLUSTER, not the seed.
				List<String> justify = ontology_.justify(
						CommonConcepts.DISJOINTWITH.getID(), entry.getKey(),
						diff);
				for (String justStr : justify) {
					if (justStr.startsWith("(disjointWith")) {
						writer.write(justStr + "\t");
						String weight = ontology_.getProperty(justStr, false,
								"disjWeight");
						if (weight == null)
							weight = "1.0";
						break;
					}
				}
				writer.write("\n");
			}
		}
		writer.close();
	}

	public void run(String[] args) {
		try {
			// Set up files
			File termFile = new File(args[0]);
			boolean directCollection = args[1].equalsIgnoreCase("T");
			File outPairs1 = new File(args[2] + "1");
			File outPairs2 = new File(args[2] + "2");
			File unknownsFile = new File(args[3]);
			File classifierFile = new File(args[4]);
			float relationThreshold = 0;
			relationThreshold = Float.parseFloat(args[5]);
			File diffFile = new File(args[6]);

			if (!outPairs1.exists() || outPairs1.length() == 0) {
				// Read and disambiguate the terms
				System.out.print("Disambiguating terms to taxonomic "
						+ "assertions...");
				readTaxonomicTerms(termFile, directCollection);
				System.out.println(" Done!");
			} else {
				System.out.println("Skipping terms.");
			}

			// Pairwise matching (pre-assertion)
			runPairwiseDisjoints(outPairs1);

			// Read in and assert disjointness
			System.out.print("Enacting assertions from assertion file...");
			wekaGeneralisation_ = new WEKAAsserter(classifierFile, ontology_);
			wekaGeneralisation_
					.processUnknowns(unknownsFile, relationThreshold, true);
			System.out.println(" Done!");

			// Pairwise matching (pre-assertion)
			runPairwiseDisjoints(outPairs2);

			// Diff
			diffFile.createNewFile();
			System.out.print("Performing diff on clusters...");
			compareClusters(outPairs1, outPairs2, diffFile);
			System.out.println(" Done!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static MultiMap<String, String> readPairFile(File clusterFile)
			throws FileNotFoundException, IOException {
		BufferedReader reader = new BufferedReader(new FileReader(clusterFile));

		// Read in the files and clusters, then deintersect
		MultiMap<String, String> clusterMap = MultiMap
				.createSortedSetMultiMap();
		// Skip header
		String line = reader.readLine();
		while ((line = reader.readLine()) != null) {
			String[] split = line.split("\t");
			clusterMap.put(split[0], split[1]);
		}

		reader.close();
		return clusterMap;
	}

	public static void main(String[] args) {
		// Either CLASSIFIED
		// Input term file
		// If direct collection file
		// Output pair file
		// Input unknowns file
		// Input model file
		// Prediction threshold
		// Output diff file
		if (args.length != 7) {
			System.err.println("Experimenter requires input term, "
					+ "boolean isDirectCollectionFile, "
					+ "output file, input unknown assertions, "
					+ "input classifier model file, "
					+ "relation/prediction threshold, "
					+ "and output diff file!");
			System.exit(1);
		}

		PairwiseDisjointExperimenter experimenter = new PairwiseDisjointExperimenter();
		experimenter.run(args);
	}

	private class QueryTask implements Runnable {
		private String[] arguments_;
		private MultiMap<String, String> results_;

		public QueryTask(String[] arguments,
				MultiMap<String, String> nonDisjoints) {
			arguments_ = arguments;
			results_ = nonDisjoints;
		}

		@Override
		public void run() {
			DAGSocket socket = (DAGSocket) ResourceAccess
					.requestOntologySocket();
			String[] results = socket.batchCommand("query", arguments_);
			for (int j = 0; j < results.length; j++) {
				// If not disjoint
				if (!socket.parseProofResult(results[j])) {
					String[] split = UtilityMethods.splitToArray(
							UtilityMethods.shrinkString(arguments_[j], 1), ' ');
					results_.put(split[1], split[2]);
				}
			}
			pairedCount_++;
			if ((pairedCount_ % 10) == 0)
				System.out.print(pairedCount_ + " ");
			else
				System.out.print(".");
		}
	}
}
