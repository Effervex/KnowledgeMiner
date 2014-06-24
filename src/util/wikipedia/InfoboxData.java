/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import java.util.HashMap;
import java.util.Map;

/**
 * Data contained within an article's infobox.
 * 
 * @author Sam Sarjant
 */
public class InfoboxData {
	/** The type of infobox. */
	private String infoboxType_;

	/** The relations of the infobox. */
	private Map<String, String> relations_;

	public InfoboxData(String type) {
		infoboxType_ = type.toLowerCase();
		relations_ = new HashMap<String, String>();
	}

	public void putRelation(String leftSide, String rightSide) {
		relations_.put(leftSide, rightSide);
	}

	public Map<String, String> getInfoboxRelations() {
		return relations_;
	}

	public String getInfoboxType() {
		return infoboxType_;
	}

	@Override
	public String toString() {
		return infoboxType_ + ", " + relations_.size() + " relations.";
	}
}
