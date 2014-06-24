/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.wikiToCyc;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * A simple heuristic of matching a Wikipedia article title to a Cyc term name.
 * 
 * @author Sam Sarjant
 */
public class WikiToCyc_TitleMatching extends
		MappingHeuristic<Integer, OntologyConcept> {
	public WikiToCyc_TitleMatching(CycMapper mappingRoot) {
		super(mappingRoot);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(Integer articleID,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		WeightedSet<OntologyConcept> weightedResults = new WeightedSet<>();

		// If the id represents a non-article
		// try {
		// // TODO Too restrictive
		// if (!wmi.getPageType(articleID).equals("article"))
		// return weightedResults;
		// } catch (Exception e) {
		// e.printStackTrace();
		// System.err.println("ID: " + articleID + ", WMI: " + wmi);
		// }

		// Attempt a 1-1 mapping
		String pageTitle = wmi.getPageTitle(articleID, true);
		String pageTitleNoContext = WMISocket.singular(wmi.getPageTitle(false,
				articleID));
		String pageTitleContext = wmi.getPageTitleContext(articleID);
		Collection<OntologyConcept> results = permutateTitle(pageTitle,
				pageTitleNoContext, pageTitleContext, ontology);

		weightedResults.addAll(results);
		LoggerFactory.getLogger(CycMapper.class).trace("W-CTitle: {} {}",
				pageTitle, weightedResults);
		return weightedResults;
	}

	/**
	 * Try different title formats (camelcase, The, no context, etc.)
	 * 
	 * @param pageTitle
	 *            The full article title.
	 * @param pageTitleNoContext
	 *            The title without context.
	 * @param pageTitleContext
	 *            The context of the title.
	 * @param ontology
	 *            The ontology access.
	 * @return The matching article(s)
	 * @throws IOException
	 *             Should something go awry...
	 */
	public Collection<OntologyConcept> permutateTitle(String pageTitle,
			String pageTitleNoContext, String pageTitleContext,
			OntologySocket ontology) throws IOException {
		String articleTitle = pageTitle.toLowerCase();
		Collection<OntologyConcept> results = ontology.findConceptByName(
				articleTitle, false, true, true);

		// Cyc form
		String cycArticle = ontology.toOntologyFormat(articleTitle)
				.toLowerCase();
		if (!cycArticle.equals(articleTitle))
			results.addAll(ontology.findConceptByName(cycArticle, false, true,
					false));

		// Context (only if no results so far)
		if (results.isEmpty() && !pageTitle.equals(pageTitleNoContext)) {
			// If there is a sense, attempt adding a 'the ' before the sense
			// (film -> the film)
			articleTitle = NLPToSyntaxModule.convertToAscii(
					pageTitleNoContext + " (The " + pageTitleContext + ")")
					.toLowerCase();
			results.addAll(ontology.findConceptByName(articleTitle, false,
					true, true));

			cycArticle = ontology.toOntologyFormat(articleTitle).toLowerCase();
			results.addAll(ontology.findConceptByName(cycArticle, false, true,
					false));

			// Try all without context
			if (results.isEmpty())
				results.addAll(permutateTitle(pageTitleNoContext,
						pageTitleNoContext, null, ontology));
		}
		return results;
	}
}
