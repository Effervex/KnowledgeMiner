/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import util.wikipedia.WikiParser;
import cyc.CycConstants;
import cyc.StringConcept;

/**
 * A very simple mining heuristic that parses the title of a Wikipedia article.
 * Produces standing and relations.
 * 
 * @author Sam Sarjant
 */
public class TitleMiner extends WikipediaArticleMiningHeuristic {
	/**
	 * Constructor for a new TitleMiner
	 * 
	 * @param miner
	 */
	public TitleMiner(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner);
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		informationProduced[InformationType.RELATIONS.ordinal()] = true;
		informationProduced[InformationType.STANDING.ordinal()] = true;
		informationProduced[InformationType.PARENTAGE.ordinal()] = true;
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws Exception {
		int article = info.getArticle();
		String title = wmi.getPageTitle(article, false).trim();
		if (WikiParser.isAListOf(title))
			return;

		// Assert the title as a canonical synonym.
		if (informationRequested(informationRequested,
				InformationType.RELATIONS))
			info.addAssertion(new PartialAssertion(
					CycConstants.SYNONYM_RELATION_CANONICAL.getConcept(),
					basicProvenance_, info.getMappableSelfRef(),
					new StringConcept(NLPToSyntaxModule.convertToAscii(title))));

		if (informationRequested(informationRequested, InformationType.STANDING)) {
			// Remove commas and text after commas (Moscow, Russia ->
			// Moscow)
			int index = title.indexOf(',');
			String contextFree = title;
			if (index != -1)
				contextFree = title.substring(0, index);

			// If the article title has at least two words, determine
			// standing by the capitalisation of the last word
			contextFree.trim();
			String[] split = contextFree.split(" ");
			if (split.length > 1) {
				if (Character.isUpperCase(split[split.length - 1].charAt(0))) {
					info.addStandingInformation(TermStanding.INDIVIDUAL,
							getWeight(), basicProvenance_);
				} else if (Character.isLowerCase(split[split.length - 1]
						.charAt(0))) {
					info.addStandingInformation(TermStanding.COLLECTION,
							getWeight(), basicProvenance_);
				}
			}
		}

		// Perform parentage check via title.
		// TODO Can do this, but needs weighting.
		// if (informationRequested(informationRequested,
		// InformationType.PARENTAGE)) {
		// CycConcept parentTerm = info.getParentTerm();
		// if (parentTerm != null
		// && UtilityMethods.findSubString(parentTerm.getPlainName(),
		// title, 0))
		// info.addAssertion(createParentAssertion(term, parentTerm));
		// }
	}

}
