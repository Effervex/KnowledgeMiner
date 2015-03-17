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

import graph.inference.CommonQuery;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.ontology.OntologySocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import cyc.OntologyConcept;
import knowledgeMiner.mining.DefiniteAssertion;
import util.UtilityMethods;

/**
 * A class for allowing a user to interactively evaluate if results are correct
 * or not. Has the ability to load and save existing evaluations to
 * automatically evaluate results during processing.
 */
public class InteractiveMode {
	private static final String ADDED_ASSERTIONS = "AddedAssertions.txt";

	private static final File IGNORED_PRED_FOLDER = new File(
			"AutomatedEvalTool/IgnoredPredicates");

	private static final File MAPPING_FOLDER = new File(
			"AutomatedEvalTool/Mapping");

	private static final File MINING_FOLDER = new File(
			"AutomatedEvalTool/Mining");

	private static final String REMOVED_ASSERTIONS = "RemovedAssertions.txt";
	public static final File FALSE_ASSERTIONS_FILE = new File(
			"evaluatedFalseAssertions.txt");
	/** If the mapping/mining should involve the user. */
	public static boolean interactiveMode_ = false;

	public static final File MAPPINGS_FILE = new File("evaluatedMappings.txt");

	public static final int NUM_DISAMBIGUATED = 3;

	public static final File TRUE_ASSERTIONS_FILE = new File(
			"evaluatedTrueAssertions.txt");

	private Map<String, Boolean> globalAdditions_;
	private Map<String, Boolean> globalRemovals_;

	/** Predicates that are ignored for evaluation. */
	private Set<String> ignoredAdditionPreds_;
	private Set<String> ignoredRemovalPreds_;
	/** Input and output streams. */
	private BufferedReader in = new BufferedReader(new InputStreamReader(
			System.in));
	private Map<String, Boolean> localAdditions_;
	private Map<String, Boolean> localRemovals_;

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

