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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import knowledgeMiner.mining.DefiniteAssertion;

/**
 * A class for allowing a user to interactively evaluate if results are correct
 * or not. Has the ability to load and save existing evaluations to
 * automatically evaluate results during processing.
 */
public class InteractiveMode {
	public static final int NUM_DISAMBIGUATED = 3;

	public static final File MAPPINGS_FILE = new File("evaluatedMappings.txt");
	public static final File TRUE_ASSERTIONS_FILE = new File(
			"evaluatedTrueAssertions.txt");
	public static final File FALSE_ASSERTIONS_FILE = new File(
			"evaluatedFalseAssertions.txt");

	/** If the mapping/mining should involve the user. */
	public static boolean interactiveMode_ = false;

	/** Input and output streams. */
	private BufferedReader in = new BufferedReader(new InputStreamReader(
			System.in));
	private PrintStream out = System.out;

	/** The evaluated mappings. */
	private Map<String, Boolean> mappings_;
	private Map<String, Boolean> additions_;
	private Map<String, Boolean> removals_;

	/** If mappings are skipped. */
	private int skipMapping_ = 0;

	/** If assertion additions are skipped. */
	private int skipAssertionAddition_ = 0;

	/** If assertion removals are skipped. */
	private int skipAssertionRemoval_ = 0;

	/** Predicates that are ignored for evaluation. */
	private Set<String> ignoredAdditionPreds_;
	private Set<String> ignoredRemovalPreds_;

