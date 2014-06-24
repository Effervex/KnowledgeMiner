/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.KMSocket;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.MinedAssertion;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;
import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class MinedAssertionTest {
	private static KMSocket wmi_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycConstants.initialiseAssertions(cyc);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.MinedAssertion#createAllAssertions(cyc.OntologyConcept, util.WeightedSet, OntologyConcept, knowledgeMiner.mining.MiningHeuristic, boolean)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateAllAssertions() throws Exception {
		OntologyConcept predicate = new OntologyConcept("occupation");
		OntologyConcept cycTerm = new OntologyConcept("UmaThurman");
		WeightedSet<OntologyConcept> predicateArgs = new WeightedSet<>();
		predicateArgs.add(OntologyConcept.parseArgument("\"Actress\""), 1.0);
		predicateArgs.add(OntologyConcept.parseArgument("Actor"), 1.0);
		predicateArgs.add(OntologyConcept.parseArgument("ProfessionalModel"), 0.8);
		predicateArgs.add(OntologyConcept.parseArgument("Dog"), 0.7);
		predicateArgs.add(OntologyConcept.parseArgument("423"), 0.6);
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		AssertionQueue result = MinedAssertion.createAllAssertions(predicate,
				predicateArgs, null, true, ontology);
		assertEquals(result.size(), 2);
		OntologyConcept occupation = new OntologyConcept("occupation");
		OntologyConcept umaThurman = new OntologyConcept("UmaThurman");
		OntologyConcept actor = new OntologyConcept("Actor");
		MinedAssertion assertion = new MinedAssertion(occupation, umaThurman,
				actor, null, null);
		assertTrue(result.contains(assertion));
		assertEquals(result.getWeight(assertion), 1.0, 0.0001);
		OntologyConcept professionalModel = new OntologyConcept("ProfessionalModel");
		assertion = new MinedAssertion(occupation, umaThurman,
				professionalModel, null, null);
		assertTrue(result.contains(assertion));
		assertEquals(result.getWeight(assertion), 0.8, 0.0001);

		// New term
		cycTerm = new OntologyConcept("SomeNewGuy");
		result = MinedAssertion.createAllAssertions(predicate, predicateArgs,
				null, true, ontology);
		assertEquals(result.size(), 2);
		assertion = new MinedAssertion(occupation, cycTerm, actor, null, null);
		assertTrue(result.contains(assertion));
		assertEquals(result.getWeight(assertion), 1.0, 0.0001);
		assertion = new MinedAssertion(occupation, cycTerm, professionalModel,
				null, null);
		assertTrue(result.contains(assertion));
		assertEquals(result.getWeight(assertion), 0.8, 0.0001);
	}
}