	public InteractiveMode() {
		try {
			// Creates directory structure if needed
			File directory = new File("AutomatedEvalTool");
			if (!directory.exists())
				directory.mkdir();
			MINING_FOLDER.mkdir();
			MAPPING_FOLDER.mkdir();
			IGNORED_PRED_FOLDER.mkdir();

			// Load the mappings and assertions
			mappings_ = loadMappingEvaluations();
			localAdditions_ = new TreeMap<>();
			localRemovals_ = new TreeMap<>();
			globalAdditions_ = loadAdditionEvaluations();
			globalRemovals_ = loadRemovalEvaluations();
			ignoredAdditionPreds_ = loadIgnoredAdditionPreds();
			ignoredRemovalPreds_ = loadIgnoredRemovalPreds();
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
	 * @param relation
	 *            The relation of the assertion.
	 * @param ontology
	 *            The ontology access.
	 * @param added
	 *            If the assertion is being added, else removed.
	 */
	private void evaluateMined(String assertion, String relation,
			OntologySocket ontology, boolean added) {
		if (!interactiveMode_)
			return;
		Set<String> ignored = (added) ? ignoredAdditionPreds_
				: ignoredRemovalPreds_;
		if (ignored.contains(relation))
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
		Map<String, Boolean> global = (added) ? globalAdditions_
				: globalRemovals_;
		Map<String, Boolean> local = (added) ? localAdditions_ : localRemovals_;
		Boolean known = global.get(assertion);
		String typeWord = (added) ? "addition" : "removal";
		if (known != null) {
			out.println(known + " " + typeWord + ": " + assertion);
			local.put(assertion, known);
			if (added)
				numAdditions_++;
			else
				numRemovals_++;
			return;
		}

		// Ask user
		String dagtotext = null;
		if (added)
			dagtotext = StringUtils.capitalize(ontology.dagToText(assertion,
					"Q", false));
		else
			dagtotext = StringUtils.capitalize(ontology.dagToText("(not "
					+ assertion + ")", "Q", false));
		boolean repeat = false;
		do {
			repeat = false;

			out.print(typeWord.toUpperCase() + ": " + dagtotext + " "
					+ assertion + "\n"
					+ "(t)rue, (f)alse, e(x)plain concepts, "
					+ "(s)kip, (ss)kip 10, (sss)kip 100, Skip (a)ll, "
					+ "(i)gnore predicate, sa(v)e progress?\n > ");
			try {
				String input = in.readLine();
				if (input.equalsIgnoreCase("T")) {
					global.put(assertion, true);
					local.put(assertion, true);
					if (added)
						numAdditions_++;
					else
						numRemovals_++;
				} else if (input.equalsIgnoreCase("F")) {
					global.put(assertion, false);
					local.put(assertion, false);
					if (added)
						numAdditions_++;
					else
						numRemovals_++;
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
					ignored.add(relation);
				} else if (input.equalsIgnoreCase("V")) {
					saveEvaluations();
					repeat = true;
				} else if (input.equalsIgnoreCase("X")) {
					// Explain each argument via minisa and mingenls
					ArrayList<String> split = UtilityMethods.split(
							UtilityMethods.shrinkString(assertion, 1), ' ');
					for (int i = 1; i < split.size(); i++) {
						String concept = split.get(i);
						StringBuilder explainLine = new StringBuilder(concept
								+ " - ");
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
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} while (repeat);
	}

	private String percentToStr(double count, double total) {
		if (total > 0 && count > 0)
			return round(100 * count / total, 2) + "%";
		else
			return "NaN%";
	}

	private void processFile(File assertionFile, boolean isAddition)
			throws IOException {
		DAGSocket ontology = (DAGSocket) ResourceAccess.requestOntologySocket();
		BufferedReader in = new BufferedReader(new FileReader(assertionFile));
		String line = null;
		while ((line = in.readLine()) != null) {
			line = line.trim();
			ArrayList<String> split = UtilityMethods.split(
					UtilityMethods.shrinkString(line, 1), ' ');
			String relation = split.get(0);
			if (isAddition)
				evaluateAddition(line, relation, ontology);
			else
				evaluateRemoval(line, relation, ontology);
		}
		in.close();

		saveEvaluations();
		ontology.close();
	}

	/**
	 * Loads the added assertions from file
	 * 
	 * @return Returns a hashmap of added assertions
	 */
	protected Map<String, Boolean> loadAdditionEvaluations() {

		Map<String, Boolean> h = new TreeMap<String, Boolean>();
		BufferedReader br = null;
		try {
			File f = new File(MINING_FOLDER, "true" + ADDED_ASSERTIONS);
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, true);

			br.close();

			f = new File(MINING_FOLDER, "false" + ADDED_ASSERTIONS);
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
	 * Loads the list of ignored addition predicates from file
	 * 
	 * @return Set of ignored addition predicates
	 */
	protected Set<String> loadIgnoredAdditionPreds() {
		Set<String> set = new HashSet<String>();
		BufferedReader br = null;
		try {
			File f = new File(IGNORED_PRED_FOLDER, "additionPredicates.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
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
	 * Loads the list of ignored removal predicates from file
	 * 
	 * @return Set of ignored removal predicates
	 */
	protected Set<String> loadIgnoredRemovalPreds() {
		Set<String> set = new HashSet<String>();
		BufferedReader br = null;
		try {
			File f = new File(IGNORED_PRED_FOLDER, "removalPredicates.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
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
	 * Loads the removed assertions from file
	 * 
	 * @return Returns a hashmap of removed assertions
	 */
	protected Map<String, Boolean> loadRemovalEvaluations() {

		Map<String, Boolean> h = new TreeMap<String, Boolean>();
		BufferedReader br = null;
		try {
			File f = new File(MINING_FOLDER, "true" + REMOVED_ASSERTIONS);
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, true);

			br.close();

			f = new File(MINING_FOLDER, "false" + REMOVED_ASSERTIONS);
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
	 * Writes the mapping of assertions to file.
	 * 
	 * @param trueFilename
	 *            The output filename for true assertions.
	 * @param falseFilename
	 *            The output filename for false assertions.
	 * @param local
	 *            The local map of assertions (evaluated this run).
	 * @param global
	 *            The global map of assertions (evaluated all time).
	 * @return An int array of true counts [local, global].
	 */
	protected int[] writeAssertionsToFile(String trueFilename,
			String falseFilename, Map<String, Boolean> local,
			Map<String, Boolean> global) throws FileNotFoundException,
			IOException {
		int[] counts = new int[2];
		File trueAddedAssertions = new File(MINING_FOLDER, trueFilename);
		File falseAddedAssertions = new File(MINING_FOLDER, falseFilename);
		BufferedWriter trueWriter = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(trueAddedAssertions)));
		BufferedWriter falseWriter = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(falseAddedAssertions)));

		// For each entry in the additions hashmap, write to correct
		// file
		for (Map.Entry<String, Boolean> entry : global.entrySet()) {
			String key = entry.getKey();
			Boolean value = entry.getValue();

			if (value) {
				trueWriter.write(key);
				trueWriter.newLine();
				// Increment true additions if a false is encountered
				counts[1]++;
				if (local.containsKey(key))
					counts[0]++;
			} else {
				falseWriter.write(key);
				falseWriter.newLine();
				// TODO Deal with opposites as negatives
			}
		}
		trueWriter.close();
		falseWriter.close();

		return counts;
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
		String assertionStr = assertion.toPrettyString();
		String relationStr = assertion.getRelation().toPrettyString();
		evaluateAddition(assertionStr, relationStr, ontology);
	}

	public void evaluateAddition(String assertion, String relation,
			OntologySocket ontology) {
		evaluateMined(assertion, relation, ontology, true);
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
		String conceptStr = concept.toPrettyString();
		Boolean known = mappings_.get(conceptStr);
		if (known != null) {
			out.println(known + ": " + conceptStr);
			return;
		}
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
		String relationStr = assertion.getRelation().toPrettyString();
		String assertionStr = assertion.toPrettyString();
		evaluateRemoval(assertionStr, relationStr, ontology);
	}

	/**
	 * Evaluates an assertion removal - it is either true or false (good or
	 * bad). Users can also opt to skip particular types of assertions, or just
	 * general skip.
	 *
	 * @param assertion
	 *            The assertion being removed.
	 * @param ontology
	 *            The ontology access.
	 */
	public void evaluateRemoval(String assertion, String relation,
			OntologySocket ontology) {
		evaluateMined(assertion, relation, ontology, false);
	}

	/**
	 * Saves all new mappings, assertions and ignored predicates to file
	 */
	public void saveEvaluations() {
		try {
			// If we're evaluating mining (assertions)
			if (globalAdditions_.size() > 0 || globalRemovals_.size() > 0) {

				// Start out with trueAdditions/Removals the size of
				// additions_/removals_

				int[] additions = writeAssertionsToFile("true"
						+ ADDED_ASSERTIONS, "false" + ADDED_ASSERTIONS,
						localAdditions_, globalAdditions_);
				int[] removals = writeAssertionsToFile("true"
						+ REMOVED_ASSERTIONS, "false" + REMOVED_ASSERTIONS,
						localRemovals_, globalRemovals_);

				// Output the statistics
				StringBuilder localVals = new StringBuilder();
				localVals.append(percentToStr(additions[0], numAdditions_)
						+ " locally correct additions (" + additions[0] + "/"
						+ numAdditions_ + ")\n");
				localVals.append(percentToStr(removals[0], numRemovals_)
						+ " locally correct removals (" + removals[0] + "/"
						+ numRemovals_ + ")");
				System.out.println(localVals);
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

				BufferedWriter additionPreds = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(addPreds)));

				for (String s : ignoredAdditionPreds_) {
					additionPreds.write(s);
					additionPreds.newLine();
				}
				additionPreds.close();
			}
			// If there's any removal predicates to write to file
			if (ignoredRemovalPreds_.size() > 0) {
				File remPreds = new File(
						"AutomatedEvalTool/IgnoredPredicates/removalPredicates.txt");

				BufferedWriter removalPreds = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(remPreds)));

				for (String s : ignoredRemovalPreds_) {
					removalPreds.write(s);
					removalPreds.newLine();
				}
				removalPreds.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Processes a list of assertions from file.
	 *
	 * @param args
	 *            The File to load the assertions from.
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Requires file to process and optional "
					+ "isAddition arg (defaults true).");
			System.exit(1);
		}
		interactiveMode_ = true;
		File assertionFile = new File(args[0]);
		boolean isAddition = (args.length < 2 || args[1].equalsIgnoreCase("T"));

		ResourceAccess.newInstance();
		InteractiveMode im = new InteractiveMode();
		try {
			im.processFile(assertionFile, isAddition);
		} catch (IOException e) {
			e.printStackTrace();
		}
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
}