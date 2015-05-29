package tools;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import knowledgeMiner.AssertionGrid;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.TextMappedConcept;
import knowledgeMiner.mining.WeightedStanding;

import org.apache.commons.collections4.CollectionUtils;

import util.UtilityMethods;
import util.collection.MultiMap;
import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;

/**
 * This class makes use of the Assertion Grid to find and output clusters of
 * consistent assertions given some input. The class is generally for comparing
 * the effects of adding/removing ontological information and observing the
 * changes.
 *
 * @author Sam Sarjant
 */
public class AssertionGridExperimenter {
	private static final String TEST_CONCEPT = "TestConcept";
	private static final String EXPERIMENT_MICROTHEORY = "AssertionGridExperimentMt";
	/** The dummy concept to replace. */
	private static final MappableConcept MAPPABLE_STAND_IN = new TextMappedConcept(
			"dummy", false, false);
	/** The assertions composing the grid. */
	private Collection<PartialAssertion> assertions_;
	/** The core grid not tied to any concept. */
	private AssertionGrid coreGrid_;
	private AssertionGrid disambiguatedGrid_;
	/** The ontology access. */
	private DAGSocket ontology_;
	/** The WMI access. */
	private WMISocket wmi_;

	public AssertionGridExperimenter() {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		assertions_ = new ArrayList<>();
	}

	/**
	 * Build and save the grid.
	 *
	 * @param outFile
	 *            The output file to save to.
	 * @param outCountsFile
	 *            The output file for assertion counts.
	 * @throws IOException
	 *             Should something go awry...
	 */
	private void disambiguateAndSave(File outFile, File outCountsFile)
			throws IOException {
		int numCases = disambiguateGrid();
		System.out.println(numCases + " disjoint cases found.");
		saveResults(outFile, outCountsFile);
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

	private void readDisjointFile(File disjointFile, float relationThreshold,
			float intraRelationThreshold) throws IOException {
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
				int result = ontology_.assertToOntology(EXPERIMENT_MICROTHEORY,
						split[0]);
				if (result < 0)
					System.out.println("Could not assert " + split[0]);
				else
					ontology_.setProperty(result, false, "disjWeight", split[8]
							+ ":" + split[14]);
			}
		}

