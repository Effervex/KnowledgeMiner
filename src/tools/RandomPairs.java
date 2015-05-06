package tools;

import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Random;

import util.collection.ProbabilityDistribution;
import knowledgeMiner.KnowledgeMiner;
import cyc.OntologyConcept;

/**
 * A class for producing a number of random pairs of concepts that are known to
 * be neither disjoint nor conjoint.
 *
 * @author Sam Sarjant
 */
public class RandomPairs {
	private DAGSocket ontology_;
	private WMISocket wmi_;
	private ProbabilityDistribution<String> relations_;

	public RandomPairs() {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		try {
			setUpRelations(new File("relationCounts.txt"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a probability distribution from the relation counts file.
	 *
	 * @param relationCounts
	 *            The file containing the relation counts.
	 * @throws IOException
	 */
	private void setUpRelations(File relationCounts) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(relationCounts));
		String input = null;
		relations_ = new ProbabilityDistribution<>();
		while ((input = in.readLine()) != null) {
			String[] split = input.split("\\t");
			relations_.add(split[0], Integer.parseInt(split[1]));
		}
		relations_.normaliseProbs();

		in.close();
	}

	public static void main(String[] args) {
		RandomPairs rp = new RandomPairs();
		try {
			rp.execute(Integer.parseInt(args[0]),
					new File("randomTraining.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Continues sampling random pairs of concepts until N are found that are
	 * unknown. These are stored with a randomly sampled relation (according to
	 * the distribution) and saved into the output file.
	 *
	 * @param requested
	 *            The number of instances requested.
	 * @param output
	 *            The output file.
	 * @throws IOException
	 */
	public void execute(int requested, File output) throws IOException {
		Collection<OntologyConcept> ptChildren = ontology_.quickQuery(
				CommonQuery.SPECS, CommonConcepts.PARTIALLY_TANGIBLE.getID());
		OntologyConcept[] ptArray = ptChildren
				.toArray(new OntologyConcept[ptChildren.size()]);
		int count = 0;
		float checkpoint = 0.1f;
		Random r = new Random();
		output.createNewFile();
		BufferedWriter out = new BufferedWriter(new FileWriter(output));

		while (count < requested) {
			OntologyConcept a = ptArray[r.nextInt(ptArray.length)];
			OntologyConcept b = ptArray[r.nextInt(ptArray.length)];
			if (a != b) {
				String result = ontology_.query(null,
						CommonConcepts.DISJOINTWITH.getID(), a.getIdentifier(),
						b.getIdentifier());
				// Unknown pair found
				// if (result.startsWith("0|NIL")) {
				if (result.startsWith("1|T") || result.startsWith("0|F")) {
					String relation = relations_.sample(false);
					try {
						// Sort a and b
						if (a.toPrettyString().compareTo(b.toPrettyString()) > 0) {
							OntologyConcept c = a;
							a = b;
							b = c;
						}

						String asweka = ontology_.command("asweka",
								a.getIdentifier() + " " + b.getIdentifier()
										+ " " + relation, false);
						String[] split = asweka.split("\\|");
						for (int i = 1; i < split.length; i++) {
							out.write(split[i] + "\n");
							count++;
							if (count >= checkpoint * requested) {
								System.out.println("..."
										+ (int) Math.round(checkpoint * 100)
										+ "%");
								checkpoint += 0.1f;
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		out.close();
	}
}
