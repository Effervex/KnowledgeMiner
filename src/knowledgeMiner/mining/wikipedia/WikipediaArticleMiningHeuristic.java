/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import util.collection.MultiMap;

import cyc.AssertionArgument;
import cyc.CycConstants;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

/**
 * An abstract class representing a mining technique for extracting new
 * information from an information source. Information can add relations to
 * existing Cyc terms, create new Cyc terms, or simple assist in the mapping
 * process.
 * 
 * @author Sam Sarjant
 */
public abstract class WikipediaArticleMiningHeuristic extends MiningHeuristic {

	/**
	 * Constructor for a new WikipediaArticleMiningHeuristic
	 * 
	 * @param mapper
	 *            The mapper.
	 * @param miner
	 *            The miner
	 */
	public WikipediaArticleMiningHeuristic(boolean usePrecomputed,
			CycMapper mapper, CycMiner miner) {
		super(usePrecomputed, mapper, miner);
	}

	/**
	 * The actual mining method. This method extracts and processes information
	 * from an article which is accessible through get methods.
	 * 
	 * @param info
	 *            The mined information to add to (contains skeletal
	 *            information).
	 * @param informationRequested
	 *            The information requested of this heuristic (bitwise).
	 * @param wmi
	 *            The WMI access point.
	 * @param ontology
	 *            The ontology access.
	 * @throws IOException
	 *             Should something go awry...
	 */
	protected abstract void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception;

	public static WikipediaMappedConcept createSelfRefConcept(Object minedObject) {
		return new WikipediaMappedConcept((int) minedObject);
	}

	/**
	 * An alternative accessor for mining Wikipedia articles. Not recommended.
	 * Primarily for tests and debugging.
	 * 
	 * @param article
	 *            The article being mined.
	 * @param informationRequested
	 *            The information requested for this mining operation.
	 * @param wmi
	 *            The WMI access.
	 * @param cyc
	 *            The Cyc access.
	 * @return The information mined from the article.
	 */
	public final MinedInformation mineArticle(int article,
			int informationRequested, WMISocket wmi, OntologySocket cyc) {
		return mineArticle(new ConceptModule(article), informationRequested,
				wmi, cyc);
	}

	@Override
	public final MinedInformation mineArticle(ConceptModule minedInformation,
			int informationRequested, WMISocket wmi, OntologySocket cyc) {
		if (super.mineArticle(minedInformation, informationRequested, wmi, cyc) == null)
			return null;

		// No null articles allowed!
		Integer article = minedInformation.getArticle();
		if (article == null || article == -1)
			return null;

		// Get precomputed info.
		MinedInformation info = (MinedInformation) KnowledgeMiner.getInstance()
				.getHeuristicResult(article, this);
		if (info != null
				&& (informationRequested & info.getMinedInformation()) == informationRequested) {
			// System.out.println(getHeuristicName() + " (Pre): "
			// + info.getAssertions());
			return info;
		}

		try {
			info = new MinedInformation(minedInformation.getArticle());
			mineArticleInternal(info, informationRequested, wmi, cyc);
			if (info != null)
				info.addMinedInfoType(informationRequested);
			// Split child mined data into separate records
			if (getInfoTypeWeight(InformationType.CHILD_ARTICLES) != 0)
				splitChildMinedData(info);

			// Record the data
			// System.out
			// .println(getHeuristicName() + " (Mined): " +
			// info.getAssertions());
			if (isPrecomputed())
				KnowledgeMinerPreprocessor.getInstance().recordData(
						getHeuristicName(), article, info);
			return info;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Splits mined data containing child-mined information into different info
	 * chunks related to the child articles, rather than the parent.
	 * 
	 * @param info
	 *            The info that was mined.
	 */
	private void splitChildMinedData(MinedInformation info) {
		Collection<PartialAssertion> assertions = info.getAssertions();
		Iterator<PartialAssertion> iter = assertions.iterator();
		MultiMap<AssertionArgument, PartialAssertion> childMap = MultiMap
				.createListMultiMap();
		while (iter.hasNext()) {
			PartialAssertion assertion = iter.next();
			AssertionArgument[] args = assertion.getArgs();
			if (assertion.getRelation().equals(
					CycConstants.ISA_GENLS.getConcept())
					&& !args[0].equals(info.getMappableSelfRef())) {
				// Add to child collection
				childMap.put(args[0], assertion);
				iter.remove();
			}
		}

		// For every child map, load up the child results and add them
		for (AssertionArgument child : childMap.keySet()) {
			// Get the child mined information
			int childArt = ((WikipediaMappedConcept) child).getArticle();
			MinedInformation childInfo = (MinedInformation) KnowledgeMiner
					.getInstance().getHeuristicResult(childArt, this);
			if (childInfo == null)
				childInfo = new MinedInformation(childArt);

			// Add the child information to the info
			for (PartialAssertion pa : childMap.get(child))
				childInfo.addAssertion(pa);

			// Record it
			if (isPrecomputed())
				KnowledgeMinerPreprocessor.getInstance().recordData(
						getHeuristicName(), childArt, childInfo);
		}
	}
}
