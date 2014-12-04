package test.mining;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.wikipedia.ListMiner;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class ListMinerTest {
	private static OntologySocket ontology_;
	private static WMISocket wmi_;
	private static ListMiner sut_;

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
	}

}
