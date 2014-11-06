/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import java.util.Collection;
import java.util.HashSet;
import java.util.SortedSet;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
import knowledgeMiner.mining.PartialAssertion;
import util.collection.CacheMap;
import util.collection.MultiMap;
import util.collection.WeightedSet;
import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class InfoboxClusterer extends MiningHeuristic {
	/**
	 * A somewhat arbitrary clustering point, where the minimum number of
	 * examples for a parent to make clustering decisions is allowed.
	 */
	public static final int MIN_CLUSTER_COUNT = 30;

	private CacheMap<Integer, InfoboxCounter> counter_;

	/**
	 * Constructor for a new InfoboxClusterer
	 * 
	 * @param mapper
	 * @param miner
	 */
	public InfoboxClusterer(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner);
		counter_ = new CacheMap<>(false);
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.PARENTAGE.ordinal()] = true;
	}

	/**
	 * Clusters articles by using like infobox counts to determine the same
	 * parentage.
	 * 
	 * @param uncreatedChildren
	 *            The potential uncreated children.
	 * @param parentTerm
	 *            The parent term of the children.
	 * @param infoboxCounts
	 *            The counts of infoboxes for the known children.
	 * @return A collection of to-be-mined articles with an initial parentage
	 *         assertion.
	 * @throws IllegalAccessException
	 *             Should something go awry...
	 */
	public Collection<MinedInformation> clusterArticles(
			Collection<MinedInformation> uncreatedChildren,
			OntologyConcept parentTerm, WeightedSet<String> infoboxCounts)
			throws IllegalAccessException {
		Collection<MinedInformation> foundChildren = new HashSet<>();
		// If there were enough common infobox types for child articles, create
		// all others with similar infoboxes.
		if (!infoboxCounts.isEmpty()) {
			Collection<String> majInfoboxes = infoboxCounts.getMostLikely();
			// More non-infoboxes than infoboxes.
			if (majInfoboxes.size() != 1)
				return foundChildren;

			String majorityInfobox = majInfoboxes.iterator().next();
			for (MinedInformation info : uncreatedChildren) {
				// Create all terms with the same majority infobox
				if (majorityInfobox.equals(info.getInfoboxTypes())) {
					// Force assert parent
					info.addAssertion(new PartialAssertion(
							CycConstants.ISA_GENLS.getConcept(), basicProvenance_, info.getMappableSelfRef(),
							parentTerm));
					foundChildren.add(info);
				}
			}
		}

		return foundChildren;
	}

	/**
	 * Records the details of a child term with its infobox, returning true if
	 * the example is positive or it is statistically positive based on common
	 * infobox (per parent).
	 * 
	 * @param concept
	 *            The concept being checked.
	 * @param infobox
	 *            The infobox of the article.
	 * @param parentArticle
	 *            The parent article of the cluster.
	 * @param isPositive
	 *            If this example is a positive child.
	 * @param parentTerm
	 *            The parent term of the concept.
	 * @return If this concept is a valid child.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public synchronized boolean noteInfoboxChild(ConceptModule concept,
			String infobox, int parentArticle, boolean isPositive)
			throws Exception {
		InfoboxCounter counter = counter_.get(parentArticle);
		if (counter == null)
			counter = new InfoboxCounter();
		counter_.put(parentArticle, counter);

		if (isPositive) {
			counter.counts_.add(infobox);
			// Fire any negative examples if threshold met.
			String majorityStr = counter.getMajorityInfobox();
			if (!counter.negativeExamples_.isKeysEmpty() && majorityStr != null) {
				for (ConceptModule cm : counter.negativeExamples_
						.get(majorityStr)) {
					// Fire each negative example
					ConceptModule newChild = new ConceptModule(cm.getArticle());
					newChild.mergeInformation(cm);
					KnowledgeMiner.getInstance().processConcept(
							new ConceptMiningTask(newChild));
				}
				counter.negativeExamples_.clear();
			}
		} else {
			String majorityStr = counter.getMajorityInfobox();
			if (majorityStr == null)
				counter.negativeExamples_.put(infobox, concept);
			else if (majorityStr.equals(infobox))
				return true;
		}
		return isPositive;
	}

	private class InfoboxCounter {
		private static final double VOTING_CONFIDENCE = 0.95;
		private WeightedSet<String> counts_ = new WeightedSet<>();
		private MultiMap<String, ConceptModule> negativeExamples_ = MultiMap
				.createListMultiMap();

		/**
		 * Gets the majority infobox if there are enough examples and the
		 * majority exceeds a threshold.
		 * 
		 * @return The majority infobox if it exceeds a threshold and there are
		 *         enough samples, otherwise null.
		 */
		public String getMajorityInfobox() {
			int sumWeight = counts_.getSumWeight();
			if (sumWeight >= MIN_CLUSTER_COUNT) {
				SortedSet<String> ordered = counts_.getOrdered();
				if (counts_.getWeight(ordered.first()) >= sumWeight
						* VOTING_CONFIDENCE)
					return ordered.first();
			}
			return null;
		}
	}
}
