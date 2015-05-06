package tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import util.Pair;
import cyc.OntologyConcept;
import knowledgeMiner.KnowledgeMiner;
import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import io.ResourceAccess;
import io.ontology.DAGSocket;

public class DisjointSiblingsCounter {
	private DAGSocket ontology_;

	public DisjointSiblingsCounter() {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
	}

	public void run(String filename) throws IOException {
		File file = new File(filename);
		file.createNewFile();
		
		// Count disjoint lifts
		Map<Pair<String, String>, int[]> countMap = countInternals();

		BufferedWriter out = new BufferedWriter(new FileWriter(file));
		for (Map.Entry<Pair<String, String>, int[]> entry : countMap.entrySet()) {
			int[] counts = entry.getValue();
			Pair<String, String> pair = entry.getKey();
			String output = pair.objA_ + "\t" + pair.objB_ + "\t" + counts[0]
					+ "\t" + counts[1];
			out.write(output + "\n");
		}
		out.close();
	}

	/**
	 * Counts the possible lifting points for the disjoints.
	 *
	 * @return A map of all the possible lifts for disjoint edges.
	 */
	private Map<Pair<String, String>, int[]> countInternals() {
		Map<Pair<String, String>, int[]> countMap = new HashMap<>();

		// For every disjoint edge
		Collection<String[]> disjointAssertions = ontology_.getAllAssertions(
				CommonConcepts.DISJOINTWITH.getID(), 1);
		int x = 0;
		for (String[] disjointAssertion : disjointAssertions) {
			// For A & B
			for (int i = 1; i <= 2; i++) {
				// For each minimum parent
				String thisDisj = disjointAssertion[i];
				String thatDisj = (i == 1) ? disjointAssertion[i + 1]
						: disjointAssertion[i - 1];
				Collection<OntologyConcept> minParents = ontology_.quickQuery(
						CommonQuery.MINGENLS, thisDisj);
				for (OntologyConcept parent : minParents) {
					Pair<String, String> possibleLift = new Pair<String, String>(
							parent.getConceptName(), thatDisj);
					if (countMap.containsKey(possibleLift))
						continue;
					// Get direct children
					Collection<OntologyConcept> children = ontology_
							.quickQuery(CommonQuery.DIRECTSPECS,
									parent.getIdentifier());
					if (children.isEmpty())
						continue;

					// For each child
					String[] arguments = new String[children.size() - 1];
					int n = 0;
					for (OntologyConcept child : children) {
						// Create disjoint query
						if (!child.getIdentifier().equals(thisDisj)) {
							arguments[n++] = "(disjointWith "
									+ child.getIdentifier() + " " + thatDisj
									+ ")";
						}
					}
					// Count true disjoints
					String[] results = ontology_.batchCommand("query",
							arguments);
					int[] count = { 1, 1 };
					for (String result : results) {
						if (ontology_.parseProofResult(result))
							count[0]++;
						count[1]++;
					}

					// Store the count for the edge
					countMap.put(possibleLift, count);
				}
			}
			if ((x % 100) == 0) {
				System.out.println(x + " complete.");
			}
			x++;
		}
		return countMap;
	}

	public static void main(String[] args) {
		DisjointSiblingsCounter dsc = new DisjointSiblingsCounter();
		try {
			dsc.run(args[0]);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
