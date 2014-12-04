/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.collection.MultiMap;

/**
 * This utility searches an article for a list of bulleted items and returns the
 * key item of each bullet as a child article, along with any other contextual
 * assertions it can make about the item.
 * 
 * @author Sam Sarjant
 */
public class BulletListParser {
	private static final Pattern BULLET_PATTERN = Pattern
			.compile("(?:\\*+\\s*.+?\\n)+");
	/** The key to use for no context bullet points. */
	public static final String NO_CONTEXT = "NO_CONTEXT";

	/**
	 * Parses the elements of a bulleted list from an article, indexed by their
	 * contextual headings.
	 * 
	 * @param article
	 *            The article to parse.
	 * 
	 */
	public static MultiMap<String, String> parseBulletList(String markup)
			throws Exception {
		MultiMap<String, String> elements = MultiMap.createListMultiMap();

		Matcher m = BULLET_PATTERN.matcher(markup);
		while (m.find()) {
			// Get and check the context
			String contextTitle = WikiParser.backSearchHeader(markup.substring(
					0, m.start()));
			if (contextTitle != null)
				contextTitle = contextTitle.trim();
			else
				contextTitle = NO_CONTEXT;

			// If useful, record the points.
			if (!contextTitle.equalsIgnoreCase("see also")
					&& !contextTitle.equalsIgnoreCase("external links")) {

				// Parse each point out.
				String bulletMarkup = m.group();
				String[] split = bulletMarkup.split("\\n");
				for (String point : split) {
					String niceString = point.replaceFirst("\\*+\\s*", "");
					niceString = WikiParser.cleanupUselessMarkup(niceString);
					niceString = WikiParser
							.cleanupExternalLinksAndStyling(niceString);
					niceString = niceString.trim();
					elements.put(contextTitle, niceString);
				}
			}
		}

		return elements;
	}
}
