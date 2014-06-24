/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Collection;

import org.slf4j.LoggerFactory;

/**
 * A class for processing the source to potentially produce a different source
 * value(s).
 * 
 * @author Sam Sarjant
 */
public abstract class MappingPreProcessor<Source> {
	/**
	 * Processes an input value and returns a weighted set containing the
	 * processed output(s) of the input.
	 * 
	 * @param input
	 *            The input value.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The Cyc access.
	 * @return A weighted set of output value(s).
	 */
	public Collection<Source> process(Source input, WMISocket wmi,
			OntologySocket ontology) {
		LoggerFactory.getLogger(getClass()).trace("Preprocessing {}", input);
		return wrapSet(processSingle(input, wmi, ontology));
	}

	/**
	 * Same as process, but returns a single element.
	 * 
	 * @param input
	 *            The input value.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The single output.
	 */
	public abstract Source processSingle(Source input, WMISocket wmi,
			OntologySocket ontology);

	/**
	 * If the preprocessing step requires a recurse to a new hierarchy.
	 * 
	 * @return True by default.
	 */
	public boolean requiresRecurse() {
		return true;
	}

	protected Collection<Source> wrapSet(Source singleValue) {
		Collection<Source> set = new ArrayList<>(1);
		set.add(singleValue);
		return set;
	}
}
