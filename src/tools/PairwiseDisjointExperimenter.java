package tools;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.HeuristicProvenance;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import util.collection.MultiMap;
import cyc.OntologyConcept;

public class PairwiseDisjointExperimenter {
	private static final String EXPERIMENT_MICROTHEORY = "PairwiseDisjointExperimentMt";
	private final static Logger logger_ = LoggerFactory
			.getLogger(ConceptMiningTask.class);

	/** The ontology access. */
	private DAGSocket ontology_;
	private ArrayList<OntologyConcept> terms_;

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

	public static void assertThresholdDisjointFile(File disjointFile,
			float relationThreshold, float intraRelationThreshold,
			OntologySocket ontology) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(disjointFile));

		// Read the disjoint assertions
		String line = reader.readLine();
		// Skip headers
		while ((line = reader.readLine()) != null) {
			String[] split = line.split("\t");
			// Check relation threshold
			if (Float.parseFloat(split[8]) >= relationThreshold) {
				// Check intra threshold
				String[] intraSplit = UtilityMethods.shrinkString(split[13], 1)
						.split(",");
				boolean keep = true;
				for (String intra : intraSplit) {
					if (Float.parseFloat(intra.trim()) < intraRelationThreshold) {
						keep = false;
						continue;
					}
				}

				if (!keep)
					continue;

				// Assert
				int result = ontology.assertToOntology(EXPERIMENT_MICROTHEORY,
						split[0]);
				if (result < 0)
					System.out.println("Could not assert " + split[0]);
				else
					ontology.setProperty(result, false, "disjWeight", split[8]
							+ ":" + split[14]);
			}
		}

		reader.close();
	}

	public static void assertWEKAClassfiedDisjointFile(File disjointFile,
			float predictionThreshold, OntologySocket ontology,
			boolean asserting) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(disjointFile));

		Map<String, String> disjoints = new HashMap<>();
		Collection<String> conjoints = new HashSet<>();

		// Read the disjoint assertions
		String line = reader.readLine();
		// Skip headers
		while ((line = reader.readLine()) != null) {
			if (line.isEmpty())
				break;
			ArrayList<String> split = UtilityMethods.split(line, ',');
			boolean isDisjoint = split.get(2).equals("1:disjoint");
			float predictionWeight = Float.parseFloat(split.get(4));
			String firstArg = split.get(8);
			String secondArg = split.get(9);
			String assertionString = "(disjointWith " + firstArg + " "
					+ secondArg + ")";
			String dataString = split.get(5) + "," + split.get(6) + ","
					+ split.get(7);

			// Add it to disjoints if high enough weight and not contradicted by
			// conjoint.
			if (isDisjoint) {
				if (predictionWeight >= predictionThreshold) {
					if (!conjoints.contains(assertionString))
						disjoints.put(assertionString, dataString);
					else
						logger_.info("{} contradicted by conjoint edge",
								assertionString);
				}
			} else {
				conjoints.add(assertionString);
				if (disjoints.containsKey(assertionString)) {
					disjoints.remove(assertionString);
					logger_.info("{} contradicted by conjoint edge",
							assertionString);
				}
			}
		}
		reader.close();

		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(
				disjointFile.getPath() + "out")));
		for (String assertionString : disjoints.keySet()) {
			String provenance = disjoints.get(assertionString);
			writer.write(assertionString + "," + provenance + "\n");
			if (asserting) {
				int id = ontology.assertToOntology(EXPERIMENT_MICROTHEORY,
						assertionString);
				logger_.info("{} asserted", assertionString);
				ontology.setProperty(id, false, HeuristicProvenance.PROVENANCE,
						provenance);
			}
		}
		writer.close();
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
			MultiMap<String, String> nonDisjoints = MultiMap
					.createSortedSetMultiMap();
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
				String[] results = ontology_.batchCommand("query", arguments);
				for (int j = 0; j < results.length; j++) {
					// If not disjoint
					if (!ontology_.parseProofResult(results[j])) {
						OntologyConcept thatTerm = terms_.get(i + j + 1);
						nonDisjoints.put(thisTerm.getConceptName(),
								thatTerm.getConceptName());
						nonDisjoints.put(thatTerm.getConceptName(),
								thisTerm.getConceptName());
					}
				}

				System.out.print(".");
				if (i % 10 == 9)
					System.out.print((i + 1) + "/" + terms_.size());
			}

			BufferedWriter out = new BufferedWriter(new FileWriter(pairFile));
			for (String key : nonDisjoints.keySet()) {
				for (String val : nonDisjoints.get(key))
					out.write(key + "\t" + val + "\t"
							+ nonDisjoints.get(key).size() + "\n");
			}
			out.close();
			System.out.println();
		} else {
			System.out.println("Skipping pairwise disjoint checks...");
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
		for (String key : diffMap.keySet()) {
			for (String diff : diffMap.get(key)) {
				writer.write(key + "\t" + diff + "\t");

				// Damn, this isn't going to work. The disjoint relationships
				// are to the CLUSTER, not the seed.
				List<String> justify = ontology_.justify(
						CommonConcepts.DISJOINTWITH.getID(), key, diff);
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
			File disjointFile = new File(args[3]);
			float relationThreshold = 0;
			float intraRelationThreshold = 0;
			File diffFile = null;
			if (args.length == 7) {
				relationThreshold = Float.parseFloat(args[4]);
				intraRelationThreshold = Float.parseFloat(args[5]);
				diffFile = new File(args[6]);
			} else {
				relationThreshold = Float.parseFloat(args[4]);
				diffFile = new File(args[5]);
			}

			if (!outPairs1.exists() || !outPairs2.exists()
					|| outPairs1.length() == 0 || outPairs2.length() == 0) {
				// Read and disambiguate the terms
				System.out.print("Disambiguating terms to taxonomic "
						+ "assertions...");
				readTaxonomicTerms(termFile, directCollection);
				System.out.println(" Done!");
			}

			// Pairwise matching (pre-assertion)
			runPairwiseDisjoints(outPairs1);

			// Read in and assert disjointness
			System.out.print("Enacting assertions from assertion file...");
//			if (args.length == 7)
//				assertThresholdDisjointFile(disjointFile, relationThreshold,
//						intraRelationThreshold, ontology_);
//			else
//				assertWEKAClassfiedDisjointFile(disjointFile,
//						relationThreshold, ontology_, true);
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
		// If just a single arg, run the classification cleaner
		if (args.length == 1) {
			try {
				assertWEKAClassfiedDisjointFile(new File(args[0]), 1,
						ResourceAccess.requestOntologySocket(), false);
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.exit(0);
		}

		// Either CLASSIFIED
		// Input term file
		// If direct collection file
		// Output pair file
		// Input disjoint assertion file
		// Prediction threshold
		// Output diff file

		// Or THRESHOLD
		// Input term file
		// If direct collection file
		// Output pair file
		// Input disjoint assertion file
		// Disjoint relation threshold
		// Disjoint intra-relation threshold
		// Output diff file
		if (args.length != 7 && args.length != 6) {
			System.err.println("Experimenter requires input term, "
					+ "boolean isDirectCollectionFile, "
					+ "output file, input disjoint assertions, "
					+ "relation/prediction threshold, "
					+ "intra-relation threshold (if thresholding), "
					+ "and output diff file!");
			System.exit(1);
		}

		PairwiseDisjointExperimenter experimenter = new PairwiseDisjointExperimenter();
		experimenter.run(args);
	}
}
