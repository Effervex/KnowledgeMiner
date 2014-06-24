/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import io.ResourceAccess;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.ConceptMiningTask;
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
		OntologyConcept cycTerm = ConceptMiningTask.createNewCycTermName(
				wmi_.getArticleByTitle("Enslaved (band)"),
				wmi_.getArticleByTitle("Musical ensemble"), wmi_);
		assertEquals(cycTerm.toString(), "Enslaved-Band");

		// Existing context
		cycTerm = ConceptMiningTask.createNewCycTermName(
				wmi_.getArticleByTitle("Enslaved (band)"),
				wmi_.getArticleByTitle("Musical ensemble"), wmi_);
		assertEquals(cycTerm.toString(), "Enslaved-Band");

		// Parental context
		cycTerm = ConceptMiningTask.createNewCycTermName(
				wmi_.getArticleByTitle("Batman"),
				wmi_.getArticleByTitle("Comics"), wmi_);
		assertEquals(cycTerm.toString(), "Batman-Comics");

		// Equals a (lowercase) predicate
		cycTerm = ConceptMiningTask.createNewCycTermName(
				wmi_.getArticleByTitle("Ionization energy"),
				wmi_.getArticleByTitle("Ion"), wmi_);
		assertEquals(cycTerm.toString(), "IonizationEnergy-Ion");
	}
}
