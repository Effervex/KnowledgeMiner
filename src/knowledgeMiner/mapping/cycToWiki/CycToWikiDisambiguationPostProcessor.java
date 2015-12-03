/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.cycToWiki;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.util.regex.Matcher;

import knowledgeMiner.mapping.MappingPostProcessor;
import util.collection.MultiMap;
import util.collection.WeightedSet;
import util.wikipedia.BulletListParser;
import util.wikipedia.WikiParser;

/**
 * This post processor explodes disambiguation pages into their links, creating
 * many more mappings wiht the same weight.
 * 
 * @author Sam Sarjant
 */
public class CycToWikiDisambiguationPostProcessor extends
		MappingPostProcessor<Integer> {
	@Override
	public WeightedSet<Integer> process(WeightedSet<Integer> collection,
			WikipediaSocket wmi, OntologySocket cyc) {
		if (collection.isEmpty())
			return collection;

		// Explode the disambiguation page (via bullet points)
		WeightedSet<Integer> newSet = new WeightedSet<>();
		try {
			Integer[] pages = collection
					.toArray(new Integer[collection.size()]);
			int index = 0;
			// Get the page type for every result
			for (String pageType : wmi.getPageType(pages)) {
				if (pageType == null)
					continue;
				int artID = pages[index];
				double weight = collection.getWeight(artID);
				// If a disambiguation article
				if (pageType.equals(WikipediaSocket.TYPE_DISAMBIGUATION)) {
					// Use first links in bullet points.
					MultiMap<String, String> bullets = BulletListParser
							.parseBulletList(wmi.getMarkup(artID));
					// Parse every bullet point.
					for (String bullet : bullets.values()) {
						Matcher m = WikiParser.ANCHOR_PARSER.matcher(bullet);
						if (m.find()) {
							int disamArt = wmi.getArticleByTitle(m.group(1));
							if (disamArt == -1)
								continue;
							double thisWeight = weight;
							if (newSet.contains(disamArt))
								thisWeight = Math.max(
										newSet.getWeight(disamArt), thisWeight);
							newSet.set(disamArt, thisWeight);
						}
					}
				} else {
					// Otherwise, just keep the article
					double thisWeight = weight;
					if (newSet.contains(artID))
						thisWeight = Math.max(newSet.getWeight(artID),
								thisWeight);
					newSet.set(artID, thisWeight);
				}
				index++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newSet;
	}
}
