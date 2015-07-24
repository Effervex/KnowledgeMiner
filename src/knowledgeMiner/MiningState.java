/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

/**
 * The state that a {@link ConceptModule} is in.
 *
 * @author Sam Sarjant
 */
public enum MiningState {
	ASSERTED, CONSISTENT, MAPPED, REVERSE_MAPPED, UNMAPPED, UNMINED;
}
