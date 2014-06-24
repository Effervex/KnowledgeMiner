/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This utility parses formal tables from Wiki markup and returns the table
 * values in separate objects.
 * 
 * @author Sam Sarjant
 */
public class TableMiner {
	private static final Pattern SORT_NAME_PATTERN = Pattern
			.compile("\\{\\{sortname\\|(.+?)\\|(.+?)(?:\\|(.+?))?\\}\\}");
	private static final Pattern TABLE_PATTERN = Pattern.compile(
			"\\{\\|.*?\\n(?:\\|\\+\\s*(.+?)\\n)?.+?\\|\\}", Pattern.DOTALL);
	/** The key to use for no context bullet points. */
	public static final String NO_CONTEXT = "NO_CONTEXT";

	/**
	 * Cleans an element of unnecessary markup.
	 * 
	 * @param element
	 *            The element being cleaned.
	 * @return The cleaned element.
	 */
	private static String cleanElement(String element) {
		Matcher sortMatcher = SORT_NAME_PATTERN.matcher(element);
		if (sortMatcher.find()) {
			if (sortMatcher.group(3) != null)
				element = sortMatcher.replaceAll("[[$3|$1 $2]]");
			else
				element = sortMatcher.replaceAll("[[$1 $2]]");
		}
		return element.trim();
	}

	/**
	 * Gets/initialises a row from the current rows with the specified size.
	 * 
	 * @param currentRows
	 *            The current rows.
	 * @param rowNumber
	 *            The row number to get.
	 * @param numColumns
	 *            The size to initialise new rows as.
	 * @return The row.
	 */
	private static String[] getRow(List<String[]> currentRows, int rowNumber,
			int numColumns) {
		while (currentRows.size() <= rowNumber) {
			// Initialise a new row.
			String[] newRow = new String[numColumns];
			currentRows.add(newRow);
		}
		String[] currentRow = currentRows.get(rowNumber);
		return currentRow;
	}

	/**
	 * Inserts an element into the current rows at the given index with special
	 * modifiers for insertion and return the observed maximum number of
	 * columns.
	 * 
	 * @param element
	 *            The element being inserted.
	 * @param currentRows
	 *            The current rows to insert into.
	 * @param rows
	 *            The number of rows this element uses (usually 1).
	 * @param columns
	 *            The number of columns this element uses (usually 1).
	 * @param numColumns
	 *            The maximum number of observed columns.
	 * @return The new maximum number of observed columns.
	 */
	private static int insertElement(String element,
			List<String[]> currentRows, int rows, int columns, int numColumns) {
		element = cleanElement(element);
		String[] currentRow = getRow(currentRows, 0,
				Math.max(numColumns, columns));

		// Find first available index
		int index = 0;
		while (index < currentRow.length && currentRow[index] != null)
			index++;

		// Expand array if necessary
		if (index >= currentRow.length) {
			String[] expandRow = new String[Math.max(currentRow.length
					+ columns, numColumns)];
			System.arraycopy(currentRow, 0, expandRow, 0, currentRow.length);
			currentRows.set(0, expandRow);
			currentRow = expandRow;
		}

		// Place the element
		for (int c = 0; c < columns; c++) {
			currentRow[index + c] = element;
			for (int r = 1; r < rows; r++)
				getRow(currentRows, r, currentRow.length)[index + c] = element;
		}
		return currentRow.length;
	}

	/**
	 * Parse a row in the table. Takes a line and adds the line data to the
	 * current rows.
	 * 
	 * @param line
	 *            The line to parse.
	 * @param currentRows
	 *            The current rows to add to.
	 * @param numColumns
	 *            The number of columns in the table. -1 if unknown yet.
	 * @return The new number of columns.
	 */
	private static int parseRow(String line, List<String[]> currentRows,
			int numColumns) {
		char delimiter = line.charAt(0);
		ArrayList<String> split = WikiParser.split(line, delimiter + ""
				+ delimiter);
		if (split.size() == 1) {
			int rows = 1, columns = 1;
			String element = null;

			// Single entry on a line.
			ArrayList<String> subSplit = WikiParser.split(split.get(0), "|");
			if (subSplit.size() == 2) {
				String context = subSplit.get(0).trim();
				if (context.startsWith("colspan")) {
					// X elements
					columns = parseValue(context);
				} else if (context.startsWith("rowspan")) {
					// X rows
					rows = parseValue(context);
				}
				element = subSplit.get(1);
			} else
				element = WikiParser.replaceAll(subSplit.get(0),
						delimiter + "", "");
			numColumns = insertElement(element.trim(), currentRows, rows,
					columns, numColumns);
		} else {
			// Add each element
			for (String element : split)
				numColumns = insertElement(element.trim(), currentRows, 1, 1,
						numColumns);
		}

		return numColumns;
	}

	/**
	 * Parses a value from a context string in the form blah="<number>".
	 * 
	 * @param context
	 *            The context string to parse.
	 * @return The integer value.
	 */
	private static int parseValue(String context) {
		String[] split = context.split("\"");
		return Integer.parseInt(split[1]);
	}

	/**
	 * Parses all tables from an article's markup.
	 * 
	 * @param markup
	 *            The markup to parse.
	 * @return A collection of all the tables on a page.
	 */
	public static Collection<WikiTable> parseTable(String markup) {
		Collection<WikiTable> pageTables = new ArrayList<>();

		Matcher m = TABLE_PATTERN.matcher(markup);
		while (m.find()) {
			// Get and check the context
			String contextTitle = m.group(1);
			if (contextTitle == null)
				contextTitle = WikiParser.backSearchHeader(markup.substring(0,
						m.start()));

			if (contextTitle != null)
				contextTitle = WikiParser.cleanAllMarkup(contextTitle).trim();
			else
				contextTitle = NO_CONTEXT;

			WikiTable table = new WikiTable(contextTitle);

			// Parse each point out.
			List<String[]> rows = new LinkedList<>();
			String[] header = null;
			int numColumns = -1;
			String tableMarkup = m.group();
			String[] split = tableMarkup.split("\\n");
			for (String line : split) {
				// Row end, record it
				if (line.startsWith("|-") || line.startsWith("|}")) {
					if (rows.isEmpty())
						continue;

					// Record the row
					String[] rowData = rows.remove(0);
					numColumns = rowData.length;
					if (header == null)
						header = rowData;
					else if (!table.recordRow(rowData, header))
						break;
					continue;
				} else if (line.startsWith("|+") || line.startsWith("{|"))
					continue;
				else {
					// If header tag, reset the headers
					if (line.startsWith("!"))
						header = null;
					numColumns = parseRow(line, rows, numColumns);
				}
			}

			if (!table.isEmpty())
				pageTables.add(table);
		}
		return pageTables;
	}
}
