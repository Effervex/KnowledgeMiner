package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.dbpedia.DBMappedConcept;
import knowledgeMiner.mining.dbpedia.DBPediaAlignmentMiner;
import knowledgeMiner.mining.dbpedia.DBPediaNamespace;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;

import com.hp.hpl.jena.rdf.model.RDFNode;

import cyc.OntologyConcept;
import cyc.PrimitiveConcept;

public class DBMappedConceptTest {

	private static WMISocket wmi_;
	private static OntologySocket ontology_;
	private static CycMapper mapper_;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		KnowledgeMiner km = KnowledgeMiner.newInstance("Enwiki_20110722");
		mapper_ = km.getMapper();
	}

	@After
	public void tearDown() throws Exception {
		wmi_.clearCachedArticles();
		OntologyConcept.parsingArgs_ = false;
	}

	@Test
	public void testMapThingPrimitives() {
		// Primitive mapping
		OntologyConcept.parsingArgs_ = true;
		RDFNode queryResult = DBPediaAlignmentMiner.askSingularQuery(
				"?yearsActive",
				DBPediaNamespace
						.format(DBPediaNamespace.DBPEDIA, "Uma_Thurman")
						+ " "
						+ DBPediaNamespace.format(DBPediaNamespace.DBPEDIAPROP,
								"yearsActive") + " ?yearsActive");
		DBMappedConcept mappable = new DBMappedConcept(queryResult, false);
		WeightedSet<OntologyConcept> results = mappable.mapThing(mapper_, wmi_,
				ontology_);
		assertNotNull(results);
		// Could be primitive here, OR a date
		assertEquals(results.toString(), results.size(), 2);
		assertTrue(results.toString(),
				results.contains(new PrimitiveConcept(1985)));
		assertTrue(results.toString(),
				results.contains(new OntologyConcept("YearFn", "'1985")));

		// Primitive mapping (Double)
		queryResult = DBPediaAlignmentMiner.askSingularQuery(
				"?gravity",
				DBPediaNamespace.format(DBPediaNamespace.DBPEDIA, "Quartz")
						+ " "
						+ DBPediaNamespace.format(DBPediaNamespace.DBPEDIAPROP,
								"gravity") + " ?gravity");
		mappable = new DBMappedConcept(queryResult, false);
		results = mappable.mapThing(mapper_, wmi_, ontology_);
		assertNotNull(results);
		assertEquals(results.toString(), results.size(), 1);
		assertTrue(results.toString(),
				results.contains(new PrimitiveConcept(2.65)));

		// Date
		queryResult = DBPediaAlignmentMiner.askSingularQuery(
				"?birthDate",
				DBPediaNamespace
						.format(DBPediaNamespace.DBPEDIA, "Uma_Thurman")
						+ " "
						+ DBPediaNamespace.format(DBPediaNamespace.DBPEDIAPROP,
								"birthDate") + " ?birthDate");
		mappable = new DBMappedConcept(queryResult, false);
		results = mappable.mapThing(mapper_, wmi_, ontology_);
		assertNotNull(results);
		assertEquals(results.toString(), results.size(), 1);
		assertTrue(results.toString(), results.contains(OntologyConcept
				.parseArgument("(DayFn '29 (MonthFn April (YearFn '1970)))")));

		// Ambiguous Date
		queryResult = DBPediaAlignmentMiner.askSingularQuery(
				"?birthDate",
				DBPediaNamespace
						.format(DBPediaNamespace.DBPEDIA, "Ethan_Hawke")
						+ " "
						+ DBPediaNamespace.format(DBPediaNamespace.DBPEDIAPROP,
								"birthDate") + " ?birthDate");
		mappable = new DBMappedConcept(queryResult, false);
		results = mappable.mapThing(mapper_, wmi_, ontology_);
		assertNotNull(results);
		assertEquals(results.toString(), results.size(), 1);
		OntologyConcept dateA = OntologyConcept
				.parseArgument("(DayFn '6 (MonthFn November (YearFn '1970)))");
		assertTrue(results.toString(), results.contains(dateA));
	}

	@Test
	public void testMapThingArticle() {
		// Primitive mapping
		OntologyConcept.parsingArgs_ = true;
		Collection<Map<String, RDFNode>> queryResult = DBPediaAlignmentMiner
				.askQuery(
						"?spouse",
						DBPediaNamespace.format(DBPediaNamespace.DBPEDIA,
								"Uma_Thurman")
								+ " "
								+ DBPediaNamespace.format(
										DBPediaNamespace.DBPEDIAOWL, "spouse")
								+ " ?spouse");
		assertEquals(queryResult.toString(), queryResult.size(), 2);
		WeightedSet<OntologyConcept> results = new WeightedSet<>();
		for (Map<String, RDFNode> varMap : queryResult) {
			RDFNode node = varMap.get("?spouse");
			DBMappedConcept mappable = new DBMappedConcept(node, false);
			results.addAll(mappable.mapThing(mapper_, wmi_, ontology_));
		}
		assertNotNull(results);
		// Could be primitive here, OR a date
		assertEquals(results.toString(), results.size(), 2);
		assertTrue(results.toString(),
				results.contains(new OntologyConcept("GaryOldman")));
		assertTrue(results.toString(),
				results.contains(new OntologyConcept("EthanHawke")));
	}
}
