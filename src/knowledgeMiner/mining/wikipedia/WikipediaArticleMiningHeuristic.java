/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.WeightedStanding;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;
import cyc.AssertionArgument;

/**
 * An abstract class representing a mining technique for extracting new
 * information from an information source. Information can add relations to
 * existing Cyc terms, create new Cyc terms, or simple assist in the mapping
 * process.
 * 
 * @author Sam Sarjant
 */
public abstract class WikipediaArticleMiningHeuristic extends MiningHeuristic {
	public static boolean partitionInformation = true;

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
			info.setModified(true);
			return info;
		}

		// If not precomputed yet, compute it, and split it up if saving
		// precomputed
		try {
			info = new MinedInformation(minedInformation.getArticle());
			mineArticleInternal(info, informationRequested, wmi, cyc);
			if (info != null)
				info.addMinedInfoType(informationRequested);

			// Split the data up and save it
			info = partitionInformation(info, article);
			return info;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Partitions the mined information into separate parts, such that the only
	 * infromation returned is that which concerns the current article. Also, if
	 * performing precomputation, all partitioned information is added to its
	 * respective article.
	 *
	 * @param info
	 *            The information to partition up.
	 * @param article
	 *            The current article to partition to.
	 * @return All information concerning the current article from info. Should
	 *         be all of it, but some cases might split it.
	 * @throws Exception
	 */
	private MinedInformation partitionInformation(MinedInformation info,
			Integer article) throws Exception {
		// No data? No need to partition
		if (!info.isModified() || !partitionInformation)
			return info;

		Map<Integer, MinedInformation> partitions = new HashMap<>();
		// TODO Separate the standing
//		Map<Integer, WeightedStanding> standing = info.getStanding();
//		for (Integer art : standing.keySet()) {
//			MinedInformation artInfo = getInfo(art, partitions);
//			artInfo.addStandingInformation(standing.get(art));
//		}

		// Separate the assertions
		for (PartialAssertion assertion : info.getAssertions()) {
			// Split by each arg
			for (int i = 0; i < assertion.getArgs().length; i++) {
				AssertionArgument aa = assertion.getArgs()[i];
				if (aa instanceof WikipediaMappedConcept) {
					WikipediaMappedConcept wmc = (WikipediaMappedConcept) aa;
					MinedInformation artInfo = getInfo(wmc.getArticle(),
							partitions);
					artInfo.addAssertion(assertion);
				}
			}
		}

		// Add other info to the core article
		MinedInformation coreInfo = partitions.get(article);
		if (coreInfo == null)
			coreInfo = new MinedInformation(article);
		for (DefiniteAssertion concrete : info.getConcreteAssertions())
			coreInfo.addAssertion(concrete);
		coreInfo.setInfoboxTypes(info.getInfoboxTypes());
		coreInfo.addMinedInfoType(info.getMinedInformation());
		// Exit now with the core info if no precomputation
		if (!isPrecomputed())
			return coreInfo;

		// Record mined info for all referenced article
		for (Integer art : partitions.keySet()) {
			MinedInformation artInfo = partitions.get(art);
			// Adding last info
			artInfo.addMinedInfoType(info.getMinedInformation());

			KnowledgeMinerPreprocessor.getInstance().recordData(
					getHeuristicName(), art, artInfo);
		}
		return coreInfo;
	}

	private MinedInformation getInfo(int article,
			Map<Integer, MinedInformation> partitions) {
		MinedInformation info = partitions.get(article);
		if (info == null) {
			// Load up the information, if it exists
			info = (MinedInformation) KnowledgeMinerPreprocessor.getInstance()
					.getLoadHeuristicResult(getHeuristicName(), article);
			if (info == null)
				info = new MinedInformation(article);
			partitions.put(article, info);
		}
		return info;
	}
}
