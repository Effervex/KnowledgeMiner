package test;

import static org.junit.Assert.*;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.IOException;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.TextMappedConcept;
import knowledgeMiner.mining.wikipedia.WikipediaMappedConcept;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.junit.BeforeClass;
import org.junit.Test;

import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;

public class DisjointnessDisambiguationTest {
	private static WikipediaSocket wmi_;
	private static OntologySocket cyc_;

	/**
	 * Sets up the mapper beforehand.
	 * 
	 * @throws Exception
	 *             If something goes awry.
	 */
	@BeforeClass
	public static void setUp() throws Exception {
		cyc_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
		KnowledgeMiner.newInstance("Enwiki_20110722");
	}

	@Test
	public void testDisjointnessDisambiguation() throws Exception {
		// Some example article and concept
		OntologyConcept horrorConcept = new OntologyConcept("HorrorMovie");
		int horrorArticle = wmi_.getArticleByTitle("Horror film");
		// Create the concept module
		ConceptModule cm = new ConceptModule(horrorConcept, horrorArticle, 1,
				true);
		// Create some test mined info (e.g. came from a heuristic)
		MinedInformation minedInfo = createMinedInfo(horrorArticle);
		// Merge it into the main concept module.
		cm.mergeInformation(minedInfo);
		// Add some mappings to various articles
		addArticleMappings();
		// Build the disambiguation grid (normally performed directly after
		// mining an article)
		cm.buildDisambiguationGrid(cyc_, wmi_);

		// Heavily bias the standing.
		cm.getStanding().addStanding(null, TermStanding.COLLECTION, 2048);
		// Disambiguate the assertions
		double weight = cm.disambiguateAssertions(cyc_);
		System.out.println(cm.getConcreteAssertions());
		System.out.println(weight);
		// Serialising the precomputed mining results to file.
		KnowledgeMinerPreprocessor.getInstance().writeHeuristics();
	}

	@Test
	public void testIsolatedDD() throws Exception {
		Integer article = wmi_.getArticleByTitle("Film");
		OntologyConcept concept = new OntologyConcept("Movie-CW");
		ConceptModule cm = new ConceptModule(concept, article, 1, true);
		KnowledgeMiner.getInstance().getMiner()
				.mineArticle(cm, MinedInformation.ALL_TYPES, wmi_, cyc_);
		// Build the DD grid
		cm.buildDisambiguationGrid(cyc_, wmi_);
		// Disambiguate.
		cm.disambiguateAssertions(cyc_);
		assertTrue(cm.getDeletedAssertions().isEmpty());
	}

	private void addArticleMappings() throws Exception {
		// Movie
		OntologyConcept concept = new OntologyConcept("Movie-CW");
		int article = wmi_.getArticleByTitle("Film");

		int result = ConceptMiningTask.addMapping(article, concept, cyc_);

		// Genre
		concept = new OntologyConcept("MovieTypeByGenre");
		article = wmi_.getArticleByTitle("Genre");
		result = ConceptMiningTask.addMapping(article, concept, cyc_);

		// Cat
		concept = new OntologyConcept("Cat");
		article = wmi_.getArticleByTitle("Cat");
		result = ConceptMiningTask.addMapping(article, concept, cyc_);
	}

	public MinedInformation createMinedInfo(int horrorArticle)
			throws IOException {
		MinedInformation minedInfo = new MinedInformation(horrorArticle);
		MappableConcept horrorMC = new WikipediaMappedConcept(horrorArticle);
		// Adding an article link
		minedInfo.addAssertion(new PartialAssertion(CycConstants.ISA_GENLS
				.getConcept(), null, horrorMC, new WikipediaMappedConcept(wmi_
				.getArticleByTitle("Film"))));
		// Adding a text assertion (with anchor)
		minedInfo.addAssertion(new PartialAssertion(CycConstants.ISA_GENLS
				.getConcept(), null, horrorMC, new TextMappedConcept(
				"[[Genre]]", false, false)));
		// Adding a text assertion (without anchor)
		minedInfo.addAssertion(new PartialAssertion(CycConstants.ISA_GENLS
				.getConcept(), null, horrorMC, new TextMappedConcept(
				"conceptual work", false, false)));
		// Adding an incorrect assertion too.
		minedInfo.addAssertion(new PartialAssertion(CycConstants.ISA_GENLS
				.getConcept(), null, horrorMC, new WikipediaMappedConcept(wmi_
				.getArticleByTitle("Cat"))));
		return minedInfo;
	}
}
