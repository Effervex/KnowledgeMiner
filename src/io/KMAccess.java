/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import util.collection.CacheMap;

/**
 * An abstract access point for dynamically allocating sockets to threads.
 * 
 * @author Sam Sarjant
 */
public abstract class KMAccess<S extends KMSocket> {

	/** If results are cached at all. */
	protected boolean cacheMapActive_ = false;

	/** The local cache for object-based commands. Will gradually move to WMI. */
	protected Map<String, CacheMap<String, Object>> resultCache_;

	/** Thread-specific socket. */
	protected final ThreadLocal<S> threadSocket_;

	/** The port to connect to. */
	protected int port_ = -1;

	/**
	 * Constructor for a new WMI access point.
	 */
	public KMAccess(int port) throws UnknownHostException, IOException {
		port_ = port;
		resultCache_ = new HashMap<>();
		threadSocket_ = new ThreadLocal<S>() {
			@Override
			protected S initialValue() {
				try {
					return createSocket(KMAccess.this);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		};
	}

	public void recreateSocket(S socket) {
		threadSocket_.remove();
	}

	/**
	 * Creates a new socket for the access point.
	 * 
	 * @param kmAccess
	 *            The access point.
	 * @return The new socket.
	 */
	protected abstract S createSocket(KMAccess<S> kmAccess) throws Exception;

	/**
	 * Caches a command result into the map.
	 * 
	 * @param command
	 *            The command being cached.
	 * @param argument
	 *            The command argument.
	 * @param value
	 *            The value being cached.
	 */
	public synchronized void cacheCommand(String command, String argument,
			Object value) {
		if (!cacheMapActive_)
			return;

		if (!resultCache_.containsKey(command))
			resultCache_.put(command, new CacheMap<String, Object>(true));
		resultCache_.get(command).put(argument, value);
	}

	public synchronized void clearCache() {
		resultCache_.clear();
	}

	/**
	 * Gets a cached command from the result cache (if one exists).
	 * 
	 * @param command
	 *            The command name.
	 * @param argument
	 *            The command arguments.
	 * @return The command result or null if no result is cached.
	 */
	public synchronized Object getCachedCommand(String command, String argument) {
		if (!cacheMapActive_)
			return null;

		if (!resultCache_.containsKey(command)) {
			resultCache_.put(command, new CacheMap<String, Object>(true));
			return null;
		}
		// TODO Need to return a copy of the cached result
		return resultCache_.get(command).get(argument);
	}

	/**
	 * Requests a socket from the socket pool. The socket may already be
	 * assigned to the thread requesting it.
	 * 
	 * @return The socket assigned to this thread.
	 */
	public synchronized S requestSocket() {
		return threadSocket_.get();
	}

	/**
	 * If this virtual machine is currently running from WMI.
	 * 
	 * @return True if running from WMI.
	 */
	public final static boolean isOnWMI() {
		String machineName;
		try {
			machineName = InetAddress.getLocalHost().getHostName();
			return machineName.equals("wmi") || machineName.equals("rautini");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return false;
	}
}
