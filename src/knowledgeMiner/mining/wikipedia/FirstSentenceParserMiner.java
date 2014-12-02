/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;

import org.slf4j.LoggerFactory;

/**
 * This class uses the Stanford Parser to deconstruct a sentence and infer
 * meaning from it.
 * 
 * @author Sam Sarjant
 */
public class FirstSentenceParserMiner extends WikipediaArticleMiningHeuristic {
	/** If the text should be wikified first. */
	public static boolean wikifyText_ = true;

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
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
		LoggerFactory.getLogger(CycMiner.class).trace(
				"firstSentenceParserMiner: {}", info.getArticle());
		int article = info.getArticle();
		String title = wmi.getPageTitle(article, false);
		// Do not mine lists
		if (title.startsWith(ListMiner.LIST_OF))
			return;
		String firstSentence = wmi.getFirstSentence(article);
		if (firstSentence == null || firstSentence.isEmpty())
			return;
		firstSentence = NLPToSyntaxModule.convertToAscii(firstSentence);

		if (wikifyText_)
			firstSentence = wmi.annotate(firstSentence);

		miner_.mineSentence(firstSentence, info, this, ontology, wmi);
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.TAXONOMIC.ordinal()] = true;
		infoTypes[InformationType.NON_TAXONOMIC.ordinal()] = true;
	}

}
