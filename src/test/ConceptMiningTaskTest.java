/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class ConceptMiningTaskTest {
	private static WMISocket wmi_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		KnowledgeMiner.getInstance();
		wmi_ = ResourceAccess.requestWMISocket();
	}

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.ConceptMiningTask#createNewCycTermName(int, int)}.
	 * 
	 * @throws Exception
	 * @throws IOException
	 */
	@Test
	public void testCreateNewCycTermName() throws IOException, Exception {
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		OntologyConcept cycTerm = ConceptMiningTask.createNewCycTermName(
				"Enslaved (band)", "Musical ensemble", ontology);
		assertEquals(cycTerm.toString(), "Enslaved-Band");

		// Existing context
		cycTerm = ConceptMiningTask.createNewCycTermName("Enslaved (band)",
				"Musical ensemble", ontology);
		assertEquals(cycTerm.toString(), "Enslaved-Band");

		// Parental context
		cycTerm = ConceptMiningTask.createNewCycTermName("Batman", "Comics",
				ontology);
		assertEquals(cycTerm.toString(), "Batman-Comics");

		// Equals a (lowercase) predicate
		cycTerm = ConceptMiningTask.createNewCycTermName("Ionization energy",
				"Ion", ontology);
		assertEquals(cycTerm.toString(), "IonizationEnergy-Ion");
	}

	@Test
	public void testTest() {
		ConceptMiningTask cmt = new ConceptMiningTask(new ConceptModule(
				new OntologyConcept("YearFn", "'2010")), -1);
		cmt.run();
	}
}
