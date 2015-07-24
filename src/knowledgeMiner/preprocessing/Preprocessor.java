/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.preprocessing;

import cyc.OntologyConcept;

/**
 * The interface for preprocessing tasks.
 * 
 * @author Sam Sarjant
 */
public interface Preprocessor {
	public void processTerm(OntologyConcept term) throws Exception;
}
