package test.mining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.DBPediaAccess;
import io.resources.DBPediaNamespace;
import io.resources.WikipediaSocket;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.dbpedia.DBMappedConcept;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;

import com.hp.hpl.jena.rdf.model.RDFNode;

import cyc.OntologyConcept;
import cyc.PrimitiveConcept;

public class DBMappedConceptTest {

	private static WikipediaSocket wmi_;
	private static OntologySocket ontology_;
	private static CycMapper mapper_;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWikipediaSocket();
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
		RDFNode queryResult = DBPediaAccess.selectSingularQuery("?yearsActive",
				DBPediaNamespace.DBPEDIA.format("Uma_Thurman") + " "
						+ DBPediaNamespace.DBPEDIAPROP.format("yearsActive")
						+ " ?yearsActive");
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
		queryResult = DBPediaAccess.selectSingularQuery("?gravity",
				DBPediaNamespace.DBPEDIA.format("Quartz") + " "
						+ DBPediaNamespace.DBPEDIAPROP.format("gravity")
						+ " ?gravity");
		mappable = new DBMappedConcept(queryResult, false);
		results = mappable.mapThing(mapper_, wmi_, ontology_);
		assertNotNull(results);
		assertEquals(results.toString(), results.size(), 1);
		assertTrue(results.toString(),
				results.contains(new PrimitiveConcept(2.65)));

		// Date
		queryResult = DBPediaAccess.selectSingularQuery("?birthDate",
				DBPediaNamespace.DBPEDIA.format("Uma_Thurman") + " "
						+ DBPediaNamespace.DBPEDIAPROP.format("birthDate")
						+ " ?birthDate");
		mappable = new DBMappedConcept(queryResult, false);
		results = mappable.mapThing(mapper_, wmi_, ontology_);
		assertNotNull(results);
		assertEquals(results.toString(), results.size(), 1);
		assertTrue(results.toString(), results.contains(OntologyConcept
				.parseArgument("(DayFn '29 (MonthFn April (YearFn '1970)))")));

		// Ambiguous Date
		queryResult = DBPediaAccess.selectSingularQuery("?birthDate",
				DBPediaNamespace.DBPEDIA.format("Ethan_Hawke") + " "
						+ DBPediaNamespace.DBPEDIAPROP.format("birthDate")
						+ " ?birthDate");
		mappable = new DBMappedConcept(queryResult, false);
		results = mappable.mapThing(mapper_, wmi_, ontology_);
		assertNotNull(results);
		assertEquals(results.toString(), results.size(), 1);
		OntologyConcept dateA = OntologyConcept
				.parseArgument("(DayFn '6 (MonthFn November (YearFn '1970)))");
		assertTrue(results.toString(), results.contains(dateA));
	}

	@Test
	public void testConceptCreation() throws Exception {
		OntologyConcept.parsingArgs_ = true;
		if (ontology_.inOntology("city"))
			ontology_.removeConcept("city");

		RDFNode queryResult = DBPediaAccess.selectSingularQuery("?predicate",
				DBPediaNamespace.DBPEDIA.format("WWCA") + " ?predicate "
						+ DBPediaNamespace.DBPEDIA.format("Gary,_Indiana"));
		DBMappedConcept mappable = new DBMappedConcept(queryResult, true);
		mappable.mapThing(mapper_, wmi_, ontology_);

		assertTrue(ontology_.inOntology("city"));
	}

	@Test
	public void testMapThingArticle() {
		// Primitive mapping
		OntologyConcept.parsingArgs_ = true;
		Collection<Map<String, RDFNode>> queryResult = DBPediaAccess.selectQuery(
				"?spouse", DBPediaNamespace.DBPEDIA.format("Uma_Thurman") + " "
						+ DBPediaNamespace.DBPEDIAOWL.format("spouse")
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
