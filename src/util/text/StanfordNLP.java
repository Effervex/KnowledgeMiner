/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.text;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import util.collection.MultiMap;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;

public class StanfordNLP {
	private static StanfordNLP instance_;
	/** The directory for language models. */
	private static final String MODEL_DIR = "lib";
	/** The language pack for dependencies. */
	private static final TreebankLanguagePack TLP = new PennTreebankLanguagePack();
	private LexicalizedParser lp_;

	private StanfordNLP() {
		String[] options = new String[] { "-maxLength", "150",
				"-retainTmpSubcategories" };
		lp_ = LexicalizedParser.loadModel(MODEL_DIR + File.separator
				+ "englishPCFG.ser.gz", options);
	}

	public Tree apply(List<String> words) {
		return lp_.apply(words);
	}

	/**
	 * Applies the parser to this string sentence.
	 * 
	 * @param sentence
	 *            The sentence to parse.
	 * @return The Tree representing the output parsing.
	 */
	public Tree apply(String sentence) {
		return lp_.apply(sentence);
	}

	/**
	 * Gets the typed dependencies between words from a sentence.
	 * 
	 * @param sentence
	 *            The sentence to get dependencies for.
	 * @return A list of dependencies between words.
	 */
	public GrammaticalStructure getGrammaticalStructure(String sentence) {
		return getGrammaticalStructure(apply(sentence));
	}

	/**
	 * Gets the typed dependencies between words from a parse.
	 * 
	 * @param parse
	 *            The parse to get grammatical dependencies between.
	 * @return A list of dependencies between words.
	 */
	public GrammaticalStructure getGrammaticalStructure(Tree parse) {
		GrammaticalStructureFactory gsf = TLP.grammaticalStructureFactory();
		return gsf.newGrammaticalStructure(parse);
	}

	/**
	 * Tags a sentence with the word types based on dependencies with other
	 * words.
	 * 
	 * @param sentence
	 *            The sentence being tagged.
	 * @return The same sentence with tags appended.
	 */
	public String tagSentence(String sentence) {
		Tree parse = apply(sentence);
		ArrayList<TaggedWord> tagged = parse.taggedYield();
		StringBuilder buffer = new StringBuilder();
		for (TaggedWord tag : tagged) {
			buffer.append(tag + " ");
		}
		return buffer.toString().trim();
	}

	public static StanfordNLP getInstance() {
		if (instance_ == null)
			instance_ = new StanfordNLP();
		return instance_;
	}

	public static void main(String[] args) {
		StanfordNLP parser = getInstance();
		System.out.println(parser.getGrammaticalStructure(args[0]));
	}

	public String[] getRootWords(String sentence) {
		GrammaticalStructure gs = getGrammaticalStructure(sentence);
		MultiMap<String, TypedDependency> dependencies = mapDependencies(gs);
		Collection<TypedDependency> rootDeps = dependencies.get("ROOT-0");
		String[] roots = new String[rootDeps.size()];
		int i = 0;
		for (TypedDependency td : rootDeps)
			roots[i] = td.dep().nodeString();
		return roots;
	}

	/**
	 * Converts a list of TypedDependencies into a map of word-based
	 * dependencies.
	 * 
	 * @param list
	 *            The list of dependencies to convert.
	 * @return A multimap of dependencies, mapped by word (with index).
	 */
	public MultiMap<String, TypedDependency> mapDependencies(
			GrammaticalStructure structure) {
		MultiMap<String, TypedDependency> wordDependencies = MultiMap
				.createListMultiMap();
		for (TypedDependency typeDep : structure.typedDependenciesCCprocessed()) {
			wordDependencies.put(typeDep.dep().toOneLineString(), typeDep);
			wordDependencies.put(typeDep.gov().toOneLineString(), typeDep);
		}
		return wordDependencies;
	}

}
