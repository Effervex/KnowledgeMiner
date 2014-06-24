/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import graph.module.NLPToSyntaxModule;
import io.ResourceAccess;
import io.resources.WMISocket;

import java.util.ArrayList;

import org.junit.Test;

import util.IllegalDelimiterException;
import util.Pair;
import util.wikipedia.WikiParser;

public class WikiParserTest {

	@Test
	public void testCleanAllMarkup() {
		String cleaned = WikiParser
				.cleanAllMarkup("'''Dennis Perry Tarnow''' is an [[United States|America]]n [[dentist]] involved in the forefront of [[dental implant|implant]] research.");
		assertEquals(
				cleaned,
				"Dennis Perry Tarnow is an American dentist involved in the forefront of implant research.");

		cleaned = WikiParser
				.cleanAllMarkup("The '''[[date palm]]''' (''[[:species:Phoenix dactylifera|Phoenix dactylifera]]'') is a [[Arecaceae|[[Arecaceae|palm]]]] in the [[genus]] ''[[Phoenix (plant)|Phoenix]]'', [[Horticulture|cultivated]] for its [[Edible mushroom|edible]] [[sweet]] [[fruit]].");
		assertEquals(
				cleaned,
				"The date palm (Phoenix dactylifera) is a palm in the genus Phoenix, cultivated for its edible sweet fruit.");
	}

	@Test
	public void testCleanBrackets() {
		assertEquals(WikiParser.cleanBrackets("{remove!} but not this.", 0,
				'{', '}', false, false), " but not this.");
		assertEquals(WikiParser.cleanBrackets("{{remove!} but not this.", 0,
				'{', '}', false, false), "{{remove!} but not this.");
		assertEquals(WikiParser.cleanBrackets("{{remove!}}} but not this.", 0,
				'{', '}', false, false), "} but not this.");

		assertEquals(
				WikiParser.cleanBrackets(
						"[[Centre Party (Germany)|Centre Party]] (1906-1945)\n[[Christian Democratic Union of Germany|CDU]] (1945-1967)",
						0, '(', ')', false, true),
				"[[Centre Party (Germany)|Centre Party]] \n[[Christian Democratic Union of Germany|CDU]] ");
	}

	@Test
	public void testConvertToASCII() throws Exception {
		WMISocket wmi = ResourceAccess.requestWMISocket();

		// Special characters
		String first = wmi.getFirstSentence(wmi
				.getArticleByTitle("List of peninsulas"));
		String result = NLPToSyntaxModule.convertToAscii(first);
		// assertFalse(result.equals(first));
		assertEquals(
				result,
				"A '''peninsula''' (, \"paene-\": almost + \"insula\": island; also called a '''byland''' or '''biland''') is a piece of [[Landform|land]] that is bordered by [[water]] on three sides but connected to [[mainland]].");

		first = wmi.getFirstSentence(wmi.getArticleByTitle("Genus"));
		result = NLPToSyntaxModule.convertToAscii(first);
		// assertFalse(result.equals(first));
		assertEquals(
				result,
				"In [[biology]], a '''genus''' (plural: '''genera''') is a low-level [[taxonomic]] rank used in the [[biological classification]] of living and [[fossil]] [[organism]]s, which is an example of [[Genus-differentia definition|definition by genus and differentia]].");

		first = wmi.getFirstSentence(wmi.getArticleByTitle("Edelweiss (beer)"));
		result = NLPToSyntaxModule.convertToAscii(first);
		// assertFalse(result.equals(first));
		assertEquals(
				result,
				"Edelweiss Weissbier (also spelled Edelweiss Weissbier) is a type of [[white beer]], or more specifically, [[weissbier]], a culturally and historically specific style of white beer, brewed near [[Salzburg]], [[Austria]] by Hofbrau Kaltenhausen.");
	}

	@Test
	public void testReplaceBracketedByWhitespace() {
		assertEquals(WikiParser.replaceBracketedByWhitespace("text", '[', ']'),
				"text");
		assertEquals(
				WikiParser.replaceBracketedByWhitespace("[[text]]", '[', ']'),
				"        ");
		assertEquals(
				WikiParser.replaceBracketedByWhitespace("[text]", '[', ']'),
				"      ");
		assertEquals(
				WikiParser.replaceBracketedByWhitespace("[text]s", '[', ']'),
				"       ");
		assertEquals(WikiParser.replaceBracketedByWhitespace(
				"Shub{{text}} catdog", '{', '}'), "             catdog");
		assertEquals(WikiParser.replaceBracketedByWhitespace(
				"S{{text}}c butt {catdog}", '{', '}'),
				"           butt         ");
	}

	@Test
	public void testIndexOf() {
		assertEquals(WikiParser.indexOf("a test pattern", "p"), 7);
		assertEquals(WikiParser.indexOf("a test pattern", "pat"), 7);
		assertEquals(WikiParser.indexOf("a test pattern", "z"), -1);
		assertEquals(WikiParser.indexOf("a test [pattern]", "p"), -1);
	}

