/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

public class QuietListInterface implements ConceptThreadInterface {

	@Override
	public void update(ConceptModule concept, SortedSet<ConceptModule> processables) {
		
	}

	@Override
	public void flush() {	
	}

}
