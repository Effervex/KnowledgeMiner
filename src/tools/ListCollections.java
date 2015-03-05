package tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collection;

import cyc.OntologyConcept;
import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import io.ontology.DAGAccess;
import io.ontology.DAGSocket;

public class ListCollections {
	public static void main(String[] args) throws Exception {
		DAGAccess access = new DAGAccess(2426);
		DAGSocket socket = (DAGSocket) access.requestSocket();
		Collection<OntologyConcept> collections = socket.quickQuery(
				CommonQuery.INSTANCES, "Collection");

		File file = new File("collections.txt");
		file.createNewFile();
		BufferedWriter out = new BufferedWriter(new FileWriter(file));
		for (OntologyConcept oc : collections) {
			try {
				out.write(oc.toPrettyString() + "\n");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		out.close();
	}
}
