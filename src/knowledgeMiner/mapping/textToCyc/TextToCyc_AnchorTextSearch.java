/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.util.Collection;
import java.util.regex.Matcher;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.OntologyConcept;

/**
 * A mapping heuristic for mapping plain text to Cyc terms simply by searching
 * Cyc.
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_AnchorTextSearch extends
		MappingHeuristic<String, OntologyConcept> {
	/**
	 * Constructor for a new TextToCyc_TextSearch
	 * 
	 * @param mapper
	 */
	public TextToCyc_AnchorTextSearch(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WikipediaSocket wmi, OntologySocket cyc) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(source);
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		if (source == null || source.isEmpty())
			return results;

		Matcher m = WikiParser.ANCHOR_PARSER_ROUGH.matcher(source);
		if (m.matches()) {
			String anchorText = (m.group(2) == null) ? m.group(1) : m.group(2);

			// Attempt a 1-1 mapping
//			String articleTitle = NLPToSyntaxModule.convertToAscii(anchorText);
			String articleTitle = anchorText;
			Collection<OntologyConcept> concepts = cyc.findConceptByName(
					articleTitle, false, true, true);
			results.addAll(concepts);
		}
		return results;
	}
}
