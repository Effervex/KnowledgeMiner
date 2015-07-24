/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.MultiMap;
import util.text.StanfordNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * 
 * @author Sam Sarjant
 */
public class StanfordNLPTest {
	private static StanfordNLP sut_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		sut_ = StanfordNLP.getInstance();
	}

	/**
	 * Test method for {@link util.text.StanfordNLP#apply(java.lang.String)}.
	 */
	@Test
	public void testApplyString() {
		String sentence = "Bob is an actor.";
		Tree parse = sut_.apply(sentence);
		assertEquals(parse.depth(), 5);
		parse.pennPrint();

		sentence = "Sam Sarjant is a research fellow, gamer, "
				+ "and PhD student from Paeroa, New Zealand.";
		parse = sut_.apply(sentence);
		assertEquals(parse.depth(), 8);
		parse.pennPrint();
	}

	/**
	 * Test method for
	 * {@link util.text.StanfordNLP#getGrammaticalStructure(edu.stanford.nlp.trees.Tree)}
	 * .
	 */
	@Test
	public void testGetTypedDependencies() {
		String sentence = "Bob was an actor.";
		MultiMap<String, TypedDependency> dependencies = sut_
				.mapDependencies(sut_.getGrammaticalStructure(sentence));
		assertEquals(dependencies.size(), 5);
		assertEquals(dependencies.get("ROOT-0").toString(),
				"[root(ROOT-0, actor-4)]");
		assertEquals(dependencies.get("actor-4").size(), 4);
		String dependencyStr = dependencies.toString();
		System.out.println(dependencyStr);
		assertTrue(dependencyStr,
				dependencyStr.contains("nsubj(actor-4, Bob-1)"));
		assertTrue(dependencyStr, dependencyStr.contains("cop(actor-4, was-2)"));
		assertTrue(dependencyStr, dependencyStr.contains("det(actor-4, an-3)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("root(ROOT-0, actor-4)"));

		sentence = "Sam Sarjant is a research fellow, gamer, "
				+ "and PhD student from Paeroa, New Zealand.";
		dependencies = sut_.mapDependencies(sut_
				.getGrammaticalStructure(sentence));
		assertEquals(dependencies.size(), 13);
		dependencyStr = dependencies.toString();
		System.out.println(dependencyStr);
		assertTrue(dependencyStr,
				dependencyStr.contains("nsubj(fellow-6, Sarjant-2)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("nn(fellow-6, research-5)"));
		assertTrue(dependencyStr, dependencyStr.contains("cop(fellow-6, is-3)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("conj_and(fellow-6, gamer-8)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("conj_and(fellow-6, student-12)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("prep_from(student-12, Zealand-17)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("nn(Zealand-17, Paeroa-14)"));
		assertTrue(dependencyStr,
				dependencyStr.contains("root(ROOT-0, fellow-6)"));
	}

	@Test
	public void testGetRootWord() {
		String[] rootWords = sut_.getRootWords("Polish atheists");
		assertEquals(rootWords.length, 1);
		assertEquals(rootWords[0], "atheists");
		
		rootWords = sut_.getRootWords("Populated coastal places in Canada");
		assertEquals(rootWords.length, 1);
		assertEquals(rootWords[0], "Populated");
	}
}
