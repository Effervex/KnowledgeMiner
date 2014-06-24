/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import io.KMSocket;

import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

public class QuietListInterface implements ConceptThreadInterface {

	@Override
	public void update(Thread thread, ConceptModule concept,
			SortedSet<ConceptModule> processables, KMSocket wmi) {
		
	}

}
