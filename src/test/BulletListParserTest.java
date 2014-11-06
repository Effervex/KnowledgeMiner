/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.mapping.CycMapper;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.MultiMap;
import util.wikipedia.BulletListParser;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class BulletListParserTest {
	private static WMISocket wmi_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper();
		mapper.initialise();
		CycConstants.initialiseAssertions(cyc);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic#mineArticle(ConceptModule, int, WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IOException
	 */
	@Test
	public void testMineArticle() throws Exception {
		int article = wmi_.getArticleByTitle("Collection");
		MultiMap<String, String> points = BulletListParser.parseBulletList(wmi_
				.getMarkup(article));
		assertEquals(points.size(), 4);
		assertTrue(points.containsKey("Mathematics"));
		assertEquals(points.get("Mathematics").size(), 4);
		assertTrue(points.containsKey("Other"));
		assertEquals(points.get("Other").size(), 6);
		assertTrue(points.containsKey("Albums"));
		assertEquals(points.get("Albums").size(), 24);
		assertTrue(points.containsKey(BulletListParser.NO_CONTEXT));
		assertEquals(points.get(BulletListParser.NO_CONTEXT).size(), 6);

		article = wmi_.getArticleByTitle("Rotting Christ");
		points = BulletListParser.parseBulletList(wmi_.getMarkup(article));
		assertEquals(points.size(), 5);
		// TODO Subcontext
		assertTrue(points.containsKey("Current members"));
		assertEquals(points.get("Current members").size(), 4);
		assertTrue(points.containsKey("Past members"));
		assertEquals(points.get("Past members").size(), 5);
		assertTrue(points.containsKey("Full length"));
		assertEquals(points.get("Full length").size(), 10);
		assertTrue(points.containsKey("Demos, singles, and DVDs"));
		assertEquals(points.get("Demos, singles, and DVDs").size(), 13);
		assertTrue(points.containsKey("Best of compilation"));
		assertEquals(points.get("Best of compilation").size(), 1);
	}
}
