/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.SentenceParserHeuristic;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import cyc.OntologyConcept;
import cyc.CycConstants;

public class SentenceParserHeuristicTest {

	private static SentenceParserHeuristic miner_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper(null);
		mapper.initialise();
		miner_ = new SentenceParserHeuristic(mapper);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() throws Exception {
		wmi_.clearCachedArticles();
	}

	@Test
	public void testExtractAssertions() throws Exception {
		String sentence = "Bill is an [[actor]].";
		ConceptModule info = new ConceptModule(OntologyConcept.PLACEHOLDER);
		// info.setParentTerm(new CycConcept("ComputerScientist"));
		Collection<AssertionQueue> assertions = miner_.extractAssertions(
				sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		WeightedSet<MinedAssertion> merged = HierarchicalWeightedSet
				.mergeSets(assertions);
		Collection<MinedAssertion> mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Actor"), null, null)));

		info.clearInformation();
		sentence = "Bill was an [[actor]], [[film director|director]], and [[film producer|producer]].";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 3);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Actor"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Director-Movie"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Producer-Movie"), null, null)));

		// No annotations
		info.clearInformation();
		sentence = "Bill is a director, actor, and producer.";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 5);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Actor"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("DirectorOfOrganization"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Producer-Movie"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Producer"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("ManufacturingOrganization"), null, null)));

