/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

/**
 * The various heuristics for determining whether an article represents an
 * individual or a collection.
 * 
 * @author Sam Sarjant
 */
public enum StandingMethod {
	EQUIVALENT_CATEGORY("Equivalent category"), INFOBOX_RELATIONS(
			"Infobox relations"), NOT_FOUND("Not found"), REGEXP_SENTENCE(
			"First sentence parsing"), TITLE_SCANNER("Article title parsing");

	private String methodName_;

	private StandingMethod(String methodName) {
		methodName_ = methodName;
	}

	public String getMethodName() {
		return methodName_;
	}
}
