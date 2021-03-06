/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.resources;

import io.KMAccess;

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * The access point for gaining access to WMI commands. This class contains a
 * pool of WMI sockets that are handed out to different Threads to allow
 * simultaneous access to WMI.
 * 
 * @author Sam Sarjant
 */
public class CapiscoAccess extends WikipediaAccess {
	/**
	 * Constructor for a new WMI access point.
	 */
	public CapiscoAccess(int port) throws UnknownHostException, IOException {
		super(port);
		cacheMapActive_ = false;
	}

	@Override
	protected CapiscoSocket createSocket(KMAccess<WikipediaSocket> kmAccess) {
		return new CapiscoSocket((CapiscoAccess) kmAccess);
	}
}
