/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;

import org.junit.Test;

import util.text.OpenNLP;
import util.text.StanfordNLP;
import edu.stanford.nlp.trees.GrammaticalStructure;

public class OpenNLPTest {

	@Test
	public void testFindSentence() throws IOException {
		SentenceDetector sd = OpenNLP.getSentenceDetector();
		String[] sentences = sd
				.sentDetect("This is an example of text. "
						+ "It could have all sorts of stuff in it. "
						+ "For example, and open bracket ( can be a real headache. "
						+ "A. B. Reviations is a fine man. He once said, \"Quoted texts.\" "
						+ "Or perhaps just a simple. example of a non-sentence.");
		assertEquals(sentences.length, 7);
	}

	@Test
	public void testTokeniser() {
		Tokenizer t = OpenNLP.getTokenizer();
		String[] tokens = t.tokenize("This is an example of text.");
		assertEquals(tokens.length, 7);
	}

	@Test
	public void testTagger() {
		POSTagger posTagger = OpenNLP.getTagger();
		String[] result = posTagger.tag(OpenNLP.getTokenizer().tokenize(
				"This is an example of text."));
		String[] answer = { "DT", "VBZ", "DT", "NN", "IN", "NN", "." };
		assertArrayEquals(result, answer);
		String[] tokens = OpenNLP.getTokenizer().tokenize(
				"This is an example of text.");
		String[] tags = posTagger.tag(tokens);
	}

	@Test
	public void testChunker() {
		Chunker chunker = OpenNLP.getChunker();
		String[] tokens = OpenNLP.getTokenizer().tokenize(
				"This is an example of text.");
		String[] tags = OpenNLP.getTagger().tag(tokens);
		String[] result = chunker.chunk(tokens, tags);
		assertEquals(result.length, 7);
	}

	private static String[] testSentence_ = OpenNLP
			.getSentenceDetector()
			.sentDetect(
//					"Hobbiton, located in Matamata, is a tourist attraction ");
					"'Pavel Roman' (January 25, 1943 - January 30, 1972) was a Czech figure skater .");
//					"Megalomys luciae, also known as the Santa Lucian Pilorie or Santa Lucian Giant Rice Rat, as well as several variant spellings, is an extinct rodent that lived on the island of Saint Lucia in the eastern Caribbean. It was the size of a small cat, and it had a darker belly than Megalomys desmarestii, a closely related species from Martinique, and slender claws. The last known specimen died in London Zoo in 1852, after three years of captivity. It probably became extinct in the latter half of the nineteenth century, with the last record dating from 1881. There is a specimen in the collection of the Natural History Museum in London.");

	// "Ecobank, whose official name is Ecobank Transnational Inc. (ETI), but is also known as Ecobank Transnational, is a pan-African banking conglomerate, with banking operations in 33 African countries. It is the leading independent regional banking group in West Africa and Central Africa, serving wholesale and retail customers. It also maintains subsidiaries in Eastern Africa, as well as in Southern Africa. ETI has representative offices in Angola, China, Dubai, France, South Africa and in the United Kingdom.");

	@Test
	public void testParser() {
		Parser p = OpenNLP.getParser();
		long start = System.currentTimeMillis();
		Parse parse = null;
		for (String str : testSentence_) {
			parse = OpenNLP.parseLine(str);
			parse.show();
		}
		System.out.println("OPN: " + (System.currentTimeMillis() - start));

		
	}

	@Test
	public void testStanfordParse() {
		StanfordNLP.getInstance().getGrammaticalStructure("Test sentence.");
		long start = System.currentTimeMillis();
		for (String str : testSentence_) {
			GrammaticalStructure gs = StanfordNLP.getInstance()
					.getGrammaticalStructure(str);
			System.out.println(GrammaticalStructure.dependenciesToString(gs,
					gs.typedDependenciesCollapsedTree(), gs.root(), false,
					false));
		}
		System.out.println("STN: " + (System.currentTimeMillis() - start));
	}
}
