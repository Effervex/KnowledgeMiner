package knowledgeMiner.mining;

import graph.core.DAGObject;
import io.IOManager;
import io.ontology.OntologySocket;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.TermStanding;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.OntologyConcept;

public class DefiniteAssertion extends MinedAssertion {
	private static final long serialVersionUID = 1L;

	public DefiniteAssertion(OntologyConcept predicate, String microtheory,
			HeuristicProvenance provenance, OntologyConcept... args) {
		super(predicate, microtheory, provenance, args);
	}

	public DefiniteAssertion(OntologyConcept predicate,
			HeuristicProvenance provenance, OntologyConcept... args) {
		super(predicate, null, provenance, args);
	}

	public DefiniteAssertion(DefiniteAssertion existing) {
		super(existing);
	}

	private static final long NEWLY_CREATED_EPSILON = 20000;

	public String asPredicate() {
		return "(" + relation_ + " " + StringUtils.join(args_, ' ') + ")";
	}

	@Override
	protected boolean needToUpdate() {
		if (getStatus() == 1)
			return false;
		return true;
	}

	@Override
	protected Double determineStatusWeight(MiningHeuristic source) {
		int status = getStatus();
		Double weight = null;
		// If the status is existing, upvote it
		if (status == 0)
			weight = 1d;
		else if (status == -1)
			weight = 0d;
		return weight;
	}

	public OntologyConcept[] asArgs() {
		OntologyConcept[] args = new OntologyConcept[args_.length + 1];
		args[0] = (OntologyConcept) getRelation();
		System.arraycopy(args_, 0, args, 1, args_.length);
		return args;
	}

	public String toPrettyString() {
		StringBuilder buffer = new StringBuilder("("
				+ relation_.toPrettyString());
		for (AssertionArgument cc : args_)
			buffer.append(" " + cc.toPrettyString());
		buffer.append(")");
		return buffer.toString();
	}

	@Override
	public String toString() {
		return asPredicate();
	}

	@Override
	public OntologyConcept getRelation() {
		return (OntologyConcept) super.getRelation();
	}

	@Override
	public OntologyConcept[] getArgs() {
		return (OntologyConcept[]) super.getArgs();
	}

	/**
	 * Asserts a parentage assertion into Cyc, grounding the type of assertion
	 * based on the term's standing.
	 * 
	 * @param standing
	 *            The term's standing.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public void makeParentageAssertion(TermStanding standing) {
		if (relation_.equals(CycConstants.ISA_GENLS.getConcept())) {
			if (standing == TermStanding.COLLECTION)
				relation_ = CycConstants.GENLS.getConcept();
			else
				relation_ = CycConstants.ISA.getConcept();
		}
	}

	/**
	 * Asserts this assertion into Cyc.
	 * 
	 * @param runIter
	 *            The run ID of the assertion. -1 if no run info.
	 * @param substitute
	 *            The substitution for placeholders (can be null).
	 * @param ontology
	 *            The Cyc access.
	 * 
	 * @return The assertion's ID.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public int makeAssertion(int runIter, OntologyConcept substitute,
			OntologySocket ontology) throws Exception {
		// Perform the assertion
		Object[] args = new Object[args_.length + 1];
		args[0] = relation_.getIdentifier();
		for (int i = 0; i < args_.length; i++)
			args[i + 1] = args_[i].getIdentifier();
		assertionID_ = ontology.assertToOntology(microtheory_, args);
		long now = System.currentTimeMillis();
		LoggerFactory.getLogger("ASSERTION")
				.info("Asserted: {}", asPredicate());

		if (assertionID_ == -1) {
			setStatus(-1);
			IOManager.getInstance().writeBlockedAssertion(this);
		} else {
			String creationDate = ontology.getProperty(assertionID_, false,
					DAGObject.CREATION_DATE);
			long diff = NEWLY_CREATED_EPSILON;
			try {
				diff = now - Long.parseLong(creationDate);
			} catch (NumberFormatException nfe) {
			}
			if (diff < NEWLY_CREATED_EPSILON)
				setStatus(1);
			else
				setStatus(0);

			IOManager.getInstance().writeAssertion(substitute, this);
			// Add provenance data
			if (heuristic_ != null)
				ontology.setProperty(assertionID_, false,
						HeuristicProvenance.PROVENANCE, heuristic_.toString());
			// Add run ID data (if none exists)
			if (getStatus() == 1 && runIter != -1)
				ontology.setProperty(assertionID_, false,
						KnowledgeMiner.RUN_ID, runIter + "");
		}

		return assertionID_;
	}

	@Override
	public DefiniteAssertion clone() {
		return new DefiniteAssertion(this);
	}

	@Override
	public DefiniteAssertion replaceArg(AssertionArgument original,
			AssertionArgument replacement) {
		// Replacing the args
		OntologyConcept[] replArgs = new OntologyConcept[args_.length];
		for (int i = 0; i < args_.length; i++) {
			if (args_[i].equals(original))
				replArgs[i] = (OntologyConcept) replacement;
			else
				replArgs[i] = (OntologyConcept) args_[i];
		}
		// Replacing the relation
		OntologyConcept replPredicate = (OntologyConcept) ((relation_
				.equals(original)) ? replacement : relation_);
		DefiniteAssertion replaced = new DefiniteAssertion(replPredicate,
				microtheory_, getProvenance(), replArgs);
		return replaced;
	}

}
