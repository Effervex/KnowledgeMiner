/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.io.Serializable;
import java.util.Arrays;

import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.MappableConcept;

/**
 * A class representing an assertion that can be made to Cyc.
 * 
 * @author Sam Sarjant
 */
public abstract class MinedAssertion extends WeightedInformation implements
		Serializable {
	private static final long serialVersionUID = 1L;

	/** The arguments of the relation. */
	protected AssertionArgument[] args_;

	/** The id assigned to this assertion by Cyc after assertion. */
	protected int assertionID_ = -1;

	/** If this assertion represents a parentage assertion. */
	protected boolean isHierarchical_;

	/** The microtheory to assert it under. */
	protected String microtheory_;

	/** The assertion relation. */
	protected AssertionArgument relation_;

	/**
	 * The weight assigned to this assertion as a representation of confidence
	 * in the assertion.
	 */
	private double weight_ = 1;

	@Override
	public double getWeight() {
		return weight_;
	}

	@Override
	public void setWeight(double weight) {
		weight_ = weight;
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
	public MinedAssertion(AssertionArgument predicate, String microtheory,
			HeuristicProvenance provenance, AssertionArgument... args) {
		super(provenance);
		if (microtheory == null)
			microtheory = determineMicrotheory(predicate).getConceptName();
		initialise(predicate, microtheory, args);
	}

	public MinedAssertion(MinedAssertion existing) {
		super(existing.getProvenance());
		args_ = Arrays.copyOf(existing.args_, existing.args_.length);
		assertionID_ = existing.assertionID_;
		isHierarchical_ = existing.isHierarchical_;
		microtheory_ = existing.microtheory_;
		relation_ = existing.relation_;
		weight_ = existing.weight_;
	}

	/**
	 * Constructor for an empty MinedAssertion (all null)
	 */
	protected MinedAssertion() {
		super(null);
	}

	/**
	 * Determines which microtheory to use for the assertion based on the
	 * relation.
	 * 
	 * @return The microtheory to use.
	 */
	private CycConstants determineMicrotheory(AssertionArgument relation) {
		if (relation instanceof MappableConcept)
			return CycConstants.DATA_MICROTHEORY;
		if (relation.equals(CycConstants.ISA_GENLS))
			return CycConstants.DATA_MICROTHEORY;
		if (relation.equals(CycConstants.COMMENT)
				|| relation.equals(CycConstants.WIKIPEDIA_COMMENT)
				|| relation.equals(CycConstants.SYNONYM_RELATION)
				|| relation.equals(CycConstants.SYNONYM_RELATION_CANONICAL)
				|| ResourceAccess.requestOntologySocket().evaluate(null,
						CommonConcepts.GENLPREDS.getID(),
						relation.getIdentifier(),
						CommonConcepts.TERM_STRING.getID()))
			return CycConstants.LEXICAL_MICROTHEORY;
		else if (relation.equals(CycConstants.WIKIPEDIA_URL.getConcept())
				|| relation.toString().startsWith("synonymousExternal"))
			return CycConstants.IMPLEMENTATION_MICROTHEORY;
		return CycConstants.DATA_MICROTHEORY;
	}

	private void initialise(AssertionArgument relation, String microtheory,
			AssertionArgument... args) {
		OntologySocket ontology = ResourceAccess.requestOntologySocket();

		relation_ = relation;
		args_ = args;
		microtheory_ = microtheory;
		for (int i = 0; i < args_.length; i++) {
			if (args_[i].getTemporalContext() != null)
				microtheory_ = compileTemporalMicrotheory(microtheory, args[i]
						.getTemporalContext().toString());
		}

		if (relation instanceof TextMappedConcept)
			isHierarchical_ = false;
		else
			isHierarchical_ = relation_.equals(CycConstants.ISA_GENLS
					.getConcept())
					|| ontology.evaluate(null,
							CommonConcepts.GENLPREDS.getID(),
							relation.getIdentifier(),
							CommonConcepts.ISA.getID())
					|| ontology.evaluate(null,
							CommonConcepts.GENLPREDS.getID(),
							relation.getIdentifier(),
							CommonConcepts.GENLS.getID());
	}

	@Override
	protected InformationType getInfoType() {
		if (isHierarchical_)
			return InformationType.TAXONOMIC;
		else
			return InformationType.NON_TAXONOMIC;
	}

	@Override
	public abstract MinedAssertion clone();

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
	public AssertionArgument[] getArgs() {
		return args_;
	}

	public String getMicrotheory() {
		return microtheory_;
	}

	public AssertionArgument getRelation() {
		return relation_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(args_);
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

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("(" + relation_.toString());
		for (AssertionArgument arg : args_)
			builder.append(" " + arg.toString());
		builder.append(")");
		return builder.toString();
	}

	public String toPrettyString() {
		StringBuilder builder = new StringBuilder();
		builder.append("(" + relation_.toPrettyString());
		for (AssertionArgument arg : args_)
			builder.append(" " + arg.toPrettyString());
		builder.append(")");
		return builder.toString();
	}

	public int getArgIndex(AssertionArgument argument) {
		for (int i = 0; i < args_.length; i++)
			if (args_[i].equals(argument))
				return i;
		return -1;
	}

	public abstract MinedAssertion replaceArg(AssertionArgument original,
			AssertionArgument replacement);
}
