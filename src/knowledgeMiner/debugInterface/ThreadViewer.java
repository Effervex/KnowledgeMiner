/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import io.KMSocket;

import java.util.HashMap;
import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class ThreadViewer implements ConceptThreadInterface {
	private HashMap<Thread, Integer> threadPositions_;
	private int currentThreads_ = 0;
	private String[] outputs_;

	public ThreadViewer(int numThreads) {
		threadPositions_ = new HashMap<>(numThreads);
		outputs_ = new String[numThreads];
	}

	@Override
	public void update(Thread thread, ConceptModule concept,
			SortedSet<ConceptModule> processables, KMSocket wmi) {
		Integer pos = threadPositions_.get(thread);
		if (pos == null) {
			pos = currentThreads_++;
			threadPositions_.put(thread, pos);
		}

		outputs_[pos] = concept.toSimpleString(wmi) + " ("
				+ processables.size() + " more)";
		redraw(outputs_, pos);
	}

	/**
	 * Redraw the outputs.
	 *
	 * @param outputs The stuff to redraw.
	 * @param pos The index of the output that changed.
	 */
	protected abstract void redraw(String[] outputs, int pos);
}
