/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.core.PrimitiveNode;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.slf4j.LoggerFactory;

import util.collection.WeightedSet;
import cyc.OntologyConcept;
import cyc.PrimitiveConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_NumericParse extends
		MappingHeuristic<String, OntologyConcept> {

	/**
	 * Constructor for a new NumberValueMiner
	 * 
	 * @param mapper
	 */
	public TextToCyc_NumericParse(CycMapper mapper) {
		super(mapper);
	}

	@Override
	public WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket cyc) {
		LoggerFactory.getLogger(getClass()).trace(source);
		WeightedSet<OntologyConcept> result = new WeightedSet<>();
		if (source == null || source.isEmpty())
			return result;
		PrimitiveNode pn = PrimitiveNode.parseNode(source);
		if (pn != null)
			result.add(new PrimitiveConcept(pn.getPrimitive()));

		// Remove commas
		if (source.matches("(\\d+,)+\\d+"))
			result.addAll(mapSourceInternal(source.replaceAll(",", ""), wmi,
					cyc));
		return result;
	}
}
