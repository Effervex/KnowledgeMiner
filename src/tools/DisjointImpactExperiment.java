package tools;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.resources.WikipediaSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.HeuristicProvenance;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.CombinatoricsUtils;

import util.UtilityMethods;
import cyc.OntologyConcept;

public class DisjointImpactExperiment {
	/** The ontology access. */
	private DAGSocket ontology_;
	private File unknownsFile_;
	private File classifierFile_;
	private WikipediaSocket wmi_;
	private float threshold_;
	private static final Pattern IMPACT_GENLS = Pattern
			.compile("(\\d+\\.\\d+)(?::[^:]+){2}\\|$");
	private static final Pattern IMPACT_ISA = Pattern
			.compile("(?:\\d+\\.\\d+:)([^:]+):[^:]+\\|$");
	private static final File DISJOINTS_FILE = new File("createdDisjoints.txt");

	public DisjointImpactExperiment(File classifier, File unknowns) {
		this(classifier, unknowns, 1);
	}

	public DisjointImpactExperiment(File classifier, File unknowns,
			float threshold) {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
		classifierFile_ = classifier;
		unknownsFile_ = unknowns;
		threshold_ = threshold;
	}

	public static void main(String[] args) {
		Options optionHandler = new Options();
		optionHandler.addOption("c", true, "Classifier file");
		optionHandler.addOption("u", true, "Unknowns file");
		optionHandler.addOption("t", true, "Threshold");
		optionHandler.addOption("g", true, "Generalising");
		optionHandler.addOption("d", true, "Convert file to DBpedia");
		CommandLineParser parser = new BasicParser();
		try {
			CommandLine cli = parser.parse(optionHandler, args);

			float threshold = (cli.hasOption("t")) ? Float.parseFloat(cli
					.getOptionValue("t")) : 1f;
			DisjointImpactExperiment die = new DisjointImpactExperiment(
					new File(cli.getOptionValue("c")), new File(
							cli.getOptionValue("u")), threshold);
			// DBpedia conversion
			if (cli.hasOption("d")) {
				File input = new File(cli.getOptionValue("d"));
				File output = new File(input.getPath() + "out");
				output.createNewFile();
				die.convertToDbpedia(input, output);
				System.exit(0);
			}

			boolean generalising = (cli.hasOption("g")) ? cli.getOptionValue(
					"g").equalsIgnoreCase("t") : true;
			die.execute(generalising);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void convertToDbpedia(File inputFile, File outputFile)
			throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(inputFile));
		BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
		String input = null;
		int count = 0;
		while ((input = in.readLine()) != null) {
			String[] split = input.split("\t");
			// Map the concepts in the left
			String[] concepts = UtilityMethods.splitToArray(
					UtilityMethods.shrinkString(split[0], 1), ' ');
			boolean fullyFound = true;
			for (int i = 1; i < concepts.length; i++) {
				int article = KnowledgeMiner.getArtMapping(new OntologyConcept(
						concepts[i]), ontology_);
				if (article > 0) {
					String url = WikipediaSocket.getArticleURLDBpedia(article);
					if (url != null)
						concepts[i] = url;
					else
						fullyFound = false;
				} else
					fullyFound = false;
			}
			try {
				out.write("(disjointWith " + concepts[1] + " " + concepts[2]
						+ ")\t" + split[1] + "\n");
			} catch (Exception e) {
				System.err.println(input);
				e.printStackTrace();
			}

			if (fullyFound)
				count++;
		}
		System.out.println("Full mappings: " + count);

		out.close();
		in.close();
	}