	@Test
	public void testFirst() throws IllegalDelimiterException {
		// No match test
		assertEquals("cats", WikiParser.first("cats", ".", null));
		// Delimit test
		assertEquals("cats.", WikiParser.first("cats.", ".", null));
		assertEquals("cats.", WikiParser.first("cats. Dogs too.", ".", null));
		// Longer delimiters
		assertEquals("cats. Dogs",
				WikiParser.first("cats. Dogs too.", "Dogs", null));
		// Brackets
		assertEquals("(cats.) Dogs too.",
				WikiParser.first("(cats.) Dogs too. And pigs.", ".", null));
		assertEquals("[cats.] Dogs too.",
				WikiParser.first("[cats.] Dogs too. And pigs.", ".", null));
		assertEquals("<cats.> Dogs too.",
				WikiParser.first("<cats.> Dogs too. And pigs.", ".", null));
		assertEquals("<<cats.>> Dogs too.",
				WikiParser.first("<<cats.>> Dogs too. And pigs.", ".", null));
		// Broken
		assertEquals("<<cats.> Dogs too. And pigs.",
				WikiParser.first("<<cats.> Dogs too. And pigs.", ".", null));
		assertEquals("<cats.>> Dogs too.",
				WikiParser.first("<cats.>> Dogs too. And pigs.", ".", null));
		// Apostrophes
		assertEquals("'cats.",
				WikiParser.first("'cats.' Dogs too. And pigs.", ".", null));
		assertEquals("''cats.'' Dogs too.",
				WikiParser.first("''cats.'' Dogs too. And pigs.", ".", null));
		assertEquals("'''cats.''' Dogs too.",
				WikiParser.first("'''cats.''' Dogs too. And pigs.", ".", null));
		assertEquals("'''''cats.''''' Dogs too.", WikiParser.first(
				"'''''cats.''''' Dogs too. And pigs.", ".", null));

		// Breakpoints
		try {
			WikiParser.first("<<cats.> Dogs}} too. And pigs.", ".", "}");
			fail("Should've thrown exception.");
		} catch (IllegalDelimiterException e) {
		}
		try {
			WikiParser.first("{{cats.} {{Rah}} Blah }} Blag trag", ".", "}");
			fail("Should've thrown exception.");
		} catch (IllegalDelimiterException e) {
		}
		try {
			WikiParser.first("<cats.>> Dogs too. And pigs.", ".", ">");
			fail("Should've thrown exception.");
		} catch (IllegalDelimiterException e) {
		}
		try {

			WikiParser.first("<cats.>> Dogs too. And pigs.", ".", "Dogs");
			fail("Should've thrown exception.");
		} catch (IllegalDelimiterException e) {
		}
		try {
			WikiParser.first("<cats.>> Dogs too. And pigs.\n\nMore text", ",",
					"\n\n");
			fail("Should've thrown exception.");
		} catch (IllegalDelimiterException e) {
		}

		// Escape characters
		assertEquals("cats\\. Dogs too.",
				WikiParser.first("cats\\. Dogs too. And pigs.", ".", null));

		assertEquals(
				"''[[God Save the Queen]]''&nbsp;{{smallsup|1}}\n|",
				WikiParser
						.first("''[[God Save the Queen]]''&nbsp;{{smallsup|1}}\n|"
								+ "capital = [[St. John's, Antigua and Barbuda|Saint John's]]",
								"|", "}"));
	}

	@Test
	public void testFind() {
		Pair<Integer, Integer> result = WikiParser.findBrackets(
				"test1, ({test2}, test3), test4", 0, '(', ')');
		assertEquals(result.objA_, 7, 0);
		assertEquals(result.objB_, 22, 0);

		result = WikiParser.findBrackets("test1, ({test2}, test3), test4", 0,
				'{', '}');
		assertEquals(result.objA_, 8, 0);
		assertEquals(result.objB_, 14, 0);

		result = WikiParser.findBrackets("test1, ({test2}, test3), test4", 8,
				'(', ')');
		assertNull(result);
	}

	@Test
	public void testSplit() {
		// No split test
		ArrayList<String> split = WikiParser.split("test", ",");
		assertEquals(split.size(), 1);
		assertEquals(split.get(0), "test");
		// Split test
		split = WikiParser.split("test1, test2", ",");
		assertEquals(split.size(), 2);
		assertEquals(split.get(0), "test1");
		assertEquals(split.get(1), " test2");
		// Brackets
		split = WikiParser.split("test1, (test2, test3), test4", ",");
		assertEquals(split.size(), 3);
		assertEquals(split.get(0), "test1");
		assertEquals(split.get(1), " (test2, test3)");
		assertEquals(split.get(2), " test4");
		// Apostrophes
		split = WikiParser.split("test1, ''test2, test3'', test4", ",");
		assertEquals(split.size(), 3);
		assertEquals(split.get(0), "test1");
		assertEquals(split.get(1), " ''test2, test3''");
		assertEquals(split.get(2), " test4");

		// Start value
		split = WikiParser.split("test thing", "test");
		assertEquals(split.size(), 2);
		assertEquals(split.get(0), "");
		assertEquals(split.get(1), " thing");

		// End value
		split = WikiParser.split("test thing", "thing");
		assertEquals(split.size(), 2);
		assertEquals(split.get(0), "test ");
		assertEquals(split.get(1), "");

		// Only value
		split = WikiParser.split("test thing", "test thing");
		assertEquals(split.size(), 1);
		assertEquals(split.get(0), "");
	}
}
