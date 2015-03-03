/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import knowledgeMiner.preprocessing.UglyString;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class CycPreprocessorTest {
	private static OntologySocket cyc_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		CycConstants.initialiseAssertions(cyc_);
	}

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testUglyString() throws Exception {
		UglyString processor = new UglyString();
		OntologyConcept term = new OntologyConcept("Dog");
		assertTrue(cyc_.evaluate("prettyString", term, "\"dog\""));
		processor.processTerm(term);
		assertTrue(cyc_.evaluate("prettyString", term, "\"dog\""));

		term = new OntologyConcept("capitalCityOfState");
		processor.processTerm(term);
		assertTrue(cyc_.evaluate("prettyString", term, "\"capital\""));
		assertTrue(cyc_.evaluate("prettyString", term, "\"capital city\""));
		assertTrue(cyc_.evaluate("prettyString", term, "\"capital city of\""));
		assertTrue(cyc_.evaluate("prettyString", term,
				"\"capital city of state\""));

		term = new OntologyConcept("Batman-TheComicStrip");
		processor.processTerm(term);
		assertEquals(cyc_.getSynonyms(term).size(), 1);

		term = new OntologyConcept("MarineBase-MCASElToro-Grounds-Eastpac");
		processor.processTerm(term);

		// Problematics
		term = new OntologyConcept("ActorSlot");
		processor.processTerm(term);
		assertTrue(cyc_.evaluate("prettyString", term, "\"actor\""));
		assertTrue(cyc_.evaluate("prettyString", term, "\"actor slot\""));

		term = new OntologyConcept("VerificationService");
		processor.processTerm(term);
		assertTrue(cyc_.evaluate("prettyString", term, "\"verification\""));
		assertTrue(cyc_.evaluate("prettyString", term,
				"\"verification service\""));
	}
}
