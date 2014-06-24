/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import io.KMSocket;
import io.ResourceAccess;
import knowledgeMiner.mapping.CycMapper;

import org.junit.After;
import org.junit.BeforeClass;


/**
 * 
 * @author Sam Sarjant
 */
public class InfoboxValueMiningHeuristicTest {
	private static CycMapper mapper_;
	private static KMSocket wmi_;

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		wmi_ = ResourceAccess.requestWMISocket();
		mapper_ = new CycMapper(null);
		mapper_.initialise();
	}
}
