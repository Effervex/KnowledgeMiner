/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;

import org.slf4j.LoggerFactory;

import util.wikipedia.WikiParser;

/**
 * This class uses the Stanford Parser to deconstruct a sentence and infer
 * meaning from it.
 * 
 * @author Sam Sarjant
 */
public class FirstSentenceParserMiner extends WikipediaArticleMiningHeuristic {
	/**
	 * Constructor for a new FirstSentenceParserMiner
	 * 
	 * @param mapper
	 *            The Cyc mapping access.
	 */
	public FirstSentenceParserMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WikipediaSocket wmi, OntologySocket ontology)
			throws Exception {
		LoggerFactory.getLogger(CycMiner.class).trace(
				"firstSentenceParserMiner: {}", info.getArticle());
		int article = info.getArticle();
		String title = wmi.getArtTitle(article, false);
		// Do not mine lists
		if (WikiParser.isAListOf(title))
			return;
		String firstSentence = wmi.getFirstSentence(article);
		if (firstSentence == null || firstSentence.isEmpty())
			return;
//		firstSentence = NLPToSyntaxModule.convertToAscii(firstSentence);

		miner_.mineSentence(firstSentence, true, info, this, ontology, wmi);
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.TAXONOMIC.ordinal()] = true;
		infoTypes[InformationType.NON_TAXONOMIC.ordinal()] = true;
	}

}
