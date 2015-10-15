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
package knowledgeMiner;

import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.wikipedia.WikipediaMappedConcept;

import org.apache.commons.lang3.StringUtils;

import util.UtilityMethods;
import cyc.OntologyConcept;
import cyc.PrimitiveConcept;

/**
 * A class for allowing a user to interactively evaluate if results are correct
 * or not. Has the ability to load and save existing evaluations to
 * automatically evaluate results during processing.
 */
public class InteractiveMode {
	private static final File IGNORED_PRED_FOLDER = new File(
			"AutomatedEvalTool/IgnoredPredicates");

	private static InteractiveMode instance_;

	private static final Pattern MAP_ART_PATTERN = Pattern
			.compile(WikipediaMappedConcept.PREFIX + "\\('(\\d+)'\\)");

	private static final File MAPPING_FOLDER = new File(
			"AutomatedEvalTool/Mapping");

	private static final File MINING_FOLDER = new File(
			"AutomatedEvalTool/Mining");

	private static final boolean PROMPT_USER = false;

	/** If the mapping/mining should involve the user. */
	public static boolean interactiveMode_ = false;

	public static final File MAPPINGS_FILE = new File("evaluatedMappings.txt");

	public static final int NUM_DISAMBIGUATED = 3;

	public static final File TRUE_ASSERTIONS_FILE = new File(
			"evaluatedTrueAssertions.txt");

	private static final File MINING_DATA_FILE = new File(
			"AutomatedEvalTool/minedData.txt");

	private static final Pattern NON_TAXONOMIC = Pattern
			.compile("('(\\d+)|\"([^\"]+)\")");

	private Collection<String> falseAssertions_;
	/** Predicates that are ignored for evaluation. */
	private Set<String> ignoredAdditionPreds_;

	private Set<String> ignoredRemovalPreds_;
	/** Input and output streams. */
	private BufferedReader in = new BufferedReader(new InputStreamReader(
			System.in));
	private Collection<MinedData> localActivity_;
	private Collection<String> localAdditions_;

	private Collection<String> localRemovals_;
	/** The evaluated mappings. */
	private Map<String, Boolean> mappings_;

	/** The number of mined additions locally evaluated. */
	private int numAdditions_ = 0;

	/** The number of mined removals locally evaluated. */
	private int numRemovals_ = 0;

	private PrintStream out = System.out;

	/** If assertion additions are skipped. */
	private int skipAssertionAddition_ = 0;
	/** If assertion removals are skipped. */
	private int skipAssertionRemoval_ = 0;

	/** If mappings are skipped. */
	private int skipMapping_ = 0;

	private Collection<String> trueAssertions_;

