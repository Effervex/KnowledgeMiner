package io.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

public class DBPediaAccess {
	public static final String DBPEDIA_ENDPOINT = "http://dbpedia.org/sparql";

	public static RDFNode selectSingularQuery(String variable, String query) {
		Collection<Map<String, RDFNode>> results = selectQuery(variable, query);
		if (!results.isEmpty())
			return results.iterator().next().get(variable);
		return null;
	}

	public static boolean askQuery(String... queries) {
		String askStr = "ASK WHERE {" + StringUtils.join(queries, " . ") + "}";

		// Calculate required prefixes.
		StringBuilder prefixes = new StringBuilder();
		for (DBPediaNamespace namespace : DBPediaNamespace.values()) {
			if (askStr.contains(" " + namespace.getShort() + ":")) {
				prefixes.append("PREFIX " + namespace.getShort() + ": <"
						+ namespace.getURI() + "> ");
			}
		}

		// Run the query
		Query query = QueryFactory.create(prefixes.toString() + askStr);
		try {
			QueryEngineHTTP qeHTTP = new QueryEngineHTTP(
					DBPediaAccess.DBPEDIA_ENDPOINT, query);
			boolean result = qeHTTP.execAsk();
			qeHTTP.close();
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public static Collection<Map<String, RDFNode>> selectQuery(
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
				+ " WHERE {" + StringUtils.join(queries, " . ") + "}";

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
			QueryEngineHTTP qeHTTP = new QueryEngineHTTP(
					DBPediaAccess.DBPEDIA_ENDPOINT, query);
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

}
