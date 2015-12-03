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

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.DAGSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cyc.CycConstants;
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
		Collection<OntologyConcept> results = sut_.findConceptByName(
				"Junior \" A\"", false, true, true);
		assertTrue(results.isEmpty());
	}

	@Test
	public void testIsInfoless() throws Exception {
		// Base
		OntologyConcept concept = CycConstants.ISA.getConcept();
		assertFalse(sut_.isInfoless(concept, false, false));
		assertFalse(sut_.isInfoless(concept, false, true));
		assertFalse(sut_.isInfoless(concept, true, false));
		assertFalse(sut_.isInfoless(concept, true, true));

		// Primitive
		concept = new OntologyConcept("charSequenceOfMaximumLength-lowercase");
		assertFalse(sut_.isInfoless(concept, false, false));
		assertFalse(sut_.isInfoless(concept, false, true));
		assertTrue(sut_.isInfoless(concept, true, false));
		assertTrue(sut_.isInfoless(concept, true, true));

		// String
		concept = new OntologyConcept(CommonConcepts.PRETTY_STRING.getID());
		assertFalse(sut_.isInfoless(concept, false, false));
		assertTrue(sut_.isInfoless(concept, false, true));
		assertFalse(sut_.isInfoless(concept, true, false));
		assertTrue(sut_.isInfoless(concept, true, true));
	}

	@Test
	public void testAssertEdge() throws Exception {
		String conceptA = "(CBLTaskFn (CBLMethodFn CBLNode-CycorpRefrigeratorMassEMailSending \"startCycorpRefrigeratorMassEmailSending_task\") \\'2)";
		String conceptB = "UndertakingSomething-Accept";
		assertEquals(-1, sut_.assertToOntology(null,
				CommonConcepts.DISJOINTWITH.getID(), conceptA, conceptB));

		conceptA = conceptA.replaceAll("\\\\'", "'");
		assertNotEquals(-1, sut_.assertToOntology(null,
				CommonConcepts.DISJOINTWITH.getID(), conceptA, conceptB));
	}
}
