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
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextToCyc_TextSearch;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.SentenceParserHeuristic;

import org.apache.commons.lang3.StringUtils;

import util.collection.WeightedSet;
import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * Mines the names of the categories this article is a member by treating them
 * as parent collections.
 * 
 * @author Sam Sarjant
 */
public class CategoryMembershipMiner extends WikipediaArticleMiningHeuristic {
	private static final Pattern BIRTH_PATTERN = Pattern
			.compile("(\\d{1,4})s? births?");
	private static final Pattern DEATH_PATTERN = Pattern
			.compile("(\\d{1,4})s? deaths?");
	public static boolean wikifyText_ = true;

	public CategoryMembershipMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
		int artID = info.getArticle();
		String artTitle = wmi.getPageTitle(artID, true);
		Collection<Integer> categories = wmi.getArticleCategories(artID);
		for (Integer category : categories) {
			String categoryTitle = wmi.getPageTitle(category, true);
			// Remove the word 'stub(s)'
			categoryTitle = categoryTitle.replaceAll(" stubs?", "");
			if (categoryTitle.equals(artTitle))
				continue;

			// Special category parsing
			if (parseSpecial(categoryTitle, info, ontology, wmi))
				continue;

			// Check article title similarity
			int result = StringUtils.getLevenshteinDistance(artTitle,
					categoryTitle, 3);
			if (result != -1)
				continue;

			// Treat the category as a chunk of text to be parsed
			String sentence = SentenceParserHeuristic.SENTENCE_PREFIX + categoryTitle + ".";
			miner_.mineSentence(sentence, info, this,
					ontology, wmi);
		}
	}

	/**
	 * Parses the category title using specialised techniques rather than the
	 * NLP parser.
	 * 
	 * @param categoryTitle
	 *            The title of the category.
	 * @param info
	 *            The info to add assertion(s) to.
	 * @param ontology
	 *            The ontology access.
	 * @param wmi
	 *            The WMI access.
	 * @return True if the category is a special category.
	 * @throws IllegalAccessException
	 *             Should something go awry.
	 */
	private boolean parseSpecial(String categoryTitle, MinedInformation info,
			OntologySocket ontology, WMISocket wmi)
			throws IllegalAccessException {
		// Births
		PartialAssertion assertion = createDatedAssertion(categoryTitle, info,
				BIRTH_PATTERN, CycConstants.BIRTH_DATE.getConcept(), ontology,
				wmi);
		if (assertion != null) {
			info.addAssertion(assertion);
			return true;
		}
		// Deaths
		assertion = createDatedAssertion(categoryTitle, info, DEATH_PATTERN,
				CycConstants.DEATH_DATE.getConcept(), ontology, wmi);
		if (assertion != null) {
			info.addAssertion(assertion);
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private PartialAssertion createDatedAssertion(String categoryTitle,
			MinedInformation info, Pattern titlePattern,
			OntologyConcept predicate, OntologySocket ontology, WMISocket wmi)
			throws IllegalAccessException {
		Matcher m = titlePattern.matcher(categoryTitle);
		if (m.matches()) {
			HeuristicProvenance provenance = new HeuristicProvenance(this,
					categoryTitle);
			WeightedSet<OntologyConcept> results = mapper_.mapViaHeuristic(
					m.group(1), TextToCyc_TextSearch.class, wmi, ontology);
			// Should only return 0-1 result
			if (!results.isEmpty())
				return new PartialAssertion(predicate, provenance,
						info.getMappableSelfRef(), results.iterator().next());
		}
		return null;
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.TAXONOMIC.ordinal()] = true;
	}

}