	public void execute(boolean generalising) {
		// Classify and assert the disjoints
		WEKAAsserter wekaAsserter = new WEKAAsserter(classifierFile_, ontology_);
		wekaAsserter.generalising_ = generalising;

		// Output the total impact
		System.out.println("Processing unknowns...");
		try {
			wekaAsserter.processUnknowns(unknownsFile_, threshold_, true);
			printAssertions(wekaAsserter.getAssertions());
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Done.");

		// Run through and measure impact
		System.out.println("Measuring Impact and Salience...");
		measureImpactAndSalience(!generalising);
		System.out.println("Done.");

		ontology_.close();
	}

	private void printAssertions(Collection<String> assertions)
			throws IOException {
		DISJOINTS_FILE.createNewFile();
		BufferedWriter out = new BufferedWriter(new FileWriter(DISJOINTS_FILE));
		for (String assertion : assertions) {
			out.write(assertion + "\n");
		}
		out.close();
	}

	/**
	 * Measures the impact of all the created disjoint edges.
	 */
	private void measureImpactAndSalience(boolean cleanRedundant) {
		DescriptiveStatistics salience = new DescriptiveStatistics();
		DescriptiveStatistics impact = new DescriptiveStatistics();
		try {
			String result = ontology_.command("searchprop", "E \"MT\" |\n"
					+ PairwiseDisjointExperimenter.EXPERIMENT_MICROTHEORY,
					false);
			// String result = ontology_.command("findedges",
			// "disjointWith (1)",
			// false);
			String[] split = result.split("\\|");
			float fraction = 0.1f;
			for (int i = 1; i < split.length; i++) {
				int id = Integer.parseInt(split[i]);
				// Resolving edge
				String[] args = ontology_.findEdgeByID(id);
				if (!args[0].equals(CommonConcepts.DISJOINTWITH.getID() + "")) {
					System.err.println("Non disjoint edge: " + id);
					continue;
				}

				// First, if necessary, check redundancy
				if (cleanRedundant) {
					// Unassert, test and reassert if necessary
					String provenance = ontology_.getProperty(id, false,
							HeuristicProvenance.PROVENANCE);
					ontology_.unassert(null, id, true);
					if (!ontology_.evaluate(null, (Object[]) args)) {
						// Not disjoint - reassert
						id = ontology_
								.assertToOntology(
										PairwiseDisjointExperimenter.EXPERIMENT_MICROTHEORY,
										(Object[]) args);
						ontology_.setProperty(id, false,
								HeuristicProvenance.PROVENANCE, provenance);
					}
				}

				// Checking impact
				String impResult = ontology_.command("impact", args[1] + " "
						+ args[2], false);
				Matcher m = IMPACT_GENLS.matcher(impResult);
				if (m.find()) {
					impact.addValue(Double.parseDouble(m.group(1)));
				}

				// Checking salience
				int artA = KnowledgeMiner.getArtMapping(new OntologyConcept(
						Integer.parseInt(args[1])), ontology_);
				int artB = KnowledgeMiner.getArtMapping(new OntologyConcept(
						Integer.parseInt(args[2])), ontology_);
				if (artA != -1 && artB != -1) {
					salience.addValue(wmi_.getRelatednessList(artA, artB)
							.get(0));
				}

				if (i >= fraction * split.length) {
					System.out.print("..." + (int) Math.round(fraction * 100)
							+ "%");
					fraction += 0.1f;
				}
			}
			System.out.println();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Count: " + impact.getN());
		System.out.println("Impact - mean: " + impact.getMean() + " +- "
				+ impact.getStandardDeviation());
		System.out.println("Salience - mean: " + salience.getMean() + " +- "
				+ salience.getStandardDeviation());
	}

	private void measureImpactSiblingDisjoint() {
		DescriptiveStatistics salience = new DescriptiveStatistics();
		DescriptiveStatistics impact = new DescriptiveStatistics();
		try {
			String result = ontology_.command("extract", "?X query ("
					+ CommonConcepts.ISA.getID() + " ?X "
					+ CommonConcepts.SIBLING_DISJOINT_COLLECTION_TYPE.getID()
					+ ")", false);
			String[] split = result.split("\\|");
			for (int i = 1; i < split.length; i++) {
				// Checking impact
				String impResult = ontology_.command("impact", split[i], false);
				Matcher matcher = IMPACT_ISA.matcher(impResult);
				if (matcher.find()) {
					try {
						double val = Double.parseDouble(matcher.group(1));
						// n! / (n - r)!r!
						long combinations = CombinatoricsUtils
								.binomialCoefficient(
										(int) Math.round(Math.exp(val)), 2);
						if (combinations > 0)
							impact.addValue(Math.log(combinations));
					} catch (Exception e) {
					}
				}

				// Checking salience
				// Iterate through each child and compare relatedness
				// String childrenResult = ontology_.command("extract",
				// "?X query (" + CommonConcepts.ISA.getID() + " ?X "
				// + split[i] + ")", false);
				// String[] childrenSplit = childrenResult.split("\\|");
				// for (int n = 1; n < childrenSplit.length - 1; n++) {
				// for (int m = n + 1; m < childrenSplit.length; m++) {
				// int artA = KnowledgeMiner.getArtMapping(
				// new OntologyConcept(n), ontology_);
				// int artB = KnowledgeMiner.getArtMapping(
				// new OntologyConcept(m), ontology_);
				// if (artA != -1 && artB != -1) {
				// salience.addValue(wmi_.getRelatednessList(artA,
				// artB).get(0));
				// }
				// }
				// }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Count: " + impact.getN());
		System.out.println("Impact - mean: " + impact.getMean() + " +- "
				+ impact.getStandardDeviation());
		System.out.println("Salience - mean: " + salience.getMean() + " +- "
				+ salience.getStandardDeviation());
	}
}
