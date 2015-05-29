package test.mining;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;
import java.util.Collection;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.ListMiner;
import knowledgeMiner.mining.wikipedia.WikipediaArticleMiningHeuristic;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.wikipedia.TableMiner;
import util.wikipedia.WikiTable;

public class ListMinerTest {
	private static OntologySocket ontology_;
	private static WMISocket wmi_;
	private static ListMiner sut_;
	public static final String[] testLists_ = {
			"List of state leaders in 1623",
			"List of MeSH codes (C02)",
			"List of colonial governors in 1584",
			"List of papal relatives created cardinal",
			"List of images in Gray's Anatomy: XI. Splanchnology",
			"List of Jean Michel Jarre concerts",
			"List of airports in Wallis and Futuna",
			"List of post-confederation New Brunswick general elections",
			"List of airlines of Djibouti",
			"List of Strangers with Candy episodes",
			"List of hospitals in Oklahoma",
			"List of Guilty Gear soundtracks",
			"List of number-one R&B singles of 1969 (U.S.)",
			"List of awards and nominations received by Missy Elliott",
			"List of animals of Malaysia",
			"List of disability rights organizations",
			"List of National Football League rushing touchdowns leaders",
			"List of US states by percentage foreign-born",
			"List of Hibiscus varieties",
			"List of Troy University alumni",
			"List of Boeing 777 operators",
			"List of sovereign states in 1814",
			"List of United States Supreme Court cases, volume 559",
			"List of minor planets/90501–90600",
			"List of Jurassic Park water rides",
			"List of number-one independent albums (U.S.)",
			"List of Canadian ambassadors to Afghanistan",
			"List of schools of the Roman Catholic Archdiocese of Chicago",
			"List of Chief Justices of Jamaica",
			"List of awards and nominations received by Akshay Kumar",
			"List of Organisation of Islamic Cooperation member states by GDP per capita (PPP)",
			"List of United States federal courthouses in Indiana",
			"List of awards and honors conferred on S. P. Balasubrahmanyam",
			"List of state-named roadways in Washington, D.C.",
			"List of overseas-born AFL players",
			"List of riots in Leeds",
			"List of political parties in Liechtenstein",
			"List of African dependencies",
			"List of Royal National College for the Blind people",
			"List of Major League Baseball players from Jamaica",
			"List of Presidents of Loyola Marymount University",
			"List of MADtv cast members",
			"List of airports in Kazakhstan",
			"List of NME number-one singles from the 1970s",
			"List of birds of Saint Vincent and the Grenadines",
			"List of Carnegie libraries in Minnesota",
			"List of Portuguese submissions for the Academy Award for Best Foreign Language Film",
			"List of IWW union shops",
			"List of fictional whales",
			"List of Chairmen of the Consultative Assembly of Oman",
			"List of Meerkat Manor meerkats",
			"List of Wyoming railroads",
			"List of Wycombe Wanderers F.C. players",
			"List of state leaders in 1241",
			"List of minor planets: 187001–188000",
			"List of channels on UPC Romania (Analogue)",
			"List of museums in Fort Lauderdale, Florida",
			"List of charity songs for Hurricane Katrina relief",
			"List of the Chief Justices of Patna High Court",
			"List of non-marine molluscs of El Hatillo Municipality, Miranda, Venezuela",
			"List of assets owned by Bell Media",
			"List of radio stations in Turin",
			"List of centuries in women's Test cricket",
			"List of properties managed by The Trustees of Reservations",
			"List of lighthouses in Sri Lanka",
			"List of Blackburn Rovers F.C. players",
			"List of Mike, Lu & Og episodes",
			"List of naval ships named for Minnesota",
			"List of Three Sheets episodes", "List of UCI ProTour records",
			"List of SNCF stations in Picardy",
			"List of lists of settlements in the United States",
			"List of the oldest mosques in the world",
			"List of parishes of Portugal" };

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		KnowledgeMiner km = KnowledgeMiner.newInstance("Enwiki_20110722");
		sut_ = (ListMiner) km.getHeuristicByString(ListMiner
				.generateHeuristicName(ListMiner.class));
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testMineArticle() throws IOException {
		// Mining a list
		int article = wmi_.getArticleByTitle("List of hobbies");
		sut_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_, ontology_);

		// Mining an article
		article = wmi_.getArticleByTitle("Hobby");
		sut_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_, ontology_);

		// Complex list title
		article = wmi_.getArticleByTitle("List of New Zealand actors");
		sut_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_, ontology_);

		// Table
		article = wmi_.getArticleByTitle("List of airports in New Zealand");
		sut_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_, ontology_);

		article = wmi_.getArticleByTitle("List of empires");
		sut_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_, ontology_);
		
		// Exception article
		article = wmi_.getArticleByTitle("List of NATO reporting names for surface-to-air missiles");
		sut_.mineArticle(article, MinedInformation.ALL_TYPES, wmi_, ontology_);
	}

	@Test
	public void testFindFocusColumn() throws IOException {
		for (String testList : testLists_) {
			Collection<WikiTable> tables = TableMiner.parseTable(wmi_
					.getMarkup(wmi_.getArticleByTitle(testList)));
			for (WikiTable table : tables) {
				String focusColumn = sut_.findFocusColumn(table.getTableData(),
						testList);
				System.out.println("Focus column for '" + testList + "' is: "
						+ focusColumn);
			}
		}
	}

	@Test
	public void testBatchTableMine() throws IOException {
		for (String testList : testLists_) {
			MinedInformation info = sut_.mineArticle(
					wmi_.getArticleByTitle(testList),
					MinedInformation.ALL_TYPES, wmi_, ontology_);
			System.out.println(testList);
//			assertFalse(info.getAssertions().isEmpty());
		}
	}
}
