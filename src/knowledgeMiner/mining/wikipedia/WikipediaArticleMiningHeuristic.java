/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.MiningHeuristic;
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
	public WikipediaArticleMiningHeuristic(CycMapper mapper, CycMiner miner) {
		super(mapper, miner);
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
				.getHeuristicResult(article, getHeuristicName());
		if (info != null)
			return info;

		try {
			info = new ConceptModule(minedInformation.getConcept(),
					minedInformation.getArticle(), weight_, true);
			mineArticleInternal(info, informationRequested, wmi, cyc);
			if (info != null)
				info.addMinedInfoType(informationRequested);
			KnowledgeMinerPreprocessor.getInstance().recordData(
					getHeuristicName(), article, info);
			return info;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
