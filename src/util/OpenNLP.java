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
package util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

public class OpenNLP {
	private static Chunker chunker_;

	private static final String MODELS_DIR = "models";

	private static SentenceDetector sentenceDetector_;

	private static POSTagger tagger_;

	private static Tokenizer tokenizer_;

	private static Parser parser_;

	static {
		try {
			SentenceModel sentenceModel = new SentenceModel(new File(MODELS_DIR
					+ File.separator + "sentdetect/en-sent.bin"));
			sentenceDetector_ = new SentenceDetectorME(sentenceModel);

			TokenizerModel tokenModel = new TokenizerModel(new File(MODELS_DIR
					+ File.separator + "tokenize/en-token.bin"));
			tokenizer_ = new TokenizerME(tokenModel);

			POSModel posModel = new POSModel(new File(MODELS_DIR
					+ File.separator + "tagger/en-pos-maxent.bin"));
			tagger_ = new POSTaggerME(posModel);

			ChunkerModel chunkerModel = new ChunkerModel(new File(MODELS_DIR
					+ File.separator + "chunker/en-chunker.bin"));
			chunker_ = new ChunkerME(chunkerModel);

			ParserModel parserModel = new ParserModel(new File(MODELS_DIR
					+ File.separator + "chunker/en-parser-chunking.bin"));
			parser_ = ParserFactory.create(parserModel);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static Chunker getChunker() {
		if (chunker_ == null) {

		}
		return chunker_;
	}

	public static SentenceDetector getSentenceDetector() {
		return sentenceDetector_;
	}

	public static POSTagger getTagger() {
		return tagger_;
	}

	public static Tokenizer getTokenizer() {
		return tokenizer_;
	}

	public static Parser getParser() {
		return parser_;
	}

	public static Parse parseLine(String text) {
		final Parse p = new Parse(text,
		// a new span covering the entire text
				new Span(0, text.length()),
				// the label for the top if an incomplete node
				AbstractBottomUpParser.INC_NODE,
				// the probability of this parse...uhhh...?
				1,
				// the token index of the head of this parse
				0);

		// make sure to initialize the _tokenizer correctly
		final Span[] spans = tokenizer_.tokenizePos(text);

		for (int idx = 0; idx < spans.length; idx++) {
			final Span span = spans[idx];
			// flesh out the parse with individual token sub-parses
			p.insert(new Parse(text, span, AbstractBottomUpParser.TOK_NODE, 0,
					idx));
		}
		return parser_.parse(p);
	}

	public static void main(String[] args) {
		String[] tokens = tokenizer_.tokenize(args[0]);
		System.out.println(Arrays.toString(tokens));
		String[] tags = tagger_.tag(tokens);
		System.out.println(Arrays.toString(tags));
		String[] chunks = chunker_.chunk(tokens, tagger_.tag(tokens));
		System.out.println(Arrays.toString(chunks));
	}
}
