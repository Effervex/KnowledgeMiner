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
import io.ontology.OntologySocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;

import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.MinedAssertion;
import util.Pair;
import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * An inner class for encapsulating the assertion grid and the various metrics
 * associated with the grid.
 * 
 * @author Sam Sarjant
 */
public class AssertionGrid {

	/** The assertions in a grid format for quick access. */
	private MinedAssertion[][] assertionGrid_;

	/** The current queue of disjoint cases. */
	private PriorityQueue<DisjointCase> cases_;

	/** The current column to seed. */
	private int column_;

	private OntologyConcept concept_;

	/** The proportions of every assertion queue, based on hierarchy. */
	private Double[] proportionVector_;

	/** The current row to seed. */
	private int row_;

	/** A stack of starting seeds, from highest weighted to least. */
	private Pair<Integer, Integer>[] seedStack_;

	/** The starting seed assertions that have been used. */
	private boolean[][] usedSeeds_;

	/** The weights of the assertions in a grid format for quick access. */
	private double[][] weightGrid_;

	/** Temporary members for non-assertion-removal code. */
	private Collection<OntologyConcept> conceptIsaTruths_;
	private Collection<OntologyConcept> conceptGenlTruths_;

	private DisjointCase[] disjointCases_;

	/**
	 * Constructor for a new AssertionGrid extending an existing one.
	 * 
	 * @param existingGrid
	 *            The existing grid to add to.
	 * @param existingAssertions
	 *            The assertions to add to the grid.
	 */
	public AssertionGrid(AssertionGrid existingGrid, OntologyConcept concept,
			Collection<MinedAssertion> existingAssertions,
			boolean assertionRemoval) {
		if (assertionRemoval) {
			int oldLength = existingGrid.assertionGrid_.length;
			int newLength = oldLength + existingAssertions.size();
			assertionGrid_ = Arrays.copyOf(existingGrid.assertionGrid_,
					newLength);
			proportionVector_ = Arrays.copyOf(existingGrid.proportionVector_,
					newLength);
			usedSeeds_ = new boolean[newLength][];
			for (int x = 0; x < oldLength; x++)
				usedSeeds_[x] = new boolean[existingGrid.usedSeeds_[x].length];
			weightGrid_ = Arrays.copyOf(existingGrid.weightGrid_, newLength);
			double proportion = 1.0 / existingAssertions.size();
			Iterator<MinedAssertion> iter = existingAssertions.iterator();
			for (int i = oldLength; i < newLength; i++) {
				assertionGrid_[i] = new MinedAssertion[] { iter.next() };
				proportionVector_[i] = proportion;
				usedSeeds_[i] = new boolean[] { true };
				weightGrid_[i] = new double[] { 1 };
			}
			conceptIsaTruths_ = null;
			conceptGenlTruths_ = null;
		} else {
			// Non-assertion-removal
			assertionGrid_ = existingGrid.assertionGrid_;
			proportionVector_ = existingGrid.proportionVector_;
			usedSeeds_ = new boolean[existingGrid.usedSeeds_.length][];
			for (int x = 0; x < usedSeeds_.length; x++)
				usedSeeds_[x] = new boolean[existingGrid.usedSeeds_[x].length];
			weightGrid_ = existingGrid.weightGrid_;
			conceptIsaTruths_ = new ArrayList<>();
			conceptGenlTruths_ = new ArrayList<>();
			for (MinedAssertion ma : existingAssertions) {
				if (ma.getRelation().equals(CycConstants.ISA.getConcept()))
					conceptIsaTruths_.add(ma.getArgs()[1]);
				else if (ma.getRelation().equals(
						CycConstants.GENLS.getConcept()))
					conceptGenlTruths_.add(ma.getArgs()[1]);
			}
		}

		seedStack_ = existingGrid.seedStack_;
		concept_ = concept;
		resetMetrics();
	}

	public AssertionGrid(SortedSet<AssertionQueue> assertions) {
		buildAssertionGrid(assertions);
		concept_ = OntologyConcept.PLACEHOLDER;
		resetMetrics();
	}

