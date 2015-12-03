package test.mining;

import static org.junit.Assert.assertFalse;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.dbpedia.DBPediaAlignmentMiner;

import org.junit.BeforeClass;
import org.junit.Test;

import cyc.OntologyConcept;

public class DBPediaAlignmentMinerTest {
	private static DBPediaAlignmentMiner sut_;
	private static OntologySocket ontology_;
	private static WikipediaSocket wmi_;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
		KnowledgeMiner km = KnowledgeMiner.newInstance("Enwiki_20110722");
		sut_ = new DBPediaAlignmentMiner(km.getMapper(), km.getMiner());
	}

	@Test
	public void testMineArticle() throws Exception {
		int article = wmi_.getArticleByTitle("Uma Thurman");
		OntologyConcept concept = new OntologyConcept("UmaThurman");
		ConceptModule cm = new ConceptModule(concept, article, 1, false);
		cm.mergeInformation(sut_.mineArticle(cm, MinedInformation.ALL_TYPES, wmi_, ontology_));
		assertFalse(cm.getAssertions().isEmpty());

		// Disambiguate
		cm.buildDisambiguationGrid(ontology_, wmi_);
		double weight = cm.disambiguateAssertions(ontology_);
		assertFalse(cm.getConcreteAssertions().isEmpty());
	}
}
