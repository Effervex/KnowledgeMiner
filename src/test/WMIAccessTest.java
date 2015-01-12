/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import knowledgeMiner.KnowledgeMiner;

import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;
import util.wikipedia.InfoboxData;
import util.wikipedia.WikiAnnotation;

public class WMIAccessTest {
	private static WMISocket wmi_;

	@BeforeClass
	public static void setUp() {
		KnowledgeMiner.getInstance();
		wmi_ = ResourceAccess.requestWMISocket();
	}

	@Test
	public void testGetArticleByTitle() throws IOException {
		// Simple
		assertEquals(wmi_.getArticleByTitle("Flea"), 77305);
		// Redirect
		assertEquals(wmi_.getArticleByTitle("flea"), 77305);
		// Jibberish
		assertEquals(wmi_.getArticleByTitle("GHJBKJFDA"), -1);
		// Non-redirect
		assertEquals(wmi_.getArticleByTitle("Samuel L. Jackson"), 54306);
		assertEquals(wmi_.getArticleByTitle("samuel l jackson"), -1);
		// Accented character
		assertEquals(wmi_.getArticleByTitle("Arthur Eichengrün"), 529207);
	}

	@Test
	public void testGetFirstSentence() throws IOException {
		// O Brother, Where Art Thou? (Infobox markup, no double new line)
		String first = null;
		first = wmi_.getFirstSentence(wmi_
				.getArticleByTitle("O Brother, Where Art Thou?"));
		assertEquals(
				first,
				"'''''O Brother, Where Art Thou?''''' is a 2000 [[comedy film]] directed by [[Coen brothers|Joel and Ethan Coen]] and starring [[George Clooney]], [[John Turturro]], [[Tim Blake Nelson]], [[John Goodman]], [[Holly Hunter]], and [[Charles Durning]].");

		// Kiwi (Space between infobox and text)
		first = wmi_.getFirstSentence(wmi_.getArticleByTitle("Kiwi"));
		assertEquals(
				first,
				"'''Kiwi''' are [[flightless bird]]s endemic to [[New Zealand]], in the [[genus]] '''''Apteryx''''' and family '''Apterygidae'''.");

		// Argentine Law 1420 (No infobox)
		first = wmi_.getFirstSentence(wmi_
				.getArticleByTitle("Argentine Law 1420"));
		assertEquals(
				first,
				"The '''Law 1420 of General Common Education''' of [[Argentina]] was a landmark national [[law]] that dictated public compulsory, free and [[secular education|secular]] [[education]].");

		// Problem sentence
		first = wmi_.getFirstSentence(wmi_
				.getArticleByTitle("Northern rat flea"));
		assertEquals(
				first,
				"The '''northern rat flea''' (''Nosopsyllus fasciatus'') is a species of [[flea]] that is found on [[domestic rat]]s and [[house mice]].");

		first = wmi_.getFirstSentence(wmi_.getArticleByTitle("Douglas Lenat"));
		assertEquals(
				first,
				"'''Douglas B. Lenat''' (born in 1950) is the [[CEO]] of [[Cycorp, Inc.]] of [[Austin, Texas]], and has been a prominent researcher in [[artificial intelligence]], especially [[machine learning]] (with his [[Automated Mathematician|AM]] and [[Eurisko]] programs), [[knowledge representation]], [[blackboard system]]s, and \"[[ontological engineering]]\" (with his [[Cyc]] program at [[Microelectronics and Computer Technology Corporation|MCC]] and at [[Cycorp]]).");

		// Special characters
		first = wmi_.getFirstSentence(wmi_
				.getArticleByTitle("List of peninsulas"));
		assertEquals(
				first,
				"A '''peninsula''' (, \"paene-\": almost + \"īnsula\": island; also called a '''byland''' or '''biland''') is a piece of [[Landform|land]] that is bordered by [[water]] on three sides but connected to [[mainland]].");

		first = wmi_.getFirstSentence(wmi_.getArticleByTitle("Lichen"));
		assertEquals(
				first,
				"'''Lichens''' are composite organisms consisting of a [[symbiosis|symbiotic]] association of a [[fungus]] (the '''mycobiont''') with a [[Photosynthesis|photosynthetic]] partner (the '''photobiont''' or '''phycobiont'''), usually either a [[green algae|green alga]] (commonly ''[[Trebouxia]]'') or [[Cyanobacteria|cyanobacterium]] (commonly ''[[Nostoc]]'').");

		// first = wmi_.getFirstSentence(wmi_
		// .getArticleByTitle("National Museum of Dentistry"));
		// assertEquals(
		// first,
		// "The '''Dr. Samuel D. Harris National Museum of Dentistry''' -- located in [[Baltimore, Maryland]], and opened in 1996—preserves and exhibits the history of [[dentistry]] in [[United States]] and throughout the world.");

		first = wmi_.getFirstSentence(46216); // (Israeli-Palestinian
		// conflict)
		assertEquals(
				first,
				"The '''Israeli–Palestinian conflict''' is the ongoing conflict between [[Israelis]] and [[Palestinian people|Palestinians]].");

		first = wmi_.getFirstSentence(wmi_.getArticleByTitle("Re Burley"));
		assertEquals(
				first,
				"'''''Re Burley''''' (1865), 1 U.C.L.J. 34, was a decision on [[extradition]] by the Court of Common Pleas of [[Upper Canada]].");

		first = wmi_.getFirstSentence(wmi_
				.getArticleByTitle("USS Nimitz (CVN-68)"));
		assertEquals(
				first,
				"'''USS ''Nimitz'' (CVN-68)''' is a [[supercarrier]] in the [[United States Navy]], the [[lead ship]] of [[Nimitz class aircraft carrier|her class]].");

		first = wmi_.getFirstSentence(wmi_.getArticleByTitle("Gul Khan Nasir"));
		assertTrue(first.startsWith("'''Mir Gul Khan Nasir'''"));

		first = wmi_.getFirstSentence(wmi_.getArticleByTitle("Politician"));
		assertEquals(
				first,
				"A '''politician''' or '''political leader''' (from Greek \"[[polis]]\") is an individual who is involved in [[politics|influencing public policy and decision making]].");

		first = wmi_.getFirstSentence(wmi_
				.getArticleByTitle("Orthopedic surgery"));
		assertEquals(
				first,
				"'''Orthopedic surgery''' or '''orthopedics''' (also spelled '''orthopaedic surgery''' and '''orthopaedics''' in [[Commonwealth countries]] and [[Ireland]]) is the branch of [[surgery]] concerned with conditions involving the [[musculoskeletal system]].");

		// Many cases
		assertFalse(wmi_.getFirstSentence(1047888).isEmpty());
		assertTrue(wmi_.getFirstSentence(17220533).isEmpty());
		assertFalse(wmi_.getFirstSentence(26786364).isEmpty());
		assertTrue(wmi_.getFirstSentence(10601996).isEmpty());
		assertFalse(wmi_.getFirstSentence(1047888).isEmpty());
		assertFalse(wmi_.getFirstSentence(2989331).isEmpty());
		assertTrue(wmi_.getFirstSentence(4740648).isEmpty());
		assertTrue(wmi_.getFirstSentence(3268443).isEmpty());
		assertTrue(wmi_.getFirstSentence(9591021).isEmpty());
		assertTrue(wmi_.getFirstSentence(1781033).isEmpty());
		assertFalse(wmi_.getFirstSentence(7735571).isEmpty());
		assertTrue(wmi_.getFirstSentence(2851112).isEmpty());
		assertFalse(wmi_.getFirstSentence(12493904).isEmpty());

		// False period ends.
		first = wmi_.getFirstSentence(14273024);
		assertTrue(first, first.length() > 10);

		first = wmi_.getFirstSentence(9280651);
		assertTrue(first, first.length() > 30);

		// Erroneous parse
		first = wmi_.getFirstSentence(18831775);
		assertTrue(first, first.length() > 30);
	}

