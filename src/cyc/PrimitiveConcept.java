/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

public class PrimitiveConcept extends OntologyConcept {
	private static final long serialVersionUID = 1L;

	public PrimitiveConcept(Object primitive) {
		super("'" + primitive, -1);
	}

	@Override
	public String getIdentifier() {
		return getConceptName();
	}

	@Override
	public OntologyConcept clone() {
		return new PrimitiveConcept(constant_.substring(1));
	}
}
