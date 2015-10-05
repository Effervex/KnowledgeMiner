/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

import graph.core.PrimitiveNode;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

import util.UniqueID;
import util.UtilityMethods;

public class OntologyConcept extends AssertionArgument implements Serializable,
		UniqueID {
	private static final long serialVersionUID = 1L;
	public static boolean parsingArgs_ = false;
	// public static final OntologyConcept PLACEHOLDER = new OntologyConcept(
	// "-PLACEHOLDER-75839-");
	protected String constant_;
	protected String[] funcArgs_;
	protected transient int id_;

	public OntologyConcept(int id) {
		id_ = id;
		getConceptName();
	}

	public OntologyConcept(String... funcArgs) {
		if (funcArgs.length == 1) {
			parseConstantName(funcArgs[0]);
		} else if (funcArgs.length > 1) {
			funcArgs_ = idFunction(funcArgs, parsingArgs_);
		}
		refreshID();
	}

	public OntologyConcept(String constant, int id) {
		id_ = id;
		parseConstantName(constant);
	}

	public OntologyConcept(OntologyConcept ontologyConcept) {
		super(ontologyConcept);
		id_ = ontologyConcept.id_;
		constant_ = ontologyConcept.constant_;
		funcArgs_ = ontologyConcept.funcArgs_;
	}

	protected void parseConstantName(String constantName) {
		if (constantName == null)
			return;
		if (constantName.startsWith("(")) {
			String function = UtilityMethods.shrinkString(constantName, 1);
			String[] functionList = UtilityMethods.splitToArray(function, ' ');
			funcArgs_ = idFunction(functionList, parsingArgs_);
		} else {
			constant_ = constantName;
		}
	}

	@Override
	public OntologyConcept clone() {
		return new OntologyConcept(this);
	}

	public String getConceptName() {
		if (constant_ == null && funcArgs_ == null) {
			if (id_ == 0)
				return null;
			parseConstantName(ResourceAccess.requestOntologySocket()
					.findConceptByID(id_));
		}
		if (funcArgs_ != null)
			return "(" + StringUtils.join(funcArgs_, ' ') + ")";
		else
			return constant_;
	}

	public int getID() {
		refreshID();
		return id_;
	}

	/**
	 * Rereads the ID from the constant/function args.
	 */
	private void refreshID() {
		if (id_ == 0) {
			if (constant_ != null)
				id_ = ResourceAccess.requestOntologySocket().getConceptID(
						constant_);
			else if (funcArgs_ != null) {
				id_ = ResourceAccess.requestOntologySocket().getConceptID(
						"(" + StringUtils.join(funcArgs_, ' ') + ")");
			}
		}
	}

	public String getIdentifier() {
		refreshID();
		if (id_ < 0)
			return toString();
		return id_ + "";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		OntologyConcept other = (OntologyConcept) obj;
		if (getIdentifier() == null) {
			if (other.getIdentifier() != null)
				return false;
		} else if (!getIdentifier().equals(other.getIdentifier()))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((getIdentifier() == null) ? 0 : getIdentifier().hashCode());
		return result;
	}

	public boolean isFunction() {
		if (funcArgs_ != null)
			return true;
		return false;
	}

	public boolean isOntologyConcept() {
		refreshID();
		return id_ > 0 || funcArgs_ != null;
	}

	public boolean isPrimitive() {
		refreshID();
		return constant_ != null && id_ == -1 && constant_.startsWith("'");
	}

	public boolean isString() {
		refreshID();
		return constant_ != null && id_ == -1 && constant_.startsWith("\"")
				&& constant_.endsWith("\"");
	}

	public void setID(int id) {
		id_ = id;
	}

	public String toPrettyString() {
		if (isFunction())
			return "(" + StringUtils.join(idFunction(funcArgs_, true), ' ')
					+ ")";
		else
			return toString();
	}

	@Override
	public String toString() {
		String constName = getConceptName();
		return constName;
	}

	/**
	 * Formats a function into an array of ID strings for quick recognition by
	 * the DAG.
	 * 
	 * @param functionList
	 *            The function arguments.
	 * @param parseArgs
	 *            If the arguments should be parsed into IDs.
	 * @return An array of strings representing IDs of the function.
	 */
	public static String[] idFunction(String[] functionList, boolean parseArgs) {
		String[] result = new String[functionList.length];
		for (int i = 0; i < result.length; i++) {
			String funcArg = functionList[i];
			result[i] = funcArg;
			if (parseArgs && funcArg.startsWith("("))
				result[i] = "("
						+ StringUtils.join(
								idFunction(
										UtilityMethods.splitToArray(
												UtilityMethods.shrinkString(
														funcArg, 1), ' '),
										parseArgs), ' ') + ")";
			else if (parseArgs && funcArg.matches("\\d+"))
				result[i] = ResourceAccess.requestOntologySocket()
						.findConceptByID(Integer.parseInt(funcArg));
			else
				result[i] = funcArg;
		}
		return result;
	}

	/**
	 * Parse an ontology concept from a string.
	 * 
	 * @param argument
	 *            The argument to parse.
	 * @return The concept parsed (or null).
	 */
	public static OntologyConcept parseArgument(String argument) {
		if (argument == null || argument.isEmpty())
			return null;

		if (argument.startsWith("("))
			return new OntologyConcept(argument);
		if (argument.startsWith("\"") && argument.endsWith("\""))
			return new StringConcept(UtilityMethods.shrinkString(argument, 1));
		if (argument.startsWith("'")) {
			if (PrimitiveNode.parseNode(argument.substring(1)) != null)
				return new PrimitiveConcept(argument.substring(1));
		}
		if (argument.contains(" "))
			return new StringConcept(argument);
		try {
			int id = Integer.parseInt(argument);
			OntologyConcept oc = new OntologyConcept(id);
			if (oc.getConceptName() == null)
				return null;
			else
				return oc;
		} catch (NumberFormatException e) {
		}
		OntologyConcept concept = new OntologyConcept(argument);
		if (concept.getID() == -13)
			return null;
		return concept;
	}
}
