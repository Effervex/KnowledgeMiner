/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

/**
 * The possible statuses of an assertion.
 * 
 * Accepted typically means the assertion did not exist before, and has been
 * incoprporated into the concept. Existing typically means that the same
 * assertion already exists. Rejected means that the assertion clashed with
 * existing information.
 * 
 * @author Sam Sarjant
 */
public enum AssertionStatus {
	ACCEPTED, EXISTING, REJECTED
}
