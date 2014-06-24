/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import graph.core.CommonConcepts;
import graph.core.DAGObject;
import io.IOManager;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.TermStanding;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import cyc.OntologyConcept;
import cyc.CycConstants;

/**
 * A class representing an assertion that can be made to Cyc.
 * 
 * @author Sam Sarjant
 */
public class MinedAssertion extends WeightedInformation implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final String PROVENANCE = "provenance";

	private static final long NEWLY_CREATED_EPSILON = 20000;

	/** The arguments of the relation. */
	protected OntologyConcept[] args_;

	/** The id assigned to this assertion by Cyc after assertion. */
	protected int assertionID_ = -1;

	/** If this assertion represents a parentage assertion. */
	protected boolean isHierarchical_;

	/** The microtheory to assert it under. */
	protected String microtheory_;

	/** The assertion relation. */
	protected OntologyConcept relation_;

	/**
	 * Constructor for a new MinedAssertion that is based on the old assertion
	 * but uses a new Cyc term as the first argument.
	 * 
	 * @param assertion
	 *            The old assertion.
	 * @param newArg
	 *            The new Cyc term to use.
	 * @param argNum
	 *            The arg index.
	 */
	public MinedAssertion(MinedAssertion assertion) {
		super(null);
		clone(assertion);
	}

	/**
	 * Constructor for a new MinedAssertion that is based on the old assertion
	 * but uses a new Cyc term as the first argument.
	 * 
	 * @param assertion
	 *            The old assertion.
	 * @param newArg
	 *            The new Cyc term to use.
	 * @param argNum
	 *            The arg index.
	 */
	public MinedAssertion(MinedAssertion assertion, OntologyConcept newArg,
			int argNum) {
		this(assertion);
		args_[argNum - 1] = newArg;
	}

	/**
	 * A constructor for a duplicate assertion with a given microtheory.
	 * 
	 * @param assertion
	 *            The assertion to clone.
	 * @param microtheory
	 *            The new microtheory.
	 */
	public MinedAssertion(MinedAssertion assertion, String microtheory) {
		this(assertion);
		microtheory_ = microtheory;
	}

	/**
	 * Constructor for a new MinedAssertion
	 * 
	 * @param predicate
	 *            The binary predicate to assert.
	 * @param arg1
	 *            The first argument of the relation.
	 * @param arg2
	 *            The second argument of the relation.
	 * @param microtheory
	 *            The microtheory to assert the assertion under.
	 * @param provenance
	 *            The source of this assertion.
	 * @throws IllegalAccessException
	 *             If the assertions have not been initialised yet.
	 */
	public MinedAssertion(OntologyConcept predicate, OntologyConcept arg1,
			OntologyConcept arg2, String microtheory, HeuristicProvenance provenance)
			throws IllegalAccessException {
		super(provenance);
		if (microtheory == null)
			microtheory = determineMicrotheory(predicate).getConceptName();
		initialise(predicate, microtheory, arg1, arg2);
	}

	/**
	 * Constructor for a new MinedAssertion
	 * 
	 * @param predicate
	 *            The binary predicate to assert.
	 * @param arg1
	 *            The first argument of the relation.
	 * @param arg2
	 *            The second argument of the relation.
	 * @param microtheory
	 *            The microtheory to assert the assertion under.
	 * @param provenance
	 *            The source of this assertion.
	 * @throws IllegalAccessException
	 *             If the assertions have not been initialised yet.
	 */
	public MinedAssertion(OntologyConcept predicate, String microtheory,
			HeuristicProvenance provenance, OntologyConcept... args)
			throws IllegalAccessException {
		super(provenance);
		if (microtheory == null)
			microtheory = determineMicrotheory(predicate).getConceptName();
		initialise(predicate, microtheory, args);
	}

	/**
	 * Determines which microtheory to use for the assertion based on the
	 * relation.
	 * 
	 * @return The microtheory to use.
	 */
	private CycConstants determineMicrotheory(OntologyConcept relation) {
		if (relation.equals(CycConstants.ISA_GENLS))
			return CycConstants.DATA_MICROTHEORY;
		if (ResourceAccess.requestOntologySocket().evaluate(null,
				CommonConcepts.GENLPREDS.getID(), relation.getIdentifier(),
				CommonConcepts.TERM_STRING.getID())
				|| relation.equals("comment"))
			return CycConstants.BASEKB;
		else if (relation.equals(CycConstants.WIKIPEDIA_URL.getConcept())
				|| relation.toString().startsWith("synonymousExternal"))
			return CycConstants.IMPLEMENTATION_MICROTHEORY;
		return CycConstants.DATA_MICROTHEORY;
	}

	private void initialise(OntologyConcept relation, String microtheory,
			OntologyConcept... args) throws IllegalAccessException {
		OntologySocket ontology = ResourceAccess.requestOntologySocket();

		relation_ = relation;
		args_ = new OntologyConcept[args.length];
		microtheory_ = microtheory;
		for (int i = 0; i < args_.length; i++) {
			args_[i] = args[i].clone();
			if (args[i].getTemporalContext() != null) {
				microtheory_ = compileTemporalMicrotheory(microtheory, args[i]
						.getTemporalContext().toString());
			}
		}

		isHierarchical_ = relation_.equals(CycConstants.ISA_GENLS.getConcept())
				|| ontology.evaluate(null, CommonConcepts.GENLPREDS.getID(),
						relation.getIdentifier(), CommonConcepts.ISA.getID())
				|| ontology.evaluate(null, CommonConcepts.GENLPREDS.getID(),
						relation.getIdentifier(), CommonConcepts.GENLS.getID());
	}

	protected void clone(MinedAssertion assertion) {
		heuristics_ = assertion.heuristics_;
		relation_ = assertion.relation_;
		microtheory_ = assertion.microtheory_;
		isHierarchical_ = assertion.isHierarchical_;
		args_ = Arrays.copyOf(assertion.args_, assertion.args_.length);
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

	@Override
	protected InformationType getInfoType() {
		if (isHierarchical_)
			return InformationType.PARENTAGE;
		else
			return InformationType.RELATIONS;
	}

	@Override
	protected boolean needToUpdate() {
		if (getStatus() == 1)
			return false;
		return true;
	}

	public String asPredicate() {
		return "(" + relation_ + " " + StringUtils.join(args_, ' ') + ")";
	}

	@Override
	public final MinedAssertion clone() {
		MinedAssertion clone = new MinedAssertion(this);
		return clone;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof MinedAssertion))
			return false;
		MinedAssertion other = (MinedAssertion) obj;
		if (!Arrays.equals(args_, other.args_))
			return false;
		if (microtheory_ == null) {
			if (other.microtheory_ != null)
				return false;
		} else if (!microtheory_.equals(other.microtheory_))
			return false;
		if (relation_ == null) {
			if (other.relation_ != null)
				return false;
		} else if (!relation_.equals(other.relation_))
			return false;
		return true;
	}

	/**
	 * Gets the arguments of the assertion.
	 * 
	 * @return The first argument.
	 */
	public OntologyConcept[] getArgs() {
		return args_;
	}

	public String getMicrotheory() {
		return microtheory_;
	}

	public OntologyConcept getRelation() {
		return relation_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(args_);
		result = prime * result
				+ ((microtheory_ == null) ? 0 : microtheory_.hashCode());
		result = prime * result
				+ ((relation_ == null) ? 0 : relation_.hashCode());
		return result;
	}

	/**
	 * If this assertion is a hierarchical assertion (isa/genls or derivatives
	 * thereof).
	 * 
	 * @return True if this assertion represents a hierarchical assertion.
	 */
	public boolean isHierarchical() {
		return isHierarchical_;
	}

	/**
	 * Asserts this assertion into Cyc.
	 * 
	 * @param substitute
	 *            The substitution for placeholders (can be null).
	 * @param standing
	 *            The standing of the term being asserted to. Only needed
	 *            (non-null) for parentage assertions.
	 * @param ontology
	 *            The Cyc access.
	 * @return The assertion's ID.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public int makeAssertion(OntologyConcept substitute, TermStanding standing,
			OntologySocket ontology) throws Exception {
		// Shape the hierarchical assertion
		if (isHierarchical())
			makeParentageAssertion(standing);

		replacePlaceholder(substitute, ontology);

		// Perform the assertion
		Object[] args = new Object[args_.length + 1];
		args[0] = relation_.getIdentifier();
		for (int i = 0; i < args_.length; i++)
			args[i + 1] = args_[i].getIdentifier();
		assertionID_ = ontology.assertToOntology(microtheory_, args);
		long now = System.currentTimeMillis();
		LoggerFactory.getLogger("ASSERTION")
				.info("Asserted: {}", asPredicate());
		// Higher order collections
		if (assertionID_ == -1 && isHierarchical()
				&& relation_ == CycConstants.GENLS.getConcept()) {
			relation_ = CycConstants.ISA.getConcept();
			args[0] = relation_.getIdentifier();
			assertionID_ = ontology.assertToOntology(microtheory_, args);
		}

		if (assertionID_ == -1) {
			setStatus(-1);
			IOManager.getInstance().writeBlockedAssertion(this);
		} else {
			String creationDate = ontology.getProperty(assertionID_, false,
					DAGObject.CREATION_DATE);
			long diff = NEWLY_CREATED_EPSILON + 1;
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
			for (HeuristicProvenance provenance : heuristics_)
				ontology.setProperty(assertionID_, false, PROVENANCE,
						provenance.toString());
		}

		return assertionID_;
	}

	public void replacePlaceholder(OntologyConcept substitute,
			OntologySocket ontology) {
		if (substitute == null || substitute.equals(OntologyConcept.PLACEHOLDER))
			return;
		int argNum = getPlaceholderArgIndex();
		if (argNum == -1)
			return;

		args_[argNum] = substitute;
	}

	public int getPlaceholderArgIndex() {
		int argNum = 0;
		for (argNum = 0; argNum < args_.length; argNum++) {
			if (args_[argNum].equals(OntologyConcept.PLACEHOLDER))
				break;
		}
		if (argNum >= args_.length)
			return -1;
		return argNum;
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

	@Override
	public String toString() {
		return asPredicate();
	}

	public String toPrettyString() {
		StringBuilder buffer = new StringBuilder("(" + relation_.toPrettyString());
		for (OntologyConcept cc : args_)
			buffer.append(" " + cc.toPrettyString());
		buffer.append(")");
		return buffer.toString();
	}

	private final static Logger logger_ = LoggerFactory
			.getLogger(MinedAssertion.class);

	/**
	 * Creates a temporal microtheory within the given interval.
	 * 
	 * @param baseMicrotheory
	 *            The base microtheory to wrap the temporal interval around.
	 * @param interval
	 *            The time interval to represent.
	 * @return A String representing the microtheory bound to a temporal subset.
	 */
	public static String compileTemporalMicrotheory(String baseMicrotheory,
			String interval) {
		return "(MtSpace " + baseMicrotheory + " '(MtTimeWithGranularityDimFn "
				+ interval + " Null-TimeParameter))";
	}

	/**
	 * Creates a set of assertions from the values given, disambiguating the
	 * core term and a weighted set of possible terms.
	 * 
	 * @param predicate
	 *            The predicate of the assertion.
	 * @param predicateArgs
	 *            The potential arguments.
	 * @param provenance
	 *            The assertion source.
	 * @param normalise
	 *            If the weights should be normalised to 1.
	 * @return A collection of assertions that could exist from the given
	 *         options.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public static AssertionQueue createAllAssertions(OntologyConcept predicate,
			WeightedSet<OntologyConcept> predicateArgs,
			HeuristicProvenance provenance, boolean normalise,
			OntologySocket ontology) throws Exception {
		logger_.trace("createAllAssertions: {} : {}", predicate,
				predicateArgs);
		AssertionQueue queue = new AssertionQueue(provenance);
		// WeightedSet<MinedAssertion> inverseDists = new WeightedSet<>(
		// predicateArgs.size());

		int termMax = 2;
		if (predicate.equals(CycConstants.ISA_GENLS.getConcept())
				|| ontology.isa(predicate.getID(),
						CommonConcepts.SYMMETRIC_BINARY.getID()))
			termMax = 1;
		for (int termIndex = 1; termIndex <= termMax; termIndex++) {
			// Check every arg
			int otherIndex = (termIndex == 1) ? 2 : 1;
			for (OntologyConcept otherArg : predicateArgs) {
				if (!ontology.validArg(predicate.getIdentifier(),
						otherArg.getIdentifier(), otherIndex))
					continue;

				MinedAssertion assertion = null;
				if (termIndex == 1)
					assertion = new MinedAssertion(predicate,
							OntologyConcept.PLACEHOLDER, otherArg, null, provenance);
				else
					assertion = new MinedAssertion(predicate, otherArg,
							OntologyConcept.PLACEHOLDER, null, provenance);

				// Record inverse distance
				// float distance = 1;
				// if (!predicate.toString().equals(CycConstants.ISA_GENLS)
				// && !predicate.toString().equals(
				// CommonConcepts.ISA.getID())
				// && !predicate.toString().equals(
				// CommonConcepts.GENLS.getID())) {
				// if (!cycTerm.inOntology()) {
				// distance = distanceFunction_
				// .assertionDistance(new MinedAssertion(
				// assertion, testingConcept, termIndex));
				// } else {
				// distance = distanceFunction_
				// .assertionDistance(assertion);
				// }
				// }
				// inverseDists.set(assertion, 1 / distance);

				// Add the assertion
				queue.add(assertion, predicateArgs.getWeight(otherArg));
			}
			// Assertion added, no need to continue;
			if (!queue.isEmpty())
				termIndex = 10;

			// If the WeightedSet is Hierarchical, recurse into the subsets
			if (predicateArgs instanceof HierarchicalWeightedSet
					&& ((HierarchicalWeightedSet<OntologyConcept>) predicateArgs)
							.hasSubSets()) {
				Set<WeightedSet<OntologyConcept>> subSets = ((HierarchicalWeightedSet<OntologyConcept>) predicateArgs)
						.getSubSets();
				for (WeightedSet<OntologyConcept> subSet : subSets)
					queue.addLower(createAllAssertions(predicate, subSet,
							provenance, normalise, ontology));
				if (queue.size() == 0 && subSets.size() == 1)
					queue = queue.getSubAssertionQueues().iterator().next();
//				termIndex = 10;
			}
		}

		// Offset by normalised term distance
		// TODO Is this working?
		// if (inverseDists.size() > 1) {
		// queue.scaleAll(inverseDists);
		// }
		if (normalise)
			queue.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);

		return queue;
	}

	/**
	 * Creates all assertions using the set of possible predicates and the set
	 * of possible arguments.
	 * 
	 * @param predicates
	 *            The weighted set of possible predicates.
	 * @param arguments
	 *            The weighted set of possible arguments.
	 * @param heuristic
	 *            The heuristic that produced the information.
	 * @param ontology
	 *            The ontology access.
	 * @return An AssertionQueue of assertions created from this information.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public static AssertionQueue createAllAssertions(
			WeightedSet<OntologyConcept> predicates,
			WeightedSet<OntologyConcept> arguments, HeuristicProvenance provenance,
			OntologySocket ontology) throws Exception {
		logger_.trace("createAllAssertions: {} : {}", predicates.toString(),
				arguments.toString());

		AssertionQueue aq = new AssertionQueue(provenance);
		// TODO Redo this process. It seems shoddily put together
		for (OntologyConcept predicate : predicates) {
			double predWeight = predicates.getWeight(predicate);

			AssertionQueue predQueue = createAllAssertions(predicate,
					arguments, provenance, false, ontology);
			predQueue.scaleAll(predWeight);
			aq.setAll(predQueue);
		}
		aq.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
		return aq;
	}

	public OntologyConcept[] asArgs() {
		OntologyConcept[] args = new OntologyConcept[args_.length + 1];
		args[0] = relation_;
		System.arraycopy(args_, 0, args, 1, args_.length);
		return args;
	}
}
