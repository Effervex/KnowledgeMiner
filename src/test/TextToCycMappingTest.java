/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.IOException;
import java.util.Collection;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextToCyc_AnchorMap;
import knowledgeMiner.mapping.textToCyc.TextToCyc_BasicString;
import knowledgeMiner.mapping.textToCyc.TextToCyc_DateParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_FunctionParser;
import knowledgeMiner.mapping.textToCyc.TextToCyc_IntervalParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_NumericParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_TextSearch;
import knowledgeMiner.mapping.textToCyc.TextToCyc_WikifySearch;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TextToCycMappingTest {
	private static CycMapper mappingRoot_;
	private static WikipediaSocket wmi_;
	private static OntologySocket cyc_;

	/**
	 * Sets up the mapper beforehand.
	 * 
	 * @throws Exception
	 *             If something goes awry.
	 */
	@BeforeClass
	public static void setUp() throws Exception {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
		mappingRoot_ = new CycMapper();
		mappingRoot_.initialise();
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
		OntologyConcept.parsingArgs_ = false;
	}

	@Test
	public void testTextToCycMapping() throws IOException {
		OntologyConcept.parsingArgs_ = true;
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("Bill Clinton"),
				new OntologyConcept("BillClinton"), cyc_);
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("Gary Oldman"),
				new OntologyConcept("GaryOldman"), cyc_);
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("Ethan Hawke"),
				new OntologyConcept("EthanHawke"), cyc_);
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("Film director"),
				new OntologyConcept("Director-Movie"), cyc_);

		WeightedSet<OntologyConcept> results = mappingRoot_.mapTextToCyc(
				"Bill Clinton", false, true, false, true, wmi_, cyc_);
		Collection<OntologyConcept> mostLikely = results.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		assertTrue(mostLikely.contains(new OntologyConcept("BillClinton")));

		results = mappingRoot_.mapTextToCyc(
				"[[Gary Oldman]] (1990-1992) [[Ethan Hawke]] (1998-2004)",
				false, false, false, true, wmi_, cyc_);
		assertTrue(results.isEmpty());

		results = mappingRoot_.mapTextToCyc("Gary Oldman (1980-1983)", false,
				false, true, true, wmi_, cyc_);
		mostLikely = results.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		OntologyConcept temporalArg = new OntologyConcept("GaryOldman");
		temporalArg.setTemporalContext(new OntologyConcept(
				"(TimeIntervalInclusiveFn (YearFn '1980) (YearFn '1983))"));
		assertTrue(mostLikely.contains(temporalArg));

		results = mappingRoot_.mapTextToCyc("[[Gary Oldman]] (1980-1983)",
				false, false, true, true, wmi_, cyc_);
		mostLikely = results.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		temporalArg = new OntologyConcept("GaryOldman");
		temporalArg.setTemporalContext(new OntologyConcept(
				"(TimeIntervalInclusiveFn (YearFn '1980) (YearFn '1983))"));
		assertTrue(mostLikely.contains(temporalArg));

		results = mappingRoot_.mapTextToCyc("[[film director|director]]",
				false, false, true, true, wmi_, cyc_);
		mostLikely = results.getMostLikely();
		assertEquals(mostLikely.size(), 6);
		assertTrue(mostLikely.contains(new OntologyConcept("Director-Movie")));

		results = mappingRoot_.mapTextToCyc("origin", false, true, false, true,
				wmi_, cyc_);
		mostLikely = results.getMostLikely();
		assertEquals(mostLikely.size(), 4);
		assertTrue(mostLikely.contains(new OntologyConcept("fromLocation")));
		assertTrue(mostLikely.contains(new OntologyConcept("origin-RoundTrip")));
		assertTrue(mostLikely.contains(new OntologyConcept("startingPoint")));
		assertTrue(mostLikely.contains(new OntologyConcept(
				"(PresentTenseVersionFn fromLocation)")));
	}

	@Test
	public void testTextToCycPreprocessors() throws Exception {
		// Single term
		OntologyConcept.parsingArgs_ = true;
		HierarchicalWeightedSet<OntologyConcept> results = mappingRoot_
				.mapTextToCyc("Actress", true, false, true, true, wmi_, cyc_);
		HierarchicalWeightedSet<OntologyConcept>[] flattened = results
				.listHierarchy();
		assertEquals(flattened.length, 1);
		assertEquals(flattened[0].getMostLikely().size(), 3);
		OntologyConcept item = OntologyConcept.parseArgument("Actor");
		assertTrue(flattened[0].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("\"Actress\"");
		assertTrue(flattened[0].getMostLikely().contains(item));
		item = OntologyConcept
				.parseArgument("(CollectionIntersectionFn (TheSet FemaleHuman Actor))");
		assertTrue(flattened[0].getMostLikely().contains(item));

		// Dual terms
		results = mappingRoot_.mapTextToCyc("Actress, model", true, false,
				true, true, wmi_, cyc_);
		flattened = results.listHierarchy();
		assertEquals(flattened.length, 3);
		assertEquals(flattened[0].getMostLikely().size(), 1);
		assertTrue(flattened[0].getMostLikely().contains(
				OntologyConcept.parseArgument("\"Actress, model\"")));
		assertEquals(flattened[1].getMostLikely().size(), 3);
		assertTrue(flattened[1].getMostLikely().contains(
				OntologyConcept.parseArgument("\"Actress\"")));
		assertTrue(flattened[1].getMostLikely().contains(
				OntologyConcept.parseArgument("Actor")));
		assertTrue(flattened[1]
				.getMostLikely()
				.contains(
						OntologyConcept
								.parseArgument("(CollectionIntersectionFn (TheSet FemaleHuman Actor))")));
		assertEquals(flattened[2].getMostLikely().size(), 8);
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("\"model\"")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("FashionModel")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("ProfessionalModel")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("Model-Artifact")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("(MakingFn Model-Artifact)")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("model")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("DisplayingSomething")));
		assertTrue(flattened[2].getMostLikely().contains(
				OntologyConcept.parseArgument("ProductTypeByBrand")));

		// Newline terms
		results = mappingRoot_.mapTextToCyc("Actress\n model", true, false,
				true, true, wmi_, cyc_);
		flattened = results.listHierarchy();
		assertEquals(flattened.length, 3);
		item = OntologyConcept.parseArgument("\"Actress model\"");
		assertEquals(flattened[0].getMostLikely().size(), 1);
		assertTrue(flattened[0].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("Actor");
		assertEquals(flattened[1].getMostLikely().size(), 3);
		assertTrue(flattened[1].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("FashionModel");
		assertEquals(flattened[2].getMostLikely().size(), 8);
		assertTrue(flattened[2].getMostLikely().contains(item));

		results = mappingRoot_.mapTextToCyc("13 February 1967 (US)", true,
				false, true, true, wmi_, cyc_);
		flattened = results.listHierarchy();
		assertEquals(flattened.length, 2);
		item = OntologyConcept.parseArgument("\"13 February 1967 (US)\"");
		assertEquals(flattened[0].getMostLikely().size(), 1);
		assertTrue(flattened[0].getMostLikely().contains(item));
		item = OntologyConcept
				.parseArgument("(DayFn '13 (MonthFn February (YearFn '1967)))");
		assertEquals(flattened[1].getMostLikely().size(), 2);
		assertTrue(flattened[1].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("\"13 February 1967\"");
		assertTrue(flattened[1].getMostLikely().contains(item));

		results = mappingRoot_.mapTextToCyc("13 February 1967 (US)\n(Test",
				true, false, true, true, wmi_, cyc_);
		flattened = results.listHierarchy();
		assertEquals(flattened.length, 4);
		item = OntologyConcept.parseArgument("\"13 February 1967 (US) (Test\"");
		assertEquals(flattened[0].getMostLikely().size(), 1);
		assertTrue(flattened[0].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("\"(Test\"");
		assertEquals(flattened[1].getMostLikely().size(), 1);
		assertTrue(flattened[1].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("\"13 February 1967 (US)\"");
		assertEquals(flattened[2].getMostLikely().size(), 1);
		assertTrue(flattened[2].getMostLikely().contains(item));
		item = OntologyConcept
				.parseArgument("(DayFn '13 (MonthFn February (YearFn '1967)))");
		assertEquals(flattened[3].getMostLikely().size(), 2);
		assertTrue(flattened[3].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("\"13 February 1967\"");
		assertTrue(flattened[3].getMostLikely().contains(item));

		// Numerical values
		results = mappingRoot_
				.mapTextToCyc(
						"1,022,234,000 (2010, [[List of continents by population|2nd]])",
						true, false, true, true, wmi_, cyc_);
		flattened = results.listHierarchy();
		assertEquals(flattened.length, 6);
		item = OntologyConcept.parseArgument("\"1,022,234,000 (2010, 2nd)\"");
		assertEquals(flattened[0].getMostLikely().size(), 1);
		assertTrue(flattened[0].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("\"1,022,234,000\"");
		assertEquals(flattened[1].getMostLikely().size(), 2);
		assertTrue(flattened[1].getMostLikely().contains(item));
		item = OntologyConcept.parseArgument("'1022234000");
		assertTrue(flattened[1].getMostLikely().contains(item));
	}

	@Test
	public void testTextToCyc_TextSearch() {
		TextToCyc_TextSearch mapper = new TextToCyc_TextSearch(mappingRoot_);

		// Single mapping
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(
				"Bill Clinton", wmi_, cyc_);
		assertEquals(results.size(), 1);
		assertTrue(results.contains(new OntologyConcept("BillClinton")));

		// Multiple mapping
		results = mapper.mapSourceToTarget("Flea", wmi_, cyc_);
		assertEquals(results.size(), 2);
		assertTrue(results.contains(new OntologyConcept("Flea")));
		assertTrue(results.contains(new OntologyConcept("Flea-Musician")));

		// Predicate mapping
		results = mapper.mapSourceToTarget("occupation", wmi_, cyc_);
		assertEquals(results.size(), 4);
		assertTrue(results.contains(new OntologyConcept("occupation")));
		assertTrue(results.contains(new OntologyConcept("MilitaryOccupation")));
		assertTrue(results.contains(new OntologyConcept(
				"Occupation-AnimalActivity")));
		assertTrue(results.contains(new OntologyConcept(
				"PersonTypeByOccupation")));

		// No result
		results = mapper.mapSourceToTarget("Effervex", wmi_, cyc_);
		assertTrue(results.isEmpty());

		// Possible exception
		results = mapper.mapSourceToTarget(
				"[[Geographic Names Information System|GNIS]] feature ID",
				wmi_, cyc_);
		results = mapper.mapSourceToTarget(
				"[[Eastern Standard Time Zone|EST]]", wmi_, cyc_);
	}

	@Test
	public void testTextToCyc_WikifySearch() {
		TextToCyc_WikifySearch mapper = new TextToCyc_WikifySearch(mappingRoot_);

		// Single mapping
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(
				"Bill Clinton", wmi_, cyc_);
		Collection<OntologyConcept> first = results.getMostLikely();
		assertTrue(first.contains(new OntologyConcept("BillClinton")));
		assertEquals(first.size(), 1);

		// Resolved Multiple Mapping
		results = mapper.mapSourceToTarget("Flea", wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first.contains(new OntologyConcept("Flea-Musician")));
		assertEquals(first.size(), 1);

		// Slightly ambiguous
		results = mapper.mapSourceToTarget("Kiwi", wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first.contains(new OntologyConcept("Kiwi-Bird")));
		assertEquals(first.size(), 1);

		results = mapper.mapSourceToTarget("Effervex", wmi_, cyc_);
		assertTrue(results.isEmpty());

		String value = "Actress";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first.contains(OntologyConcept.parseArgument("Actor")));
		assertEquals(first.size(), 1);

		value = "Actress, model";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertEquals(results.size(), 0);
	}

	@Test
	public void testTextToCyc_ParseDate() throws Exception {
		TextToCyc_DateParse mapper = new TextToCyc_DateParse(mappingRoot_);
		OntologyConcept.parsingArgs_ = true;
		String value = "{{birthdate and age|1974|11|16}}";
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(value,
				wmi_, cyc_);
		Collection<OntologyConcept> first = results.getMostLikely();
		assertTrue(first
				.contains(OntologyConcept
						.parseArgument("(DayFn '16 (MonthFn November (YearFn '1974)))")));
		assertEquals(first.size(), 1);

		value = "{{Birth year and age|1978|9}}";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first.contains(OntologyConcept
				.parseArgument("(MonthFn September (YearFn '1978))")));
		assertEquals(first.size(), 1);

		value = "{{death date and age|mf=yes|1970|9|18|1942|11|27}}";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first
				.contains(OntologyConcept
						.parseArgument("(DayFn '18 (MonthFn September (YearFn '1970)))")));
		assertEquals(first.size(), 1);

		value = "{{start-date|Tuesday, September 11, 2001}}";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first
				.contains(OntologyConcept
						.parseArgument("(DayFn '11 (MonthFn September (YearFn '2001)))")));
		assertEquals(first.size(), 1);

		// TODO value =
		// "{{start-date|Tuesday, September 11, 2001 8:46 am|8:46&nbsp;am}}";
		// results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		// first = results.getMostLikely();
		// assertTrue(first
		// .contains(CycConcept
		// .parseArgument("(MinuteFn '46 (HourFn '8 (DayFn '2 (MonthFn September (YearFn '2001)))))")));
		// assertEquals(first.size(), 1);
	}

	@Test
	public void testTextToCyc_IntervalParse() throws Exception {
		OntologyConcept.parsingArgs_ = true;
		TextToCyc_IntervalParse mapper = new TextToCyc_IntervalParse(
				mappingRoot_);
		String value = "1987-2012";
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(value,
				wmi_, cyc_);
		Collection<OntologyConcept> first = results.getMostLikely();
		assertTrue(first
				.contains(OntologyConcept
						.parseArgument("(TimeIntervalInclusiveFn (YearFn '1987) (YearFn '2012))")));
		assertEquals(first.size(), 1);

		value = "1987 - 2012";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first
				.contains(OntologyConcept
						.parseArgument("(TimeIntervalInclusiveFn (YearFn '1987) (YearFn '2012))")));
		assertEquals(first.size(), 1);

		value = "1987-present";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first
				.contains(OntologyConcept
						.parseArgument("(TimeIntervalInclusiveFn (YearFn '1987) Now-Generally)")));
		assertEquals(first.size(), 1);

		value = "Ultra-Mega";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertEquals(results.size(), 0);

		value = "[[1987-2012]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertEquals(results.size(), 0);
	}

	@Test
	public void testTextToCyc_AnchorMap() throws Exception {
		TextToCyc_AnchorMap mapper = new TextToCyc_AnchorMap(mappingRoot_);

		String value = "[[Actor]]";
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(value,
				wmi_, cyc_);
		Collection<OntologyConcept> first = results.getMostLikely();
		assertTrue(first.contains(OntologyConcept.parseArgument("Actor")));
		assertEquals(first.size(), 1);

		value = "[[Actor|Bill Joeson]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		first = results.getMostLikely();
		assertTrue(first.contains(OntologyConcept.parseArgument("Actor")));
		assertEquals(first.size(), 1);

		value = "[[Boston]], [[U.S.]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.isEmpty());
	}

	@Test
	public void testTextToCyc_NumericParse() throws Exception {
		TextToCyc_NumericParse mapper = new TextToCyc_NumericParse(mappingRoot_);
		String value = "Actress";
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(value,
				wmi_, cyc_);
		assertEquals(results.size(), 0);

		value = "53";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept.parseArgument("'53")));
		assertEquals(results.size(), 1);

		value = "6.457E-42";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results
				.contains(OntologyConcept.parseArgument("'6.457E-42")));
		assertEquals(results.size(), 1);
	}

	@Test
	public void testTextToCyc_BasicString() throws Exception {
		TextToCyc_BasicString mapper = new TextToCyc_BasicString(mappingRoot_);
		String value = "Actress";
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(value,
				wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("\"Actress\"")));
		assertEquals(results.size(), 1);

		value = "[[Actor]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept.parseArgument("\"Actor\"")));
		assertEquals(results.size(), 1);

		value = "[[Cat|dog]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept.parseArgument("\"dog\"")));
		assertEquals(results.size(), 1);

		value = "[[Recursive (query)|[[Anchor|Within an anchor]]]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("\"Within an anchor\"")));
		assertEquals(results.size(), 1);

		value = "[[Recursive (query)|[[Anchor]]]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results
				.contains(OntologyConcept.parseArgument("\"Anchor\"")));
		assertEquals(results.size(), 1);

		value = "[[Recursive (query)|[[Anchor]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("\"[[Recursive (query)|Anchor\"")));
		assertEquals(results.size(), 1);

		value = "[[Recursive (query)|Anchor]]]]";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("\"Anchor]]\"")));
		assertEquals(results.size(), 1);

		value = "{{Year|1|2|3}}";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertEquals(results.size(), 0);

		value = "Some text {{Year|1|2|3}}";
		results = mapper.mapSourceToTarget(value, wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("\"Some text\"")));
		assertEquals(results.size(), 1);
	}

	@Test
	public void testTextToCyc_FunctionParser() throws Exception {
		OntologyConcept.parsingArgs_ = true;
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("Horse"),
				new OntologyConcept("Horse"), cyc_);
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("Lamivudine"),
				new OntologyConcept("Lamivudine"), cyc_);
		ConceptMiningTask.addMapping(wmi_.getArticleByTitle("New Zealand"),
				new OntologyConcept("NewZealand"), cyc_);

		TextToCyc_FunctionParser mapper = new TextToCyc_FunctionParser(
				mappingRoot_);
		// Technically a function - but no mappable target
		WeightedSet<OntologyConcept> results = mapper.mapSourceToTarget(
				"Small horse", wmi_, cyc_);
		assertEquals(results.size(), 0);

		results = mapper.mapSourceToTarget("Small [[horse]]", wmi_, cyc_);
		OntologyConcept funcConcept = new OntologyConcept("SmallFn", "Horse");
		assertTrue(results.contains(funcConcept));
		assertEquals(results.getMostLikely().size(), 5);
		assertEquals(results.size(), 5);

		// Function with multi-word alias
		// Also, conflicting functions (SideEffectOf/SideEffectOfDrugSubstance)
		results = mapper.mapSourceToTarget("Side effect of [[lamivudine]]",
				wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("(SideEffectsOfUsingDrugTypeFn Lamivudine)")));
		assertEquals(results.size(), 1);

		// Function with "'s"
		results = mapper.mapSourceToTarget("[[New Zealand]]'s government",
				wmi_, cyc_);
		assertTrue(results.contains(OntologyConcept
				.parseArgument("(GovernmentFn NewZealand)")));
		assertEquals(results.size(), 1);
	}
}
