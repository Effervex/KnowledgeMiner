package tools;

import graph.module.NLPToSyntaxModule;
import io.ResourceAccess;
import io.resources.WikipediaSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.WordUtils;

import util.wikipedia.WikiParser;

public class ExtractArticleText {
	public static final File EXTRACTION_FOLDER = new File("extractedArticles");
	private static final String[] REMOVABLE_SECTIONS = { "External links",
			"See also", "History", "References" };
	private WikipediaSocket wmi_;

	public ExtractArticleText() {
		ResourceAccess.newInstance();
		wmi_ = ResourceAccess.requestWikipediaSocket();

		EXTRACTION_FOLDER.mkdir();
	}

	public static void main(String[] args) {
		String page = null;
		boolean context = false;
		boolean recurse = false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-context"))
				context = true;
			else if (args[i].equals("-recurse"))
				recurse = true;
			else
				page = args[i];
		}
		ExtractArticleText eat = new ExtractArticleText();
		try {
			eat.extract(page, context, recurse);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Extracts the text from an article and optionally all context articles
	 * around the article. The text is stripped of markup and Wikipedia
	 * structure, then saved to file.
	 * 
	 * @param pageName
	 *            The article to process.
	 * @param andArticleContext
	 * @param andContext
	 *            If the article is a category.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public boolean extract(String pageName, boolean andArticleContext,
			boolean recurseSubCategory) throws IOException {
		int coreArticleID = wmi_.getArticleByTitle(pageName);
		if (coreArticleID == -1) {
			System.err.println("Couldn't find article/category.");
			System.exit(1);
		}

		return extract(coreArticleID, pageName, andArticleContext,
				recurseSubCategory);
	}

	public boolean extract(int coreArticleID, String pageName,
			boolean andArticleContext, boolean recurseSubCategory)
			throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		Collection<Integer> toProcess = new HashSet<>();
		// Add extra context
		if (andArticleContext) {
			// toProcess.addAll(wmi_.getOutLinks(coreArticleID));
			for (Integer cat : wmi_.getArticleCategories(coreArticleID))
				toProcess.addAll(getCategoryArticles(cat, recurseSubCategory));
		} else
			toProcess.add(coreArticleID);

		// Process each article
		File parentFolder = new File(EXTRACTION_FOLDER, WordUtils.capitalize(
				pageName).replaceAll("\\s", ""));
		if (parentFolder.exists())
			return true;
		// else {
		// String artTitle = wmi_.getPageTitle(coreArticleID, true);// Extract
		// the links and their text
		// artTitle = artTitle.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
		// String artText = wmi_.getMarkup(coreArticleID);
		// // Remove specific sections
		// artText = removeSections(artText);
		// extractLinks(artTitle, parentFolder, artText);
		// return true;
		// }
		boolean created = false;
		for (Integer art : toProcess) {
			// Ignore lists & disambiguations
			String type = wmi_.getPageType(art);
			if (type != null
					&& type.equals(WikipediaSocket.TYPE_DISAMBIGUATION))
				continue;
			String artTitle = wmi_.getArtTitle(art, true);
			if (artTitle.toLowerCase().startsWith("list of"))
				continue;

			// Named entity removal
			String noContextTitle = wmi_.getArtTitle(art, false);
			int spaceIndex = noContextTitle.lastIndexOf(" ");
			if (spaceIndex > 0
					&& spaceIndex < artTitle.length() - 1
					&& Character.isLowerCase(noContextTitle
							.charAt(spaceIndex + 1)))
				continue;

			// Remove infoboxes
			// List<InfoboxData> infoboxData = wmi_.getInfoboxData(art);
			// if (!infoboxData.isEmpty())
			// continue;

			// Extract the links and their text
			String artText = wmi_.getMarkup(art);
			// Remove specific sections
			artText = removeSections(artText);

			// Get the art text
			String cleanText = toPlainText(artText);

			if (cleanText.isEmpty())
				continue;

			// Check if it's acceptable
			System.out.println("Is " + artTitle + " acceptable? (Y) or (N)");
			String result = "Y";// in.readLine();
			if (result.equalsIgnoreCase("Y")) {
				parentFolder.mkdirs();

				artTitle = artTitle.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
				extractLinks(artTitle, parentFolder, artText);
				created = true;
				savePlainText(artTitle, parentFolder, cleanText);

			}
		}
		return created;
	}

	private void extractLinks(String artTitle, File parentFolder, String artText)
			throws IOException {
		File linkFile = new File(parentFolder, artTitle + "LINKS.txt");
		BufferedWriter out = new BufferedWriter(new FileWriter(linkFile));
		Matcher m = WikiParser.ANCHOR_PARSER.matcher(artText);
		while (m.find()) {
			String linkArt = m.group(1);
			String linkText = m.group(2);
			if (linkText == null)
				linkText = linkArt;
			out.write(linkText + "\t" + linkArt + "\n");
		}
		out.close();
	}

	private void savePlainText(String artTitle, File parentFolder,
			String artText) {
		try {
			File extractionFile = new File(parentFolder, artTitle + ".txt");
			extractionFile.createNewFile();

			BufferedWriter out = new BufferedWriter(new FileWriter(
					extractionFile));
			out.write(artText);
			out.close();
		} catch (IOException e) {
			System.err.println(artTitle);
			e.printStackTrace();
		}
	}

	private String toPlainText(String artText) throws IOException {
		artText = NLPToSyntaxModule.convertToAscii(artText);
		artText = WikiParser.cleanAllMarkup(artText);

		// Remove section headings
		artText = artText.replaceAll("==+[^\n]+?==+", "");

		// Clean up non-sentences ending with [:;,]
		artText = artText.replaceAll("(^|\n).+?\\w.+?[:,;\\w]\n\n", "");

		// Clean up floating characters
		artText = artText.replaceAll("[*=]\n", "");

		// Condense whitespace
		artText = artText.replaceAll("\n{3,}", "\n\n");
		artText = artText.replaceAll(" {2,}", " ");
		artText = artText.trim();
		return artText;
	}

	private String removeSections(String artText) {
		int artLength = artText.length();
		for (String section : REMOVABLE_SECTIONS) {
			Pattern p = Pattern.compile("==+ ?" + section
					+ " ?==+.+?(?=(==)|$)", Pattern.DOTALL
					+ Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(artText);
			artText = m.replaceAll("\n");
			artLength = artText.length();
		}
		return artText;
	}

	protected Collection<Integer> getCategoryArticles(int categoryID,
			boolean recurseSubCategory) throws IOException {
		Collection<Integer> subCategories = null;
		if (recurseSubCategory)
			subCategories = wmi_.getPageSubCategories(categoryID);
		else {
			subCategories = new ArrayList<>(1);
			subCategories.add(categoryID);
		}
		return WikipediaSocket.union(wmi_.getChildArticles(subCategories
				.toArray(new Integer[subCategories.size()])));
	}
}
