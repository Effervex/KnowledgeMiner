/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;
import java.util.SortedSet;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.InfoboxRelationMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;
import cyc.OntologyConcept;
import cyc.CycConstants;
import cyc.PrimitiveConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class InfoboxRelationMinerTest {
	private static OntologySocket cyc_;
	private static InfoboxRelationMiner miner_;
	private static WMISocket wmi_;

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.InfoboxRelationMiner#mineArticleInternal(MinedInformation, int, WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public void testMineArticleInternal() throws Exception {
		ConceptModule module;
		MinedInformation info;
		SortedSet<AssertionQueue> assertions;
		WeightedSet<MinedAssertion> merged;
		Collection<MinedAssertion> likely;
		MinedAssertion assertion;

		// module = new ConceptModule(wmi_.getArticleByTitle("James Rollins"),
		// new CycConcept("Veterinarian"),
		// wmi_.getArticleByTitle("Veterinarian"), true);
		// info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
		// cyc_);
		//
		// module = new ConceptModule(new CycConcept("PhoenicianLanguage"),
		// wmi_.getArticleByTitle("Phoenician language"), 1.0, true);
		// info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
		// cyc_);
		//
		// module = new ConceptModule(new CycConcept("KonradAdenauer"),
		// wmi_.getArticleByTitle("Konrad Adenauer"), 1.0, true);
		// info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
		// cyc_);
		//
		// module = new ConceptModule(new CycConcept("ContinentOfAfrica"),
		// wmi_.getArticleByTitle("Africa"), 1.0, true);
		// info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
		// cyc_);

		module = new ConceptModule(wmi_.getArticleByTitle("Allan Dwan"));
		info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
				cyc_);

		module = new ConceptModule(wmi_.getArticleByTitle("Jaguar"));
		info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
				cyc_);

		module = new ConceptModule(new OntologyConcept("UmaThurman"),
				wmi_.getArticleByTitle("Uma Thurman"), 1.0, true);
		info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(info.getArticle(), wmi_.getArticleByTitle("Uma Thurman"),
				0);
		// assertEquals(info.getStanding(), TermStanding.INDIVIDUAL);
		assertions = info.getAmbiguousAssertions();
		assertEquals(assertions.size(), 7); // Four sources.
		merged = WeightedSet.mergeSets(assertions);
		likely = merged.getMostLikely();
		assertEquals(likely.size(), 7); // Eight assertions.
		// assertTrue(assertions.contains(new MinedAssertion(
		// MinedAssertion.SYNONYM_RELATION, "UmaThurman",
		// "Uma Karuna Thurman", miner_)));
		OntologyConcept birthDate = new OntologyConcept("birthDate");
		OntologyConcept umaThurman = new OntologyConcept("UmaThurman");
		OntologyConcept umaBDay = new OntologyConcept(
				"(DayFn 29 (MonthFn April (YearFn 1970)))");
		assertion = new MinedAssertion(birthDate, umaThurman, umaBDay, null,
				new HeuristicProvenance(miner_, "birthDate"));
		assertTrue(likely.contains(assertion));

		OntologyConcept birthPlace = new OntologyConcept("birthPlace");
		OntologyConcept cityOfBostonMA = new OntologyConcept("CityOfBostonMA");
		assertion = new MinedAssertion(birthPlace, umaThurman, cityOfBostonMA,
				null, new HeuristicProvenance(miner_, "birthPlace"));
		assertTrue(likely.contains(assertion));

		OntologyConcept unitedStatesOfAmerica = new OntologyConcept(
				"UnitedStatesOfAmerica");
		assertion = new MinedAssertion(birthPlace, umaThurman,
				unitedStatesOfAmerica, null, new HeuristicProvenance(miner_,
						"birthDate"));
		assertTrue(likely.contains(assertion));

		OntologyConcept temporalTerm = new OntologyConcept("GaryOldman");
		temporalTerm.setTemporalContext(new OntologyConcept(
				"(TimeIntervalInclusiveFn (YearFn 1990) (YearFn 1992))"));
		OntologyConcept spouse = new OntologyConcept("spouse");
		assertion = new MinedAssertion(spouse, umaThurman, temporalTerm, null,
				new HeuristicProvenance(miner_, "spouse"));
		assertTrue(likely.contains(assertion));

		temporalTerm = new OntologyConcept("EthanHawke");
		temporalTerm.setTemporalContext(new OntologyConcept(
				"(TimeIntervalInclusiveFn (YearFn 1998) (YearFn 2004))"));
		assertion = new MinedAssertion(spouse, umaThurman, temporalTerm, null,
				new HeuristicProvenance(miner_, "birthDate"));
		assertTrue(likely.contains(assertion));

		OntologyConcept occupation = new OntologyConcept("occupation");
		OntologyConcept Actor = new OntologyConcept("Actor");
		assertion = new MinedAssertion(occupation, umaThurman, Actor, null,
				new HeuristicProvenance(miner_, "occupation"));
		assertTrue(likely.contains(assertion));

		OntologyConcept ProfessionalModel = new OntologyConcept("ProfessionalModel");
		assertion = new MinedAssertion(occupation, umaThurman,
				ProfessionalModel, null, new HeuristicProvenance(miner_,
						"occupation"));
		assertTrue(likely.contains(assertion));

		OntologyConcept zeus = new OntologyConcept("Zeus");
		module = new ConceptModule(zeus, wmi_.getArticleByTitle("Zeus"), 1.0,
				true);
		info = miner_.mineArticle(module, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(info.getArticle(), wmi_.getArticleByTitle("Zeus"), 0);
		// assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertions = info.getAmbiguousAssertions();
		assertEquals(assertions.size(), 15); // Three sources.
		merged = WeightedSet.mergeSets(assertions);
		assertEquals(merged.size(), 16); // Fifteen assertions.
		// TODO Fix this test

		double weight = 1.0 / 8;
		OntologyConcept children = new OntologyConcept("children");
		OntologyConcept artemis = new OntologyConcept("Artemis-TheGoddess");
		assertion = new MinedAssertion(children, zeus, artemis, null,
				new HeuristicProvenance(miner_, "children"));
		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept athena = new OntologyConcept("Athena-TheGoddess");
		assertion = new MinedAssertion(children, zeus, athena, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept ares = new OntologyConcept("Ares-TheGod");
		assertion = new MinedAssertion(children, zeus, ares, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept aphrodite = new OntologyConcept("Aphrodite-TheGoddess");
		assertion = new MinedAssertion(children, zeus, aphrodite, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept dionysus = new OntologyConcept("Dionysus-TheGod");
		assertion = new MinedAssertion(children, zeus, dionysus, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept hebe = new OntologyConcept("Hebe-TheGoddess");
		assertion = new MinedAssertion(children, zeus, hebe, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept hermes = new OntologyConcept("Hermes-TheGod");
		assertion = new MinedAssertion(children, zeus, hermes, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept hephaestus = new OntologyConcept("Hephaestus-TheGod");
		assertion = new MinedAssertion(children, zeus, hephaestus, null,
				new HeuristicProvenance(miner_, "children"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		weight = 1.0 / 2;
		OntologyConcept biologicalOffspring = new OntologyConcept("biologicalOffspring");
		OntologyConcept kronos = new OntologyConcept("Kronos-TheTitan");
		assertion = new MinedAssertion(biologicalOffspring, kronos, zeus, null,
				new HeuristicProvenance(miner_, "parent"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept rhea = new OntologyConcept("Rhea-TheTitaness");
		assertion = new MinedAssertion(biologicalOffspring, rhea, zeus, null,
				new HeuristicProvenance(miner_, "parent"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		weight = 1.0 / 5;
		OntologyConcept hades = new OntologyConcept("Hades-TheGod");
		OntologyConcept siblings = new OntologyConcept("siblings");
		assertion = new MinedAssertion(siblings, zeus, hades, null,
				new HeuristicProvenance(miner_, "siblings"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept hestia = new OntologyConcept("Hestia-TheGoddess");
		assertion = new MinedAssertion(siblings, zeus, hestia, null,
				new HeuristicProvenance(miner_, "siblings"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept poseidon = new OntologyConcept("Poseidon-TheGod");
		assertion = new MinedAssertion(siblings, zeus, poseidon, null,
				new HeuristicProvenance(miner_, "siblings"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept demeter = new OntologyConcept("Demeter-TheGoddess");
		assertion = new MinedAssertion(siblings, zeus, demeter, null,
				new HeuristicProvenance(miner_, "siblings"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
		OntologyConcept hera = new OntologyConcept("Hera-TheGoddess");
		assertion = new MinedAssertion(siblings, zeus, hera, null,
				new HeuristicProvenance(miner_, "siblings"));

		assertTrue(merged.contains(assertion));
		assertEquals(merged.getWeight(assertion), weight, 0.0);
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.InfoboxRelationMiner#parseRelation(java.lang.String, java.lang.String, MinedInformation)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public void testParseRelation() throws Exception {
		// Single anchor
		ConceptModule info = new ConceptModule(new OntologyConcept("UmaThurman"),
				wmi_.getArticleByTitle("Uma Thurman"), 1.0, true);
		// assertTrue(miner_.parseRelation("birth_place",
		// "[[Boston, Massachussets]]", info, "person", wmi_, cyc_));
		SortedSet<AssertionQueue> ambiguousAssertions = info
				.getAmbiguousAssertions();
		WeightedSet<MinedAssertion> merged = WeightedSet
				.mergeSets(ambiguousAssertions);
		Collection<MinedAssertion> mostLikely = merged.getMostLikely();
		// assertEquals(mostLikely.size(), 1);
		// CycConcept birthPlace = new CycConcept("birthPlace");
		// CycConcept CityOfBostonMA = new CycConcept("CityOfBostonMA");
		// assertTrue(mostLikely.contains(new MinedAssertion(birthPlace,
		// CycConcept.PLACEHOLDER, CityOfBostonMA, null, miner_)));
		//
		// // Broken thingamee
		// info.clearInformation();
		// assertTrue(miner_
		// .parseRelation(
		// "spouse",
		// "[[Pauline Bush (actress)|Pauline Bush]] (1915â1921) <br /> Marie Shelton (1922â1954)",
		// info, null, wmi_, cyc_));
		//
		// // Occupation
		// info = new ConceptModule(new CycConcept("UmaThurman"),
		// wmi_.getArticleByTitle("Uma Thurman"), 1.0, true);
		// assertTrue(miner_.parseRelation("occupation", "[[Musician]]", info,
		// "person", wmi_, cyc_));
		// ambiguousAssertions = info.getAmbiguousAssertions();
		// merged = WeightedSet.mergeSets(ambiguousAssertions);
		// mostLikely = merged.getMostLikely();
		// assertEquals(mostLikely.size(), 1);
		// CycConcept occupation = new CycConcept("occupation");
		// CycConcept Musician = new CycConcept("Musician");
		// assertTrue(mostLikely.contains(new MinedAssertion(occupation,
		// CycConcept.PLACEHOLDER, Musician, null, miner_)));
		//
		// // Double anchor
		// info.clearInformation();
		// assertTrue(miner_.parseRelation("birth_place",
		// "[[Boston, Massachusetts]], [[U.S.]]", info, "person", wmi_,
		// cyc_));
		// ambiguousAssertions = info.getAmbiguousAssertions();
		// // assertEquals(ambiguousAssertions.size(), 2);
		// merged = WeightedSet.mergeSets(ambiguousAssertions);
		// mostLikely = merged.getMostLikely();
		// assertEquals(mostLikely.size(), 2);
		// assertTrue(mostLikely.contains(new MinedAssertion(birthPlace,
		// CycConcept.PLACEHOLDER, CityOfBostonMA, null, miner_)));
		// CycConcept UnitedStatesOfAmerica = new CycConcept(
		// "UnitedStatesOfAmerica");
		// assertTrue(mostLikely.contains(new MinedAssertion(birthPlace,
		// CycConcept.PLACEHOLDER, UnitedStatesOfAmerica, null, miner_)));
		//
		// // Birth date with comma
		// info.clearInformation();
		// assertTrue(miner_.parseRelation("birth_date", "29 April, 1970", info,
		// "person", wmi_, cyc_));
		// ambiguousAssertions = info.getAmbiguousAssertions();
		// assertEquals(ambiguousAssertions.size(), 1);
		// mostLikely = ambiguousAssertions.first().getMostLikely();
		// assertEquals(mostLikely.size(), 1);
		// CycConcept umaBDay = new CycConcept(
		// "(DayFn '29 (MonthFn April (YearFn '1970)))");
		// CycConcept birthDate = new CycConcept("birthDate");
		// assertTrue(mostLikely.contains(new MinedAssertion(birthDate,
		// CycConcept.PLACEHOLDER, umaBDay, null, miner_)));
		// assertEquals(
		// ambiguousAssertions.first().getSubAssertionQueues().size(), 2);
		//
		// // Unknown relation
		// info.clearInformation();
		// assertFalse(miner_
		// .parseRelation(
		// "alt",
		// "An African-American man is at the "
		// + "centre of the image looking to the left "
		// + "and smiling. He is wearing a hat, glasses, "
		// + "a white jacket and a black t-shirt that says \"MoFo\".",
		// info, "person", wmi_, cyc_));
		//
		// // Multiple assertions (with generalisation)
		// info.clearInformation();
		// assertTrue(miner_.parseRelation("occupation", "Actress, model", info,
		// "person", wmi_, cyc_));
		// ambiguousAssertions = info.getAmbiguousAssertions();
		// assertEquals(ambiguousAssertions.size(), 2);
		// merged = WeightedSet.mergeSets(ambiguousAssertions);
		// mostLikely = merged.getMostLikely();
		// assertEquals(mostLikely.size(), 2);
		// CycConcept Actor = new CycConcept("Actor");
		// assertTrue(mostLikely.contains(new MinedAssertion(occupation,
		// CycConcept.PLACEHOLDER, Actor, null, miner_)));
		// CycConcept FashionModel = new CycConcept("FashionModel");
		// assertTrue(mostLikely.contains(new MinedAssertion(occupation,
		// CycConcept.PLACEHOLDER, FashionModel, null, miner_)));
		// CycConcept ProfessionalModel = new CycConcept("ProfessionalModel");
		// assertEquals(merged.getWeight(new MinedAssertion(occupation,
		// CycConcept.PLACEHOLDER, ProfessionalModel, null, miner_)),
		// 0.985, 0.001);

		// Very tricky with multiple anchors and measures
		info.clearInformation();
		assertTrue(miner_.parseRelation("spouse",
				"[[Gary Oldman]] (1990-1992)\n[[Ethan Hawke]] (1998-2004)",
				info, "person", wmi_, cyc_));
		ambiguousAssertions = info.getAmbiguousAssertions();
		assertEquals(ambiguousAssertions.size(), 2);
		for (AssertionQueue aq : ambiguousAssertions) {
			assertEquals(aq.getMostLikely().size(), 1);
			MinedAssertion firstAssertion = aq.getOrdered().first();
			if (firstAssertion.getArgs()[1]
					.equals(new OntologyConcept("GaryOldman")))
				assertTrue(aq
						.getOrdered()
						.first()
						.getMicrotheory()
						.contains(
								"(TimeIntervalInclusiveFn (YearFn 1990) (YearFn 1992))"));
			if (firstAssertion.getArgs()[1]
					.equals(new OntologyConcept("EthanHawke")))
				assertTrue(aq
						.getOrdered()
						.first()
						.getMicrotheory()
						.contains(
								"(TimeIntervalInclusiveFn (YearFn 1998) (YearFn 2004))"));
			// assertEquals(aq.getSubAssertionQueues().size(), 0);
		}

		// Comma-separated numbers
		info = new ConceptModule(new OntologyConcept("NewZealand"),
				wmi_.getArticleByTitle("New Zealand"), 1.0, true);
		assertTrue(miner_.parseRelation("population", "5,876,932,000", info,
				"country", wmi_, cyc_));
		ambiguousAssertions = info.getAmbiguousAssertions();
		assertEquals(ambiguousAssertions.size(), 1);
		mostLikely = ambiguousAssertions.first().getMostLikely();
		assertEquals(mostLikely.size(), 1);
		OntologyConcept numberOfInhabitants = new OntologyConcept("numberOfInhabitants");
		OntologyConcept NewZealand = new OntologyConcept("NewZealand");
		MinedAssertion assertion = new MinedAssertion(numberOfInhabitants,
				OntologyConcept.PLACEHOLDER, new PrimitiveConcept(5876932000l),
				null, new HeuristicProvenance(miner_, "population"));
		assertTrue(mostLikely.contains(assertion));
		assertEquals(
				ambiguousAssertions.first().getSubAssertionQueues().size(), 4);

		// Capital
		info = new ConceptModule(new OntologyConcept("NewZealand"),
				wmi_.getArticleByTitle("New Zealand"), 1.0, true);
		assertTrue(miner_.parseRelation("capital", "[[Wellington]]", info,
				"country", wmi_, cyc_));
		ambiguousAssertions = info.getAmbiguousAssertions();
		assertEquals(ambiguousAssertions.size(), 1);
		merged = WeightedSet.mergeSets(ambiguousAssertions);
		mostLikely = merged.getMostLikely();
		assertEquals(mostLikely.size(), 1);
		OntologyConcept capitalCity = new OntologyConcept("capitalCity");
		OntologyConcept CityOfWellingtonNewZealand = new OntologyConcept(
				"CityOfWellingtonNewZealand");
		assertTrue(mostLikely.contains(new MinedAssertion(capitalCity,
				NewZealand, CityOfWellingtonNewZealand, null,
				new HeuristicProvenance(miner_, "capital"))));
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.InfoboxTypeMiner#voteStanding(int[], java.lang.String)}
	 * .
	 */
	@Test
	public void testVoteStandingByRelation() {
		// Person individual
		TermStanding result = miner_.voteStanding("birth_place");
		assertEquals(result, TermStanding.INDIVIDUAL);
		result = miner_.voteStanding("death_place");
		assertEquals(result, TermStanding.INDIVIDUAL);
		result = miner_.voteStanding("occupation");
		assertEquals(result, TermStanding.INDIVIDUAL);

		// Business individual
		result = miner_.voteStanding("founder");
		assertEquals(result, TermStanding.INDIVIDUAL);

		// Simple collection.
		result = miner_.voteStanding("genus");
		assertEquals(result, TermStanding.COLLECTION);
	}

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper(null);
		mapper.initialise();
		CycMiner miner = new CycMiner(null, mapper);
		miner_ = new InfoboxRelationMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}
}
