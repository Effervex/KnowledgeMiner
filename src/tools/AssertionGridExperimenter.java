package tools;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;
import knowledgeMiner.AssertionGrid;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.WeightedStanding;

/**
 * This class makes use of the Assertion Grid to find and output clusters of
 * consistent assertions given some input. The class is generally for comparing
 * the effects of adding/removing ontological information and observing the
 * changes.
 *
 * @author Sam Sarjant
 */
public class AssertionGridExperimenter {
	/** The dummy concept to replace. */
	private static final MappableConcept MAPPABLE_STAND_IN = new TextMappedConcept(
			"dummy", false, false);
	/** The assertions composing the grid. */
	private Collection<PartialAssertion> assertions_;
	/** The core grid not tied to any concept. */
	private AssertionGrid coreGrid_;
	/** The ontology access. */
	private OntologySocket ontology_;
	/** The WMI access. */
	private WMISocket wmi_;
	private AssertionGrid disambiguatedGrid_;

	public AssertionGridExperimenter() {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		assertions_ = new ArrayList<>();
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

		OntologyConcept concept = new OntologyConcept("TestConcept");
		disambiguatedGrid_ = new AssertionGrid(coreGrid_, concept, standing,
				existingAssertions, assertionRemoval);
		disambiguatedGrid_.findNConjoint(10000, ontology_);
		return disambiguatedGrid_.getNumCases();
	}

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Experimenter requires input term "
					+ "file and output file!");
			System.exit(1);
		}

		AssertionGridExperimenter experimenter = new AssertionGridExperimenter();
		File termFile = new File(args[0]);
		try {
			experimenter.readTaxonomicTerms(termFile);

			experimenter.buildGrid();
			int numCases = experimenter.disambiguateGrid();
			System.out.println(numCases + " disjoint cases found.");
			File outFile = new File(args[1]);
			outFile.createNewFile();
			experimenter.saveResults(outFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Saves the results to file
	 *
	 * @param outFile
	 * @throws IOException
	 */
	private void saveResults(File outFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
		writer.write("Seed\tAssertions\tProvenance\tWeight\n");
		int numCases = disambiguatedGrid_.getNumCases();
		for (int i = 0; i < numCases; i++) {
			float caseWeight = disambiguatedGrid_.getCaseWeight(i);
			Collection<DefiniteAssertion> assertions = disambiguatedGrid_
					.getAssertions(i);
			for (DefiniteAssertion assertion : assertions) {
				writer.write(disambiguatedGrid_.getSeedAssertion(i)
						.toPrettyString()
						+ "\t"
						+ assertion.toPrettyString()
						+ "\t"
						+ assertion.getProvenance().toString()
						+ "\t"
						+ caseWeight + "\n");
			}
		}
		writer.close();
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
	private void readTaxonomicTerms(File termFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(termFile));

		String line = null;
		while ((line = reader.readLine()) != null) {
			Collection<OntologyConcept> colls = disambiguateToCollection(line);
			if (colls.isEmpty()) {
				System.out.println("WARNING: '" + line
						+ "' did not disambiguate into a collection(s).");
				continue;
			}
			HeuristicProvenance provenance = new HeuristicProvenance(
					"AssertionGridHeuristic", line);
			PartialAssertion parentAssertion = new PartialAssertion();
			for (OntologyConcept concept : colls) {
				PartialAssertion pa = new PartialAssertion(
						CycConstants.ISA_GENLS.getConcept(), provenance,
						MAPPABLE_STAND_IN, concept);
				parentAssertion.addSubAssertion(pa);
			}
			assertions_.add(parentAssertion);
		}

		reader.close();
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
}
