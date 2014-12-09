/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper();
		mapper.initialise();
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
		MultiMap<String, String> points = BulletListParser
				.parseBulletList("* [[Walt Disney Motion Pictures Group]]\n** [[Walt Disney Pictures]]\n** [[Touchstone Pictures]]\n");
		assertEquals(points.sizeTotal(), 3);

		int article = wmi_.getArticleByTitle("Collection");
		points = BulletListParser.parseBulletList(wmi_.getMarkup(article));
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

		article = wmi_.getArticleByTitle("List of assets owned by Disney");
		points = BulletListParser.parseBulletList(wmi_.getMarkup(article));
		assertEquals(points.size(), 16);
		assertTrue(points
				.containsKey("[[Walt Disney Studios (Burbank)|The Walt Disney Studios]]"));
		assertEquals(
				points.get(
						"[[Walt Disney Studios (Burbank)|The Walt Disney Studios]]")
						.size(), 29);
		assertTrue(points.containsKey("[[Disney-ABC Television Group]]"));
		assertEquals(points.get("[[Disney-ABC Television Group]]").size(), 67);
		assertTrue(points.containsKey("[[ESPN|ESPN, Inc.]]"));
		assertEquals(points.get("[[ESPN|ESPN, Inc.]]").size(), 25);
		assertTrue(points.containsKey("[[Disney Interactive Media Group]]"));
		assertEquals(points.get("[[Disney Interactive Media Group]]").size(),
				37);
		assertTrue(points.containsKey("[[Disney Consumer Products]]"));
		assertEquals(points.get("[[Disney Consumer Products]]").size(), 34);
		assertTrue(points.containsKey("[[Disneyland Resort]]"));
		assertEquals(points.get("[[Disneyland Resort]]").size(), 7);
		assertTrue(points.containsKey("[[Walt Disney World Resort]]"));
		assertEquals(points.get("[[Walt Disney World Resort]]").size(), 31);
		assertTrue(points.containsKey("[[Tokyo Disney Resort]]"));
		assertEquals(points.get("[[Tokyo Disney Resort]]").size(), 7);
		assertTrue(points.containsKey("[[Disneyland Paris]]"));
		assertEquals(points.get("[[Disneyland Paris]]").size(), 12);
		assertTrue(points.containsKey("[[Hong Kong Disneyland Resort]]"));
		assertEquals(points.get("[[Hong Kong Disneyland Resort]]").size(), 6);
		assertTrue(points.containsKey("[[Disney Cruise Line]]"));
		assertEquals(points.get("[[Disney Cruise Line]]").size(), 5);
		assertTrue(points.containsKey("[[Disney Vacation Club]]"));
		assertEquals(points.get("[[Disney Vacation Club]]").size(), 10);
		assertTrue(points.containsKey("Outreach Programs"));
		assertEquals(points.get("Outreach Programs").size(), 8);
		assertTrue(points.containsKey("Other Assets"));
		assertEquals(points.get("Other Assets").size(), 12);
		assertTrue(points.containsKey("Former Assets"));
		assertEquals(points.get("Former Assets").size(), 3);
		assertTrue(points.containsKey("Dormant or Shuttered Disney businesses"));
		assertEquals(points.get("Dormant or Shuttered Disney businesses")
				.size(), 17);
		assertFalse(points.containsKey("References"));
		assertFalse(points.containsKey("See also"));

		points = BulletListParser
				.parseBulletList("===[[Hong Kong Disneyland Resort]]===\n\n* [[Hong Kong International Theme Parks]]\n[[Penny's Bay]], [[Lantau Island]], [[Hong Kong]] (Disney 48%, Hong Kong Government 52%)\n** [[Hong Kong Disneyland]] - 2005\n** [[Inspiration Lake]] - 2005\n** Resorts:\n*** [[Disneyland Hotel (Hong Kong)|Hong Kong Disneyland Hotel]]\n*** [[Disney's Hollywood Hotel]]\n");
		assertTrue(points.containsKey("[[Hong Kong Disneyland Resort]]"));
		assertEquals(points.get("[[Hong Kong Disneyland Resort]]").size(), 6);
	}
}
