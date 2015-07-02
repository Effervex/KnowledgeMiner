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

import java.util.Collection;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

public class CycMapperTest {
	/** The mapper under test. */
	private static CycMapper mapper_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
		OntologyConcept.parsingArgs_ = false;
	}

	@Test
	public void testMapCycToWikipedia() {
		try {
			WeightedSet<Integer> mapped = mapper_.mapCycToWikipedia(
					new OntologyConcept("Flea"), null, wmi_, cyc_);
			Collection<Integer> first = mapped.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(wmi_.getArticleByTitle("Flea")));

			mapped = mapper_.mapCycToWikipedia(new OntologyConcept(
					"BillClinton"), null, wmi_, cyc_);
			first = mapped.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(wmi_.getArticleByTitle("Bill Clinton")));

			// Function mapping
			mapped = mapper_.mapCycToWikipedia(new OntologyConcept(
					"(TerritoryFn CityOfBostonMA)"), null, wmi_, cyc_);
			first = mapped.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(wmi_.getArticleByTitle("Boston")));
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception!");
		}
	}

	@Test
	public void testMapRelationToPredicate() {
		WeightedSet<OntologyConcept> results = mapper_.mapRelationToPredicate(
				"birth_date", wmi_, cyc_);
		Collection<OntologyConcept> likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		OntologyConcept item = new OntologyConcept("birthDate");
		assertTrue(likely.contains(item));

		results = mapper_.mapRelationToPredicate("birth_place", wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("birthPlace");
		assertTrue(likely.contains(item));

		results = mapper_.mapRelationToPredicate("occupation", wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("occupation");
		assertTrue(likely.contains(item));

		results = mapper_.mapRelationToPredicate("death_date", wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("dateOfDeath");
		assertTrue(likely.contains(item));
	}

	@Test
	public void testMapTextToCyc() {
		// Simple
		OntologyConcept.parsingArgs_ = true;
		HierarchicalWeightedSet<OntologyConcept> results = mapper_
				.mapTextToCyc("BillClinton", false, true, false, true, wmi_, cyc_);
		Collection<OntologyConcept> likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		OntologyConcept item = new OntologyConcept("BillClinton");
		assertTrue(likely.contains(item));

		// Anchors
		results = mapper_.mapTextToCyc("Kiwi [[Record producer|Producer]]",
				false, true, false, true, wmi_, cyc_);
		results = (HierarchicalWeightedSet<OntologyConcept>) results
				.cleanEmptyParents();
		WeightedSet<OntologyConcept> merged = WeightedSet.mergeSets(results
				.getSubSets());
		likely = merged.getMostLikely();
		assertEquals(likely.size(), 4);
		item = new OntologyConcept("Kiwi-Bird");
		assertTrue(likely.contains(item));

		// Brackets
		results = mapper_.mapTextToCyc("Kiwi (the bird) Kid", false, true,
				false, true, wmi_, cyc_);
		results = (HierarchicalWeightedSet<OntologyConcept>) results
				.cleanEmptyParents();
		merged = WeightedSet.mergeSets(results.getSubSets());
		likely = merged.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("Kiwi-Bird");
		assertTrue(likely.contains(item));

		// Difficult
		results = mapper_.mapTextToCyc("Kiwi", false, true, false, true, wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("Kiwi-Bird");
		assertTrue(likely.contains(item));

		// Potentially incorrect
		results = mapper_.mapTextToCyc("Flea", false, true, false, true, wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("Flea-Musician");
		assertTrue(likely.contains(item));

		// TIME FUNCTIONS
		// Year
		results = mapper_.mapTextToCyc("1987", false, true, false, true, wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("(YearFn '1987)");
		assertTrue(likely.contains(item));

		// Year Month
		results = mapper_.mapTextToCyc("4, 1987", false, true, false, true,
				wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("(MonthFn April (YearFn '1987))");
		assertTrue(likely.contains(item));

		// Day Year Month
		results = mapper_.mapTextToCyc("4, 4, 1987", false, true, false, true,
				wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("(DayFn '4 (MonthFn April (YearFn '1987)))");
		assertTrue(likely.contains(item));

		results = mapper_.mapTextToCyc("1987, 4, 5", false, true, false, true,
				wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("(DayFn '5 (MonthFn April (YearFn '1987)))");
		assertTrue(likely.contains(item));

		results = mapper_.mapTextToCyc("April 4, 1987", false, true, false,
				true, wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("(DayFn '4 (MonthFn April (YearFn '1987)))");
		assertTrue(likely.contains(item));

		results = mapper_.mapTextToCyc("4 April, 1987", false, true, false,
				true, wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept("(DayFn '4 (MonthFn April (YearFn '1987)))");
		assertTrue(likely.contains(item));

		// Day Month
		results = mapper_.mapTextToCyc("4 April", false, true, false, true,
				wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept(
				"(DayFn '4 (MonthFn April TheYear-Indexical))");
		assertTrue(likely.contains(item));

		results = mapper_.mapTextToCyc("April 4", false, true, false, true,
				wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept(
				"(DayFn '4 (MonthFn April TheYear-Indexical))");
		assertTrue(likely.contains(item));

		// Weekday date
		results = mapper_.mapTextToCyc("Tuesday, September 11, 2001", false,
				true, false, true, wmi_, cyc_);
		likely = results.getMostLikely();
		assertEquals(likely.size(), 1);
		item = new OntologyConcept(
				"(DayFn '11 (MonthFn September (YearFn '2001)))");
		assertTrue(likely.contains(item));
	}

	@Test
	public void testMapWikipediaToCyc() {
		try {
			OntologyConcept.parsingArgs_ = true;
			// Test obvious term (Bill Clinton)
			WeightedSet<OntologyConcept> result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Bill Clinton"), wmi_, cyc_);
			Collection<OntologyConcept> first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("BillClinton")));

			// Test not completely obvious (Dog => Dog and Hotdog when
			// searched)
			result = mapper_.mapWikipediaToCyc(wmi_.getArticleByTitle("Dog"),
					wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("Dog")));

			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Batman (comic strip)"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept(
					"Batman-TheComicStrip")));

			result = mapper_.mapWikipediaToCyc(wmi_.getArticleByTitle("Konin"),
					wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept(
					"Konin-ProvincePoland")));

			// Test a difficult query (Jaguar the cat)
			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Jaguar"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("JaguarCat")));

			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Jaguar Cars"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("JaguarCar")));
			// TODO Would prefer the company

			// Test an article not present in Cyc
			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Stam1na"), wmi_, cyc_);
			assertEquals(result.size(), 0);

			// Working with numbers
			result = mapper_.mapWikipediaToCyc(wmi_.getArticleByTitle("42"),
					wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("(YearFn '42)")));

			result = mapper_.mapWikipediaToCyc(wmi_.getArticleByTitle("Fives"),
					wmi_, cyc_);
			assertEquals(result.size(), 0);

			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Chigoe flea"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("ChigoeFlea")));

			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Programmer"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first
					.contains(new OntologyConcept("ComputerProgrammer")));

			// result = mapper_.mapWikipediaToCyc(wmi_
			// .getArticleByTitle("John Martin (oceanographer)"));
			// assertEquals(result.size(), 0);

			// Function mapping
			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Obstetrics"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept(
					"(FocalFieldOfStudyFn Obstetrician)")));

			result = mapper_.mapWikipediaToCyc(
					wmi_.getArticleByTitle("Boston"), wmi_, cyc_);
			first = result.getMostLikely();
			assertEquals(first.size(), 1);
			assertTrue(first.contains(new OntologyConcept("CityOfBostonMA")));
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
		mapper_ = new CycMapper();
		mapper_.initialise();
		KnowledgeMiner.getInstance();
	}
}