	public InteractiveMode() {
		try {
			// Creates directory structure if needed
			File directory = new File("AutomatedEvalTool");
			if (!directory.exists())
				directory.mkdir();
			directory = new File("AutomatedEvalTool/Mining");
			if (!directory.exists())
				directory.mkdir();
			directory = new File("AutomatedEvalTool/Mapping");
			if (!directory.exists())
				directory.mkdir();
			directory = new File("AutomatedEvalTool/IgnoredPredicates");
			if (!directory.exists())
				directory.mkdir();

			// Load the mappings and assertions
			mappings_ = loadMappingEvaluations();
			additions_ = loadAdditionEvaluations();
			removals_ = loadRemovalEvaluations();
			ignoredAdditionPreds_ = loadIgnoredAdditionPreds();
			ignoredRemovalPreds_ = loadIgnoredRemovalPreds();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// TODO Save mappings
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
			File f = new File(
					"AutomatedEvalTool/IgnoredPredicates/removalPredicates.txt");
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
	 * Loads the list of ignored addition predicates from file
	 * 
	 * @return Set of ignored addition predicates
	 */
	protected Set<String> loadIgnoredAdditionPreds() {
		Set<String> set = new HashSet<String>();
		BufferedReader br = null;
		try {
			File f = new File(
					"AutomatedEvalTool/IgnoredPredicates/additionPredicates.txt");
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
	 * Loads the removed assertions from file
	 * 
	 * @return Returns a hashmap of removed assertions
	 */
	protected HashMap<String, Boolean> loadRemovalEvaluations() {

		HashMap<String, Boolean> h = new HashMap<String, Boolean>();
		BufferedReader br = null;
		try {
			File f = new File(
					"AutomatedEvalTool/Mining/trueRemovedAssertions.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, true);

			br.close();

			f = new File("AutomatedEvalTool/Mining/falseRemovedAssertions.txt");
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
	 * Loads the added assertions from file
	 * 
	 * @return Returns a hashmap of added assertions
	 */
	protected HashMap<String, Boolean> loadAdditionEvaluations() {

		HashMap<String, Boolean> h = new HashMap<String, Boolean>();
		BufferedReader br = null;
		try {
			File f = new File(
					"AutomatedEvalTool/Mining/trueAddedAssertions.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, true);

			br.close();

			f = new File("AutomatedEvalTool/Mining/falseAddedAssertions.txt");
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
	 * Loads the mapping evaluations from file
	 * 
	 * @return Returns hashmap of mappings
	 */
	protected HashMap<String, Boolean> loadMappingEvaluations() {

		HashMap<String, Boolean> h = new HashMap<String, Boolean>();

		BufferedReader br = null;
		try {
			File f = new File("AutomatedEvalTool/Mapping/trueMappings.txt");
			if (!f.exists())
				f.createNewFile();

			br = new BufferedReader(new FileReader(f));
			String line;

			// Add all true assertions to the hashmap
			while ((line = br.readLine()) != null)
				h.put(line, true);

			br.close();

			f = new File("AutomatedEvalTool/Mapping/falseMappings.txt");
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
	 * Saves all new mappings, assertions and ignored predicates to file
	 */
	public void saveEvaluations() {
		try {
			// If we're evaluating mining (assertions)
			if (additions_.size() > 0 || removals_.size() > 0) {

				// Start out with trueAdditions/Removals the size of
				// additions_/removals_
				double additions = additions_.size(), trueAdditions = additions_
						.size();
				double removals = removals_.size(), trueRemovals = removals_
						.size();

				File trueAddedAssertions = new File(
						"AutomatedEvalTool/Mining/trueAddedAssertions.txt");
				File falseAddedAssertions = new File(
						"AutomatedEvalTool/Mining/falseAddedAssertions.txt");
				BufferedWriter trueAddedAssertionWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(
								trueAddedAssertions)));
				BufferedWriter falseAddedAssertionWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(
								falseAddedAssertions)));

				// For each entry in the additions hashmap, write to correct
				// file
				for (Map.Entry<String, Boolean> entry : additions_.entrySet()) {
					String key = entry.getKey();
					Boolean value = entry.getValue();

					if (value == true) {
						trueAddedAssertionWriter.write(key);
						trueAddedAssertionWriter.newLine();
					} else {
						falseAddedAssertionWriter.write(key);
						falseAddedAssertionWriter.newLine();
						// Decrement true additions if a false is encountered
						trueAdditions--;
					}
				}
				File trueRemAssertions = new File(
						"AutomatedEvalTool/Mining/trueRemovedAssertions.txt");
				File falseRemAssertions = new File(
						"AutomatedEvalTool/Mining/falseRemovedAssertions.txt");
				BufferedWriter trueRemAssertionWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(
								trueRemAssertions)));
				BufferedWriter falseRemAssertionWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(
								falseRemAssertions)));

				// For each entry in the removals hashmap, write to correct file
				// (reverse)
				for (Map.Entry<String, Boolean> entry : removals_.entrySet()) {
					String key = entry.getKey();
					Boolean value = entry.getValue();

					if (value == true) {
						trueRemAssertionWriter.write(key);
						trueRemAssertionWriter.newLine();
					} else {
						falseRemAssertionWriter.write(key);
						falseRemAssertionWriter.newLine();
						trueRemovals--;
					}
				}
				double addPercentage = 0, remPercentage = 0;
				if (trueAdditions > 0 && additions > 0)
					addPercentage = round(((trueAdditions / additions) * 100),
							2);
				else
					addPercentage = 100;
				if (trueRemovals > 0 && removals > 0)
					remPercentage = round(((trueRemovals / removals) * 100), 2);
				else
					remPercentage = 100;

				System.out.println("\n" + addPercentage
						+ "% correct assertion additions");
				System.out.println(remPercentage
						+ "% correct assertion removals");

				trueAddedAssertionWriter.close();
				falseAddedAssertionWriter.close();
				trueRemAssertionWriter.close();
				falseRemAssertionWriter.close();
			}
			// If we're evaluating mappings
			if (mappings_.size() > 0) {
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
				+ ": (T)rue, (F)alse, (S)kip, (SS)kip 10, (SSS)kip 100, "
				+ "Skip (A)ll?\n > ");
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

	/**
	 * Evaluates an assertion removal - it is either true or false (good or
	 * bad). Users can also opt to skip particular types of assertions, or just
	 * general skip.
	 *
	 * @param assertion
	 *            The assertion being removed.
	 */
	public void evaluateRemoval(DefiniteAssertion assertion) {
		if (!interactiveMode_)
			return;
		String relationStr = assertion.getRelation().toPrettyString();
		if (ignoredRemovalPreds_.contains(relationStr))
			return;
		skipAssertionRemoval_--;
		if (skipAssertionRemoval_ > 0)
			return;

		// Check prior results
		String assertionStr = assertion.toPrettyString();
		Boolean known = removals_.get(assertionStr);
		if (known != null) {
			out.println(known + " removal: " + assertionStr);
			return;
		}
		// Ask user
		out.print("EVALUATE REMOVAL: " + assertion
				+ ": (T)rue, (F)alse, (S)kip, (SS)kip 10, (SSS)kip 100, "
				+ "Skip (A)ll, (I)gnore predicate?\n > ");
		try {
			String input = in.readLine();
			if (input.equalsIgnoreCase("T")) {
				removals_.put(assertionStr, true);
			} else if (input.equalsIgnoreCase("F")) {
				removals_.put(assertionStr, false);
			} else if (input.equalsIgnoreCase("SS")) {
				skipAssertionRemoval_ = 10;
			} else if (input.equalsIgnoreCase("SSS")) {
				skipAssertionRemoval_ = 100;
			} else if (input.equalsIgnoreCase("A")) {
				skipAssertionRemoval_ = Integer.MAX_VALUE;
			} else if (input.equalsIgnoreCase("I")) {
				ignoredRemovalPreds_.add(relationStr);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Evaluates an assertion addition - it is either true or false (good or
	 * bad). Users can also opt to skip particular types of assertions, or just
	 * general skip.
	 *
	 * @param assertion
	 *            The assertion being added.
	 */
	public void evaluateAddition(DefiniteAssertion assertion) {
		if (!interactiveMode_)
			return;
		String relationStr = assertion.getRelation().toPrettyString();
		if (ignoredAdditionPreds_.contains(relationStr))
			return;
		skipAssertionAddition_--;
		if (skipAssertionAddition_ > 0)
			return;

		// Check prior results
		String assertionStr = assertion.toPrettyString();
		Boolean known = additions_.get(assertionStr);
		if (known != null) {
			out.println(known + " addition: " + assertionStr);
			return;
		}
		// Ask user
		out.print("EVALUATE ADDITION: " + assertionStr
				+ ": (T)rue, (F)alse, (S)kip, (SS)kip 10, (SSS)kip 100, "
				+ "Skip (A)ll, (I)gnore predicate?\n > ");
		try {
			String input = in.readLine();
			if (input.equalsIgnoreCase("T")) {
				additions_.put(assertionStr, true);
			} else if (input.equalsIgnoreCase("F")) {
				additions_.put(assertionStr, false);
			} else if (input.equalsIgnoreCase("SS")) {
				skipAssertionAddition_ = 10;
			} else if (input.equalsIgnoreCase("SSS")) {
				skipAssertionAddition_ = 100;
			} else if (input.equalsIgnoreCase("A")) {
				skipAssertionAddition_ = Integer.MAX_VALUE;
			} else if (input.equalsIgnoreCase("I")) {
				ignoredAdditionPreds_.add(relationStr);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}