	/**
	 * Builds a 2d array of assertions (and an identical array of weights), so
	 * they can be approached in a easy-to-access and principled manner.
	 * 
	 * @param assertions
	 *            The assertions to build into a 2D array.
	 */
	@SuppressWarnings("unchecked")
	private void buildAssertionGrid(SortedSet<AssertionQueue> assertions) {
		ArrayList<MinedAssertion[]> assertionGrid = new ArrayList<>();
		ArrayList<double[]> weightGrid = new ArrayList<>();
		ArrayList<Double> proportion = new ArrayList<>();
		ArrayList<Pair<Integer, Integer>> coords = new ArrayList<>();
		// Iterate through
		for (AssertionQueue aq : assertions)
			recurseBuild(aq, 0, assertionGrid, weightGrid, proportion,
					1f / assertions.size(), coords);

		assertionGrid_ = assertionGrid.toArray(new MinedAssertion[assertionGrid
				.size()][]);
		weightGrid_ = weightGrid.toArray(new double[weightGrid.size()][]);
		usedSeeds_ = new boolean[weightGrid_.length][];
		for (int x = 0; x < weightGrid_.length; x++) {
			usedSeeds_[x] = new boolean[weightGrid_[x].length];
		}
		proportionVector_ = proportion.toArray(new Double[proportion.size()]);
		seedStack_ = coords.toArray(new Pair[coords.size()]);
		Arrays.sort(seedStack_, new Comparator<Pair<Integer, Integer>>() {
			@Override
			public int compare(Pair<Integer, Integer> arg0,
					Pair<Integer, Integer> arg1) {
				// Weight first
				int result = -Double.compare(
						weightGrid_[arg0.objA_][arg0.objB_],
						weightGrid_[arg1.objA_][arg1.objB_]);
				if (result != 0)
					return result;

				// Then row
				result = Integer.compare(arg0.objB_, arg1.objB_);
				if (result != 0)
					return result;

				// Then column
				result = Integer.compare(arg0.objA_, arg1.objA_);
				if (result != 0)
					return result;
				return 0;
			}
		});
	}

	private double findAvailableSeed() {
		int i = 0;
		while (usedSeeds_[seedStack_[i].objA_][seedStack_[i].objB_]) {
			i++;
			if (i >= seedStack_.length)
				return -1;
		}
		row_ = seedStack_[i].objB_;
		column_ = seedStack_[i].objA_;
		return weightGrid_[column_][row_];
	}

	private MinedAssertion getAssertion(int x, int y) {
		// Check for ArrayIndexException
		if (x >= assertionGrid_.length)
			return null;
		if (y >= assertionGrid_[x].length)
			return null;
		return assertionGrid_[x][y];
	}

	private double getWeight(int x, int y) {
		if (x >= weightGrid_.length)
			return 0;
		if (y >= weightGrid_[x].length)
			return 0;
		return weightGrid_[x][y];
	}

