/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertEquals;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.meta.ImpliedStandingMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;
import cyc.CycConstants;
import cyc.StringConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class ImpliedStandingMinerTest {
	private static ImpliedStandingMiner miner_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

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
		miner_ = new ImpliedStandingMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * BillGates method for
	 * {@link knowledgeMiner.mining.meta.ImpliedStandingMiner#mineArticle(ConceptModule, int, WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IllegalAccessException
	 */
	@Test
	public void testMineArticle() throws IllegalAccessException {
		// Empty assertions
		OntologyConcept billGates = new OntologyConcept("BillGates");
		ConceptModule info = new ConceptModule(billGates, 0, 1, true);
		MinedInformation result = miner_.mineArticle(info,
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertEquals(result.getStanding(), TermStanding.UNKNOWN);

		// Single individual assertion
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept("isa"),
				billGates, new OntologyConcept("Lawyer"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.INDIVIDUAL);

		// Occupation
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept(
				"occupation"), billGates, new OntologyConcept("Actor"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.INDIVIDUAL);

		// Birthday
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(
				new OntologyConcept("birthDate"), billGates, new OntologyConcept(
						"(DayFn 2 (MonthFn May (YearFn 2000)))"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.INDIVIDUAL);

		// Second-order collection
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept("isa"),
				billGates, new OntologyConcept("BiologicalSpecies"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.COLLECTION);

		// Collection pred
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept(
				"superTaxons"), billGates, new OntologyConcept("CanisGenus"), null,
				new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.COLLECTION);

		// Genls
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept("genls"),
				billGates, new OntologyConcept("Sawdust"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.COLLECTION);

		// Comment
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept("comment"),
				billGates, new StringConcept("Wowza"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.UNKNOWN);

		// Conflicting facts
		info.clearInformation();
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept("genls"),
				billGates, new OntologyConcept("Sawdust"), null, new HeuristicProvenance(miner_, null)));
		info.addConcreteAssertion(new MinedAssertion(new OntologyConcept("isa"),
				billGates, new OntologyConcept("Lawyer"), null, new HeuristicProvenance(miner_, null)));
		result = miner_.mineArticle(info, MinedInformation.ALL_TYPES, wmi_,
				cyc_);
		assertEquals(result.getStanding(), TermStanding.UNKNOWN);
	}
}
