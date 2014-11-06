/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.regex.Matcher;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import util.wikipedia.WikiParser;
import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_AnchorMap extends
		MappingHeuristic<String, OntologyConcept> {
	/**
	 * Constructor for a new AnchorMap mapper
	 * 
	 * @param mapper
	 */
	public TextToCyc_AnchorMap(CycMapper mapper) {
		super(mapper);
	}

	@Override
	public WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket cyc) throws Exception {
		LoggerFactory.getLogger(getClass()).trace(source);
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		if (source == null || source.isEmpty())
			return results;

		Matcher m = WikiParser.ANCHOR_PARSER_ROUGH.matcher(source);
		if (m.matches()) {
			String anchorText = m.group(1);
			// Replace newlines
			anchorText = anchorText.replaceAll(" ?\\n ?", " ");
			int articleID = wmi.getArticleByTitle(anchorText);
			if (articleID != -1) {
				WikipediaMappedConcept wikiMapped = new WikipediaMappedConcept(
						articleID);
				results.addAll(wikiMapped.mapThing(mapper_, wmi, cyc));
			}
		}
		return results;
	}
}