	private boolean isDisjoint(OntologyConcept testCollection,
			Collection<OntologyConcept> truths, OntologySocket ontology) {
		for (OntologyConcept truth : truths) {
			// Is disjoint?
			if (ontology.evaluate(null, CommonConcepts.DISJOINTWITH.getID(),
					testCollection.getIdentifier(), truth.getIdentifier())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Recursively build the grid
	 * 
	 * @param aq
	 *            The assertion queue to recurse through (lower queues).
	 * @param offset
	 *            The offset (for lower queues)
	 * @param assertionGrid
	 *            The assertion grid to add to.
	 * @param weightGrid
	 *            The weight grid to add to.
	 * @param proportion
	 *            The proportions to learn for calculating total weight.
	 * @param fraction
	 *            The value to use for proportions.
	 * @param coords
	 *            The coord locations of elements in the grid.
	 */
	private void recurseBuild(AssertionQueue aq, int offset,
			ArrayList<MinedAssertion[]> assertionGrid,
			ArrayList<double[]> weightGrid, ArrayList<Double> proportion,
			double fraction, ArrayList<Pair<Integer, Integer>> coords) {
		int size = aq.size();
		// Add the current AQ
		int x = assertionGrid.size();
		if (size > 0) {
			MinedAssertion[] assertions = new MinedAssertion[size + offset];
			double[] weights = new double[size + offset];
			int i = offset;
			for (MinedAssertion ma : aq) {
				assertions[i] = ma;
				weights[i] = aq.getWeight(ma);
				coords.add(new Pair<Integer, Integer>(x, i));
				i++;
			}
			assertionGrid.add(assertions);
			weightGrid.add(weights);
			proportion.add(fraction);
		}

		// Recurse to lower AQs, offsetting
		Set<AssertionQueue> subAssertionQueues = aq.getSubAssertionQueues();
		for (AssertionQueue subAQ : subAssertionQueues)
			recurseBuild(subAQ, size + offset, assertionGrid, weightGrid,
					proportion, fraction / subAssertionQueues.size(), coords);
	}

	private DisjointCase requestDisjointCase(boolean isaCollection,
			OntologySocket ontology) {
		// Identify weight of next row/column case
		double seedWeight = findAvailableSeed();
		if (seedWeight == -1) {
			if (cases_.isEmpty())
				return null;
			else
				return cases_.remove();
		}

		// If greater than top priority case, use that
		DisjointCase resultDC = null;
		if (cases_.isEmpty() || seedWeight > cases_.peek().getPotentialWeight()) {
			resultDC = new DisjointCase(row_, column_, isaCollection, ontology);
			usedSeeds_[column_][row_] = true;
			if (resultDC.getPotentialWeight() < 0)
				resultDC = requestDisjointCase(isaCollection, ontology);
		}

		if (resultDC == null && !cases_.isEmpty())
			return cases_.remove();

		return resultDC;
	}

	private void resetMetrics() {
		row_ = 0;
		column_ = 0;
		cases_ = new PriorityQueue<>();
		disjointCases_ = null;
	}

	public Collection<MinedAssertion> findMaximalConjoint(
			boolean isaCollection, OntologySocket ontology) {
		findNConjoint(1, isaCollection, ontology);
		DisjointnessDisambiguator.logger_.debug(
				"Disambiguated {} with weight {}", concept_,
				Math.min(1.0, disjointCases_[0].getPotentialWeight()));
		return disjointCases_[0].getAssertions();
	}

	public void findNConjoint(int numDisambiguated, boolean isaCollection,
			OntologySocket ontology) {
		// Iterate through the assertions in a priority queue until completed
		resetMetrics();
		disjointCases_ = new DisjointCase[numDisambiguated];
		int caseNum = 0;
		do {
			DisjointCase dc = requestDisjointCase(isaCollection, ontology);
			// No assertions left!
			if (dc == null)
				return;
			// DC not yet completed.
			if (!dc.isCompleted()) {
				dc.processRow(isaCollection, ontology);
				if (dc.isCompleted())
					disjointCases_[caseNum++] = dc;
				else
					cases_.add(dc);
			}
		} while (caseNum < numDisambiguated);
	}

	private class DisjointCase implements Comparable<DisjointCase> {
		private ArrayList<MinedAssertion> allAssertions_;
		private ArrayList<Double> assertionWeights_;
		private int caseRow_;
		private boolean[] completed_;
		private double completedWeight_;
		private Collection<OntologyConcept> genlsTruth_;
		private Collection<OntologyConcept> isaTruth_;

		/**
		 * Constructor for a new DisjointCase which progressively identifies
		 * consistent assertions.
		 * 
		 * @param row
		 *            The row to start with.
		 * @param column
		 *            The column to start with.
		 * @param isaCollection
		 *            If the concept is a collection.
		 * @param ontology
		 *            The ontology access.
		 * @param isaTruth
		 *            The isa truths.
		 * @param genlTruth
		 *            The genls truths.
		 */
		public DisjointCase(int row, int column, boolean isaCollection,
				OntologySocket ontology) {
			completed_ = new boolean[assertionGrid_.length];
			Arrays.fill(completed_, false);
			caseRow_ = 0;
			isaTruth_ = new HashSet<>();
			if (conceptIsaTruths_ != null)
				isaTruth_.addAll(conceptIsaTruths_);
			genlsTruth_ = new HashSet<>();
			if (conceptGenlTruths_ != null)
				genlsTruth_.addAll(conceptGenlTruths_);
			allAssertions_ = new ArrayList<>();
			assertionWeights_ = new ArrayList<>();
			completedWeight_ = 0;
			checkDisjointness(getAssertion(column, row), column, row,
					isaCollection, ontology);
			// If seed assertion is invalid
			if (completedWeight_ == 0)
				completedWeight_ = -1;

		}

		private double calculatePotentialWeight() {
			double weight = 0;
			for (int x = 0; x < completed_.length; x++) {
				if (!completed_[x] && getWeight(x, caseRow_) > 0)
					weight += getWeight(x, caseRow_) * proportionVector_[x];
			}
			return weight;
		}

		/**
		 * Checks if arg constraints can be created or if the true information
		 * is disjoint with them.
		 * 
		 * @param assertion
		 *            The assertion to check constraints for.
		 * @param ontology
		 *            The ontology access.
		 * @return True if the constraints can be satisfied.
		 */
		private boolean checkArgConstraints(MinedAssertion assertion,
				OntologySocket ontology) {
			// Check disjointness of constraints vs truth
			int placeholderIndex = assertion.getPlaceholderArgIndex() + 1;
			Collection<OntologyConcept> isaConstraints = ontology.quickQuery(
					CommonQuery.MINARGNISA, assertion.getRelation()
							.getIdentifier() + " '" + placeholderIndex);
			for (OntologyConcept constraint : isaConstraints)
				if (isDisjoint(constraint, isaTruth_, ontology))
					return false;

			Collection<OntologyConcept> genlsConstraints = ontology.quickQuery(
					CommonQuery.MINARGNGENL, assertion.getRelation()
							.getIdentifier() + " '" + placeholderIndex);
			for (OntologyConcept constraint : genlsConstraints)
				if (isDisjoint(constraint, genlsTruth_, ontology))
					return false;

			// Add as truth & assertions
			try {
				isaTruth_.addAll(isaConstraints);
				for (OntologyConcept constraint : isaConstraints)
					if (!constraint.getConceptName().equals("Thing")) {
						MinedAssertion ma = new MinedAssertion(
								CycConstants.ISA.getConcept(), concept_,
								constraint,
								CycConstants.DATA_MICROTHEORY.getConceptName(),
								null);
						if (!allAssertions_.contains(ma))
							allAssertions_.add(ma);
					}

				genlsTruth_.addAll(genlsConstraints);
				for (OntologyConcept constraint : genlsConstraints)
					if (!constraint.getConceptName().equals("Thing")) {
						MinedAssertion ma = new MinedAssertion(
								CycConstants.GENLS.getConcept(), concept_,
								constraint,
								CycConstants.DATA_MICROTHEORY.getConceptName(),
								null);
						if (!allAssertions_.contains(ma))
							allAssertions_.add(ma);
					}
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		/**
		 * Checks if an assertion is consistent with existing information about
		 * a concept and adds it if it is.
		 * 
		 * @param assertion
		 *            The assertion to check.
		 * @param x
		 *            The x pos of the assertion in the grid.
		 * @param y
		 *            The y pos of the assertion in the grid.
		 * @param isaCollection
		 *            If the concept is a collection.
		 * @param ontology
		 *            The ontology access.
		 */
		private void checkDisjointness(MinedAssertion assertion, int x, int y,
				boolean isaCollection, OntologySocket ontology) {
			assertion = assertion.clone();
			if (assertion.isHierarchical()) {
				// Check collection first
				boolean asserted = false;
				if (isaCollection
						&& !isDisjoint(assertion.getArgs()[1], genlsTruth_,
								ontology)) {
					assertion.makeParentageAssertion(TermStanding.COLLECTION);
					genlsTruth_.add(assertion.getArgs()[1]);
					recordAssertion(assertion, x, y, isaCollection, ontology);
					asserted = true;
				}

				// Check isa relationship as a backup.
				if (!asserted
						&& !isDisjoint(assertion.getArgs()[1], isaTruth_,
								ontology)) {
					assertion.makeParentageAssertion(TermStanding.INDIVIDUAL);
					isaTruth_.add(assertion.getArgs()[1]);
					recordAssertion(assertion, x, y, isaCollection, ontology);
				}
			} else if (checkArgConstraints(assertion, ontology)) {
				recordAssertion(assertion, x, y, isaCollection, ontology);
			}
		}

		private void noteCompleted(int x, int y) {
			usedSeeds_[x][y] = true;
			if (!completed_[x]) {
				completed_[x] = true;
				completedWeight_ += getWeight(x, y) * proportionVector_[x];

				// Also mark any child collections
				int originalX = x;
				x++;
				while (x < assertionGrid_.length
						&& y < assertionGrid_[x].length
						&& assertionGrid_[x][y] == null) {
					completed_[x] = true;
					// If parent becomes used, so do all children
					Arrays.fill(usedSeeds_[x], true);
					x++;
				}

				// Mark parent collections as completed to disallow potential
				if (y > 0) {
					x = originalX;
					int nullY = y;
					// Find the null point
					while (assertionGrid_[x][nullY] != null) {
						if (nullY == 0)
							return;
						nullY--;
					}

					// Move backwards until all parents found
					while (x > 0 && assertionGrid_[x][0] == null) {
						// If value at nullY is not null (or out of bounds),
						// mark completed.
						x--;
						if (assertionGrid_[x].length <= nullY
								|| assertionGrid_[x][nullY] != null)
							completed_[x] = true;
					}
				}
			}
		}

		/**
		 * Records the assertion and checks for other assertions in the same
		 * queue with the same weight that can also be recorded.
		 * 
		 * @param assertion
		 *            The assertion to check.
		 * @param x
		 *            The x pos of the assertion in the grid.
		 * @param y
		 *            The y pos of the assertion in the grid.
		 * @param isaCollection
		 *            If the concept is a collection.
		 * @param ontology
		 *            The ontology access.
		 */
		private void recordAssertion(MinedAssertion assertion, int x, int y,
				boolean isaCollection, OntologySocket ontology) {
			assertion.replacePlaceholder(concept_, ontology);
			noteCompleted(x, y);
			if (!allAssertions_.contains(assertion)) {
				allAssertions_.add(assertion);
				assertionWeights_.add(getWeight(x, y));
			}

			// Recurse down
			if (getWeight(x, y + 1) == getWeight(x, y))
				checkDisjointness(getAssertion(x, y + 1), x, y + 1,
						isaCollection, ontology);
		}

		@Override
		public int compareTo(DisjointCase arg0) {
			if (arg0 == null)
				return -1;
			if (equals(arg0))
				return 0;

			// Greater potential weight first
			int result = -Double.compare(getPotentialWeight(),
					arg0.getPotentialWeight());
			if (result != 0)
				return result;

			// Then closer to completion
			int numCompleted = 0;
			int numCompletedA = 0;
			for (int x = 0; x < completed_.length; x++) {
				numCompleted += completed_[x] ? 1 : 0;
				numCompletedA += arg0.completed_[x] ? 1 : 0;
			}
			result = -Integer.compare(numCompleted, numCompletedA);
			if (result != 0)
				return result;

			// Then higher row
			result = Integer.compare(caseRow_, arg0.caseRow_);
			if (result != 0)
				return result;

			// Otherwise by hash
			return Integer.compare(hashCode(), arg0.hashCode());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DisjointCase other = (DisjointCase) obj;
			if (caseRow_ != other.caseRow_)
				return false;
			if (!Arrays.equals(completed_, other.completed_))
				return false;
			return true;
		}

		public ArrayList<MinedAssertion> getAssertions() {
			return allAssertions_;
		}

		/**
		 * Returns the most optimistic possible weight for the case.
		 * 
		 * @return The weight of the case.
		 */
		public double getPotentialWeight() {
			double value = completedWeight_ + calculatePotentialWeight();
			// Adjust the concept weight to ignore the existing assertions.
			if (conceptIsaTruths_ == null && conceptGenlTruths_ == null)
				value -= 1;
			return value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + caseRow_;
			result = prime * result + Arrays.hashCode(completed_);
			return result;
		}

		public boolean isCompleted() {
			if (caseRow_ == 0)
				return false;

			for (int x = 0; x < completed_.length; x++) {
				if (!completed_[x] && getAssertion(x, caseRow_ - 1) != null)
					return false;
			}
			return true;
		}

		/**
		 * Process a row of the assertion grid, checking disjointness of the
		 * assertions encountered in the row.
		 * 
		 * @param isaCollection
		 *            If the concept is a collection.
		 * @param ontology
		 *            The ontology access.
		 */
		public void processRow(boolean isaCollection, OntologySocket ontology) {
			// Run through the row, pairing with other assertions and checking
			// conjointness
			for (int x = 0; x < completed_.length; x++) {
				if (completed_[x])
					continue;
				MinedAssertion assertion = getAssertion(x, caseRow_);
				if (assertion == null)
					continue;
				checkDisjointness(assertion, x, caseRow_, isaCollection,
						ontology);
			}
			caseRow_++;
		}

		@Override
		public String toString() {
			return allAssertions_.toString();
		}
	}

	public double getCaseWeight(int caseNum) {
		return Math.min(disjointCases_[caseNum].getPotentialWeight(), 1);
	}

	public Collection<MinedAssertion> getAssertions(int caseNum) {
		return disjointCases_[caseNum].getAssertions();
	}
}