	@Test
	public void testGetInfoboxData() throws Exception {
		// O Brother, Where Art Thou? (Infobox markup, no double new line)
		int artID = wmi_.getArticleByTitle("O Brother, Where Art Thou?");
		InfoboxData data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "film");
		Map<String, String> relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 18);
		assertEquals(relations.get("name"), "O Brother, Where Art Thou?");
		assertEquals(relations.get("director"),
				"[[Coen brothers|Joel Coen\nEthan Coen]]");
		assertEquals(relations.get("producer"),
				"[[Tim Bevan]]\n[[Eric Fellner]]\nEthan Coen\nJoel Coen");
		assertEquals(relations.get("released"), "{{Film date|2000|12|22}}");
		assertEquals(relations.get("runtime"), "108 minutes");
		assertEquals(relations.get("gross"), "$71,868,327");

		// Kiwi (Space between infobox and text)
		artID = wmi_.getArticleByTitle("Kiwi");
		data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "taxobox");
		relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 15);
		assertEquals(relations.get("name"), "Kiwi");
		assertEquals(relations.get("genus_authority"),
				"[[George Shaw|Shaw]], 1813");
		assertEquals(relations.get("subdivision"),
				"''[[Apteryx haastii]]'' \n\n" + "''[[Apteryx owenii]]'' \n\n"
						+ "''[[Apteryx rowi]]'' \n\n"
						+ "''[[Apteryx australis]]'' \n\n"
						+ "''[[Apteryx mantelli]]''");

		// Argentine Law 1420 (No infobox)
		artID = wmi_.getArticleByTitle("Argentine Law 1420");
		assertTrue(wmi_.getInfoboxData(artID).isEmpty());

		// Flea (Jump before infobox)
		artID = wmi_.getArticleByTitle("Flea");
		data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "taxobox");
		relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 16);
		assertEquals(relations.get("name"), "Flea");

		// Northern rat flea (empty relations)
		artID = wmi_.getArticleByTitle("Northern rat flea");
		data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "taxobox");
		relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 12);
		assertEquals(relations.get("name"), "Northern rat flea");
		assertFalse(relations.containsKey("binomial_authority"));

		// Clumped syntax
		artID = wmi_.getArticleByTitle("Ctenophthalmus pseudagyrtes");
		data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "taxobox");
		relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 9);
		assertEquals(relations.get("regnum"), "[[Animal]]ia");

		// Broken syntax
		artID = wmi_.getArticleByTitle("Gul Khan Nasir");
		data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "president");
		relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 12);
		assertEquals(relations.get("image"), "Mir Gul Khan Nasir.jpg");

		// Multiple infoboxes
		artID = wmi_.getArticleByTitle("USS Nimitz (CVN-68)");
		List<InfoboxData> datas = wmi_.getInfoboxData(artID);
		assertEquals(datas.get(0).getInfoboxType(), "ship begin");
		assertEquals(datas.get(1).getInfoboxType(), "ship image");
		assertEquals(datas.get(2).getInfoboxType(), "ship career");
		assertEquals(datas.get(3).getInfoboxType(), "ship characteristics");
		relations = datas.get(0).getInfoboxRelations();
		assertEquals(relations.size(), 0);
		relations = datas.get(1).getInfoboxRelations();
		assertEquals(relations.size(), 2);
		relations = datas.get(2).getInfoboxRelations();
		assertEquals(relations.size(), 15);
		relations = datas.get(3).getInfoboxRelations();
		assertEquals(relations.size(), 15);

		// Erroneous:
		artID = wmi_.getArticleByTitle("Team Performance Management (journal)");
		datas = wmi_.getInfoboxData(artID);
		assertNotNull(datas);

		// Weird characters
		artID = wmi_.getArticleByTitle("Phoenician language");
		data = wmi_.getInfoboxData(artID).get(0);
		assertEquals(data.getInfoboxType(), "language");
		relations = data.getInfoboxRelations();
		assertEquals(relations.size(), 12);
	}

	@Test
	public void testGetRelatedness() throws IOException {
		// Simple
		int artA = wmi_.getArticleByTitle("Flea");
		int artB = wmi_.getArticleByTitle("Kiwi");
		assertEquals(wmi_.getRelatednessPair(artA, artB).get(0), 0.5085, 0.001);

		// Null art
		assertEquals(wmi_.getRelatednessPair(-1, artB).get(0), 0.0, 0.0);
		assertEquals(wmi_.getRelatednessPair(artA, -1).get(0), 0.0, 0.0);

		// Same art
		assertEquals(wmi_.getRelatednessPair(artA, artA).get(0), 1.0, 0.0);
		assertEquals(wmi_.getRelatednessPair(artB, artB).get(0), 1.0, 0.0);

		// Symmetry
		assertEquals(wmi_.getRelatednessPair(artA, artB).get(0), wmi_
				.getRelatednessPair(artB, artA).get(0), 0.0000001);

		// Disambiguation
		artA = wmi_.getArticleByTitle("Bank robbery");
		artB = wmi_.getArticleByTitle("Bank");
		int artC = wmi_.getArticleByTitle("Ocean bank (topography)");
		assertTrue(wmi_.getRelatednessPair(artA, artB).get(0) > wmi_
				.getRelatednessPair(artA, artC).get(0));

		// Testing batches
		List<Double> results = wmi_.getRelatednessPair(artA, artA, artA, artB,
				artA, artC);
		assertEquals(results.size(), 3);
		assertEquals(results.get(0), 1, 0);
		assertEquals(results.get(1), 0.4293, 0.001);
		assertEquals(results.get(2), 0, 0);

		results = wmi_.getRelatednessList(artA, artA, artB, artC);
		assertEquals(results.size(), 3);
		assertEquals(results.get(0), 1, 0);
		assertEquals(results.get(1), 0.4293, 0.001);
		assertEquals(results.get(2), 0, 0);
	}

	@Test
	public void testGetWeightedArticles() throws IOException {
		WeightedSet<Integer> weighted = wmi_.getWeightedArticles("Kiwi");
		assertEquals(weighted.size(), 14);
		SortedSet<Integer> ordered = weighted.getOrdered();
		assertTrue(ordered.first() == wmi_.getArticleByTitle("Kiwi"));

		// Context-weighted articles
		// Country weighted
		Collection<Integer> context = new ArrayList<>();
		context.add(wmi_.getArticleByTitle("Country"));
		context.add(wmi_.getArticleByTitle("People"));
		WeightedSet<Integer> contextWeighted = wmi_.getWeightedArticles("Kiwi",
				KnowledgeMiner.CUTOFF_THRESHOLD, context);
		assertEquals(contextWeighted.size(), 14);
		SortedSet<Integer> contextOrdered = contextWeighted.getOrdered();
		assertTrue(!contextOrdered.equals(ordered));
		// assertTrue(contextOrdered.get(0) == wmi_
		// .getArticleByTitle("New Zealanders"));

		// Fruit weighted
		context.clear();
		context.add(wmi_.getArticleByTitle("Fruit"));
		contextWeighted = wmi_.getWeightedArticles("Kiwi",
				KnowledgeMiner.CUTOFF_THRESHOLD, context);
		assertEquals(contextWeighted.size(), 14);
		contextOrdered = contextWeighted.getOrdered();
		assertTrue(!contextOrdered.equals(ordered));
		// assertTrue(contextOrdered.get(0) ==
		// wmi_.getArticleByTitle("Kiwifruit"));

		// Rugby
		context.clear();
		context.add(wmi_.getArticleByTitle("Rugby football"));
		contextWeighted = wmi_.getWeightedArticles("Kiwi",
				KnowledgeMiner.CUTOFF_THRESHOLD, context);
		assertEquals(contextWeighted.size(), 14);
		contextOrdered = contextWeighted.getOrdered();
		assertTrue(!contextOrdered.equals(ordered));
		// assertTrue(contextOrdered.get(0) == wmi_
		// .getArticleByTitle("New Zealand national rugby league team"));
	}

	@Test
	public void testGetPageTitle() throws IOException {
		int artID = wmi_.getArticleByTitle("O Brother, Where Art Thou?");
		assertEquals(wmi_.getPageTitle(artID, true),
				"O Brother, Where Art Thou?");
		assertEquals(wmi_.getPageTitle(artID, false),
				"O Brother, Where Art Thou?");

		artID = wmi_.getArticleByTitle("Set (mathematics)");
		assertEquals(wmi_.getPageTitle(artID, true), "Set (mathematics)");
		assertEquals(wmi_.getPageTitle(artID, false), "Set");

		// Special character 'Neel temperature'
		artID = 284139;
		assertEquals(wmi_.getPageTitle(artID, true), "Néel temperature");
	}

	@Test
	public void testGetTopics() throws IOException {
		// Single topic
		SortedSet<WikiAnnotation> topics = wmi_.getTopics("cat");
		assertEquals(topics.size(), 1);
		WikiAnnotation a = topics.first();
		assertEquals(a, new WikiAnnotation("cat", "Cat", -1, -1,
				0.7399097776773746, wmi_));

		// Differing text
		topics = wmi_.getTopics("actress");
		assertEquals(topics.size(), 1);
		a = topics.first();
		assertEquals(a, new WikiAnnotation("actress", "Actor", -1, -1,
				0.5950602139834359, wmi_));

		// Multitext
		topics = wmi_
				.getTopics("American film and television actor and film producer.");
		assertEquals(topics.size(), 7);
		Iterator<WikiAnnotation> iter = topics.iterator();
		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("American", "United States", -1, -1,
				0.7374749662186019, wmi_));

		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("actor", "Actor", -1, -1,
				0.5745089454397226, wmi_));

		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("television actor", "Actor", -1, -1,
				0.5745089454397226, wmi_));

		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("film", "Film", -1, -1,
				0.49725625971138443, wmi_));

		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("television", "Television", -1, -1,
				0.4826978181529428, wmi_));

		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("film producer", "Film producer",
				-1, -1, 0.47920818387711667, wmi_));

		assertTrue(iter.hasNext());
		a = iter.next();
		assertEquals(a, new WikiAnnotation("producer", "Record producer", -1,
				-1, 0.25521056810505643, wmi_));

		String text = "{{script|Phnx|[[Image:Phoenician daleth.svg|12px|???]][[Image:Phoenician beth.svg|12px|???]][[Image:Phoenician res.svg|12px|???]][[Image:Phoenician yodh.svg|12px|???]][[Image:Phoenician mem.svg|12px|???]] [[Image:Phoenician kaph.svg|12px|???]][[Image:Phoenician nun.svg|12px|???]][[Image:Phoenician ayin.svg|12px|???]][[Image:Phoenician nun.svg|12px|???]][[Image:Phoenician yodh.svg|12px|???]][[Image:Phoenician mem.svg|12px|???]]}}";
		topics = wmi_.getTopics(text);
		assertTrue(topics.isEmpty());
	}

	@Test
	public void testAnnotate() throws IOException {
		// Single terms
		assertEquals(wmi_.annotate("Actor", 0, false), "[[Actor]]");
		assertEquals(wmi_.annotate("cat", 0, false), "[[cat]]");
		assertEquals(wmi_.annotate("actress", 0, false), "[[Actor|actress]]");
		assertEquals(wmi_.annotate("American", 0, false),
				"[[United States|American]]");
		assertEquals(wmi_.annotate("SAusaGe", 0, false), "[[SAusaGe]]");
		assertEquals(wmi_.annotate("GFDhkjs", 0, false), "GFDhkjs");
		assertEquals(wmi_.annotate("American actor", 0, false),
				"[[United States|American]] [[actor]]");

		// Multilines
		String text = "American film and television actor and film producer.";
		assertEquals(
				wmi_.annotate(text, 0, false),
				"[[United States|American]] [[film]] and [[Actor|television actor]] and [[film producer]].");
		text = "American screenwriter, director, actor, comedian, author, playwright, and musician whose career spans over half a century.";
		assertEquals(
				wmi_.annotate(text, 0, false),
				"[[United States|American]] [[screenwriter]], [[Film director|director]], [[actor]], [[comedian]], [[author]], [[playwright]], and [[musician]] whose career spans over half a century.");

		// Already annotated
		text = "[[United States|American]] [[screenwriter]], [[Film director|director]], [[actor]], [[comedian]], [[author]], [[playwright]], and [[musician]] whose career spans over half a century.";
		assertEquals(
				wmi_.annotate(text, 0, false),
				"[[United States|American]] [[screenwriter]], [[Film director|director]], [[actor]], [[comedian]], [[author]], [[playwright]], and [[musician]] whose career spans over half a century.");

		// Partially annotated
		text = "[[United States|American]] screenwriter, director, [[actor]], [[comedian]], [[author]], playwright, and [[musician]] whose career spans over half a century.";
		assertEquals(
				wmi_.annotate(text, 0, false),
				"[[United States|American]] [[screenwriter]], [[Film director|director]], [[actor]], [[comedian]], [[author]], [[playwright]], and [[musician]] whose career spans over half a century.");

		// Already annotated (problem string)
		text = "An electronic keyboard (also called digital keyboard, portable keyboard and home keyboard) is an electronic or digital [[keyboard instrument]].";
		assertEquals(
				wmi_.annotate(text, 0, false),
				"An [[electronic keyboard]] (also called [[Electronic keyboard|digital keyboard]], portable [[Keyboard instrument|keyboard]] and home [[Keyboard instrument|keyboard]]) is an [[Electronic music|electronic]] or [[digital]] [[keyboard instrument]].");
	}
}
