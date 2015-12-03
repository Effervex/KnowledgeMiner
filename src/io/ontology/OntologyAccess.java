/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.ontology;

import io.KMAccess;

import java.io.IOException;
import java.net.UnknownHostException;

public abstract class OntologyAccess extends KMAccess<OntologySocket> {
	public OntologyAccess(int port) throws UnknownHostException,
			IOException {
		super(port);
	}
	
	public OntologyAccess() throws UnknownHostException, IOException {
		super();
	}

	@Override
	protected abstract OntologySocket createSocket(
			KMAccess<OntologySocket> kmAccess) throws Exception;
}
