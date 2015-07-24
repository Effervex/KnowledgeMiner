/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package test;

import static org.junit.Assert.*;

import java.util.Collection;

import io.ResourceAccess;
import io.ontology.DAGSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cyc.OntologyConcept;

public class DAGSocketTest {

	private DAGSocket sut_;

	@Before
	public void setUp() throws Exception {
		ResourceAccess.newInstance();
		sut_ = (DAGSocket) ResourceAccess.requestOntologySocket();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFindConceptByName() {
		Collection<OntologyConcept> results = sut_.findConceptByName("Junior \" A\"", false, true, true);
		assertTrue(results.isEmpty());
	}

}
