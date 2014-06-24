/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io;

import io.ontology.DAGAccess;
import io.ontology.OntologyAccess;
import io.ontology.OntologySocket;
import io.resources.WMIAccess;
import io.resources.WMISocket;

/**
 * A singleton class for accessing the external resources.
 * 
 * @author Sam Sarjant
 */
public final class ResourceAccess {
	/** The instance */
	private static ResourceAccess instance_;

	/** The WMI access point. */
	private WMIAccess wmiAccess_;

	/** The Cyc access point. */
	private OntologyAccess cycAccess_;

	private ResourceAccess(int dagPort) {
		try {
			wmiAccess_ = new WMIAccess(-1);
		} catch (Exception e) {
			System.err.println("Could not establish connection to WMI!");
		}
		try {
			cycAccess_ = new DAGAccess(dagPort);
		} catch (Exception e) {
			System.err.println("Could not establish connection to ontology!");
		}
	}

	public static OntologySocket requestOntologySocket() {
		if (instance_ == null)
			newInstance();
		return instance_.cycAccess_.requestSocket();
	}

	public static WMISocket requestWMISocket() {
		if (instance_ == null)
			newInstance();
		return instance_.wmiAccess_.requestSocket();
	}

	public static void recreateOntologySocket(OntologySocket socket) {
		if (instance_ != null)
			instance_.cycAccess_.recreateSocket(socket);
	}

	public static void recreateWMISocket(WMISocket socket) {
		if (instance_ != null)
			instance_.wmiAccess_.recreateSocket(socket);
	}

	public static void newInstance() {
		newInstance(-1);
	}

	public static void newInstance(int port) {
		if (instance_ != null) {
			return;
		}
		instance_ = new ResourceAccess(port);
	}
}
