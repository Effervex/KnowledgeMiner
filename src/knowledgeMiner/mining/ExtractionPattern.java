/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package knowledgeMiner.mining;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import opennlp.tools.parser.Parse;

public class ExtractionPattern {
	private String[] posTypes_;
	private Parse[] parseArgs_;
	private int index_;

	public ExtractionPattern(String... posTypes) {
		posTypes_ = posTypes;
		index_ = 0;
		parseArgs_ = new Parse[posTypes.length];
	}

	private ExtractionPattern(ExtractionPattern ep) {
		posTypes_ = ep.posTypes_;
		index_ = ep.index_;
		parseArgs_ = Arrays.copyOf(ep.parseArgs_, ep.parseArgs_.length);
	}

	/**
	 * Checks if a current parse meets the current index argument POS type. If
	 * so, spawns a new Extraction Pattern with the parse included.
	 * 
	 * @param currentParse
	 *            The current parse.
	 * @return A new EP if the current parse is a valid argument.
	 */
	public ExtractionPattern checkParse(Parse currentParse) {
		if (currentParse.getType().startsWith(posTypes_[index_])) {
			ExtractionPattern newEP = new ExtractionPattern(this);
			newEP.parseArgs_[index_] = currentParse;
			newEP.index_++;
			return newEP;
		}
		return null;
	}
	
	public boolean isComplete() {
		return index_ >= posTypes_.length;
	}
	
	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("[" + StringUtils.join(posTypes_, ' ') + "]");
		buffer.append("=");
		buffer.append("\"" + StringUtils.join(parseArgs_, '|') + "\"");
		return buffer.toString();
	}
}
