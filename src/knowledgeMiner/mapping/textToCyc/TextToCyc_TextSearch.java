/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;

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
public class TextToCyc_TextSearch extends MappingHeuristic<String, OntologyConcept> {
	/**
	 * Constructor for a new TextToCyc_TextSearch
	 * 
	 * @param mapper
	 */
	public TextToCyc_TextSearch(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket cyc) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(source);
		// Strip off any anchors
//		Matcher m = null;
		// TODO Fix this up.
		source = WikiParser.cleanAllMarkup(source);
		// if ((m = WikiParser.ANCHOR_PARSER.matcher(source)).find())
		// return new WeightedSet<>(0);

		// Attempt a 1-1 mapping
//		String articleTitle = NLPToSyntaxModule.convertToAscii(source);
		String articleTitle = source;
		Collection<OntologyConcept> results = cyc.findConceptByName(articleTitle,
				false, true, true);
		WeightedSet<OntologyConcept> mappings = new WeightedSet<>(results.size());
		mappings.addAll(results);
		return mappings;
	}
}
