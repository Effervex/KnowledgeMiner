package test.mining;

import static org.junit.Assert.*;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.SentenceParserHeuristic;
import opennlp.tools.parser.Parse;

import org.junit.BeforeClass;
import org.junit.Test;

import util.Tree;
import cyc.CycConstants;
import cyc.MappableConcept;

public class SentenceParserHeuristicTest {
	private static OntologySocket ontology_;
	private static WMISocket wmi_;
	private static SentenceParserHeuristic sut_;

	@BeforeClass
	public static void setUp() throws Exception {
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		KnowledgeMiner km = KnowledgeMiner.newInstance("Enwiki_20110722");
		sut_ = new SentenceParserHeuristic(km.getMapper(), km.getMiner());
	}

	@Test
	public void testExtractAssertions() throws Exception {
		boolean wikifyText = false;
		MappableConcept focusConcept = new TextMappedConcept("Test", false,
				false);

		// Simple test
		String sentence = "Test was a battle.";
		Collection<PartialAssertion> output = sut_.extractAssertions(sentence,
				focusConcept, wikifyText, wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept, "battle", null)));
		assertEquals(output.size(), 1);

		// Adding a conjunction
		sentence = "Test was a battle and an event.";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept, "battle", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "event", null)));
		assertEquals(output.size(), 2);

		// Adjectives (Double JJ)
		sentence = "Test was an indecisive naval battle.";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept, "battle", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"indecisive battle",
				buildPartial(focusConcept, "indecisive", null))));
		assertTrue(output.contains(buildPartial(focusConcept, "naval battle",
				buildPartial(focusConcept, "naval", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"indecisive naval battle",
				buildPartial(focusConcept, "indecisive naval", null))));
		assertEquals(output.size(), 4);

		// Double NN
		sentence = "Test is a figure skater.";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept, "figure skater",
				null)));
		assertTrue(output.contains(buildPartial(focusConcept, "skater", null)));
		assertEquals(output.size(), 2);

		// Anchors & wikification
		sentence = "Test is a professional [[American football|football]] "
				+ "[[End (American football)|end]] and later a coach "
				+ "for the [[Miami Dolphins]].";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output
				.contains(buildPartial(
						focusConcept,
						"[[American football|football]] [[End (American football)|end]]",
						null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[End (American football)|end]]", null)));
		assertTrue(output
				.contains(buildPartial(
						focusConcept,
						"professional [[American football|football]] [[End (American football)|end]]",
						buildPartial(focusConcept, "professional", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"professional [[End (American football)|end]]",
				buildPartial(focusConcept, "professional", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[American football|football]]", null)));
		// assertTrue(output.contains(buildPartial(focusConcept, "coach",
		// null)));
		// assertEquals(output.size(), 6);

		// Three JJs + 2 NNs
		sentence = "Test was a Swiss former competitive figure skater.";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept, "figure skater",
				null)));
		assertTrue(output.contains(buildPartial(focusConcept, "skater", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"competitive figure skater",
				buildPartial(focusConcept, "competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"former competitive figure skater",
				buildPartial(focusConcept, "former competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss former competitive figure skater",
				buildPartial(focusConcept, "Swiss former competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"former figure skater",
				buildPartial(focusConcept, "former", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss figure skater",
				buildPartial(focusConcept, "Swiss", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss former figure skater",
				buildPartial(focusConcept, "Swiss former", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"competitive skater",
				buildPartial(focusConcept, "competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"former competitive skater",
				buildPartial(focusConcept, "former competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss former competitive skater",
				buildPartial(focusConcept, "Swiss former competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept, "former skater",
				buildPartial(focusConcept, "former", null))));
		assertTrue(output.contains(buildPartial(focusConcept, "Swiss skater",
				buildPartial(focusConcept, "Swiss", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss former skater",
				buildPartial(focusConcept, "Swiss former", null))));
		assertEquals(output.size(), 14);

		// Another big example (1 JJ + 3 NN with anchor)
		sentence = "Test was a [[public access television]] show.";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[public access television]]", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "show", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"television show", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"access television show", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "public show",
				buildPartial(focusConcept, "public", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"public television show",
				buildPartial(focusConcept, "public", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[public access television]] show",
				buildPartial(focusConcept, "public", null))));
		assertEquals(output.size(), 7);

		// Adjective as anchor (no redundant sub assertion)
		sentence = "Uma Karuna Thurman (born April 29, 1970) is an "
				+ "[[United States|American]] [[actress]] and "
				+ "[[Model (person)|model]].";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[United States|American]]", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "[[actress]]",
				null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[Model (person)|model]]", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[United States|American]] [[actress]]", null)));
		assertEquals(output.size(), 4);
	}

	@Test
	public void testExtractAssertionsWikified() throws Exception {
		boolean wikifyText = true;
		MappableConcept focusConcept = new TextMappedConcept("Test", false,
				false);

		// Simple test
		String sentence = "Test was a battle.";
		Collection<PartialAssertion> output = sut_.extractAssertions(sentence,
				focusConcept, wikifyText, wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept, "[[battle]]",
				null)));
		assertEquals(output.size(), 1);

		// Existing anchors
		sentence = "An '''electronic keyboard''' (also called '''digital keyboard''', '''portable keyboard''' and '''home keyboard''') is an electronic or digital [[keyboard instrument]].";
		output = sut_.extractAssertions(sentence, focusConcept, wikifyText,
				wmi_, ontology_, null);
		assertNotNull(output);
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[keyboard instrument]]", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "instrument",
				null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[digital]] instrument", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[Electronic music|electronic]]", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "[[digital]]",
				null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"[[digital]] [[keyboard instrument]]", null)));
		// assertTrue(output.contains(buildPartial(focusConcept,
		// "[[Electronic music|electronic]] [[keyboard instrument]]", null)));
		// assertTrue(output.contains(buildPartial(focusConcept,
		// "[[Electronic music|electronic]] instrument", null)));
		assertEquals(output.size(), 6);
	}

	private PartialAssertion buildPartial(MappableConcept focusConcept,
			String string, PartialAssertion subAssertion) {
		PartialAssertion pa = new PartialAssertion(
				CycConstants.ISA_GENLS.getConcept(), null, focusConcept,
				new TextMappedConcept(string, false, false));
		if (subAssertion != null)
			pa.addSubAssertion(subAssertion);
		return pa;
	}

	@Test
	public void testNonTaxonomic() throws Exception {
		String sentence = "Aim toothpaste is made in Australia by Pental Limited, Australia's largest soap manufacturer, with production facilities in Shepparton, Victoria.";
		MappableConcept focusConcept = new TextMappedConcept("Test", false,
				false);
		Collection<PartialAssertion> output = sut_.extractAssertions(sentence,
				focusConcept, true, wmi_, ontology_, null);
		System.out.println(output);
	}

	@Test
	public void testComposeAdjNounsTree() {
		SortedMap<String, String> anchors = new TreeMap<>();
		// Basic noun
		String sentence = "a battle";
		Parse parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		Collection<Tree<String>> adjNounTree = sut_.composeAdjNounsTree(parse,
				anchors);
		assertEquals(adjNounTree.size(), 1);
		assertTrue(adjNounTree.contains(new Tree<String>("battle")));

		// Adj noun combo
		sentence = "a naval battle";
		parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertEquals(adjNounTree.size(), 2);
		assertTrue(adjNounTree.contains(new Tree<String>("battle")));
		Tree<String> subT = new Tree<String>("naval battle");
		subT.addSubValue("naval");
		assertTrue(adjNounTree.contains(subT));

		// Double JJ
		sentence = "an indecisive naval battle";
		parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertEquals(adjNounTree.size(), 4);
		assertTrue(adjNounTree.contains(new Tree<String>("battle")));
		subT = new Tree<String>("naval battle");
		subT.addSubValue("naval");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("indecisive battle");
		subT.addSubValue("indecisive");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("indecisive naval battle");
		subT.addSubValue("indecisive naval");
		assertTrue(adjNounTree.contains(subT));

		// Double NN
		sentence = "a figure skater";
		parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertEquals(adjNounTree.size(), 2);
		assertTrue(adjNounTree.contains(new Tree<String>("skater")));
		assertTrue(adjNounTree.contains(new Tree<String>("figure skater")));

		// Double JJ + double NN
		sentence = "a former Swiss figure skater";
		parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertEquals(adjNounTree.size(), 8);
		assertTrue(adjNounTree.contains(new Tree<String>("skater")));
		assertTrue(adjNounTree.contains(new Tree<String>("figure skater")));
		subT = new Tree<String>("Swiss skater");
		subT.addSubValue("Swiss");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("former skater");
		subT.addSubValue("former");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("former Swiss skater");
		subT.addSubValue("former Swiss");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("Swiss figure skater");
		subT.addSubValue("Swiss");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("former figure skater");
		subT.addSubValue("former");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>("former Swiss figure skater");
		subT.addSubValue("former Swiss");
		assertTrue(adjNounTree.contains(subT));

		// Anchors
		sentence = "a professional football end";
		parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		anchors.clear();
		anchors.put("end", "[[End (American football)|end]]");
		anchors.put("football", "[[American football|football]]");
		adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertEquals(adjNounTree.size(), 5);
		assertTrue(adjNounTree.contains(new Tree<String>(
				"[[American football|football]]")));
		assertTrue(adjNounTree.contains(new Tree<String>(
				"[[End (American football)|end]]")));
		assertTrue(adjNounTree
				.contains(new Tree<String>(
						"[[American football|football]] [[End (American football)|end]]")));
		subT = new Tree<String>("professional [[End (American football)|end]]");
		subT.addSubValue("professional");
		assertTrue(adjNounTree.contains(subT));
		subT = new Tree<String>(
				"professional [[American football|football]] [[End (American football)|end]]");
		subT.addSubValue("professional");
		assertTrue(adjNounTree.contains(subT));
	}
}
