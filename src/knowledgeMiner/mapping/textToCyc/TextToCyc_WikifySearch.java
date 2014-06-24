/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.regex.Matcher;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.OntologyConcept;

/**
 * A mapping heuristic that wikifies the plain text into a Wikipedia article,
 * then maps the articles to Cyc terms.
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_WikifySearch extends
		MappingHeuristic<String, OntologyConcept> {
	/**
	 * Constructor for a new TextToCyc_WikifySearch
	 * 
	 * @param mapper
	 */
	public TextToCyc_WikifySearch(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(String term,
			WMISocket wmi, OntologySocket cyc) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(term);
		String annotated = wmi.annotate(term);
		Matcher m = WikiParser.ANCHOR_PARSER.matcher(annotated);
		// Return if the term could not be entirely wikified (if at all)
		if (annotated == null || annotated.equals(term) || !m.matches())
			return new WeightedSet<>(0);
		WeightedSet<OntologyConcept> result = new WeightedSet<>();
		result.addAll(mapper_.getVerifiedMappings(
				wmi.getArticleByTitle(m.group(1)), null, wmi, cyc));
		return result;
	}
}
