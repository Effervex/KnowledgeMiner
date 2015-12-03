/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io;

import io.ontology.DAGAccess;
import io.ontology.OntologyAccess;
import io.ontology.OntologySocket;
import io.resources.WMIAccess;
import io.resources.WikipediaAccess;
import io.resources.WikipediaSocket;

/**
 * A singleton class for accessing the external resources.
 * 
 * @author Sam Sarjant
 */
public final class ResourceAccess {
	/** The instance */
	private static ResourceAccess instance_;

	/** The WMI access point. */
	private WikipediaAccess wikiAccess_;

	/** The Cyc access point. */
	private OntologyAccess cycAccess_;

	private ResourceAccess(int dagPort) {
		this(dagPort, DAGAccess.class, WMIAccess.class);
	}

	private ResourceAccess(int dagPort,
			Class<? extends OntologyAccess> ontologyAccess,
			Class<? extends WikipediaAccess> wikipediaAccess) {
		try {
			wikiAccess_ = wikipediaAccess.newInstance();
		} catch (Exception e) {
			System.err.println("Could not establish connection to WMI!");
		}
		try {
			cycAccess_ = ontologyAccess.newInstance();
		} catch (Exception e) {
			System.err.println("Could not establish connection to ontology!");
		}
	}

	public static OntologySocket requestOntologySocket() {
		if (instance_ == null)
			newInstance();
		return instance_.cycAccess_.requestSocket();
	}

	public static WikipediaSocket requestWikipediaSocket() {
		if (instance_ == null)
			newInstance();
		return instance_.wikiAccess_.requestSocket();
	}

	public static void recreateOntologySocket(OntologySocket socket) {
		if (instance_ != null)
			instance_.cycAccess_.recreateSocket(socket);
	}

	public static void recreateWikipediaSocket(WikipediaSocket socket) {
		if (instance_ != null)
			instance_.wikiAccess_.recreateSocket(socket);
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

	public static void newInstance(Class<? extends OntologyAccess> ontologyAccess,
			Class<? extends WikipediaAccess> wikipediaAccess) {
		if (instance_ != null)
			return;
		instance_ = new ResourceAccess(-1, ontologyAccess, wikipediaAccess);
	}
}
