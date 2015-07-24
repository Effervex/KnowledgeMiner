/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opencyc.api.CycApiException;

import util.DistanceFunction;
import cyc.ConceptDistanceFunction;
import cyc.OntologyConcept;
import cyc.CycConstants;
import cyc.PrimitiveConcept;
import cyc.StringConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class ConceptDistanceFunctionTest extends DistanceFunctionTest {
	private static ConceptDistanceFunction sut_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		CycConstants.initialiseAssertions(ResourceAccess.requestOntologySocket());
		sut_ = new ConceptDistanceFunction();
	}

	@Test
	public void testDistance2() throws Exception {
		// Simple case
		OntologyConcept termA = new OntologyConcept("Dog");
		OntologyConcept collectionB = new OntologyConcept("Mammal");
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		float distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 4, 0);

		// Same element
		distance = sut_.distance(termA, termA, cyc);
		assertEquals(distance, 0, 0);
		distance = sut_.distance(collectionB, collectionB, cyc);
		assertEquals(distance, 0, 0);

		// Disjoint elements
		collectionB = new OntologyConcept("Cat");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, -1, 0);

		// Individual
		termA = new OntologyConcept("UmaThurman");
		collectionB = new OntologyConcept("HomoSapiens");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 5, 0);

		collectionB = new OntologyConcept("Actor");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 1, 0);

		// Illegal individual argument
		collectionB = new OntologyConcept("SamuelLJackson");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, -1, 0);

		// Function
		termA = new OntologyConcept("(FruitFn AppleTree)");
		collectionB = new OntologyConcept("BiologicalLivingObject");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 5, 0);

		// Individual Function
		termA = new OntologyConcept("(TheFn Dog)");
		collectionB = new OntologyConcept("Dog");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 1, 0);

		collectionB = new OntologyConcept("Mammal");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 5, 0);

		// More 'The'
		termA = new OntologyConcept("Actor");
		collectionB = new OntologyConcept("HomoSapiens");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 4, 0);

		termA = new OntologyConcept("(TheFn Actor)");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 5, 0);

		// String
		termA = new StringConcept("Hey man!");
		collectionB = new OntologyConcept("CharacterString");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 1, 0);

		// Number
		termA = new PrimitiveConcept(4);
		collectionB = new OntologyConcept("NonNegativeInteger");
		distance = sut_.distance(termA, collectionB, cyc);
		assertEquals(distance, 1, 0);

		termA = new OntologyConcept("ASillyMadeUpConstant");
		collectionB = new OntologyConcept("Thing");
		try {
			distance = sut_.distance(termA, collectionB, cyc);
			fail("Should have thrown Exception.");
		} catch (CycApiException e) {
		}
	}

	@Override
	protected DistanceFunction getDistanceFunction() {
		return new ConceptDistanceFunction();
	}
}