	private InteractiveMode() {
		try {
			// Creates directory structure if needed
			MINING_FOLDER.mkdirs();
			MAPPING_FOLDER.mkdir();
			IGNORED_PRED_FOLDER.mkdir();

			// Load the mappings and assertions
			mappings_ = loadMappingEvaluations();
			localAdditions_ = new HashSet<>();
			localRemovals_ = new HashSet<>();
			localActivity_ = new HashSet<>();
			OntologySocket ontology = ResourceAccess.requestOntologySocket();
			trueAssertions_ = loadAssertions(new File(MINING_FOLDER,
					"trueAssertions.txt"), ontology);
			falseAssertions_ = loadAssertions(new File(MINING_FOLDER,
					"falseAssertions.txt"), ontology);
			ignoredAdditionPreds_ = loadIgnoredPreds(new File(
					IGNORED_PRED_FOLDER, "additionPredicates.txt"));
			ignoredRemovalPreds_ = loadIgnoredPreds(new File(
					IGNORED_PRED_FOLDER, "removalPredicates.txt"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * A convenience method called by both evaluate addition and evaluate
	 * removal.
	 *
	 * @param assertion
	 *            The assertion being added/removed.
	 * @param added
	 *            If the assertion is being added, else removed.
	 * @param ontology
	 *            The ontology access.
	 */
	private void evaluateMined(DefiniteAssertion assertion, boolean added,
			OntologySocket ontology) {
		if (!interactiveMode_)
			return;
		OntologyConcept relation = assertion.getRelation();
		String assertionStr = assertion.toPrettyString();
		Set<String> ignored = (added) ? ignoredAdditionPreds_
				: ignoredRemovalPreds_;
		if (ignored.contains(relation.toPrettyString()))
			return;
		if (added) {
			skipAssertionAddition_--;
			if (skipAssertionAddition_ > 0)
				return;
		} else {
			skipAssertionRemoval_--;
			if (skipAssertionRemoval_ > 0)
				return;
		}

		// Check prior results
		Collection<String> correct = trueAssertions_;
		Collection<String> incorrect = falseAssertions_;
		Collection<String> local = localAdditions_;
		String typeWord = "ADDITION";
		if (!added) {
			correct = falseAssertions_;
			incorrect = trueAssertions_;
			local = localRemovals_;
			typeWord = "REMOVAL";
		}

		byte truth = -1;
		if (correct.contains(assertionStr))
			truth = 1;
		else if (incorrect.contains(assertionStr))
			truth = 0;
		if (truth != -1) {
			String knownWord = (truth == 1) ? "CORRECT" : "INCORRECT";
			out.println(knownWord + " " + typeWord + ": " + assertionStr);
			local.add(assertionStr);
			localActivity_.add(new MinedData(assertion, added, truth));
			if (added)
				numAdditions_++;
			else
				numRemovals_++;
			return;
		}

		boolean saveMinedData = true;
		if (PROMPT_USER) {
			// Ask user
			String dagtotext = null;
			if (added)
				dagtotext = StringUtils.capitalize(ontology.dagToText(
						assertionStr, "Q", false));
			else
				dagtotext = StringUtils.capitalize(ontology.dagToText("(not "
						+ assertionStr + ")", "Q", false));
			boolean repeat = false;
			saveMinedData = false;
			do {
				repeat = false;

				out.print(typeWord + ": " + dagtotext + " " + assertionStr
						+ "\n" + "(t)rue, (f)alse, e(x)plain concepts, "
						+ "(s)kip, (ss)kip 10, (sss)kip 100, Skip (a)ll, "
						+ "(i)gnore predicate, sa(v)e progress?\n > ");
				try {
					String input = in.readLine();
					if (input.equalsIgnoreCase("T")) {
						correct.add(assertionStr);
						local.add(assertionStr);
						if (added) {
							numAdditions_++;
							truth = 1;
						} else {
							numRemovals_++;
							truth = 0;
						}
						saveMinedData = true;
					} else if (input.equalsIgnoreCase("F")) {
						incorrect.add(assertionStr);
						local.add(assertionStr);
						if (added) {
							numAdditions_++;
							truth = 0;
						} else {
							numRemovals_++;
							truth = 1;
						}
						saveMinedData = true;
					} else if (input.equalsIgnoreCase("S")) {
						if (added)
							skipAssertionAddition_ = 1;
						else
							skipAssertionRemoval_ = 1;
					} else if (input.equalsIgnoreCase("SS")) {
						if (added)
							skipAssertionAddition_ = 10;
						else
							skipAssertionRemoval_ = 10;
					} else if (input.equalsIgnoreCase("SSS")) {
						if (added)
							skipAssertionAddition_ = 100;
						else
							skipAssertionRemoval_ = 100;
					} else if (input.equalsIgnoreCase("A")) {
						if (added)
							skipAssertionAddition_ = Integer.MAX_VALUE;
						else
							skipAssertionRemoval_ = Integer.MAX_VALUE;
					} else if (input.equalsIgnoreCase("I")) {
						ignored.add(relation.toPrettyString());
					} else if (input.equalsIgnoreCase("V")) {
						saveEvaluations();
						repeat = true;
					} else if (input.equalsIgnoreCase("X")) {
						// Explain each argument via minisa and mingenls
						ArrayList<String> split = UtilityMethods.split(
								UtilityMethods.shrinkString(assertionStr, 1),
								' ');
						for (int i = 1; i < split.size(); i++) {
							String concept = split.get(i);
							StringBuilder explainLine = new StringBuilder(
									concept + " - ");
							Collection<OntologyConcept> minIsa = ontology
									.quickQuery(CommonQuery.MINISA, concept);
							explainLine.append("ISA: "
									+ StringUtils.join(minIsa, ", ") + "; ");
							Collection<OntologyConcept> minGenls = ontology
									.quickQuery(CommonQuery.MINGENLS, concept);
							explainLine.append("GENLS: "
									+ StringUtils.join(minGenls, ", "));
							System.out.println(explainLine);
						}
						repeat = true;
					} else
						repeat = true;
				} catch (Exception e) {
					e.printStackTrace();
				}
			} while (repeat);
		}
		if (saveMinedData)
			localActivity_.add(new MinedData(assertion, added, truth));
	}

	private Collection<String> loadAssertions(File file, OntologySocket ontology) {
		Collection<String> assertions = new HashSet<>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String strAssertion;
			line: while ((strAssertion = reader.readLine()) != null) {
				// Deal with mapArts, etc
				Matcher m = MAP_ART_PATTERN.matcher(strAssertion);
				int start = 0;
				StringBuilder compiledAssertion = new StringBuilder();
				while (m.find()) {
					// Parse the mappable article
					int id = Integer.parseInt(m.group(1));
					OntologyConcept concept = KnowledgeMiner.getConceptMapping(
							id, ontology);
					if (concept == null) {
						System.out.println("No mapping for article: " + id);
						continue line;
					}

					// Replace the mappable with the actual
					compiledAssertion.append(strAssertion.subSequence(start,
							m.start()));
					compiledAssertion.append(concept.toPrettyString());
					start = m.end();
				}
				// Add the last part
				if (start != 0) {
					strAssertion = compiledAssertion.toString()
							+ strAssertion.substring(start);
				}

				// strAssertion = checkSemanticConstraints(strAssertion,
				// (DAGSocket) ontology);
				// if (strAssertion != null)
				assertions.add(strAssertion);
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return assertions;
	}

	/**
	 * Checks the semantic constraints of an input assertion, removing it if it
	 * cannot possibly be correct.
	 *
	 * @param strAssertion
	 *            The assertion to check.
	 * @param ontology
	 *            The ontology socket.
	 * @return True if the assertion is semantically (but not necessarily
	 *         ontologically) valid.
	 */
	private String checkSemanticConstraints(String strAssertion,
			DAGSocket ontology) {
		try {
			String result = ontology.command("validedge", strAssertion, false);
			if (result.startsWith("1"))
				return strAssertion;

			// Try converting to primitive
			if (strAssertion.contains("\"")) {
				strAssertion = strAssertion.replaceAll("\"(\\d+)\"", "'$1");
				result = ontology.command("validedge", strAssertion, false);
				if (result.startsWith("1"))
					return strAssertion;
			}

			// Try converting to date
			Matcher m = NON_TAXONOMIC.matcher(strAssertion);
			if (m.find()) {
				Collection<OntologyConcept> dates = ontology.findConceptByName(
						m.group(1), false, true, false);

				// Try converting to date where possible
				for (OntologyConcept concept : dates) {
					if (ontology.isa(concept, CommonConcepts.DATE.getID())) {
						String dateAssertion = m.replaceAll(concept
								.toPrettyString());
						result = ontology.command("validedge", dateAssertion,
								false);
						if (result.startsWith("1"))
							return dateAssertion;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private void writeDataToFile(Collection<? extends Object> data,
			String header, File outfile) throws IOException {
		if (!outfile.exists())
			outfile.createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(outfile));
		if (header != null)
			writer.write(header + "\n");
		for (Object datum : data)
			writer.write(datum + "\n");

		writer.close();
	}

	// private void processFile(File assertionFile, boolean isAddition)
	// throws IOException {
	// DAGSocket ontology = (DAGSocket) ResourceAccess.requestOntologySocket();
	// BufferedReader in = new BufferedReader(new FileReader(assertionFile));
	// String line = null;
	// while ((line = in.readLine()) != null) {
	// line = line.trim();
	// ArrayList<String> split = UtilityMethods.split(
	// UtilityMethods.shrinkString(line, 1), ' ');
	// String relation = split.get(0);
	// if (isAddition)
	// evaluateAddition(line, relation, ontology);
	// else
	// evaluateRemoval(line, relation, ontology);
	// }
	// in.close();
	//
	// saveEvaluations();
	// ontology.close();
	// }

	/**
	 * Loads the list of ignored addition predicates from file
	 * 
	 * @return Set of ignored addition predicates
	 */
	protected Set<String> loadIgnoredPreds(File ignoredFile) {
		Set<String> set = new HashSet<String>();
		BufferedReader br = null;
		try {
			if (!ignoredFile.exists())
				ignoredFile.createNewFile();

			br = new BufferedReader(new FileReader(ignoredFile));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				set.add(line);

			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return set;
	}

	/**
	 * Loads the mapping evaluations from file
	 * 
	 * @return Returns hashmap of mappings
	 */
	protected Map<String, Boolean> loadMappingEvaluations() {

		Map<String, Boolean> h = new TreeMap<String, Boolean>();

		BufferedReader br = null;
		try {
			File f = new File(MAPPING_FOLDER, "trueMappings.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, true);

			br.close();

			f = new File(MAPPING_FOLDER, "falseMappings.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));

			// Add all false assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, false);

			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Return hashmap with true and false assertions
		return h;
	}

	/**
	 * Evaluates an assertion addition - it is either true or false (good or
	 * bad). Users can also opt to skip particular types of assertions, or just
	 * general skip.
	 *
	 * @param assertion
	 *            The assertion being added.
	 * @param ontology
	 *            The ontology access.
	 */
	public void evaluateAddition(DefiniteAssertion assertion,
			OntologySocket ontology) {
		evaluateMined(assertion, true, ontology);
	}

	/**
	 * Evaluates a mapping - it is either true or false. Users can also opt to
	 * skip.
	 *
	 * @param concept
	 *            The concept to evaluate.
	 */
	public void evaluateMapping(ConceptModule concept) {
		if (!interactiveMode_)
			return;
		skipMapping_--;
		if (skipMapping_ > 0)
			return;

		// Check prior results
		String conceptStr = concept.toPrettyString(false);
		Boolean known = mappings_.get(conceptStr);
		if (known != null) {
			out.println(known + ": " + conceptStr);
			return;
		}

		if (!PROMPT_USER)
			return;

		// Ask user
		out.print("EVALUATE MAPPING: " + conceptStr
				+ ": (t)rue, (f)alse, (s)kip, (ss)kip 10, (sss)kip 100, "
				+ "Skip (a)ll, sa(v)e progress?\n > ");
		try {
			String input = in.readLine();
			if (input.equalsIgnoreCase("T")) {
				mappings_.put(conceptStr, true);
			} else if (input.equalsIgnoreCase("F")) {
				mappings_.put(conceptStr, false);
			} else if (input.equalsIgnoreCase("SS")) {
				skipMapping_ = 10;
			} else if (input.equalsIgnoreCase("SSS")) {
				skipMapping_ = 100;
			} else if (input.equalsIgnoreCase("A")) {
				skipMapping_ = Integer.MAX_VALUE;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void evaluateRemoval(DefiniteAssertion assertion,
			OntologySocket ontology) {
		evaluateMined(assertion, false, ontology);
	}

	public void outputStats(int[] counts, String typeStr) {
		StringBuilder builder = new StringBuilder();
		builder.append(typeStr + ":");
		int total = counts[0] + counts[1];
		builder.append("  Precision: " + counts[0]
				/ (1f * total) + " (" + counts[0] + "/" + total
				+ ")");
		// builder.append("  Recall: " + counts[0] / (1f * counts[0] + FN));
		System.out.println(builder.toString());
	}

	/**
	 * Saves all new mappings, assertions and ignored predicates to file
	 */
	public void saveEvaluations() {
		try {
			// If we're evaluating mining (assertions)
			if (trueAssertions_.size() > 0 || falseAssertions_.size() > 0) {

				// Start out with trueAdditions/Removals the size of
				// additions_/removals_
				writeDataToFile(trueAssertions_, null, new File(MINING_FOLDER,
						"trueAssertions.txt"));
				writeDataToFile(falseAssertions_, null, new File(MINING_FOLDER,
						"falseAssertions.txt"));

				// Count local additions
				int[] additionCounts = new int[2];
				for (String localAdd : localAdditions_) {
					if (trueAssertions_.contains(localAdd))
						additionCounts[0]++;
					else if (falseAssertions_.contains(localAdd))
						additionCounts[1]++;
				}
				int[] removalCounts = new int[2];
				for (String localRem : localRemovals_) {
					if (trueAssertions_.contains(localRem))
						removalCounts[1]++;
					else if (falseAssertions_.contains(localRem))
						removalCounts[0]++;
				}

				// Output stats
				outputStats(additionCounts, "additions");
				outputStats(removalCounts, "removals");

				// Output the statistics
				// StringBuilder localVals = new StringBuilder();
				// localVals.append(percentToStr(additionCounts[0],
				// numAdditions_)
				// + " locally correct additions (" + additionCounts[0]
				// + "/" + numAdditions_ + ")\n");
				// localVals.append(percentToStr(removalCounts[0], numRemovals_)
				// + " locally correct removals (" + removalCounts[0]
				// + "/" + numRemovals_ + ")");
				// System.out.println(localVals);
			}

			if (!localActivity_.isEmpty()) {
				writeDataToFile(localActivity_,
						"Assertion\tHeuristic\tPrediction\tActual",
						MINING_DATA_FILE);
			}

			// If we're evaluating mappings
			if (mappings_.size() > 0) {
				// TODO Local mappings
				double mappingCount = mappings_.size(), trueMappingCount = mappings_
						.size();
				File trueMappings = new File(
						"AutomatedEvalTool/Mapping/trueMappings.txt");
				File falseMappings = new File(
						"AutomatedEvalTool/Mapping/falseMappings.txt");

				BufferedWriter trueMappingWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(
								trueMappings)));
				BufferedWriter falseMappingWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(
								falseMappings)));

				for (Map.Entry<String, Boolean> entry : mappings_.entrySet()) {
					String key = entry.getKey();
					Boolean value = entry.getValue();

					if (value == true) {
						trueMappingWriter.write(key);
						trueMappingWriter.newLine();
					} else {
						falseMappingWriter.write(key);
						falseMappingWriter.newLine();
						trueMappingCount--;
					}
				}
				double mapPercentage = 0;
				if (trueMappingCount > 0 && mappingCount > 0)
					mapPercentage = round(
							((trueMappingCount / mappingCount) * 100), 2);

				System.out.println(mapPercentage + "% correct mappings");
				trueMappingWriter.close();
				falseMappingWriter.close();
			}

			// If there's any addition predicates to write to file
			if (ignoredAdditionPreds_.size() > 0) {
				File addPreds = new File(
						"AutomatedEvalTool/IgnoredPredicates/additionPredicates.txt");
				writeDataToFile(ignoredAdditionPreds_, null, addPreds);
			}
			// If there's any removal predicates to write to file
			if (ignoredRemovalPreds_.size() > 0) {
				File remPreds = new File(
						"AutomatedEvalTool/IgnoredPredicates/removalPredicates.txt");
				writeDataToFile(ignoredRemovalPreds_, null, remPreds);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static InteractiveMode getInstance() {
		if (instance_ == null)
			instance_ = new InteractiveMode();
		return instance_;
	}

	/**
	 * Rounds a double to n decimal places
	 * 
	 * @param value
	 *            Double to round
	 * @param n
	 *            Number of decimal places to round to
	 * @return Rounded double
	 */
	public static double round(double value, int n) {
		if (n < 0)
			throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(n, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

	/**
	 * Processes a list of assertions from file.
	 *
	 * @param args
	 *            The File to load the assertions from.
	 */
	// public static void main(String[] args) {
	// if (args.length < 1) {
	// System.err.println("Requires file to process and optional "
	// + "isAddition arg (defaults true).");
	// System.exit(1);
	// }
	// interactiveMode_ = true;
	// File assertionFile = new File(args[0]);
	// boolean isAddition = (args.length < 2 || args[1].equalsIgnoreCase("T"));
	//
	// ResourceAccess.newInstance();
	// InteractiveMode im = new InteractiveMode();
	// try {
	// im.processFile(assertionFile, isAddition);
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }

	private class MinedData {
		private DefiniteAssertion assertion_;
		private byte correctAnswer_;
		private boolean prediction_;
		private String provenance_;

		public MinedData(DefiniteAssertion assertion, boolean added,
				byte trueAdded) {
			assertion_ = assertion;
			prediction_ = added;
			correctAnswer_ = trueAdded;
			if (assertion.getProvenance() != null)
				provenance_ = assertion.getProvenance().getHeuristic();
		}

		public String toString() {
			byte predByte = (byte) (prediction_ ? 1 : 0);
			return assertion_.toPrettyString() + "\t" + provenance_ + "\t"
					+ predByte + "\t" + correctAnswer_;
		}
	}
}
