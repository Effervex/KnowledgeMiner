/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import io.KMSocket;

import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

/**
 * A simple interface that outputs the concept state, regardless of thread.
 * 
 * @author Sam Sarjant
 */
public class SimpleListInterface implements ConceptThreadInterface {

	@Override
	public synchronized void update(Thread thread, ConceptModule concept,
			SortedSet<ConceptModule> processables, KMSocket wmi) {
		switch (concept.getState()) {
		case UNMINED:
			System.out.println("  " + concept.toSimpleString(wmi));
			break;
		case UNMAPPED:
			System.out.println("   " + concept.toSimpleString(wmi));
			break;
		case MAPPED:
			System.out.println("    " + concept.toSimpleString(wmi));
			break;
		case REVERSE_MAPPED:
			System.out.println("     " + concept.toSimpleString(wmi));
			break;
		case CONSISTENT:
			System.out.println("      " + concept.toSimpleString(wmi));
			break;
		case ASSERTED:
			System.out.println("       " + concept.toSimpleString(wmi));
			break;
		default:
			break;
		}
	}

}
