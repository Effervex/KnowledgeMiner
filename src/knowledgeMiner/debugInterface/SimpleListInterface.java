/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

/**
 * A simple interface that outputs the concept state, regardless of thread.
 * 
 * @author Sam Sarjant
 */
public class SimpleListInterface implements ConceptThreadInterface {

	@Override
	public synchronized void update(ConceptModule concept,
			SortedSet<ConceptModule> processables) {
		switch (concept.getState()) {
		case UNMINED:
			System.out.println("  " + concept.toSimpleString());
			break;
		case UNMAPPED:
			System.out.println("   " + concept.toSimpleString());
			break;
		case MAPPED:
			System.out.println("    " + concept.toSimpleString());
			break;
		case REVERSE_MAPPED:
			System.out.println("     " + concept.toSimpleString());
			break;
		case CONSISTENT:
			System.out.println("      " + concept.toSimpleString());
			break;
		case ASSERTED:
			System.out.println("       " + concept.toSimpleString());
			break;
		default:
			break;
		}
	}

	@Override
	public void flush() {	
	}

}
