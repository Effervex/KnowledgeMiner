/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.cycToWiki.CycToWiki_ContextRelatedSynonyms;
import knowledgeMiner.mapping.cycToWiki.CycToWiki_TitleMatching;
import knowledgeMiner.mapping.cycToWiki.CycToWiki_VoteSynonyms;

import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

public class CycToWikiMappingTest {
	private static CycMapper mappingRoot_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

	@Test
	public void testCycToWiki_ContextRelatedSynonyms() {
		try {
			CycToWiki_ContextRelatedSynonyms mapper = new CycToWiki_ContextRelatedSynonyms(
					mappingRoot_);

			// Find Jaguar (Cars) related articles
			WeightedSet<Integer> mapped = mapper.mapSourceToTarget(
					new OntologyConcept("JaguarTheCompany"), wmi_, cyc_);
			int first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Jaguar Cars");

			// Functions
			mapped = mapper.mapSourceToTarget(new OntologyConcept("ActorSlot"),
					wmi_, cyc_);
			assertEquals(mapped.size(), 0);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception!");
		}
	}

	@Test
	public void testCycToWiki_TitleMatching() {
		try {
			CycToWiki_TitleMatching mapper = new CycToWiki_TitleMatching(
					mappingRoot_);
			// Simple single answer
			WeightedSet<Integer> mapped = mapper.mapSourceToTarget(
					new OntologyConcept("BillClinton"), wmi_, cyc_);
			assertEquals(mapped.size(), 1);
			int first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Bill Clinton");

			// Possibly several, but only one named
			mapped = mapper.mapSourceToTarget(new OntologyConcept("Dog"), wmi_,
					cyc_);
			assertEquals(mapped.size(), 1);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Dog");

			// Redirected
			mapped = mapper.mapSourceToTarget(new OntologyConcept("SOAD"),
					wmi_, cyc_);
			assertEquals(mapped.size(), 1);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "System of a Down");

			// Sense
			mapped = mapper.mapSourceToTarget(new OntologyConcept(
					"TheCastle-Film"), wmi_, cyc_);
			assertEquals(mapped.size(), 1);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "The Castle (film)");

			// Sense with 'The'
			mapped = mapper.mapSourceToTarget(new OntologyConcept(
					"Batman-TheComicStrip"), wmi_, cyc_);
			assertEquals(mapped.size(), 1);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Batman (comic strip)");

			// No article
			mapped = mapper.mapSourceToTarget(new OntologyConcept(
					"HumanWithMohawkHairstyle"), wmi_, cyc_);
			assertTrue(mapped.isEmpty());

			mapped = mapper.mapSourceToTarget(new OntologyConcept("Fantasy"),
					wmi_, cyc_);
			assertEquals(mapped.size(), 1);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Fantasy");
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception!");
		}
	}

	@Test
	public void testCycToWiki_VoteSynonyms() {
		try {
			CycToWiki_VoteSynonyms mapper = new CycToWiki_VoteSynonyms(
					mappingRoot_);
			// No synonyms
			WeightedSet<Integer> mapped = mapper.mapSourceToTarget(
					new OntologyConcept("Dog"), wmi_, cyc_);
			// assertEquals(mapped.size(), 88);
			int first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Dog");

			// Synonym mapping (Planet Earth leads to disambiguation, but Earth
			// gives an article)
			mapped = mapper.mapSourceToTarget(
					new OntologyConcept("PlanetEarth"), wmi_, cyc_);
			// assertEquals(mapped.size(), 111);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Earth");

			mapped = mapper.mapSourceToTarget(
					new OntologyConcept("GreekPerson"), wmi_, cyc_);
			// assertEquals(mapped.size(), 231);
			first = mapped.getOrdered().first();
			assertEquals(wmi_.getPageTitle(first, true), "Greeks");
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception!");
		}
	}

	/**
	 * Sets up the mapper beforehand.
	 * 
	 * @throws Exception
	 *             If something goes awry.
	 */
	@BeforeClass
	public static void setUp() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		mappingRoot_ = new CycMapper();
		mappingRoot_.initialise();
	}
}
