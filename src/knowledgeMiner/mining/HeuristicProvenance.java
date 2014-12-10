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

import graph.module.NLPToSyntaxModule;

import java.io.Serializable;
import java.util.Collection;

/**
 * Heuristic provenance keeps track of where information came from, and passes
 * this onto the assertion itself.
 * 
 * @author Sam Sarjant
 */
public class HeuristicProvenance implements Serializable {
	private static final long serialVersionUID = 1L;

	/** The heuristic. */
	private String heuristicStr_;

	/** Any provenance details to be recorded with the heuristic. */
	private String details_;

	public HeuristicProvenance(MiningHeuristic heuristic, String details) {
		if (heuristic == null)
			heuristicStr_ = "<null>";
		else
			heuristicStr_ = heuristic.getHeuristicName();
		details_ = NLPToSyntaxModule.convertToAscii(details);
	}

	public HeuristicProvenance(String heuristic, String details) {
		heuristicStr_ = heuristic;
		details_ = NLPToSyntaxModule.convertToAscii(details);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((details_ == null) ? 0 : details_.hashCode());
		result = prime * result
				+ ((heuristicStr_ == null) ? 0 : heuristicStr_.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HeuristicProvenance other = (HeuristicProvenance) obj;
		if (details_ == null) {
			if (other.details_ != null)
				return false;
		} else if (!details_.equals(other.details_))
			return false;
		if (heuristicStr_ == null) {
			if (other.heuristicStr_ != null)
				return false;
		} else if (!heuristicStr_.equals(other.heuristicStr_))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		if (heuristicStr_ != null)
			buffer.append(heuristicStr_);
		if (details_ != null) {
			if (buffer.length() != 0)
				buffer.append("|");
			buffer.append(details_);
		}
		return buffer.toString();
	}

	public String getDetails() {
		return details_;
	}

	public static HeuristicProvenance joinProvenance(
			Collection<HeuristicProvenance> collection) {
		StringBuilder heuristicStr = new StringBuilder();
		StringBuilder details = new StringBuilder();
		boolean first = false;
		for (HeuristicProvenance prov : collection) {
			if (!first) {
				heuristicStr.append(", ");
				details.append("\n");
			}
			heuristicStr.append(prov.heuristicStr_);
			details.append(prov.details_);
			first = true;
		}
		return new HeuristicProvenance(heuristicStr.toString(),
				details.toString());
	}
}
