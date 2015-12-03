/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.*;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import knowledgeMiner.mapping.CycMapper;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.MultiMap;
import util.wikipedia.TableMiner;
import util.wikipedia.WikiTable;
import cyc.CycConstants;

/**
 * 
 * @author Sam Sarjant
 */
public class TableMinerTest {
	private static WikipediaSocket wmi_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		OntologySocket cyc = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
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
	 * {@link util.wikipedia.TableMiner#parseTable(java.lang.String)}.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testParseTable() throws IOException {
		// No table
		int article = wmi_.getArticleByTitle("Dog");
		Collection<WikiTable> tables = TableMiner.parseTable(wmi_
				.getMarkup(article));
		assertEquals(tables.size(), 0);

		article = wmi_.getArticleByTitle("Uma Thurman");
		tables = TableMiner.parseTable(wmi_.getMarkup(article));
		assertEquals(tables.size(), 2);
		Iterator<WikiTable> iter = tables.iterator();
		WikiTable table = iter.next();
		assertEquals(table.getContextTitle(), "Film");
		MultiMap<String, String> tableContents = table.getTableData();
		assertEquals(tableContents.size(), 4);
		assertTrue(tableContents.containsKey("Year"));
		assertTrue(tableContents.containsKey("Title"));
		assertTrue(tableContents.containsKey("Role"));
		assertTrue(tableContents.containsKey("Notes"));

		assertEquals(tableContents.values().size(), 44 * 4);
		assertEquals(((List<String>) tableContents.get("Year")).get(0), "1988");
		assertEquals(((List<String>) tableContents.get("Year")).get(3), "1988");
		assertEquals(((List<String>) tableContents.get("Year")).get(6), "1991");
		assertEquals(((List<String>) tableContents.get("Title")).get(0),
				"''[[Johnny Be Good]]''");
		assertEquals(((List<String>) tableContents.get("Title")).get(3),
				"''[[The Adventures of Baron Munchausen]]''");
		assertEquals(((List<String>) tableContents.get("Title")).get(6),
				"''[[Robin Hood (1991 film)|Robin Hood]]''");
		assertEquals(((List<String>) tableContents.get("Role")).get(0),
				"Georgia Elkans");
		assertEquals(((List<String>) tableContents.get("Role")).get(3),
				"[[Venus (mythology)|Venus]]/Rose");
		assertEquals(((List<String>) tableContents.get("Role")).get(6),
				"[[Maid Marian]]");
		assertEquals(((List<String>) tableContents.get("Notes")).get(0), "");
		assertEquals(((List<String>) tableContents.get("Notes")).get(3), "");
		assertEquals(((List<String>) tableContents.get("Notes")).get(6),
				"[[John Irvin]] directed TV movie.");

		table = iter.next();
		assertEquals(table.getContextTitle(), "Awards");
		tableContents = table.getTableData();
		assertEquals(tableContents.size(), 5);
		assertEquals(tableContents.values().size(), 32 * 5);
		assertTrue(tableContents.containsKey("Year"));
		assertTrue(tableContents.containsKey("Award"));
		assertTrue(tableContents.containsKey("Category"));
		assertTrue(tableContents.containsKey("Film"));
		assertTrue(tableContents.containsKey("Result"));
	}

	@Test
	public void testBatchLists() throws IOException {
		for (String list : ListMinerTest.testLists_) {
			String markup = wmi_.getMarkup(wmi_.getArticleByTitle(list));
			Collection<WikiTable> tables = TableMiner.parseTable(markup);
			for (WikiTable table : tables) {
				// Can't assert too much, other than basic reqs.
				MultiMap<String, String> tableData = table.getTableData();
				assertTrue(list + ": " + tableData.keySet(),
						tableData.size() > 1);
				for (Map.Entry<String, Collection<String>> entry : tableData
						.entrySet())
					assertFalse(list + ": " + entry.getKey(), entry.getValue()
							.isEmpty());
			}
		}
	}
}
