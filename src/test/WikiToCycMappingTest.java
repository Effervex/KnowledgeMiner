/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.wikiToCyc.WikiToCyc_TitleMatching;
import knowledgeMiner.mapping.wikiToCyc.WikiToCyc_VoteSynonyms;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

public class WikiToCycMappingTest {
	private static CycMapper mappingRoot_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

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

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	@Test
	public void testWikiToCyc_TitleMatching() throws Exception {
		WikiToCyc_TitleMatching mapper = new WikiToCyc_TitleMatching(
				mappingRoot_);
		// Simple single answer
		WeightedSet<OntologyConcept> mapped = mapper.mapSourceToTarget(
				wmi_.getArticleByTitle("Bill Clinton"), wmi_, cyc_);
		assertEquals(mapped.size(), 1);
		OntologyConcept first = mapped.getOrdered().first();
		assertEquals(first, new OntologyConcept("BillClinton"));

		// Possibly several, but only one named
		mapped = mapper.mapSourceToTarget(wmi_.getArticleByTitle("Dog"), wmi_, cyc_);
		assertEquals(mapped.size(), 2);
		assertTrue(mapped.contains(new OntologyConcept("Dog")));
		assertTrue(mapped.contains(new OntologyConcept("HotDog")));
		assertEquals(mapped.getWeight(new OntologyConcept("Dog")),
				mapped.getWeight(new OntologyConcept("HotDog")), 0.0001);

		// Redirected
		mapped = mapper.mapSourceToTarget(
				wmi_.getArticleByTitle("System of a Down"), wmi_, cyc_);
		assertEquals(mapped.size(), 1);
		first = mapped.getOrdered().first();
		assertEquals(first, new OntologyConcept("SystemOfADown"));

		// Sense
		mapped = mapper.mapSourceToTarget(
				wmi_.getArticleByTitle("Titanic (1997 film)"), wmi_, cyc_);
		assertEquals(mapped.size(), 2);
		first = mapped.getOrdered().first();
		assertTrue(mapped.contains(new OntologyConcept("Titanic-TheMovie")));
		assertTrue(mapped.contains(new OntologyConcept("TitanicSoundtrack")));
		assertEquals(mapped.getWeight(new OntologyConcept("Titanic-TheMovie")),
				mapped.getWeight(new OntologyConcept("TitanicSoundtrack")),
				0.0001);

		// Sense with 'The'
		mapped = mapper.mapSourceToTarget(
				wmi_.getArticleByTitle("Batman (comic strip)"), wmi_, cyc_);
		assertEquals(mapped.size(), 1);
		first = mapped.getOrdered().first();
		assertEquals(first, new OntologyConcept("Batman-TheComicStrip"));

		// No article
		mapped = mapper.mapSourceToTarget(wmi_.getArticleByTitle("Stam1na"),
				wmi_, cyc_);
		assertTrue(mapped.isEmpty());

		mapped = mapper.mapSourceToTarget(wmi_.getArticleByTitle("Fantasy"),
				wmi_, cyc_);
		assertEquals(mapped.size(), 2);
		first = mapped.getOrdered().first();
		assertTrue(mapped.contains(new OntologyConcept(
				"PropositionalConceptualWork-FantasyGenre")));
		assertTrue(mapped.contains(new OntologyConcept("Fantasy")));
		assertEquals(mapped.getWeight(new OntologyConcept("Fantasy")),
				mapped.getWeight(new OntologyConcept(
						"PropositionalConceptualWork-FantasyGenre")), 0.0001);
	}

	@Test
	public void testWikiToCyc_VoteSynonyms() throws IOException {
		WikiToCyc_VoteSynonyms mapper = new WikiToCyc_VoteSynonyms(mappingRoot_);
		WeightedSet<OntologyConcept> mapped = mapper.mapSourceToTarget(
				wmi_.getArticleByTitle("Bill Clinton"), wmi_, cyc_);
		OntologyConcept first = mapped.getOrdered().first();
		assertEquals(first, new OntologyConcept("BillClinton"));
	}
}
