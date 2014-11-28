package test.mining;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.SentenceParserHeuristic;
import opennlp.tools.parser.Parse;

import org.junit.Before;
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
		PartialAssertion result = new PartialAssertion(
				CycConstants.ISA_GENLS.getConcept(), null, focusConcept,
				new TextMappedConcept("battle", false, false));

		// Adding a conjunction
		sentence = "Test was a battle and an event.";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);

		// Adjectives
		sentence = "Test was an indecisive naval battle.";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);

		// Anchors
		sentence = "Test is a professional [[American football|football]] "
				+ "[[End (American football)|end]] and later a coach "
				+ "for the [[Miami Dolphins]].";
		output = sut_.extractAssertions(sentence, focusConcept, wmi_,
				ontology_, null);
		assertNotNull(output);
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
