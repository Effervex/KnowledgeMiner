/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import util.DistanceFunction;
import cyc.CycSemanticDistanceFunction;

/**
 *
 * @author Sam Sarjant
 */
public class CycSemanticDistanceFunctionTest extends DistanceFunctionTest {
	@Override
	protected DistanceFunction getDistanceFunction() {
		return new CycSemanticDistanceFunction();
	}
}
