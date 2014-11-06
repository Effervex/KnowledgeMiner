/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.meta;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * This meta miner uses the assertions produced by other miners to infer the
 * standing of a concept, based on the details of the assertions.
 * 
 * @author Sam Sarjant
 */
public class ImpliedStandingMiner extends MetaMiningHeuristic {
	/**
	 * Constructor for a new ImpliedStandingMiner.
	 * 
	 * @param mapper
	 *            The mapper access.
	 * @param miner
	 */
	public ImpliedStandingMiner(CycMapper mapper, CycMiner miner) {
		super(mapper, miner);
	}

	@Override
	public MinedInformation mineArticle(ConceptModule cm, WMISocket wmi,
			OntologySocket cyc) {
		if (super.mineArticle(cm, wmi, cyc) == null)
			return null;

		MinedInformation info = new MinedInformation(cm.getArticle());

		// Vote for every assertion
		for (DefiniteAssertion assertion : cm.getConcreteAssertions()) {
			if (assertion.getRelation().equals(CycConstants.ISA_GENLS.getConcept())
					|| assertion.getMicrotheory().equals("BaseKB"))
				continue;
			TermStanding relStanding = voteStanding(assertion, cm.getConcept(),
					cyc);
			if (relStanding != TermStanding.UNKNOWN) {
				info.addStandingInformation(relStanding, getWeight(),
						basicProvenance_);
			}
		}
		return info;
	}

	/**
	 * Votes on a standing using the assertion to deduce whether the focus of
	 * the assertion is an Individual or Collection.
	 * 
	 * @param assertion
	 *            The assertion for deduction.
	 * @param cycTerm
	 *            The term being asserted to.
	 * @param ontology
	 *            The ontology access.
	 * @return The implied TermStanding.
	 */
	private TermStanding voteStanding(DefiniteAssertion assertion,
			OntologyConcept cycTerm, OntologySocket ontology) {
		// TODO Incomplete. Needs a rework
		// Isa individual
		String isaInd = "(#$isa ?X #$Individual)";
		boolean ind = false;
		// TODO Sort out this implied standing miner.
		// boolean ind = cyc.implies(genericAssertion, isaInd);

		// Isa collection
		String isaColl = "(#$isa ?X #$Collection)";
		boolean coll = false;
		// boolean coll = cyc.implies(genericAssertion, isaColl);

		if (ind && !coll)
			return TermStanding.INDIVIDUAL;
		else if (coll && !ind)
			return TermStanding.COLLECTION;
		else
			return TermStanding.UNKNOWN;
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.STANDING.ordinal()] = true;
	}
}
