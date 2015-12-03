/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test.mining;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;

import org.junit.After;
import org.junit.BeforeClass;

import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class CycMinerTest {
	private static CycMiner miner_;
	private static WikipediaSocket wmi_;
	private static OntologySocket cyc_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		miner_ = KnowledgeMiner.getInstance().getMiner();
		wmi_ = ResourceAccess.requestWikipediaSocket();
		cyc_ = ResourceAccess.requestOntologySocket();
	}

	@After
	public void tearDown() {
		wmi_.clearCachedArticles();
	}

	/**
	 * Test method for
	 * {@link knowledgeMiner.mining.CycMiner#mineArticle(MinedInformation, int, WikipediaSocket, CycSocket)}
	 * .
	 * 
	 * @throws Exception
	 * 
	 * @throws Exception
	 */
//	@Test
	public void testMineArticle() throws Exception {
		// Pure data
		ConceptModule mapping = new ConceptModule(new OntologyConcept("Lenat"),
				wmi_.getArticleByTitle("Douglas Lenat"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);

		mapping = new ConceptModule(new OntologyConcept("RickWakeman"),
				wmi_.getArticleByTitle("Rick Wakeman"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);

		// Problem assertion
		mapping = new ConceptModule(new OntologyConcept("Lawyer"),
				wmi_.getArticleByTitle("Lawyer"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);

		// Problem assertion
		// mapping = new ConceptModule(new CycConcept("Nationalism",
		// wmi_.getArticleByTitle("Nationalism"), 1f);
		// miner_.mineArticle(mapping);

		// Child mining
		mapping = new ConceptModule(new OntologyConcept("Flea"),
				wmi_.getArticleByTitle("Flea"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);

		// A LOT of children
		mapping = new ConceptModule(new OntologyConcept("Dentist"),
				wmi_.getArticleByTitle("Dentist"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);

		// Exception
		mapping = new ConceptModule(new OntologyConcept("Temperature"),
				wmi_.getArticleByTitle("Temperature"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);

		mapping = new ConceptModule(new OntologyConcept("NeutronStar"),
				wmi_.getArticleByTitle("Neutron Star"), 1f, true);
		miner_.mineArticle(mapping, MinedInformation.ALL_TYPES, wmi_, cyc_);
	}
}
