/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import org.apache.commons.lang3.StringUtils;

import util.collection.MultiMap;

/**
 * 
 * @author Sam Sarjant
 */
public class WikiTable {
	/** The maximum number of characters to display per column. */
	public static final int MAX_CHAR_PER_COLUMN = 20;

	/** The header of the table. */
	private String[] header_;
	/** The number of rows. */
	private int numEntries_;
	/** The table title context. */
	private String contextTitle_;
	/** The contents of the table. */
	private MultiMap<String, String> tableContents_ = MultiMap
			.createListMultiMap();

	/**
	 * Constructor for a new WikiTable
	 * 
	 * @param contextTitle
	 */
	public WikiTable(String contextTitle) {
		contextTitle_ = contextTitle;
	}

	public boolean recordRow(String[] row, String[] headers) {
		if (headers == null)
			return false;
		header_ = headers;
		for (int i = 0; i < headers.length; i++) {
			String rowElement = (i >= row.length || row[i] == null) ? ""
					: row[i];
			tableContents_.put(headers[i], rowElement);
		}
		numEntries_++;
		return true;
	}

	public String getContextTitle() {
		return contextTitle_;
	}

	public MultiMap<String, String> getTableData() {
		return tableContents_;
	}

	public boolean isEmpty() {
		return tableContents_.isKeysEmpty();
	}

	@Override
	public String toString() {
		if (header_ == null)
			return contextTitle_ + "\n<NO TABLE>";

		// First determine the max size of the column
		int[] colSize = new int[header_.length];
		for (int i = 0; i < colSize.length; i++) {
			colSize[i] = header_[i].length() + 1;
			for (String str : tableContents_.get(header_[i]))
				colSize[i] = Math.max(colSize[i], str.length() + 1);
			// Cap colSize at MAX CHARS
			colSize[i] = Math.min(colSize[i], MAX_CHAR_PER_COLUMN + 1);
		}

		// Format the table as a string table
		StringBuilder builder = new StringBuilder(contextTitle_ + "\n");
		for (int i = 0; i < colSize.length; i++)
			builder.append(formatCell(header_[i], i, colSize[i]));

		try {
			for (int i = 0; i < numEntries_; i++) {
				builder.append("\n");
				for (int j = 0; j < header_.length; j++)
					builder.append(formatCell(
							tableContents_.getIndex(header_[j], i), j,
							colSize[j]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return builder.toString();
	}

	/**
	 * Formats a string in a table cell to the appropriate size and padding.
	 *
	 * @param string
	 *            The string to display.
	 * @param column
	 *            The current column.
	 * @param maxColSize
	 *            The largest string in the column.
	 * @return The string as it should be displayed in the column.
	 */
	private String formatCell(String string, int column, int maxColSize) {
		// Cutting off too large strings
		if (string.length() > MAX_CHAR_PER_COLUMN)
			return string.substring(0, MAX_CHAR_PER_COLUMN - 3) + "... ";
		// If last column, don't worry about right padding
		if (column == header_.length - 1)
			return string;
		// Otherwise, return with padding
		return string + StringUtils.repeat(' ', maxColSize - string.length());
	}
}
