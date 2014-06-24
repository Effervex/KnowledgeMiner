/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import util.collection.MultiMap;

/**
 * 
 * @author Sam Sarjant
 */
public class WikiTable {
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
		StringBuilder buffer = new StringBuilder(contextTitle_);
		try {
			for (int i = 0; i < numEntries_; i++) {
				buffer.append("\n");
				boolean first = true;
				for (String header : header_) {
					if (!first)
						buffer.append(", ");
					buffer.append(header + ": "
							+ tableContents_.getIndex(header, i));
					first = false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return buffer.toString();
	}
}
