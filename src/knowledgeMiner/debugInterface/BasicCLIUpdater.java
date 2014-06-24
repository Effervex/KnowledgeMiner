/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.debugInterface;

import knowledgeMiner.KnowledgeMiner;

/**
 * 
 * @author Sam Sarjant
 */
public class BasicCLIUpdater extends ThreadViewer {
	/**
	 * Constructor for a new BasicCLIUpdater
	 * 
	 * @param numThreads
	 */
	public BasicCLIUpdater(int numThreads) {
		super(numThreads);
	}

	public static final int UPDATE_FREQ = KnowledgeMiner.getNumThreads();
	private int requests_ = 0;

	@Override
	protected synchronized void redraw(String[] outputs, int pos) {
		requests_++;
		if (requests_ >= UPDATE_FREQ) {
			System.out.println();
			for (String output : outputs) {
				System.out.println(output);
			}
			System.out.println();
			requests_ = 0;
		}
	}

}