		reader.close();
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
			HeuristicProvenance provenance = new HeuristicProvenance(
					"AssertionGridHeuristic", line);
			PartialAssertion parentAssertion = null;
			if (colls.size() == 1)
				parentAssertion = new PartialAssertion(
						CycConstants.ISA.getConcept(), provenance,
						MAPPABLE_STAND_IN, colls.iterator().next());
			else {
				parentAssertion = new PartialAssertion();
				for (OntologyConcept concept : colls) {
					PartialAssertion pa = new PartialAssertion(
							CycConstants.ISA_GENLS.getConcept(), provenance,
							MAPPABLE_STAND_IN, concept);
					parentAssertion.addSubAssertion(pa);
				}
			}
			assertions_.add(parentAssertion);
		}

		reader.close();
	}

	/**
	 * Saves the results to file
	 *
	 * @param outFile
	 *            The file to save results to.
	 * @param outCountsFile
	 *            The file to save assertion counts to.
	 * @throws IOException
	 *             Should something go awry...
	 */
	private void saveResults(File outFile, File outCountsFile)
			throws IOException {
		Map<String, Integer> assertionCount = new TreeMap<>();
		BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
		writer.write("Cluster #\tSeed\tAssertions\tProvenance\tWeight\n");
		int numCases = disambiguatedGrid_.getNumCases();
		for (int i = 0; i < numCases; i++) {
			float caseWeight = disambiguatedGrid_.getCaseWeight(i);
			Collection<DefiniteAssertion> assertions = disambiguatedGrid_
					.getAssertions(i);
			for (DefiniteAssertion assertion : assertions) {
				String prettyString = assertion.toPrettyString();
				writer.write(i
						+ "\t"
						+ disambiguatedGrid_.getSeedAssertion(i)
								.toPrettyString() + "\t" + prettyString + "\t"
						+ assertion.getProvenance().toString() + "\t"
						+ caseWeight + "\n");

				Integer count = assertionCount.get(prettyString);
				if (count == null)
					assertionCount.put(prettyString, 1);
				else
					assertionCount.put(prettyString, count + 1);
			}
		}
		writer.close();

		Map<String, Integer> sortedCount = UtilityMethods
				.sortByValue(assertionCount);
		writer = new BufferedWriter(new FileWriter(outCountsFile));
		for (Map.Entry<String, Integer> entry : sortedCount.entrySet()) {
			writer.write(entry.getKey() + "\t" + entry.getValue() + "\n");
		}
		writer.close();
	}

	/**
	 * Builds the core assertion grid which is not tied to any concept. This
	 * must be performed before disambiguation.
	 */
	public void buildGrid() {
		coreGrid_ = new AssertionGrid(assertions_, MAPPABLE_STAND_IN,
				ontology_, wmi_);
	}

	/**
	 * A simple default disambiguation where there is equal chance for
	 * collection and individual, no existing assertions, and hence no assertion
	 * removal.
	 *
	 * @return The number of separate disjoint cases found.
	 */
	public int disambiguateGrid() {
		return disambiguateGrid(new WeightedStanding(), new ArrayList<>(),
				false);
	}

	/**
	 * Disambiguates the core grid using a given concept, standing bias,
	 * existing assertions, and optional assertion removal (not permanent - just
	 * returns removed assertions).
	 *
	 * @param standing
	 *            The standing bias of the disambiguation.
	 * @param existingAssertions
	 *            The existing assertions to act as initial truth.
	 * @param assertionRemoval
	 *            If assertions can be (marked as) removed from the concept
	 *            during disambiguation.
	 * @return The number of separate disjoint cases found.
	 */
	public int disambiguateGrid(WeightedStanding standing,
			Collection<DefiniteAssertion> existingAssertions,
			boolean assertionRemoval) {
		if (coreGrid_ == null)
			buildGrid();

		OntologyConcept concept = new OntologyConcept(TEST_CONCEPT);
		disambiguatedGrid_ = new AssertionGrid(coreGrid_, concept, standing,
				existingAssertions, assertionRemoval);
		disambiguatedGrid_.findNConjoint(10000, ontology_);
		return disambiguatedGrid_.getNumCases();
	}

	public void run(String[] args) {
		try {
			// Set up files
			File termFile = new File(args[0]);
			boolean directCollection = args[1].equalsIgnoreCase("T");
			File outFile1 = new File(args[2] + "1");
			File outCounts1 = new File(outFile1.getParent(),
					"assertionsCounts1.txt");
			File outFile2 = new File(args[2] + "2");
			File outCounts2 = new File(outFile2.getParent(),
					"assertionsCounts2.txt");
			File disjointFile = new File(args[3]);
			float relationThreshold = Float.parseFloat(args[4]);
			float intraRelationThreshold = Float.parseFloat(args[5]);
			File diffFile = new File(args[6]);

			if (!outFile1.exists() || !outFile2.exists()
					|| outFile1.length() == 0 || outFile2.length() == 0) {
				// Read and disambiguate the terms
				System.out.print("Disambiguating terms to taxonomic "
						+ "assertions...");
				readTaxonomicTerms(termFile, directCollection);
				System.out.println(" Done!");

				// Build the grid and resolve
				System.out.print("Building assertion grid...");
				buildGrid();
				System.out.println(" Done!");
			} else
				System.out.println("Cluster files exist - "
						+ "skipping term disambiguation.");

			if (!outFile1.exists() || outFile1.length() == 0) {
				System.out.print("Disambiguating grid into clusters "
						+ "(pre-assert)...");
				outFile1.createNewFile();
				outCounts1.createNewFile();
				disambiguateAndSave(outFile1, outCounts1);
				System.out.println(" Done!");
			} else
				System.out.println("Pre-assert cluster file exists - "
						+ "skipping pre-assert clustering.");

			// Read in and assert disjointness
			System.out.print("Enacting assertions from assertion file...");
			readDisjointFile(disjointFile, relationThreshold,
					intraRelationThreshold);
			System.out.println(" Done!");

			if (!outFile2.exists() || outFile2.length() == 0) {
				// Restart
				System.out.print("Disambiguating grid into clusters "
						+ "(post-assert)...");
				outFile2.createNewFile();
				outCounts2.createNewFile();
				disambiguateAndSave(outFile2, outCounts2);
				System.out.println(" Done!");
			} else
				System.out.println("Post-assert cluster file exists - "
						+ "skipping disjointness assertion and "
						+ "post-assert clustering.");

			// Diff
			diffFile.createNewFile();
			System.out.print("Performing diff on clusters...");
			compareClusters(outFile1, outFile2, diffFile);
			System.out.println(" Done!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static MultiMap<String, String> readClusterFile(File clusterFile)
			throws FileNotFoundException, IOException {
		BufferedReader reader = new BufferedReader(new FileReader(clusterFile));

		// Read in the files and clusters, then deintersect
		MultiMap<String, String> clusterMap = MultiMap
				.createSortedSetMultiMap();
		// Skip header
		String line = reader.readLine();
		while ((line = reader.readLine()) != null) {
			String[] split = line.split("\t");
			clusterMap.put(split[1], split[2]);
		}

		reader.close();
		return clusterMap;
	}

	public void compareClusters(File before, File after, File diffFile)
			throws IOException {
		// Read the clusters in
		MultiMap<String, String> clusterMapA = readClusterFile(before);
		MultiMap<String, String> clusterMapB = readClusterFile(after);

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
		BufferedWriter writer = new BufferedWriter(new FileWriter(diffFile));
		for (Map.Entry<String, Collection<String>> entry : diffMap.entrySet()) {
			for (String diff : entry.getValue()) {
				writer.write(entry.getKey() + "\t");
				// If the key is in clusterMapA, but it is not in clusterMapB
				if (clusterMapA.containsKey(entry.getKey())
						&& entry.getValue().contains(diff))
					writer.write("-");
				else
					writer.write("+");
				writer.write(diff + "\n");
			}
		}
		writer.close();
	}

	public static void main(String[] args) {
		// Four args
		// Input term file
		// Output cluster file
		// Input disjoint assertion file
		// Disjoint relation threshold
		// Disjoint intra-relation threshold
		// Output diff file
		if (args.length != 7) {
			System.err.println("Experimenter requires input term, "
					+ "boolean isDirectCollectionFile, "
					+ "file, output file, input disjoint assertions, "
					+ "relation threshold, intra-relation threshold"
					+ "and output diff file!");
			System.exit(1);
		}

		AssertionGridExperimenter experimenter = new AssertionGridExperimenter();
		experimenter.run(args);
	}
}
