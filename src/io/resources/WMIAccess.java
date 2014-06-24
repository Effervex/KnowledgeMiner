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
public class WMIAccess extends KMAccess<WMISocket> {
	/**
	 * Constructor for a new WMI access point.
	 */
	public WMIAccess(int port) throws UnknownHostException, IOException {
		super(port);
		cacheMapActive_ = true;
	}

	@Override
	protected WMISocket createSocket(KMAccess<WMISocket> kmAccess) {
		return new WMISocket((WMIAccess) kmAccess);
	}
}
