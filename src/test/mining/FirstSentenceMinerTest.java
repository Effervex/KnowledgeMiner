/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;
import cyc.CycConstants;
import cyc.StringConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class FirstSentenceMinerTest {
	private static FirstSentenceMiner miner_;
	private static WMISocket wmi_;
	private static OntologySocket cyc_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		CycMapper mapper = new CycMapper(null);
		mapper.initialise();
		CycMiner miner = new CycMiner(null, mapper);
		miner_ = new FirstSentenceMiner(mapper, miner);
		CycConstants.initialiseAssertions(cyc_);
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	@Before
	public void setUp() {
		FirstSentenceMiner.wikifyText_ = false;
	}

	/**
	 * Tests the extractCollection method.
	 * 
	 * @throws Exception
	 *             Should something go awry...
	 */
	@Test
	public void testExtractParentLabels() throws Exception {
		// Basic single collection
		MinedInformation info = new MinedInformation(0);
		ArrayList<String> result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Blain'''",
								"'''Blain''' is an [[electoral divisions of the Northern Territory|electoral division]] of the [[Northern Territory Legislative Assembly|Legislative Assembly]] in [[Australia]]'s [[Northern Territory]].",
								info, wmi_), false, wmi_);
		assertEquals(result.size(), 1);
		assertTrue(result
				.contains("electoral divisions of the Northern Territory|electoral division"));

		// Basic 2 collection
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Tortellini Western'''",
								"'''Tortellini Western''' is an [[animated film]] [[Television program|series]] on NickToons Network.",
								info, wmi_), false, wmi_);
		assertEquals(result.size(), 2);
		assertTrue(result.contains("animated film"));
		assertTrue(result.contains("Television program|series"));

		// 3 collections
		result = miner_.extractParentLabels(
				miner_.regExpMatch("'''X'''",
						"'''X''' is a [[super]] [[cool]] [[hero]] from Y.",
						info, wmi_), false, wmi_);
		assertTrue(result.contains("super"));
		assertTrue(result.contains("cool"));
		assertTrue(result.contains("hero"));

		// Hyphenated 2 collection
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Leis Nobari'''",
								"'''Leis Nobari''' ([[Persian language|Persian]]: ) born '''Leis Naseri''' in [[Baghdad]], [[Iraq]] on [[September 23]],[[1977]] is an [[Iran]]ian-[[Iraqi]] (born from Iranian Parents) football player.",
								info, wmi_), false, wmi_);
		assertTrue(result.contains("Iran|Iranian"));
		assertTrue(result.contains("Iraqi"));

		// Comma separated list
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''David Aaronovitch'''",
								"'''David Aaronovitch''' (born [[July 8]], [[1954]]) is an [[England|English]] [[author]], [[broadcaster]] and [[journalist]].",
								info, wmi_), false, wmi_);
		assertTrue(result.contains("England|English"));
		assertTrue(result.contains("author"));
		assertTrue(result.contains("broadcaster"));
		assertTrue(result.contains("journalist"));

		// Several things
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Alexander \"Skunder\" Boghossian'''",
								"'''Alexander \"Skunder\" Boghossian''' (1937 in [[Addis Ababa]], [[Ethiopia]] &ndash; [[May 4]], [[2003]]) was an [[Ethiopia]]n-[[Armenia]]n [[Painting|painter]].",
								info, wmi_), false, wmi_);
		assertTrue(result.contains("Ethiopia|Ethiopian"));
		assertTrue(result.contains("Armenia|Armenian"));
		assertTrue(result.contains("Painting|painter"));

		// X is a Y of Z
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Basque Shepherd Dog'''",
								"The '''Basque Shepherd Dog''' ({{lang-eu|Euskal artzain txakurra}}) is a [[dog breed|breed]] of [[dog]] originating in the [[Basque Country (historical territory)|Basque Country]].",
								info, wmi_), false, wmi_);
		assertTrue(result.contains("dog breed|breed"));
		assertTrue(result.contains("dog"));

		// Checking regular loosening
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Human flea'''",
								"The many so-called '''Human flea''' (''Pulex irritans'' L., 1758) is a super cosmopolitan [[Siphonaptera|flea]] species that has, in spite of the common name, a wide host spectrum.",
								info, wmi_), false, wmi_);
		assertTrue(result.contains("Siphonaptera|flea"));

		// Some sort of infinite loop issue
		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Ivor Hugh Norman Evans'''",
								"'''Ivor Hugh Norman Evans''' (1886-1957) was a British [[anthropologist]], [[ethnographer]] and [[archaeologist]] who spent most of his working life in peninsular [[British Malaya]] (now [[Malaysia]]) and in [[North Borneo]] (now [[Sabah]], Malaysia).",
								info, wmi_), false, wmi_);
		assertTrue(result.contains("anthropologist"));
		assertTrue(result.contains("ethnographer"));
		assertTrue(result.contains("archaeologist"));

		result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''List of the NCAA Division I Men's Basketball Tournament Final Four Participants'''",
								"'''List of the NCAA [[Division I]] Men's Basketball Tournament Final Four Participants''' ''' Year''' ''' School''' ''' Conference''' ''' Tournament Region''' ''' Final Four Outcome''' [[1939 NCAA Men's Division I Basketball Tournament|1939]] '''Oregon '''Pacific Coast '''West '''National Champions''' ''Ohio State ''Big Ten ''East ''National Runner-Up'' Oklahoma Big Six West Villanova Independent East [[1940 NCAA Men's Division I Basketball Tournament|1940]] '''Indiana '''Big Ten '''East '''National Champions''' ''Kansas ''Big Six ''West ''National Runner-Up'' Duquesne Independent East USC Pacific Coast West [[1941 NCAA Men's Division I Basketball Tournament|1941]] '''Wisconsin '''Big Ten '''East '''National Champions''' ''Washington State ''Pacific Coast ''West ''National Runner-Up'' Arkansas Southwest West Pittsburgh Independent East [[1942 NCAA Men's Division I Basketball Tournament|1942]] '''Stanford '''Pacific Coast '''West '''National Champions''' ''Dartmouth ''EIL (Ivy) ''East ''National Runner-Up'' Colorado Mountain States West Kentucky Southeastern East [[1943 NCAA Men's Division I Basketball Tournament|1943]] '''Wyoming '''Mountain States '''West '''National Champions''' ''Georgetown ''Independent ''East ''National Runner-Up'' DePaul Independent East Texas Southwest West [[1944 NCAA Men's Division I Basketball Tournament|1944]] '''Utah '''Mountain States '''West '''National Champions''' ''Dartmouth ''EIL (Ivy) ''East ''National Runner-Up'' Iowa State Big Six West Ohio State Big Ten East [[1945 NCAA Men's Division I Basketball Tournament|1945]] '''Oklahoma A&M '''Missouri Valley '''West '''National Champions''' ''NYU ''Independent ''East ''National Runner-Up'' Arkansas Southwest West Ohio State Big Ten East [[1946 NCAA Men's Division I Basketball Tournament|1946]] '''Oklahoma A&M '''Missouri Valley '''West '''National Champions''' ''North Carolina ''Southern ''East ''National Runner-Up'' California Pacific Coast West Ohio State Big Ten East [[1947 NCAA Men's Division I Basketball Tournament|1947]] '''Holy Cross '''Independent '''East '''National Champions''' ''Oklahoma ''Big Six ''West ''National Runner-Up'' CCNY Independent East Texas Southwest West [[1948 NCAA Men's Division I Basketball Tournament|1948]] '''Kentucky '''Southeastern '''East '''National Champions''' ''Baylor ''Southwest ''West ''National Runner-Up'' Kansas State Big Seven West Holy Cross Independent East [[1949 NCAA Men's Division I Basketball Tournament|1949]] '''Kentucky '''Southeastern '''East '''National Champions''' ''Oklahoma A&M ''Missouri Valley ''West ''National Runner-Up'' Oregon State Pacific Coast West Illinois Big Ten East [[1950 NCAA Men's Division I Basketball Tournament|1950]] '''CCNY '''Independent '''East '''National Champions''' ''Bradley ''Missouri Valley ''West ''National Runner-Up'' Baylor Southwest West North Carolina State Southern East [[1951 NCAA Men's Division I Basketball Tournament|1951]] '''Kentucky '''Southeastern '''East '''National Champions''' ''Kansas State ''Big Seven ''West ''National Runner-Up'' Oklahoma A&M Missouri Valley West Illinois Big Ten East [[1952 NCAA Men's Division I Basketball Tournament|1952]] '''Kansas '''Big Seven '''West '''National Champions''' ''St.",
								info, wmi_), false, wmi_);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testExtractWikifiedParentLabels() throws Exception {
		FirstSentenceMiner.wikifyText_ = true;

		// Basic single collection
		ArrayList<String> result = miner_
				.extractParentLabels(
						miner_.regExpMatch(
								"'''Blain'''",
								"'''Blain''' is an [[electoral divisions of the Northern Territory|electoral division]] of the [[Northern Territory Legislative Assembly|Legislative Assembly]] in [[Australia]]'s [[Northern Territory]].",
								new MinedInformation(0), wmi_), false, wmi_);
		assertEquals(result.size(), 1);
		assertTrue(result
				.contains("electoral divisions of the Northern Territory|electoral division"));
	}

	@Test
	public void testFragmentExtract() throws Exception {
		// Invalid string
		ArrayList<String> terms = new ArrayList<String>();
		terms = miner_.extractParentLabels("invalid sort of string.", false,
				wmi_);
		assertEquals(terms.size(), 0);

		// Basic string
		terms.clear();
		terms = miner_.extractParentLabels("[[Tasty]] cheese.", false, wmi_);
		assertTrue(terms.contains("Tasty"));

		// Space separated and proximity linked
		terms.clear();
		terms = miner_.extractParentLabels("[[Tasty]] [[Cuba]]n cheese.",
				false, wmi_);
		assertTrue(terms.contains("Tasty"));
		assertTrue(terms.contains("Cuba|Cuban"));

		// Comma seperated
		terms.clear();
		terms = miner_.extractParentLabels(
				"[[Tasty]], [[Super]] [[Cuba]]n and [[Brown]] cheese.", false,
				wmi_);
		assertTrue(terms.contains("Tasty"));
		assertTrue(terms.contains("Super"));
		assertTrue(terms.contains("Cuba|Cuban"));
		assertTrue(terms.contains("Brown"));

		// Comma seperated, but not fully linked
		terms.clear();
		terms = miner_
				.extractParentLabels(
						"[[Tasty]], Super [[Cuba]]n and [[Brown]] cheese.",
						false, wmi_);
		assertTrue(terms.contains("Tasty"));
		assertFalse(terms.contains("Super"));
		assertFalse(terms.contains("Cuba|Cuban"));
		assertFalse(terms.contains("Brown"));

		// Hyphenated
		terms.clear();
		terms = miner_.extractParentLabels(
				"[[Iran]]ian-[[Iraq|Iraqi]] cheese.", false, wmi_);
		assertTrue(terms.contains("Iran|Iranian"));
		assertTrue(terms.contains("Iraq|Iraqi"));

		// X is a Y of Z
		terms.clear();
		terms = miner_
				.extractParentLabels(
						"[[dog breed|breed]] of [[dog]] originating in the [[Basque Country (historical territory)|Basque Country]].",
						false, wmi_);
		assertTrue(terms.contains("dog breed|breed"));
		assertTrue(terms.contains("dog"));

		// X is a <word> of Y
		terms.clear();
		terms = miner_
				.extractParentLabels(
						"family of [[flea]]s native to [[South America]], where they are found on [[rodents]].",
						false, wmi_);
		assertTrue(terms.contains("flea|fleas"));
		assertFalse(terms.contains("family"));
	}

	@Test
	public void testRegExpMatch() throws Exception {
		// Individuals
		// 'X was one of the Y'
		MinedInformation info = new MinedInformation(0);
		String result = miner_
				.regExpMatch(
						"Pappus of Alexandria",
						"'''Pappus of Alexandria''' was one of the last great "
								+ "[[Greek mathematics|Greek mathematician]]s of antiquity",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.INDIVIDUAL);
		assertEquals(result,
				"last great [[Greek mathematics|Greek mathematician]]s of antiquity");

		// 'Are a'
		info.clearInformation();
		result = miner_.regExpMatch("Bloc Party",
				"'''Bloc Party''' are a [[UK|British]] [[indie rock]] band.",
				info, wmi_);
		assertEquals(info.getStanding(), TermStanding.INDIVIDUAL);
		assertEquals(result, "[[UK|British]] [[indie rock]] band.");

		// Contradictions
		// 'Is a' in past tense (Was a)
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Louis Kelso",
						"'''Louis O. Kelso''' (1913-1991) was a [[lawyer]], "
								+ "[[private equity|investor]] and [[economics|economic thinker]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result,
				"[[lawyer]], [[private equity|investor]] and [[economics|economic thinker]].");

		// Basic 'is a' case (Contradiction)
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Ciumani",
						"'''Ciumani''' is a commune in [[Harghita County]], [[Romania]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "commune in [[Harghita County]], [[Romania]].");

		// 'Is an' (Contradiction)
		info.clearInformation();
		result = miner_.regExpMatch("Nicole Kidman",
				"'''Nicole Mary Kidman''', [[Order of Australia|AC]] (born June 20, "
						+ "1967) is an [[Academy Award]]-winning [[actress]].",
				info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "[[Academy Award]]-winning [[actress]].");

		// 'Is a' case with stuff between
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"American Head Charge",
						"'''American Head Charge''' (often referred to as '''Head Charge''' "
								+ "or abbreviated '''AHC''') is a [[hard rock]]/[[industrial metal]] "
								+ "band from [[Minneapolis]], [[Minnesota]] [[USA]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "[[hard rock]]/[[industrial metal]] "
				+ "band from [[Minneapolis]], [[Minnesota]] [[USA]].");

		// Prefix of 'the' for 'is a'
		info.clearInformation();
		result = miner_.regExpMatch("Pocklington Canal",
				"The '''Pocklington Canal''' is a broad canal "
						+ "which runs for 9.5 miles (15.2km) through "
						+ "nine [[canal lock|locks]].", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "broad canal "
				+ "which runs for 9.5 miles (15.2km) through "
				+ "nine [[canal lock|locks]].");

		// Present tense sort of 'is the'
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Earth",
						"'''Earth''' (pronounced ) is the third [[planet]] from the [[Sun]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "third [[planet]] from the [[Sun]].");

		// 'X is one of the Y'
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Dubai",
						"'''Dubai''' is one of the seven [[Emirates of the United "
								+ "Arab Emirates|emirates]] and the most populous city of the "
								+ "[[United Arab Emirates]] (UAE).", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "seven [[Emirates of the United "
				+ "Arab Emirates|emirates]] and the most populous city of the "
				+ "[[United Arab Emirates]] (UAE).");

		// 'Were an'
		info.clearInformation();
		result = miner_.regExpMatch("Billy Thorpe & the Aztecs",
				"'''Billy Thorpe and the Aztecs''' were an "
						+ "[[Australia]]n pop and rock group dating "
						+ "from the mid-[[sixties]].", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "[[Australia]]n pop and rock group dating "
				+ "from the mid-[[sixties]].");

		// Collections
		// 'Are the'
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Japanese People",
						"The '''Japanese people''' are the dominant [[ethnic group]] of [[Japan]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.COLLECTION);
		assertEquals(result, "dominant [[ethnic group]] of [[Japan]].");

		// 'Xs are Y'
		info.clearInformation();
		result = miner_.regExpMatch("Submarine aircraft carrier",
				"'''Submarine aircraft carriers''' are "
						+ "submarines equipped with [[fixed wing aircraft]] "
						+ "for observation or attack missions.", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.COLLECTION);
		assertEquals(result,
				"submarines equipped with [[fixed wing aircraft]] "
						+ "for observation or attack missions.");

		// 'The Xs are Y' 's' outside the bold
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Elfin MS8 Clubman",
						"The '''Elfin MS8 Clubman''' sports cars are successors to the Elfin MS7.",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.COLLECTION);
		assertEquals(result, "successors to the Elfin MS7.");

		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Basque Shepherd Dog",
						"The '''Basque Shepherd Dog''' ({{lang-eu|Euskal artzain txakurra}}) "
								+ "is a [[dog breed|breed]] of [[dog]] originating in the [[Basque "
								+ "Country (historical territory)|Basque Country]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result,
				"[[dog breed|breed]] of [[dog]] originating in the [[Basque "
						+ "Country (historical territory)|Basque Country]].");

		// Contradictions
		// Collection 'was a' case
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Rolls-Royce RB162",
						"The '''Rolls-Royce RB162''' was a simply constructed "
								+ "and lightweight British [[turbojet]] engine produced "
								+ "by [[Rolls-Royce Limited]].", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "simply constructed "
				+ "and lightweight British [[turbojet]] engine produced "
				+ "by [[Rolls-Royce Limited]].");

		// 'A X is a'
		info.clearInformation();
		result = miner_.regExpMatch("Wiki",
				"A '''wiki''' is a collection of [[web page]]s "
						+ "designed to enable anyone who accesses it to "
						+ "contribute or modify content, using a simplified "
						+ "[[markup language]].", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertEquals(result, "collection of [[web page]]s "
				+ "designed to enable anyone who accesses it to "
				+ "contribute or modify content, using a simplified "
				+ "[[markup language]].");

		// 'An X is a'
		info.clearInformation();
		result = miner_.regExpMatch("Aircraft carrier",
				"An '''aircraft carrier''' is a [[warship]] "
						+ "designed with a primary mission of deploying "
						+ "and recovering [[aircraft]], acting as "
						+ "a seagoing [[airbase]].", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertEquals(result, "[[warship]] "
				+ "designed with a primary mission of deploying "
				+ "and recovering [[aircraft]], acting as "
				+ "a seagoing [[airbase]].");

		// A contradiction 'is a'
		info.clearInformation();
		result = miner_.regExpMatch("Bread",
				"'''Bread''' is a [[staple food]] prepared by "
						+ "[[baking]] a [[dough]] of [[flour]] and [[water]].",
				info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertEquals(result, "[[staple food]] prepared by "
				+ "[[baking]] a [[dough]] of [[flour]] and [[water]].");

		// Another contradication 'the X is a'
		info.clearInformation();
		result = miner_.regExpMatch("Dog",
				"The '''dog''' (''Canis lupus familiaris'') "
						+ "is a [[Domestication|domesticated]] "
						+ "[[subspecies]] of the [[Gray Wolf|wolf]], "
						+ "a [[mammal]] of the [[Canidae]] family of "
						+ "the order [[Carnivora]].", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertEquals(result, "[[Domestication|domesticated]] "
				+ "[[subspecies]] of the [[Gray Wolf|wolf]], "
				+ "a [[mammal]] of the [[Canidae]] family of "
				+ "the order [[Carnivora]].");

		// 'Is the'
		info.clearInformation();
		result = miner_.regExpMatch("May",
				"'''May''' is the fifth [[month]] of the [[year]] "
						+ "in the [[Gregorian Calendar]] and one of seven "
						+ "Gregorian months with the length of 31 days.", info,
				wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result, "fifth [[month]] of the [[year]] "
				+ "in the [[Gregorian Calendar]] and one of seven "
				+ "Gregorian months with the length of 31 days.");

		// A bunch of junk with 'The X is the'
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Audi RS6 ",
						"The '''Audi RS6 quattro''', commonly referred to as "
								+ "the '''RS6''', is the highest performing version, "
								+ "and top-of-the-line specification of the [[Audi A6]], "
								+ "positioned above the [[Audi S6]].", info,
						wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertEquals(result, "highest performing version, "
				+ "and top-of-the-line specification of the [[Audi A6]], "
				+ "positioned above the [[Audi S6]].");

		// 'Xs are a Y' case
		info.clearInformation();
		result = miner_.regExpMatch("Hornbill",
				"'''Hornbills'''  ('''Bucerotidae''') are a [[family "
						+ "(biology)|family]] of [[bird]] found in tropical "
						+ "and sub-tropical [[Africa]] and [[Asia]].", info,
				wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertTrue(result.contains("[[family "
				+ "(biology)|family]] of [[bird]] found in tropical "
				+ "and sub-tropical [[Africa]] and [[Asia]]."));

		// X were a
		info.clearInformation();
		result = miner_.regExpMatch("Kipchaks",
				"'''Kipchaks''' (also spelled as ''Kypchaks'', "
						+ "''Kipczaks'', ''Qipchaqs'', ''Qypchaqs'') were "
						+ "an ancient [[Turkic people|Turkic people]].", info,
				wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN); // TermStanding.COLLECTION
		assertEquals(result, "ancient [[Turkic people|Turkic people]].");

		// Other tests
		// Article title matching
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Samuel L. Jackson",
						"'''Samuel Leon Jackson''' (born December 21, 1948) "
								+ "was one of the American film and television actors.",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.INDIVIDUAL);
		assertEquals(result, "American film and television actors.");

		// Interesting case... Calling it undetermined for now.
		info.clearInformation();
		result = miner_.regExpMatch("Julius Kronberg",
				"'''Julius Kronberg''', [[Swedish people|Swedish]] "
						+ "[[Painting|painter]], 1850 - 1921.", info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);

		// Lenat
		info.clearInformation();
		result = miner_
				.regExpMatch(
						"Douglas Lenat",
						"'''Douglas B. Lenat''' (born in 1950) is the [[CEO]] "
								+ "of [[Cycorp, Inc.]] of [[Austin, Texas]], and has been a "
								+ "prominent researcher in [[artificial intelligence]].",
						info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(
				result,
				"[[CEO]] of [[Cycorp, Inc.]] of [[Austin, Texas]], and has been a "
						+ "prominent researcher in [[artificial intelligence]].");

		// Genus
		info.clearInformation();
		result = miner_.regExpMatch("Genus",
				wmi_.getFirstSentence(wmi_.getArticleByTitle("Genus")), info,
				wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(
				result,
				"low-level [[taxonomic]] rank used in the [[biological classification]] "
						+ "of living and [[fossil]] [[organism]]s, which is an example of "
						+ "[[Genus-differentia definition|definition by genus and differentia]].");

		info.clearInformation();
		result = miner_.regExpMatch("Will Smith",
				wmi_.getFirstSentence(wmi_.getArticleByTitle("Will Smith")),
				info, wmi_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		assertEquals(result,
				"American actor, film producer and [[pop-rap|pop rapper]].");
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.wikipedia.FirstSentenceMiner#mineArticleInternal(MinedInformation, int, WMISocket, CycSocket)}
	 * .
	 * 
	 * @throws IOException
	 * @throws IllegalAccessException
	 */
	@Test
	public void testMineArticle() throws IOException, IllegalAccessException {
		OntologyConcept placeholder = OntologyConcept.PLACEHOLDER;

		// Basic article
		MinedInformation info = miner_.mineArticle(
				wmi_.getArticleByTitle("Dog"), MinedInformation.ALL_TYPES,
				wmi_, cyc_);
		assertTrue(info.getChildArticles().isEmpty());
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		Collection<MinedAssertion> concreteAssertions = info
				.getConcreteAssertions();
		assertEquals(concreteAssertions.size(), 2);
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("domestic dog"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"The domestic dog (Canis lupus familiaris and Canis lupus dingo) is a domesticated form of the gray wolf, a member of the Canidae family of the order Carnivora."),
						null, new HeuristicProvenance(miner_, null))));

		// Informative article
		info = miner_.mineArticle(wmi_.getArticleByTitle("Charlie Chaplin"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getChildArticles().isEmpty());
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		concreteAssertions = info.getConcreteAssertions();
		assertEquals(concreteAssertions.size(), 3);
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(CycConstants.SYNONYM_RELATION
						.getConcept(), placeholder, new StringConcept(
						"Sir Charles Spencer Chaplin"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("Charlie Chaplin"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"Sir Charles Spencer \"Charlie\" Chaplin, KBE (16 April 1889 25 December 1977) was an English comic actor, film director and composer best-known for his work during the silent film era."),
						null, new HeuristicProvenance(miner_, null))));

		// Doug Lenat
		info = miner_.mineArticle(wmi_.getArticleByTitle("Douglas Lenat"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getChildArticles().isEmpty());
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		concreteAssertions = info.getConcreteAssertions();
		assertEquals(concreteAssertions.size(), 2);
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("Douglas B. Lenat"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"Douglas B. Lenat (born in 1950) is the CEO of Cycorp, Inc. of Austin, Texas, and has been a prominent researcher in artificial intelligence, especially machine learning (with his AM and Eurisko programs), knowledge representation, blackboard systems, and \"ontological engineering\" (with his Cyc program at MCC and at Cycorp)."),
						null, new HeuristicProvenance(miner_, null))));

		info = miner_.mineArticle(wmi_.getArticleByTitle("Gul Khan Nasir"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getChildArticles().isEmpty());
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		concreteAssertions = info.getConcreteAssertions();
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("Mir Gul Khan Nasir"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(CycConstants.SYNONYM_RELATION
						.getConcept(), placeholder, new StringConcept(
						"Malek o-Sho'ara Balochistan"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"Mir Gul Khan Nasir, also widely regarded as Malek o-Sho'ara Balochistan, was a prominent politician, poet, historian, and journalist of Balochistan, Pakistan."),
						null, new HeuristicProvenance(miner_, null))));
		assertEquals(concreteAssertions.size(), 3);

		info = miner_.mineArticle(wmi_.getArticleByTitle("Lichen"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertTrue(info.getChildArticles().isEmpty());
		assertEquals(info.getStanding(), TermStanding.COLLECTION);
		concreteAssertions = info.getConcreteAssertions();
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"Lichens are composite organisms consisting of a symbiotic association of a fungus (the mycobiont) with a photosynthetic partner (the photobiont or phycobiont), usually either a green alga (commonly Trebouxia) or cyanobacterium (commonly Nostoc)."),
						null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("Lichens"), null, new HeuristicProvenance(miner_, null))));

		// Exception catching
		info = miner_.mineArticle(wmi_.getArticleByTitle("Genus"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		concreteAssertions = info.getConcreteAssertions();
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"In biology, a genus (plural: genera) is a low-level taxonomic rank used in the biological classification of living and fossil organisms, which is an example of definition by genus and differentia."),
						null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("genus"), null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("genera"), null, new HeuristicProvenance(miner_, null))));

		info = miner_.mineArticle(wmi_.getArticleByTitle("Sandra Harwood"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
		assertEquals(info.getStanding(), TermStanding.UNKNOWN);
		concreteAssertions = info.getConcreteAssertions();
		assertTrue(concreteAssertions
				.contains(new MinedAssertion(
						CycConstants.COMMENT.getConcept(),
						placeholder,
						new StringConcept(
								"Sandra Harwood is a Democratic politician who formerly served in the Ohio House of Representatives, representing the 65th District from 2003 to 2010."),
						null, new HeuristicProvenance(miner_, null))));
		assertTrue(concreteAssertions.contains(new MinedAssertion(
				CycConstants.SYNONYM_RELATION.getConcept(), placeholder,
				new StringConcept("Sandra Harwood"), null, new HeuristicProvenance(miner_, null))));

		// Exception mining
		info = miner_.mineArticle(
				wmi_.getArticleByTitle("John Hill (Texas politician)"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);

		info = miner_.mineArticle(wmi_.getArticleByTitle("Dating"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);

		info = miner_.mineArticle(wmi_.getArticleByTitle("Gatbawi"),
				MinedInformation.ALL_TYPES, wmi_, cyc_);
	}

	@Test
	public void testExtractSynonyms() {
		ArrayList<String> synonyms = miner_
				.extractSynonyms("'''Sonoma Mountain Zen Center''' (or, '''Genjoji''') is a [[Soto Zen]] practice center located on {{convert|80|acre|ha|-1}} in the mountainous region of [[Sonoma County]] in [[California]]—near [[Santa Rosa]]—carrying on the tradition and lineage of [[Shunryu Suzuki]]. Founded by [[Jakusho Kwong]] and his wife Laura Kwong in 1973, Kwong-[[roshi]] is the current guiding teacher of the Zen center. Offering residential training, Sonoma Mountain Zen Center also offers a practice regimen for members of the surrounding area and elsewhere who are not residents.");
		assertEquals(synonyms.size(), 2);
		assertTrue(synonyms.contains("Sonoma Mountain Zen Center"));
		assertTrue(synonyms.contains("Genjoji"));

		synonyms = miner_
				.extractSynonyms("'''''The Emperor of the Bathroom''''' is an [[extended play|EP]] by [[United States|American]] [[rock music|rock]] band [[the Minus 5]].  It was released in 1995 on [[East Side Digital]]. Presented by Rick Buckler, former drummer of the British mod rock band, The Jam.");
		assertEquals(synonyms.size(), 1);
		assertTrue(synonyms.contains("The Emperor of the Bathroom"));

		synonyms = miner_
				.extractSynonyms("The '''Transvaal Horse Artillery''' (usually abbreviated to ''\"THA\"'') is an [[artillery]] [[regiment]] of the [[South African Army]]. As a reserve unit, it has a status roughly equivalent to that of a [[British Territorial Army]] or [[United States]] [[Army National Guard]] unit. It is part of the South African Army Artillery Formation.");
		assertEquals(synonyms.size(), 1);
		assertTrue(synonyms.contains("Transvaal Horse Artillery"));

		synonyms = miner_
				.extractSynonyms("The '''[[Nissan]] Forum''' is a new [[concept car|concept]] [[minivan]] that debuted during the 2008 [[North American International Auto Show]].");
		assertEquals(synonyms.size(), 1);
		assertTrue(synonyms.contains("Nissan Forum"));

		synonyms = miner_
				.extractSynonyms("The '''''Blah'' de-Blah''' is a Blah de Blah.");
		assertEquals(synonyms.size(), 1);
		assertTrue(synonyms.contains("Blah de-Blah"));

		synonyms = miner_
				.extractSynonyms("'''New Zealanders''', colloquially known as '''[[Kiwi (people)|Kiwis]]''', are [[New Zealand nationality law|citizens]] of [[New Zealand]].");
		assertEquals(synonyms.size(), 2);
		assertTrue(synonyms.contains("New Zealanders"));
		assertTrue(synonyms.contains("Kiwis"));

		synonyms = miner_
				.extractSynonyms("'''Roderick Raynor \"Rod\" Paige''' (born June 17, 1933), served as the 7th [[United States Secretary of Education]] from 2001 to 2005.");
		assertEquals(synonyms.size(), 2);
		assertTrue(synonyms.contains("Roderick Raynor Paige"));
		assertTrue(synonyms.contains("Rod Paige"));

		synonyms = miner_
				.extractSynonyms("'''Willard Christopher''' \"'''Will'''\" '''Smith, Jr.''' (born September 25, 1968) is an American [[actor]], producer, and [[rapper]].");
		assertEquals(synonyms.size(), 2);
		assertTrue(synonyms.contains("Willard Christopher Smith, Jr."));
		assertTrue(synonyms.contains("Will Smith, Jr."));
	}
}
