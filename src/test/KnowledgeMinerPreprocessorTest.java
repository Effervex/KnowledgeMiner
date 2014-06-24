/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package test;

import static org.junit.Assert.*;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KnowledgeMinerPreprocessorTest {
	KnowledgeMinerPreprocessor sut_;

	@Before
	public void setUp() throws Exception {
		sut_ = KnowledgeMinerPreprocessor.getInstance();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetLoadHeuristicMap() {
		fail("Not yet implemented");
	}

	@Test
	public void testPrecomputeData() {
		fail("Not yet implemented");
	}

	@Test
	public void testClearLoaded() {
		fail("Not yet implemented");
	}

}
