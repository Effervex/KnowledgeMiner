/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

import java.io.Serializable;

import graph.core.PrimitiveNode;
import io.ResourceAccess;

import org.apache.commons.lang3.StringUtils;

import de.ruedigermoeller.serialization.annotations.Compress;

import util.UniqueID;
import util.UtilityMethods;

public class OntologyConcept implements Serializable, UniqueID {
	private static final long serialVersionUID = 1L;
	public static final OntologyConcept PLACEHOLDER = new OntologyConcept(
			"-PLACEHOLDER-75839-");
	@Compress
	protected String constant_;
	protected String[] funcArgs_;
	protected int id_ = -1;
	private OntologyConcept temporalContext_;
	public static boolean parsingArgs_ = false;

	public OntologyConcept(int id) {
		id_ = id;
	}

	public OntologyConcept(String... funcArgs) {
		if (funcArgs.length == 1) {
			id_ = ResourceAccess.requestOntologySocket().getConceptID(
					funcArgs[0]);
			parseConstantName(funcArgs[0]);
			return;
		}

		if (funcArgs.length > 1) {
			funcArgs_ = idFunction(funcArgs, parsingArgs_);
			id_ = ResourceAccess.requestOntologySocket().getConceptID(
					"(" + StringUtils.join(funcArgs_, ' ') + ")");
		}
	}

	public OntologyConcept(String constant, int id) {
		id_ = id;
		parseConstantName(constant);
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

	public String[] idFunction(String[] functionList, boolean parseArgs) {
		String[] result = new String[functionList.length];
		for (int i = 0; i < result.length; i++) {
			String funcArg = functionList[i];
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

	@Override
	public OntologyConcept clone() {
		OntologyConcept clone = new OntologyConcept(id_);
		clone.constant_ = constant_;
		clone.funcArgs_ = funcArgs_;
		clone.temporalContext_ = temporalContext_;
		return clone;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OntologyConcept other = (OntologyConcept) obj;
		if (getIdentifier() == null) {
			if (other.getIdentifier() != null)
				return false;
		} else if (!getIdentifier().equals(other.getIdentifier()))
			return false;
		if (temporalContext_ == null) {
			if (other.temporalContext_ != null)
				return false;
		} else if (!temporalContext_.equals(other.temporalContext_))
			return false;
		return true;
	}

	public String getConceptName() {
		if (constant_ == null && funcArgs_ == null) {
			parseConstantName(ResourceAccess.requestOntologySocket()
					.findConceptByID(id_));
		}
		if (funcArgs_ != null)
			return "(" + StringUtils.join(funcArgs_, ' ') + ")";
		else
			return constant_;
	}

	public int getID() {
		return id_;
	}

	public String getIdentifier() {
		if (id_ == -1)
			return toString();
		return id_ + "";
	}

	public OntologyConcept getTemporalContext() {
		return temporalContext_;
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
		return id_ != -1 || funcArgs_ != null;
	}

	public boolean isPrimitive() {
		return constant_ != null && id_ == -1 && constant_.startsWith("'");
	}

	public boolean isString() {
		return constant_ != null && id_ == -1 && constant_.startsWith("\"")
				&& constant_.endsWith("\"");
	}

	public void setTemporalContext(OntologyConcept context) {
		temporalContext_ = context;
	}

	@Override
	public String toString() {
		String constName = getConceptName();
		return constName;
	}

	public String toPrettyString() {
		if (isFunction())
			return "(" + StringUtils.join(idFunction(funcArgs_, true), ' ')
					+ ")";
		else
			return toString();
	}

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
			return new OntologyConcept(id);
		} catch (NumberFormatException e) {
		}
		OntologyConcept concept = new OntologyConcept(argument);
		if (concept.getID() == -13)
			return null;
		return concept;
	}

	public void setID(int id) {
		id_ = id;
	}
}
