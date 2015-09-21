/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.ontology;

import graph.core.CommonConcepts;
import io.KMAccess;

import java.io.IOException;
import java.net.UnknownHostException;

public class DAGAccess extends OntologyAccess {
	public DAGAccess(int port) throws UnknownHostException, IOException {
		super(port);
		cacheMapActive_ = false;
		DAGSocket dag = (DAGSocket) requestSocket();
		if (dag != null) {
			for (CommonConcepts cc : CommonConcepts.values()) {
				cc.setID(dag.getConceptID(cc.getNodeName()));
			}
		}
	}

	@Override
	protected DAGSocket createSocket(KMAccess<OntologySocket> kmAccess)
			throws Exception {
		if (port_ == -1)
			return new DAGSocket((DAGAccess) kmAccess);
		else
			return new DAGSocket((DAGAccess) kmAccess, port_);
	}
}