		// More than isa
		info.clearInformation();
		sentence = "Hobbiton, located in Matamata, is a tourist attraction.";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("TouristAttraction"), null, null)));
		// Damn, 'located in' no longer applies
		// assertTrue(mostLikely.contains(new MinedAssertion(
		// "objectFoundInLocation", CycConcept.PLACEHOLDER,
		// new CycConcept("Matamata-CountyNewZealand"), null, null)));

		// PP after NP
		info.clearInformation();
		sentence = "Manila is the capital city of The Phillippines.";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("CapitalCityOfRegion"), null, null)));
		// assertTrue(mostLikely.contains(new MinedAssertion(new CycConcept(
		// "capitalCity"), CycConcept.PLACEHOLDER, new CycConcept(
		// "ThePhillippines"), null, null)));

		// PP after VP

	}

	/**
	 * Tests the extractCollection method.
	 * 
	 * @throws Exception
	 *             Should something go awry...
	 */
	@Test
	public void testExtractAssertionsCopy() throws Exception {
		// Basic single collection
		ConceptModule info = new ConceptModule(OntologyConcept.PLACEHOLDER);
		String sentence;
		Collection<AssertionQueue> assertions;
		WeightedSet<MinedAssertion> merged;
		Collection<MinedAssertion> mostLikely;

		sentence = "The '''visored bat''', (Sphaeronycteris toxophyllum), is a [[bat]] species from tropical [[South America]].";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 2);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Bat-Mammal"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("BiologicalSpecies"), null, null)));

		sentence = "The '''date palm''' ('''') is a [[Arecaceae|palm]] in the genus ''[[Phoenix (plant)|Phoenix]]'', cultivated for its edible sweet [[fruit]].";
		info.clearInformation();
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("PalmTree"), null, null)));

		// Basic 2 collection
		sentence = "'''Tortellini Western''' is an [[animated film]] [[Television program|series]] on NickToons Network.";
		info.clearInformation();
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 3);
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("AnimatedMovie"), null, null)));
		assertTrue(mostLikely.contains(new MinedAssertion(
				CycConstants.ISA_GENLS.getConcept(), OntologyConcept.PLACEHOLDER,
				new OntologyConcept("Series"), null, null)));

		// Hyphenated 2 collection
		sentence = "'''Laith Nobari''' or '''Laith Naseri''' (born in [[Baghdad]], [[Iraq]] on September 23, 1977 Officially) is an [[Iraq]]-born [[Moaved]] [[Iranian Arab]] retired [[association football|football player]] who He has played for [[Persepolis F.C.|Persepolis]] and has represented [[Iran national football team]].";
		info.clearInformation();
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 7);

		// Comma separated list
		sentence = "'''David Aaronovitch''' (born [[July 8]], [[1954]]) is an [[England|English]] [[author]], [[broadcaster]] and [[journalist]].";
		info.clearInformation();
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
		merged = WeightedSet.mergeSets(assertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 3);

		// Several things
		// result = miner_
		// .extractParentLabels(
		// miner_.regExpMatch(
		// "'''Alexander \"Skunder\" Boghossian'''",
		// "'''Alexander \"Skunder\" Boghossian''' (1937 in [[Addis Ababa]], [[Ethiopia]] &ndash; [[May 4]], [[2003]]) was an [[Ethiopia]]n-[[Armenia]]n [[Painting|painter]].",
		// info, wmi_), false, wmi_);
		// assertTrue(result.contains("Ethiopia|Ethiopian"));
		// assertTrue(result.contains("Armenia|Armenian"));
		// assertTrue(result.contains("Painting|painter"));
		//
		// // X is a Y of Z
		// result = miner_
		// .extractParentLabels(
		// miner_.regExpMatch(
		// "'''Basque Shepherd Dog'''",
		// "The '''Basque Shepherd Dog''' ({{lang-eu|Euskal artzain txakurra}}) is a [[dog breed|breed]] of [[dog]] originating in the [[Basque Country (historical territory)|Basque Country]].",
		// info, wmi_), false, wmi_);
		// assertTrue(result.contains("dog breed|breed"));
		// assertTrue(result.contains("dog"));
		//
		// // Checking regular loosening
		// result = miner_
		// .extractParentLabels(
		// miner_.regExpMatch(
		// "'''Human flea'''",
		// "The many so-called '''Human flea''' (''Pulex irritans'' L., 1758) is a super cosmopolitan [[Siphonaptera|flea]] species that has, in spite of the common name, a wide host spectrum.",
		// info, wmi_), false, wmi_);
		// assertTrue(result.contains("Siphonaptera|flea"));
		//
		// // Some sort of infinite loop issue
		// result = miner_
		// .extractParentLabels(
		// miner_.regExpMatch(
		// "'''Ivor Hugh Norman Evans'''",
		// "'''Ivor Hugh Norman Evans''' (1886-1957) was a British [[anthropologist]], [[ethnographer]] and [[archaeologist]] who spent most of his working life in peninsular [[British Malaya]] (now [[Malaysia]]) and in [[North Borneo]] (now [[Sabah]], Malaysia).",
		// info, wmi_), false, wmi_);
		// assertTrue(result.contains("anthropologist"));
		// assertTrue(result.contains("ethnographer"));
		// assertTrue(result.contains("archaeologist"));
		//
		// result = miner_
		// .extractParentLabels(
		// miner_.regExpMatch(
		// "'''List of the NCAA Division I Men's Basketball Tournament Final Four Participants'''",
		// "'''List of the NCAA [[Division I]] Men's Basketball Tournament Final Four Participants''' ''' Year''' ''' School''' ''' Conference''' ''' Tournament Region''' ''' Final Four Outcome''' [[1939 NCAA Men's Division I Basketball Tournament|1939]] '''Oregon '''Pacific Coast '''West '''National Champions''' ''Ohio State ''Big Ten ''East ''National Runner-Up'' Oklahoma Big Six West Villanova Independent East [[1940 NCAA Men's Division I Basketball Tournament|1940]] '''Indiana '''Big Ten '''East '''National Champions''' ''Kansas ''Big Six ''West ''National Runner-Up'' Duquesne Independent East USC Pacific Coast West [[1941 NCAA Men's Division I Basketball Tournament|1941]] '''Wisconsin '''Big Ten '''East '''National Champions''' ''Washington State ''Pacific Coast ''West ''National Runner-Up'' Arkansas Southwest West Pittsburgh Independent East [[1942 NCAA Men's Division I Basketball Tournament|1942]] '''Stanford '''Pacific Coast '''West '''National Champions''' ''Dartmouth ''EIL (Ivy) ''East ''National Runner-Up'' Colorado Mountain States West Kentucky Southeastern East [[1943 NCAA Men's Division I Basketball Tournament|1943]] '''Wyoming '''Mountain States '''West '''National Champions''' ''Georgetown ''Independent ''East ''National Runner-Up'' DePaul Independent East Texas Southwest West [[1944 NCAA Men's Division I Basketball Tournament|1944]] '''Utah '''Mountain States '''West '''National Champions''' ''Dartmouth ''EIL (Ivy) ''East ''National Runner-Up'' Iowa State Big Six West Ohio State Big Ten East [[1945 NCAA Men's Division I Basketball Tournament|1945]] '''Oklahoma A&M '''Missouri Valley '''West '''National Champions''' ''NYU ''Independent ''East ''National Runner-Up'' Arkansas Southwest West Ohio State Big Ten East [[1946 NCAA Men's Division I Basketball Tournament|1946]] '''Oklahoma A&M '''Missouri Valley '''West '''National Champions''' ''North Carolina ''Southern ''East ''National Runner-Up'' California Pacific Coast West Ohio State Big Ten East [[1947 NCAA Men's Division I Basketball Tournament|1947]] '''Holy Cross '''Independent '''East '''National Champions''' ''Oklahoma ''Big Six ''West ''National Runner-Up'' CCNY Independent East Texas Southwest West [[1948 NCAA Men's Division I Basketball Tournament|1948]] '''Kentucky '''Southeastern '''East '''National Champions''' ''Baylor ''Southwest ''West ''National Runner-Up'' Kansas State Big Seven West Holy Cross Independent East [[1949 NCAA Men's Division I Basketball Tournament|1949]] '''Kentucky '''Southeastern '''East '''National Champions''' ''Oklahoma A&M ''Missouri Valley ''West ''National Runner-Up'' Oregon State Pacific Coast West Illinois Big Ten East [[1950 NCAA Men's Division I Basketball Tournament|1950]] '''CCNY '''Independent '''East '''National Champions''' ''Bradley ''Missouri Valley ''West ''National Runner-Up'' Baylor Southwest West North Carolina State Southern East [[1951 NCAA Men's Division I Basketball Tournament|1951]] '''Kentucky '''Southeastern '''East '''National Champions''' ''Kansas State ''Big Seven ''West ''National Runner-Up'' Oklahoma A&M Missouri Valley West Illinois Big Ten East [[1952 NCAA Men's Division I Basketball Tournament|1952]] '''Kansas '''Big Seven '''West '''National Champions''' ''St.",
		// info, wmi_), false, wmi_);
		// assertTrue(result.isEmpty());

		sentence = "'''Mount Taranaki''', or '''Mount Egmont''', is an [[volcano|active]] but quiescent [[stratovolcano]] in the [[Taranaki region|Taranaki]] region on the west coast of [[New Zealand]]'s [[North Island]].";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);

		sentence = "'''''Toy Story''''' is a 1995 American [[computer animation|computer-animated]] [[family film|family]] [[buddy film|buddy]] [[comedy film]] produced by [[Pixar]] and directed by [[John Lasseter]].";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);

		sentence = "'HMS Reindeer' ( also Rein Deer ) was a Royal Navy 18-gun Cruizer class brig-sloop of the Royal Navy, built by Samuel & Daniel Brent at Rotherhithe and was launched in 1804.";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);

		sentence = "An 'exhibition', in the most general sense, is an organized presentation and display of a selection of items.";
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);

		sentence = "In computer and machine-based telecommunications terminology, a 'character' is a unit of information that roughly corresponds to a grapheme, grapheme-like unit, or symbol, such as in an alphabet or syllabary in the written form of a natural language.";
		info.clearInformation();
		assertions = miner_.extractAssertions(sentence, wmi_, cyc_, null);
		assertFalse(assertions.isEmpty());
	}
}
