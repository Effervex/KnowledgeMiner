package test.mining;

import static org.junit.Assert.assertTrue;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.wikipedia.CategoryChildMiner;
import knowledgeMiner.mining.wikipedia.CategoryMembershipMiner;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;
import knowledgeMiner.mining.wikipedia.FirstSentenceParserMiner;
import knowledgeMiner.mining.wikipedia.InfoboxRelationMiner;
import knowledgeMiner.mining.wikipedia.InfoboxTypeMiner;
import knowledgeMiner.mining.wikipedia.ListMiner;
import knowledgeMiner.mining.wikipedia.SubCategoryMiner;
import knowledgeMiner.mining.wikipedia.TitleMiner;
import knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.UtilityMethods;
import util.collection.MultiMap;

@RunWith(Parameterized.class)
public class CycMinerParameterisedTest {
	private static final File MINING_FILE = new File("miningTests.txt");
	private static OntologySocket ontology_;
	private static WikipediaSocket wmi_;

	private int art_;
	private static String artTitle_;
	private static KnowledgeMiner km_;
	private MultiMap<String, String> heuristicMap_;
	private static final boolean TEST_ALL = true;

	public CycMinerParameterisedTest(String articleTitle,
			MultiMap<String, String> heuristicAssertionMap) {
		try {
			if (!articleTitle.equals(artTitle_))
				System.out.println("== " + articleTitle + " ==");
			artTitle_ = articleTitle;
			art_ = wmi_.getArticleByTitle(articleTitle);
			heuristicMap_ = heuristicAssertionMap;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean shouldPerformTest(String heurName) {
		if (TEST_ALL)
			return true;
		if (!heuristicMap_.containsKey(heurName)
				|| heuristicMap_.isValueEmpty(heurName)) {
			System.out.println("No need to test " + heurName + " for '"
					+ artTitle_ + "'");
			return false;
		}
		return true;
	}

	/**
	 * The actual testing part of the test class.
	 * 
	 * @param heurName
	 *            The heuristic to load and test.
	 */
	private void testMiner(String heurName) {
		if (!shouldPerformTest(heurName))
			return;

		WikipediaArticleMiningHeuristic heuristic = (WikipediaArticleMiningHeuristic) km_
				.getHeuristicByString(heurName);
		if (heuristic == null) {
			System.err.println(heurName + " is not active!");
			return;
		}
			
		MinedInformation info = heuristic.mineArticle(art_,
				MinedInformation.ALL_TYPES, wmi_, ontology_);
		Collection<PartialAssertion> infoAssertions = info.getAssertions();
		String assertionString = infoAssertions.toString();
		Collection<String> assertions = heuristicMap_.get(heurName);
		// Check every assertion is in the info set
		for (String assertion : assertions) {
			// Clean up the assertion
			assertion = assertion.replaceAll("XXX", "mapArt('" + art_ + "')")
					.trim();

			// Test
			assertTrue("'" + assertion + "' not found in " + assertionString,
					assertionString.contains(assertion));
		}
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	@Test
	public void testCategoryChildMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(CategoryChildMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testCategoryMembershipMiner() throws Exception {
		CategoryMembershipMiner.wikifyText_ = true;
		String heurName = WeightedHeuristic
				.generateHeuristicName(CategoryMembershipMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testFirstSentenceMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(FirstSentenceMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testFirstSentenceParserMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(FirstSentenceParserMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testInfoboxRelationMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(InfoboxRelationMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testInfoboxTypeMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(InfoboxTypeMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testListMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(ListMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testSubCategoryMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(SubCategoryMiner.class);
		testMiner(heurName);
	}

	@Test
	public void testTitleMiner() throws Exception {
		String heurName = WeightedHeuristic
				.generateHeuristicName(TitleMiner.class);
		testMiner(heurName);
	}

	// @Parameters(name="{index}:{0}")
	@Parameters
	public static Collection<Object[]> loadMappingValues() throws IOException {
		Collection<Object[]> minedArts = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new FileReader(MINING_FILE));
		String[] header = reader.readLine().split("\\t");
		// Read every article line
		String input = null;
		while ((input = reader.readLine()) != null) {
			ArrayList<String> data = UtilityMethods.split(input, '\t',
					UtilityMethods.JUST_QUOTE);
			Object[] aData = { data.get(0), MultiMap.createListMultiMap() };
			for (int i = 1; i < data.size(); i++) {
				if (!data.get(0).isEmpty()) {
					String heurData = data.get(i);
					if (heurData.startsWith("\"") && heurData.endsWith("\""))
						heurData = UtilityMethods.shrinkString(heurData, 1);
					heurData = heurData.replaceAll("\"\"", "\"");
					ArrayList<String> assertions = UtilityMethods.split(
							heurData, ',');
					((MultiMap) aData[1]).putCollection(header[i], assertions);
				}
			}
			minedArts.add(aData);
		}
		reader.close();
		return minedArts;
	}

	/**
	 * Sets up the mapper beforehand.
	 * 
	 * @throws Exception
	 *             If something goes awry.
	 */
	@BeforeClass
	public static void setUp() throws Exception {
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
		km_ = KnowledgeMiner.newInstance("Enwiki_20110722");
	}
}
