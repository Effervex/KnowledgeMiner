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
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import knowledgeMiner.mining.DefiniteAssertion;
import cyc.OntologyConcept;

/**
 * A class for allowing a user ot interactively evaluate if results are correct
 * or not. Has the ability to load and save existing evaluations to
 * automatically evaluate results during processing.
 */
public class InteractiveMode {
	public static final int NUM_DISAMBIGUATED = 3;

	public static final File MAPPINGS_FILE = new File("evaluatedMappings.txt");
	public static final File ASSERTIONS_FILE = new File(
			"evaluatedAssertions.txt");

	/** If the mapping/mining should involve the user. */
	public static boolean interactiveMode_ = false;

	/** Input and output streams. */
	private BufferedReader in = new BufferedReader(new InputStreamReader(
			System.in));
	private PrintStream out = System.out;

	/** The evaluated mappings. */
	private Map<ConceptModule, Boolean> mappings_;
	private Map<DefiniteAssertion, Boolean> additions_;
	private Map<DefiniteAssertion, Boolean> removals_;

	/** If mappings are skipped. */
	private int skipMapping_ = 0;

	/** If assertion additions are skipped. */
	private int skipAssertionAddition_ = 0;

	/** If assertion removals are skipped. */
	private int skipAssertionRemoval_ = 0;

	/** Predicates that are ignored for evaluation. */
	private Set<OntologyConcept> ignoredAdditionPreds_;
	private Set<OntologyConcept> ignoredRemovalPreds_;

	public InteractiveMode() {
		// TODO Liam: This is basically for loading existing mapping/assertion
		// evaluation results

		// Load the mappings and assertions
		mappings_ = loadMappingEvaluations();
		additions_ = loadAdditionEvaluations();
		removals_ = loadRemovalEvaluations();
		ignoredAdditionPreds_ = loadIgnoredAdditionPreds();
		ignoredRemovalPreds_ = loadIgnoredRemovalPreds();
		// TODO Save mappings
	}

	protected Set<OntologyConcept> loadIgnoredRemovalPreds() {
		// TODO Liam: Implement this (or load some other way)
		return null;
	}

	protected HashSet<OntologyConcept> loadIgnoredAdditionPreds() {
		// TODO Liam: Implement this (or load some other way)
		return new HashSet<>();
	}

	protected Map<DefiniteAssertion, Boolean> loadRemovalEvaluations() {
		// TODO Liam: Implement this (or load some other way)
		return null;
	}

	protected HashMap<DefiniteAssertion, Boolean> loadAdditionEvaluations() {
		// TODO Liam: Implement this (or load some other way)
		return new HashMap<>();
	}

	protected HashMap<ConceptModule, Boolean> loadMappingEvaluations() {
		// TODO Liam: Implement this (or load some other way)
		return new HashMap<>();
	}
	
	public void saveEvaluations() {
		// TODO Liam: Implement this (or save some other way)
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
		ConceptModule duplicate = new ConceptModule(concept.getConcept(),
				concept.getArticle(), concept.getModuleWeight(),
				concept.isCycToWiki());
		Boolean known = mappings_.get(duplicate);
		if (known != null) {
			out.println(known + ": " + duplicate);
			return;
		}

		// Ask user
		out.print("EVALUATE MAPPING: " + duplicate.toPrettyString()
				+ ": (T)rue, (F)alse, (S)kip, (SS)kip 10, (SSS)kip 100, "
				+ "Skip (A)ll?\n > ");
		try {
			String input = in.readLine();
			if (input.equalsIgnoreCase("T")) {
				mappings_.put(duplicate, true);
			} else if (input.equalsIgnoreCase("F")) {
				mappings_.put(duplicate, false);
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
		if (ignoredRemovalPreds_.contains(assertion.getRelation()))
			return;
		skipAssertionRemoval_--;
		if (skipAssertionRemoval_ > 0)
			return;

		// Check prior results
		Boolean known = removals_.get(assertion);
		if (known != null) {
			out.println(known + ": " + assertion);
			return;
		}

		// Ask user
		out.print("EVALUATE REMOVAL: " + assertion.toPrettyString()
				+ ": (T)rue, (F)alse, (S)kip, (SS)kip 10, (SSS)kip 100, "
				+ "Skip (A)ll, (I)gnore predicate?\n > ");
		try {
			String input = in.readLine();
			if (input.equalsIgnoreCase("T")) {
				removals_.put(assertion, true);
			} else if (input.equalsIgnoreCase("F")) {
				removals_.put(assertion, false);
			} else if (input.equalsIgnoreCase("SS")) {
				skipAssertionRemoval_ = 10;
			} else if (input.equalsIgnoreCase("SSS")) {
				skipAssertionRemoval_ = 100;
			} else if (input.equalsIgnoreCase("A")) {
				skipAssertionRemoval_ = Integer.MAX_VALUE;
			} else if (input.equalsIgnoreCase("I")) {
				ignoredRemovalPreds_.add(assertion.getRelation());
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
		if (ignoredAdditionPreds_.contains(assertion.getRelation()))
			return;
		skipAssertionAddition_--;
		if (skipAssertionAddition_ > 0)
			return;

		// Check prior results
		Boolean known = additions_.get(assertion);
		if (known != null) {
			out.println(known + ": " + assertion);
			return;
		}

		// Ask user
		out.print("EVALUATE ADDITION: " + assertion.toPrettyString()
				+ ": (T)rue, (F)alse, (S)kip, (SS)kip 10, (SSS)kip 100, "
				+ "Skip (A)ll, (I)gnore predicate?\n > ");
		try {
			String input = in.readLine();
			if (input.equalsIgnoreCase("T")) {
				additions_.put(assertion, true);
			} else if (input.equalsIgnoreCase("F")) {
				additions_.put(assertion, false);
			} else if (input.equalsIgnoreCase("SS")) {
				skipAssertionAddition_ = 10;
			} else if (input.equalsIgnoreCase("SSS")) {
				skipAssertionAddition_ = 100;
			} else if (input.equalsIgnoreCase("A")) {
				skipAssertionAddition_ = Integer.MAX_VALUE;
			} else if (input.equalsIgnoreCase("I")) {
				ignoredAdditionPreds_.add(assertion.getRelation());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
