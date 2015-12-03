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
public class WMIAccess extends WikipediaAccess {
	/**
	 * Constructor for a new WMI access point.
	 */
	public WMIAccess(int port) throws UnknownHostException, IOException {
		super(port);
		cacheMapActive_ = false;
	}

	public WMIAccess() throws UnknownHostException, IOException {
		super();
		cacheMapActive_ = false;
	}

	@Override
	protected WikipediaSocket createSocket(KMAccess<WikipediaSocket> kmAccess) {
		return new WMISocket((WMIAccess) kmAccess);
	}
}
