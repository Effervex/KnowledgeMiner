/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

public class StringConcept extends OntologyConcept {
	private static final long serialVersionUID = 1L;

	public StringConcept(String str) {
		super('"' + str.replaceAll("(?<!\\\\)\"", "\\\\\"") + '"', -1);
	}

	@Override
	public String getIdentifier() {
		return getConceptName();
	}

	@Override
	public OntologyConcept clone() {
		return new StringConcept(constant_.substring(1, constant_.length() - 1));
	}
}
