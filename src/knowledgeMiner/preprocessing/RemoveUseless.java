/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.preprocessing;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import graph.core.CommonConcepts;
import graph.core.CycDAG;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import cyc.OntologyConcept;

/**
 * Removes useless concepts from the ontology. This is defined by concepts that
 * contribute little to no ontological definitions.
 * 
 * @author Sam Sarjant
 */
public class RemoveUseless implements Preprocessor {
	@Override
	public void processTerm(OntologyConcept concept) {
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		try {
			if (shouldRemove(concept, ontology)) {
				System.out.println("Removing " + concept.getConceptName());
				ontology.removeConcept(concept);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * An inner method for returning a boolean to remove.
	 *
	 * @param concept
	 *            The concept to check.
	 * @param ontology
	 *            The ontology access.
	 * @return True if the concept should be removed.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private boolean shouldRemove(OntologyConcept concept,
			OntologySocket ontology) throws Exception {
		if (ontology.isInfoless(concept))
			return true;

		return false;
	}

	public static void main(String[] args) {
		ResourceAccess.newInstance();
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			try {
				String str = in.readLine();
				OntologyConcept concept = new OntologyConcept(str);
				if (ontology.isInfoless(concept))
					System.out.println("true");
				else
					System.out.println("false");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
