/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import util.collection.WeightedSet;

/**
 * A class for processing the results received after mapping heuristics are
 * complete.
 * 
 * @author Sam Sarjant
 */
public abstract class MappingPostProcessor<Target> {
	/**
	 * Processes a collection of Target objects. Default implementation simply
	 * returns the collection.
	 * 
	 * @param collection
	 *            The collection being processed.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The Cyc access.
	 * @return The processed set (defaults to the input).
	 */
	public abstract WeightedSet<Target> process(WeightedSet<Target> collection,
			WMISocket wmi, OntologySocket ontology);
}
