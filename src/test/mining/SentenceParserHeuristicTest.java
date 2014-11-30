package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
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
		MappableConcept focusConcept = new TextMappedConcept("Test", false,
				false);

		// Simple test
		String sentence = "Test was a battle.";
		Collection<PartialAssertion> output = sut_.extractAssertions(sentence,
				focusConcept, wmi_, ontology_, null);
		assertNotNull(output);
		assertEquals(output.size(), 1);
		assertTrue(output.contains(buildPartial(focusConcept, "battle", null)));

		// Adding a conjunction
		sentence = "Test was a battle and an event.";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);
		assertEquals(output.size(), 2);
		assertTrue(output.contains(buildPartial(focusConcept, "battle", null)));
		assertTrue(output.contains(buildPartial(focusConcept, "event", null)));

		// Adjectives (Double JJ)
		sentence = "Test was an indecisive naval battle.";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);
		assertEquals(output.size(), 4);
		assertTrue(output.contains(buildPartial(focusConcept, "battle", null)));
		assertTrue(output.contains(buildPartial(focusConcept,
				"indecisive battle",
				buildPartial(focusConcept, "indecisive", null))));
		assertTrue(output.contains(buildPartial(focusConcept, "naval battle",
				buildPartial(focusConcept, "naval", null))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"indecisive naval battle",
				buildPartial(focusConcept, "indecisive naval", null))));

		// Double NN
		sentence = "Test is a figure skater.";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);
		assertEquals(output.size(), 2);
		assertTrue(output.contains(buildPartial(focusConcept, "figure skater",
				null)));
		assertTrue(output.contains(buildPartial(focusConcept, "skater", null)));

		// Anchors
		sentence = "Test is a professional [[American football|football]] "
				+ "[[End (American football)|end]] and later a coach "
				+ "for the [[Miami Dolphins]].";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);
		assertEquals(output.size(), 5);
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

		// Three JJs + 2 NNs
		sentence = "Test was a Swiss former competitive figure skater.";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);
		assertEquals(output.size(), 14);
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
		assertTrue(output
				.contains(buildPartial(
						focusConcept,
						"former competitive skater",
						buildPartial(
								focusConcept,
								"former",
								buildPartial(focusConcept,
										"former competitive", null)))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss former competitive skater",
				buildPartial(focusConcept, "Swiss former competitive", null))));
		assertTrue(output.contains(buildPartial(focusConcept, "former skater",
				buildPartial(focusConcept, "former", null))));
		assertTrue(output.contains(buildPartial(
				focusConcept,
				"Swiss skater",
				buildPartial(focusConcept, "Swiss",
						buildPartial(focusConcept, "Swiss", null)))));
		assertTrue(output.contains(buildPartial(focusConcept,
				"Swiss former skater",
				buildPartial(focusConcept, "Swiss former", null))));
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
	public void testComposeAdjNounsTree() {
		SortedMap<String, String> anchors = new TreeMap<>();
		// Basic
		String sentence = "a battle";
		Parse parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		Tree<String> adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertNotNull(adjNounTree);
		assertEquals(adjNounTree.getValue(), "battle");
		assertTrue(adjNounTree.getCurrentDepth() == 0);
		assertTrue(adjNounTree.getMaxDepth() == 0);

		sentence = "a naval battle.";
		parse = SentenceParserHeuristic.parseLine(sentence).getChildren()[0];
		adjNounTree = sut_.composeAdjNounsTree(parse, anchors);
		assertNotNull(adjNounTree);
		assertEquals(adjNounTree.getValue(), "naval battle");
		assertTrue(adjNounTree.getCurrentDepth() == 0);
		assertTrue(adjNounTree.getMaxDepth() == 1);
		Collection<Tree<String>> subTrees = adjNounTree.getSubTrees();
		assertEquals(subTrees.size(), 2);
		Iterator<Tree<String>> iter = subTrees.iterator();
		assertEquals(iter.next().getValue(), "naval");
		assertEquals(iter.next().getValue(), "battle");
	}

}
