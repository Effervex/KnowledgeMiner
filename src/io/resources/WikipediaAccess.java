/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.resources;

import io.KMAccess;

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * The abstract access point for gaining access to a Wikipedia interface's
 * commands. This class contains a pool of Wikipedia sockets that are handed out
 * to different Threads to allow simultaneous access to the interface.
 * 
 * @author Sam Sarjant
 */
public abstract class WikipediaAccess extends KMAccess<WikipediaSocket> {
	/**
	 * Constructor for a new Wikipedia access point.
	 */
	public WikipediaAccess(int port) throws UnknownHostException, IOException {
		super(port);
		cacheMapActive_ = false;
	}
	
	public WikipediaAccess() throws UnknownHostException, IOException {
		super();
		cacheMapActive_ = false;
	}

	@Override
	protected abstract WikipediaSocket createSocket(
			KMAccess<WikipediaSocket> kmAccess);
}
