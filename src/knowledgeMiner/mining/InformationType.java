/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

/**
 * The types of information a mining heuristic can produce.
 * 
 * @author Sam Sarjant
 */
public enum InformationType {
	// TODO STANDING, TAXONOMIC, SYNONYM, COMMENT, NON_TAXONOMIC
	PARENTAGE, RELATIONS, STANDING, CHILD_ARTICLES;
}
