/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import java.util.SortedSet;

import knowledgeMiner.ConceptModule;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class ThreadViewer implements ConceptThreadInterface {
	private ThreadLocal<Integer> threadPositions_;
	private int currentThreads_ = 0;
	private String[] outputs_;

	public ThreadViewer(int numThreads) {
		threadPositions_ = new ThreadLocal<>();
		outputs_ = new String[numThreads];
	}

	@Override
	public void update(ConceptModule concept,
			SortedSet<ConceptModule> processables) {
		Integer pos = threadPositions_.get();
		if (pos == null) {
			pos = currentThreads_++;
			threadPositions_.set(pos);
		}

		outputs_[pos] = concept.toSimpleString() + " ("
				+ processables.size() + " more)";
		redraw(outputs_, pos);
	}

	/**
	 * Redraw the outputs.
	 *
	 * @param outputs
	 *            The stuff to redraw.
	 * @param pos
	 *            The index of the output that changed.
	 */
	protected abstract void redraw(String[] outputs, int pos);

	@Override
	public void flush() {	
	}
}
