/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.preprocessing;

import graph.core.CommonConcepts;
import io.IOManager;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Predicate;

import cyc.OntologyConcept;

/**
 * 
 * @author Sam Sarjant
 */
public class RemoveUseless implements Preprocessor {
	private static final Pattern SPECIALISED_CONSTANT_PATTERN = Pattern
			.compile("\\w+:.+");
	private Collection<Predicate<OntologyConcept>> uselessQualifiers_;

	public RemoveUseless() {
		uselessQualifiers_ = new ArrayList<>();
		uselessQualifiers_.add(new SpecialisedConstants());
		uselessQualifiers_.add(new InfolessPredicate());
	}

	@Override
	public void processTerm(OntologyConcept term) throws Exception {
		for (Predicate<OntologyConcept> qualifier : uselessQualifiers_) {
			if (qualifier.apply(term)) {
				IOManager.getInstance().writeRemovedConstant(
						term.getConceptName());
				ResourceAccess.requestOntologySocket().removeConcept(
						term.getID());
				return;
			}
		}
	}

	/**
	 * Removes constants that are part of a specialised aspect of Cyc (e.g.
	 * neuroLex:, oboGo:...). Any strings that match the word-colon-word
	 * pattern.
	 * 
	 * @author Sam Sarjant
	 */
	private class SpecialisedConstants implements Predicate<OntologyConcept> {
		@Override
		public boolean apply(OntologyConcept constant) {
			if (!constant.isFunction()) {
				Matcher m = SPECIALISED_CONSTANT_PATTERN.matcher(constant
						.getConceptName());
				return m.matches();
			}
			return false;
		}
	}

	/**
	 * Removes predicates that have no information about their arguments.
	 * 
	 * @author Sam Sarjant
	 */
	private class InfolessPredicate implements Predicate<OntologyConcept> {
		@Override
		public boolean apply(OntologyConcept constant) {
			try {
				OntologySocket ontology = ResourceAccess
						.requestOntologySocket();
				if (ontology.isa(constant.getIdentifier(),
						CommonConcepts.PREDICATE.getID())
						&& ontology.isInfoless(constant)
						&& ontology.query(
								null,
								CommonConcepts.ASSERTED_SENTENCE.getID(),
								"(" + CommonConcepts.GENLPREDS.getID() + " "
										+ constant.getIdentifier() + " ?X)")
								.startsWith("0"))
					return true;
			} catch (Exception e) {
			}
			return false;
		}
	}
}
