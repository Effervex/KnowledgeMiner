/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

/**
 * A basic interface description for displaying the state of the mining
 * algorithm.
 * 
 * @author Sam Sarjant
 */
public interface ConceptThreadInterface {
	/**
	 * Updates the interface to reflect the changes.
	 * 
	 * @param concept
	 *            The concept being processed in the thread.
	 * @param processables
	 *            The remaining concepts to be processed.
	 */
	void update(ConceptModule concept, SortedSet<ConceptModule> processables);

	/**
	 * Flushes the interface (allows it to write to file, or otherwise). Called
	 * when a concept mining task has completed its processing.
	 */
	void flush();
}
