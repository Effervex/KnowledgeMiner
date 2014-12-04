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

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
import knowledgeMiner.mining.PartialAssertion;

import org.apache.commons.lang3.StringUtils;

import cyc.CycConstants;

public class InteractiveMode {
	public static final int NUM_DISAMBIGUATED = 3;

	/** If the mapping/mining should involve the user. */
	public static boolean interactiveMode_ = false;

	private int SKIP_ALL = 0;
	private int SKIP_MINE = 1;
	private int SKIP_MAP = 2;
	private int SKIP_REVERSE_MAP = 3;
	private int SKIP_DISAM = 4;

	private ConcurrentHashMap<Integer, Integer> skip_;

	private BufferedReader in = new BufferedReader(new InputStreamReader(
			System.in));
	private PrintStream out = System.out;

	public InteractiveMode() {
		skip_ = new ConcurrentHashMap<>();
	}

	public void interactiveMining(MinedInformation mined,
			MiningHeuristic heuristic, WMISocket wmi, OntologySocket ontology) {
		if (!interactiveMode_ || shouldSkip(mined.getArticle(), SKIP_MINE))
			return;

		try {
			String title = wmi.getPageTitle(mined.getArticle(), true);
			out.println("'" + title + "' standing: " + mined.getAllMinedStanding()
					+ " [" + heuristic + "]");
			Collection<PartialAssertion> assertions = mined.getAssertions();

			// For every assertion queue
			for (PartialAssertion pa : assertions) {
				HeuristicProvenance provenance = pa.getProvenance();
				out.println("From source: " + provenance + ":");

				// Display the assertions and prompt user to select.
				Collection<PartialAssertion> flattened = PartialAssertion
						.flattenHierarchy(pa);
				String input = "";
				do {
					int i = 1;
					for (PartialAssertion assertion : flattened) {
						String assertionStr = assertion.toPrettyString();
						out.println(i++ + ":\t" + assertionStr + ":"
								+ assertion.getWeight());
					}
					if (i == 2)
						out.print("Select correct assertion (1, (S)kip): ");
					else
						out.print("Select correct assertion (1-" + (i - 1)
								+ ", (S)kip): ");
					input = in.readLine().toLowerCase();

					// Store the selected assertion as a concrete assertion.
					if (StringUtils.isNumeric(input)) {
						int index = Integer.parseInt(input) - 1;
						MinedAssertion selected = assertions
								.toArray(new MinedAssertion[assertions.size()])[index];
						assertions.remove(selected);
						mined.addAssertion(selected);
					} else if (input.startsWith("s")) {
						if (skip(mined.getArticle(), SKIP_MINE))
							return;
					}
				} while (!input.isEmpty() && !assertions.isEmpty());
			}
		} catch (Exception e) {
		}
	}

	public void interactiveMap(ConceptModule mappingInput,
			Collection<ConceptModule> mapped, WMISocket wmi,
			OntologySocket ontology) {
		int phase = (mappingInput.getState() == MiningState.UNMAPPED) ? SKIP_MAP
				: SKIP_REVERSE_MAP;
		if (!interactiveMode_ || shouldSkip(mappingInput.getArticle(), phase))
			return;

		try {
			// List the mappings, allowing the user to pick the one to use.
			int i = 1;
			out.println("Mappings for " + mappingInput + ":");
			for (ConceptModule cm : mapped)
				out.println(i++ + ":\t" + cm);

			if (i == 2)
				out.print("Select correct mapping (1, (S)kip): ");
			else
				out.print("Select correct mapping (1-" + (i - 1)
						+ ", (S)kip): ");

			String input = in.readLine().toLowerCase();
			if (StringUtils.isNumeric(input)) {
				int index = Integer.parseInt(input) - 1;
				ConceptModule selected = mapped
						.toArray(new ConceptModule[mapped.size()])[index];
				mapped.remove(selected);
				selected.setState(1.0000001, selected.getState());
				mapped.add(selected);
			} else if (input.startsWith("s")) {
				if (skip(mappingInput.getArticle(), phase))
					return;
			}
		} catch (Exception e) {
		}
	}

	public int interactiveDisambiguation(ConceptModule concept,
			AssertionGrid assertionGrid, OntologySocket ontology) {
		if (!interactiveMode_ || shouldSkip(concept.getArticle(), SKIP_DISAM))
			return 0;

		try {
			// Create interactive selection of disjoint cases
			int i = 0;
			for (i = 0; i < NUM_DISAMBIGUATED; i++) {
				Collection<DefiniteAssertion> assertions = assertionGrid
						.getAssertions(i);
				if (assertions == null)
					break;
				Collection<DefiniteAssertion> isas = new ArrayList<>();
				Collection<DefiniteAssertion> genls = new ArrayList<>();
				for (DefiniteAssertion assertion : assertions) {
					if (assertion.getRelation().equals(
							CycConstants.ISA.getConcept()))
						isas.add(assertion);
					if (assertion.getRelation().equals(
							CycConstants.GENLS.getConcept()))
						genls.add(assertion);
				}
				if (!isas.isEmpty()) {
					out.print("Isa:");
					for (MinedAssertion isa : isas)
						out.print(" " + isa.getArgs()[1]);
					out.println();
				}
				if (!genls.isEmpty()) {
					out.print("Genls:");
					for (MinedAssertion genl : genls)
						out.print(" " + genl.getArgs()[1]);
					out.println();
				}
			}

			if (i == 1)
				out.print("Select correct consistent facts (1, (S)kip): ");
			else
				out.print("Select correct mapping (1-" + i + ", (S)kip): ");

			String input = in.readLine().toLowerCase();
			if (StringUtils.isNumeric(input)) {
				return Integer.parseInt(input) - 1;
			} else if (input.startsWith("s")) {
				if (skip(concept.getArticle(), SKIP_DISAM))
					return 0;
			}
		} catch (Exception e) {
		}
		return 0;
	}

	private boolean shouldSkip(Integer article, int phase) {
		return skip_.containsKey(article)
				&& (skip_.get(article) == phase || skip_.get(article) == 0);
	}

	private boolean skip(int article, int phase) throws IOException {
		out.print("Skip (A)ll or Skip (P)hase? ");
		String input = in.readLine().toLowerCase();
		if (input.startsWith("a")) {
			skip_.put(article, SKIP_ALL);
			return true;
		}
		if (input.startsWith("p")) {
			skip_.put(article, phase);
			return true;
		}
		return false;
	}

	public void interactiveAssertion(ConceptModule concept, WMISocket wmi_,
			OntologySocket ontology_) {
		if (!interactiveMode_)
			return;

		skip_.remove(concept.getArticle());
	}
}
