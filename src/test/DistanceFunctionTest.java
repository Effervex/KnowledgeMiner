/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import knowledgeMiner.mining.DefiniteAssertion;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import util.DistanceFunction;
import cyc.OntologyConcept;
import cyc.CycConstants;
import cyc.PrimitiveConcept;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class DistanceFunctionTest {
	private DistanceFunction sut_;

	@Before
	public void setUp() {
		sut_ = getDistanceFunction();
	}

	protected abstract DistanceFunction getDistanceFunction();

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		CycConstants.initialiseAssertions(ResourceAccess
				.requestOntologySocket());
	}

	@Test
	public void testDistance() throws Exception {
		// All distances should be considered relative

		// Same element
		OntologyConcept collection = new OntologyConcept("Plant");
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		assertEquals(
				sut_.distance(new OntologyConcept("Plant"), collection, cyc),
				0, 0);

		// Non-collection
		collection = new OntologyConcept("SamuelLJackson");
		assertEquals(sut_.distance(new OntologyConcept("UmaThurman"),
				collection, cyc), -1, 0);

		// Simple case
		collection = new OntologyConcept("Mammal");
		float distA = sut_
				.distance(new OntologyConcept("Dog"), collection, cyc);
		float distB = sut_.distance(new OntologyConcept("City"), collection,
				cyc);
		assertTrue(distA + ", " + distB, distA < distB || distB == -1
				&& distA != -1);

		collection = new OntologyConcept("Plant");
		distA = sut_
				.distance(new OntologyConcept("Kiwi-Bird"), collection, cyc);
		distB = sut_.distance(new OntologyConcept("KiwifruitVine"), collection,
				cyc);
		assertTrue(distA + ", " + distB, distA > distB || distA == -1
				&& distB != -1);

		collection = new OntologyConcept("Mammal");
		distA = sut_.distance(new OntologyConcept("Dog"), collection, cyc);
		distB = sut_.distance(new OntologyConcept("Hound"), collection, cyc);
		assertTrue(distA + ", " + distB, distA < distB || distB == -1
				&& distA != -1);
	}

	@Test
	public void testAssertionDistance() throws Exception {
		SortedMap<Float, OntologyConcept> sortedMap = new TreeMap<>();
		OntologyConcept capitalCity = new OntologyConcept("capitalCity");
		OntologyConcept australia = new OntologyConcept("Australia");
		OntologyConcept canberra = new OntologyConcept(
				"CityOfCanberraAustralia");
		DefiniteAssertion assertion = new DefiniteAssertion(capitalCity, null,
				australia, canberra);
		float distance = sut_.assertionDistance(assertion);
		sortedMap.put(distance, capitalCity);

		OntologyConcept assets = new OntologyConcept("assets");
		assertion = new DefiniteAssertion(assets, null, australia, canberra);
		distance = sut_.assertionDistance(assertion);
		sortedMap.put(distance, assets);

		OntologyConcept capitalGain = new OntologyConcept("capitalGain");
		assertion = new DefiniteAssertion(capitalGain, null, australia,
				canberra);
		distance = sut_.assertionDistance(assertion);
		sortedMap.put(distance, capitalGain);
		Iterator<Float> order = sortedMap.keySet().iterator();
		// TODO Test: Check this - might require distance enforcement
		assertEquals(sortedMap.get(order.next()), capitalCity);
		assertEquals(sortedMap.get(order.next()), assets);
		assertEquals(sortedMap.get(order.next()), capitalGain);

		// Population
		// Not an ideal test
		sortedMap.clear();
		OntologyConcept numInhabitants = new OntologyConcept(
				"numberOfInhabitants");
		OntologyConcept num = new PrimitiveConcept(5812600);
		OntologyConcept nz = new OntologyConcept("NewZealand");
		assertion = new DefiniteAssertion(numInhabitants, null, nz, num);
		distance = sut_.assertionDistance(assertion);
		sortedMap.put(distance, numInhabitants);

		OntologyConcept popAtRisk = new OntologyConcept("populationAtRisk");
		assertion = new DefiniteAssertion(popAtRisk, null, nz, num);
		distance = sut_.assertionDistance(assertion);
		sortedMap.put(distance, popAtRisk);

		OntologyConcept popGrowthRate = new OntologyConcept(
				"populationGrowthRate");
		assertion = new DefiniteAssertion(popGrowthRate, null, nz, num);
		distance = sut_.assertionDistance(assertion);
		sortedMap.put(distance, popGrowthRate);
		order = sortedMap.keySet().iterator();
		assertEquals(sortedMap.get(order.next()), numInhabitants);
		assertEquals(sortedMap.get(order.next()), popAtRisk);
		assertEquals(sortedMap.get(order.next()), popGrowthRate);
	}
}
