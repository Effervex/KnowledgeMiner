/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package knowledgeMiner.mapping.textToCyc;

import graph.core.CommonConcepts;
import graph.inference.VariableNode;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Collection;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;

import org.apache.commons.lang3.StringUtils;

import util.UtilityMethods;
import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * The function parser attempts to parse wrapping functions from a string. The
 * string must have more than one tokens for the function parser to be able to
 * produce a result.
 * 
 * @author Sam Sarjant
 */
public class TextToCyc_FunctionParser extends
		MappingHeuristic<String, OntologyConcept> {
	private static final String FUNCTION_STRING = "______";

	public TextToCyc_FunctionParser(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapSourceInternal(String source,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		WeightedSet<OntologyConcept> results = new WeightedSet<>();

		String[] split = UtilityMethods.splitToArray(source, ' ');
		if (split.length <= 1)
			return results;

		// Replace different words with underscores and search
		Object[] isaFunction = { CommonConcepts.ISA.getID(),
				VariableNode.DEFAULT, CommonConcepts.FUNCTION.getID() };
		for (int textEnd = 0; textEnd < split.length - 1; textEnd++) {
			results.addAll(createPossibleFunction(textEnd, true, split,
					isaFunction, wmi, ontology));
		}
		for (int textStart = 1; textStart < split.length; textStart++) {
			results.addAll(createPossibleFunction(textStart, false, split,
					isaFunction, wmi, ontology));
		}

		return results;
	}

	/**
	 * Create possile functions by splitting text at a given index and treating
	 * one side as the function, and the other as the argument.
	 *
	 * @param textIndex
	 *            The split point.
	 * @param prefixText
	 *            If the text should be prefix or suffix.
	 * @param split
	 *            The split text.
	 * @param isaFunction
	 *            The query for isa ?X Function
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return All possible function-arguments found in the text.
	 */
	protected Collection<OntologyConcept> createPossibleFunction(int textIndex,
			boolean prefixText, String[] split, Object[] isaFunction,
			WMISocket wmi, OntologySocket ontology) {
		// Process the function text
		String possibleFunction = null;
		if (prefixText)
			possibleFunction = StringUtils.join(split, ' ', 0, textIndex + 1)
					+ " " + FUNCTION_STRING;
		else
			possibleFunction = FUNCTION_STRING + " "
					+ StringUtils.join(split, ' ', textIndex, split.length);
		Collection<OntologyConcept> functionConcepts = ontology
				.findFilteredConceptByName(possibleFunction, false, true, true,
						isaFunction);

		// Also check "'s" pattern
		possibleFunction = possibleFunction.replaceAll(FUNCTION_STRING,
				FUNCTION_STRING + "'s");
		functionConcepts.addAll(ontology
				.findFilteredConceptByName(possibleFunction, false, true, true,
						isaFunction));

		// Process the remaining text
		String remText = null;
		if (prefixText)
			remText = StringUtils.join(split, ' ', textIndex + 1, split.length);
		else
			remText = StringUtils.join(split, ' ', 0, textIndex);
		// TODO Recursive infinite loop
		HierarchicalWeightedSet<OntologyConcept> functionTarget = mapper_
				.mapTextToCyc(remText, true, false, false, false, wmi, ontology);
		return resolveFunctionCombination(functionConcepts, functionTarget,
				ontology);
	}

	/**
	 * Resolves the combination of functions and arguments.
	 *
	 * @param functionConcepts
	 *            The function concepts.
	 * @param functionTarget
	 *            The arguments of the function.
	 * @param ontologyThe
	 *            ontology access.
	 * @return All valid combined functions.
	 */
	private Collection<OntologyConcept> resolveFunctionCombination(
			Collection<OntologyConcept> functionConcepts,
			HierarchicalWeightedSet<OntologyConcept> functionTarget,
			OntologySocket ontology) {
		Collection<OntologyConcept> results = new ArrayList<>();
		for (OntologyConcept func : functionConcepts) {
			// Combine the two and accept ontology's arg checking
			for (OntologyConcept arg : functionTarget) {
				if (ontology.isValidArg(func.getIdentifier(),
						arg.getIdentifier(), 1)) {
					OntologyConcept combined = new OntologyConcept(
							func.getIdentifier(), arg.getIdentifier());
					results.add(combined);
				}
			}
		}
		return results;
	}
}
