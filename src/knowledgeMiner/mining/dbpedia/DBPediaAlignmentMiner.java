package knowledgeMiner.mining.dbpedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.wikipedia.WikipediaMappedConcept;

import org.apache.commons.lang3.StringUtils;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

import cyc.CycConstants;

public class DBPediaAlignmentMiner extends DBPediaMiningHeuristic {
	private static final String DBPEDIA_ENDPOINT = "http://dbpedia.org/sparql";
	/** The linking relation for article IDs. */
	public static final String DBPAGE_ID_RELATION = "dbpedia-owl:wikiPageID";
	private static final File BLACKLIST = new File("dbPediaBlacklist.txt");

	private Collection<String> blackList_;

	public DBPediaAlignmentMiner(CycMapper mapper, CycMiner miner) {
		super(true, mapper, miner);
		blackList_ = new HashSet<>();
		try {
			if (BLACKLIST.exists()) {
				BufferedReader reader = new BufferedReader(new FileReader(
						BLACKLIST));
				String input = null;
				while ((input = reader.readLine()) != null) {
					blackList_.add(input);
				}
				reader.close();
			} else {
				BLACKLIST.createNewFile();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Processes a triple, converting it to a PartialAssertion
	 *
	 * @param triple
	 *            The triple to convert to Partial Assertion
	 * @return A PartialAssertion representing the triple.
	 */
	private PartialAssertion processTriple(Map<String, RDFNode> triple,
			WikipediaMappedConcept focusConcept) {
		DBMappedConcept predicate = new DBMappedConcept(
				triple.get("?property"), true);
		String[] tripleString = new String[3];
		tripleString[1] = predicate.getResource().toString();
		if (isBlacklisted(predicate.getResource()))
			return null;

		if (triple.containsKey("?hasValue")) {
			tripleString[0] = triple.get("?article").toString();

			// Article Property Object
			RDFNode rdfId = triple.get("?valID");
			int id = (rdfId == null) ? -1 : rdfId.asLiteral().getInt();
			DBMappedConcept object = new DBMappedConcept(
					triple.get("?hasValue"), id, false);
			tripleString[2] = object.getResource().toString();
			HeuristicProvenance provenance = new HeuristicProvenance(this,
					StringUtils.join(tripleString, ','));
			PartialAssertion pa = new PartialAssertion(predicate,
					CycConstants.DATA_MICROTHEORY.getConceptName(), provenance,
					focusConcept, object);
			return pa;
		}
		// else if (triple.containsValue("?isValueOf")) {
		// tripleString[2] = triple.get("?article").toString();
		//
		// // Subject Property Article
		// DBMappedConcept subject = new DBMappedConcept(
		// triple.get("?isValueOf"), false);
		// tripleString[0] = subject.getResource().toString();
		// HeuristicProvenance provenance = new HeuristicProvenance(this,
		// StringUtils.join(tripleString, ','));
		// PartialAssertion pa = new PartialAssertion(predicate,
		// CycConstants.DATA_MICROTHEORY.getConceptName(), provenance,
		// subject, focusConcept);
		// return pa;
		// }
		return null;
	}

	/**
	 * Ignores predicates that are blacklisted.
	 *
	 * @param resource
	 *            The predicate to check if blacklisted.
	 * @return True if the predicate is in the blacklist.
	 */
	private boolean isBlacklisted(RDFNode resource) {
		return blackList_.contains(resource.toString());
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
		// Find the linked article and its triples
		int artID = info.getArticle();
		if (artID == -1)
			return;

		// TODO Restricted to DBOntology - it's cleaner.
		Collection<Map<String, RDFNode>> queryResults = askQuery(
				"?property",
				"?hasValue",
				"?article",
				"?valID",
				"?article dbowl:wikiPageID " + artID,
				"?article ?property ?hasValue",
				"OPTIONAL {?hasValue dbowl:wikiPageID ?valID}",
				"FILTER regex(str(?property), \""
						+ DBPediaNamespace.DBPEDIAOWL.getURI() + "\")");

		// Convert triples to partial assertions
		for (Map<String, RDFNode> triple : queryResults) {
			PartialAssertion pa = processTriple(triple,
					(WikipediaMappedConcept) info.getMappableSelfRef());
			if (pa != null)
				info.addAssertion(pa);
		}
	}

	@Override
	protected void setInformationTypes(boolean[] infoTypes) {
		infoTypes[InformationType.TAXONOMIC.ordinal()] = true;
		infoTypes[InformationType.NON_TAXONOMIC.ordinal()] = true;
		infoTypes[InformationType.SYNONYM.ordinal()] = true;
	}

	public static Collection<Map<String, RDFNode>> askQuery(
			String... variablesAndQueries) {
		// Split into variables and queries
		ArrayList<String> variables = new ArrayList<>();
		ArrayList<String> queries = new ArrayList<>();
		int i = 0;
		for (; i < variablesAndQueries.length; i++) {
			if (variablesAndQueries[i].matches("\\?\\S+"))
				variables.add(variablesAndQueries[i]);
			else {
				queries.add(variablesAndQueries[i]);
			}
		}
		String queryString = "SELECT " + StringUtils.join(variables, ' ')
				+ " WHERE { " + StringUtils.join(queries, " . ") + " }";

		// Calculate required prefixes.
		StringBuilder prefixes = new StringBuilder();
		for (DBPediaNamespace namespace : DBPediaNamespace.values()) {
			if (queryString.contains(" " + namespace.getShort() + ":")) {
				prefixes.append("PREFIX " + namespace.getShort() + ": <"
						+ namespace.getURI() + "> ");
			}
		}

		// Run the query
		ArrayList<Map<String, RDFNode>> results = new ArrayList<>();
		Query query = QueryFactory.create(prefixes.toString() + queryString);
		try {
			QueryEngineHTTP qeHTTP = new QueryEngineHTTP(DBPEDIA_ENDPOINT,
					query);
			ResultSet qResults = qeHTTP.execSelect();
			for (; qResults.hasNext();) {
				QuerySolution soln = qResults.nextSolution();
				Map<String, RDFNode> single = new HashMap<>();
				for (String var : variables) {
					if (soln.contains(var.substring(1)))
						single.put(var, soln.get(var.substring(1)));
				}
				results.add(single);
			}
			qeHTTP.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return results;
	}

	public static RDFNode askSingularQuery(String variable, String query) {
		Collection<Map<String, RDFNode>> results = askQuery(variable, query);
		if (!results.isEmpty())
			return results.iterator().next().get(variable);
		return null;
	}

}
