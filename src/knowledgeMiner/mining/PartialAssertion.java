package knowledgeMiner.mining;

import graph.core.CommonConcepts;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;

import org.apache.commons.collections4.CollectionUtils;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;

public class PartialAssertion extends MinedAssertion {
	private static final Pattern INCREMENT_PATTERN = Pattern.compile("^(.+)",
			Pattern.MULTILINE);

	private static final long serialVersionUID = 1L;

	/** Optional sub-assertions for hierarchically structured mined information. */
	private Collection<PartialAssertion> subAssertions_;

	public PartialAssertion() {
		super();
	}

	public PartialAssertion(AssertionArgument mappablePredicate,
			HeuristicProvenance provenance, AssertionArgument... args) {
		super(mappablePredicate, null, provenance, args);
	}

	public PartialAssertion(AssertionArgument mappablePredicate,
			String microtheory, HeuristicProvenance provenance,
			AssertionArgument... args) {
		super(mappablePredicate, microtheory, provenance, args);
	}

	public PartialAssertion(PartialAssertion existing) {
		super(existing);
		if (existing.subAssertions_ != null) {
			subAssertions_ = new ArrayList<>();
			for (PartialAssertion subAssertion : subAssertions_)
				subAssertions_.add(subAssertion.clone());
		}
	}

	/**
	 * Compiles an assertion queue of assertions consisting of combinations of
	 * the expanded relation(s) and expanded args.
	 * 
	 * @param expandedRelation
	 *            The weighted set of relations to use for assertions.
	 * @param expandedArgs
	 *            The weighted set of arguments to use for the assertion
	 *            arguments. Null arguments represent a reference to args_.
	 * @param ontology
	 *            The ontology access.
	 * @return An assertion queue containing every valid assertion consisting of
	 *         combinations of the relations and args.
	 */
	private AssertionQueue compileAssertionQueue(
			WeightedSet<OntologyConcept> expandedRelation,
			WeightedSet<OntologyConcept>[] expandedArgs, OntologySocket ontology) {
		AssertionQueue aq = new AssertionQueue(getProvenance());
		// For every relation
		for (OntologyConcept relation : expandedRelation) {
			// relation needs to be a predicate of the correct arity
			if (relation.equals(CycConstants.ISA_GENLS.getConcept())
					|| ontology.isa(relation.getIdentifier(),
							CommonConcepts.PREDICATE.getID()))
				recurseBuildArgs(relation, 0,
						new AssertionArgument[args_.length], 1, expandedArgs,
						aq, ontology);
		}

		// Deal with sub-relations
		if (expandedRelation instanceof HierarchicalWeightedSet) {
			Collection<WeightedSet<OntologyConcept>> subRelations = ((HierarchicalWeightedSet<OntologyConcept>) expandedRelation)
					.getSubSets();
			for (WeightedSet<OntologyConcept> subRelation : subRelations) {
				AssertionQueue subAQ = compileAssertionQueue(subRelation,
						expandedArgs, ontology);
				if (!subAQ.isEmpty() & !subAQ.equals(aq))
					aq.addLower(subAQ);
			}
		}
		return aq;
	}

	/**
	 * Partially grounds a partial assertion by grounding all mappable concepts
	 * that are not excluded concepts into ontology concepts. The grounding
	 * process may produce multiple assertions with varying weights, so all
	 * concepts are returned within an AssertionQueue.
	 *
	 * @param excluded
	 *            The concepts that are not allowed to be grounded (typically
	 *            the focus article of the assertion).
	 * @param mapper
	 *            The mapper for grounding concepts.
	 * @param ontology
	 *            The ontology access.
	 * @param wmi
	 *            The WMI access.
	 * @return An assertion queue of all the potential groundings created in
	 *         this process.
	 */
	@SuppressWarnings("unchecked")
	private AssertionQueue expandInternal(Collection<MappableConcept> excluded,
			CycMapper mapper, OntologySocket ontology, WMISocket wmi) {
		AssertionQueue aq = new AssertionQueue(heuristic_);
		if (relation_ != null) {
			// Find relation(s)
			WeightedSet<OntologyConcept> expandedRelation = new WeightedSet<>();
			if (relation_ instanceof MappableConcept) {
				expandedRelation = ((MappableConcept) relation_).mapThing(
						mapper, wmi, ontology);

				// No relation found.
				// TODO Modify this to allow relation creation (if arg known)
				if (expandedRelation.isEmpty())
					return aq;
			} else
				expandedRelation.add((OntologyConcept) relation_);

			// Expand the args
			WeightedSet<OntologyConcept>[] expandedArgs = new WeightedSet[args_.length];
			for (int i = 0; i < args_.length; i++) {
				if (!excluded.contains(args_[i])
						&& args_[i] instanceof MappableConcept) {
					expandedArgs[i] = ((MappableConcept) args_[i]).mapThing(
							mapper, wmi, ontology);
					// TODO Avoid self-referential excluded anchors

					// No concept found.
					if (expandedArgs[i].isEmpty())
						return aq;
				}
			}

			// Combine the expanded args into valid assertions
			aq = compileAssertionQueue(expandedRelation, expandedArgs, ontology);
			aq.scaleAll(getWeight());
		}
		return aq;
	}

	/**
	 * Recursively build the args from the expanded arguments.
	 * 
	 * @param relation
	 *            The relation to use for checking arg constraints.
	 * @param i
	 *            The current recursive index.
	 * @param argArray
	 *            The array to recursively build.
	 * @param weight
	 *            The weight of the combined args.
	 * @param expandedArgs
	 *            The args to use.
	 * @param aq
	 *            The assertion queue to add to.
	 * @param ontology
	 *            The ontology access.
	 */
	private void recurseBuildArgs(OntologyConcept relation, int i,
			AssertionArgument[] argArray, double weight,
			WeightedSet<OntologyConcept>[] expandedArgs, AssertionQueue aq,
			OntologySocket ontology) {
		// Base case - if i > arg length, save
		if (i >= argArray.length) {
			aq.add(new PartialAssertion(relation, microtheory_, heuristic_,
					argArray), weight);
			return;
		}

		// If null expansion, use args value.
		if (expandedArgs[i] == null) {
			argArray[i] = args_[i];
			recurseBuildArgs(relation, i + 1, argArray, weight, expandedArgs,
					aq, ontology);
		} else {
			// Otherwise, recurse into every possibility, stopping if invalid.
			boolean copyArray = false;
			for (OntologyConcept concept : expandedArgs[i]) {
				if (ontology.isValidArg(relation.getIdentifier(), concept,
						i + 1)) {
					if (copyArray)
						argArray = Arrays.copyOf(argArray, argArray.length);
					argArray[i] = concept;
					recurseBuildArgs(relation, i + 1, argArray, weight
							* expandedArgs[i].getWeight(concept), expandedArgs,
							aq, ontology);
					copyArray = true;
				}
			}

			// Step into subassertions
			if (expandedArgs[i] instanceof HierarchicalWeightedSet) {
				Collection<WeightedSet<OntologyConcept>> subConcepts = ((HierarchicalWeightedSet<OntologyConcept>) expandedArgs[i])
						.getSubSets();
				for (WeightedSet<OntologyConcept> subSet : subConcepts) {
					AssertionQueue subAQ = new AssertionQueue(
							aq.getProvenance());
					WeightedSet<OntologyConcept>[] subExpandedArgs = expandedArgs
							.clone();
					subExpandedArgs[i] = subSet;
					argArray = Arrays.copyOf(argArray, argArray.length);
					recurseBuildArgs(relation, i, argArray, weight,
							subExpandedArgs, subAQ, ontology);
					if (!subAQ.isEmpty() && !subAQ.equals(aq))
						aq.addLower(subAQ);
				}
			}
		}
	}

	@Override
	protected Double determineStatusWeight(MiningHeuristic source) {
		return 0d;
	}

	@Override
	protected boolean needToUpdate() {
		return false;
	}

	public void addSubAssertion(PartialAssertion pa) {
		if (subAssertions_ == null)
			subAssertions_ = new ArrayList<>();
		if (!subAssertions_.contains(pa))
			subAssertions_.add(pa);
	}

	@Override
	public PartialAssertion clone() {
		return new PartialAssertion(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PartialAssertion other = (PartialAssertion) obj;
		if (subAssertions_ == null) {
			if (other.subAssertions_ != null)
				return false;
		} else if (!subAssertions_.equals(other.subAssertions_))
			return false;
		return true;
	}

	/**
	 * Expands this partial asertion into an AssertionQueue by finding mappings
	 * for all non-excluded mappable concepts.
	 * 
	 * @param excluded
	 *            The mappable concepts not to expand.
	 * @param mapper
	 *            The mapping class to perform the mappings.
	 * @param ontology
	 *            The ontology access.
	 * @param wmi
	 *            The WMI access.
	 * @return An assertion queue of Partial Assertions resulting from this
	 *         assertion.
	 */
	public AssertionQueue expand(Collection<MappableConcept> excluded,
			CycMapper mapper, OntologySocket ontology, WMISocket wmi) {
		AssertionQueue aq = expandInternal(excluded, mapper, ontology, wmi);

		// Recurse through sub-assertions
		if (subAssertions_ != null) {
			for (PartialAssertion pa : subAssertions_) {
				AssertionQueue subQueue = pa.expand(excluded, mapper, ontology,
						wmi);
				if (subQueue != null)
					aq.addLower(subQueue);
			}
		}

		return aq;
	}

	@SuppressWarnings("unchecked")
	public Collection<PartialAssertion> getSubAssertions() {
		if (subAssertions_ == null)
			return CollectionUtils.EMPTY_COLLECTION;
		return subAssertions_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((subAssertions_ == null) ? 0 : subAssertions_.hashCode());
		return result;
	}

	/**
	 * Replace a mappable concept with a definite concept.
	 * 
	 * @param replacedConcept
	 *            The replaced concept.
	 * @param replacementConcept
	 *            The replacement concept.
	 * @return The assertion resulting from the replacement.
	 */
	public DefiniteAssertion instantiate(MappableConcept replacedConcept,
			OntologyConcept replacementConcept) throws ClassCastException {
		OntologyConcept newRelation = null;
		if (relation_.equals(replacedConcept))
			newRelation = replacementConcept;
		else
			newRelation = (OntologyConcept) relation_;

		OntologyConcept[] newArgs = new OntologyConcept[args_.length];
		for (int i = 0; i < args_.length; i++) {
			newArgs[i] = (OntologyConcept) ((args_[i].equals(replacedConcept)) ? replacementConcept
					: args_[i]);
		}

		return new DefiniteAssertion(newRelation, microtheory_,
				getProvenance(), newArgs);
	}

	@Override
	public PartialAssertion replaceArg(AssertionArgument original,
			AssertionArgument replacement) {
		// Replacing the args
		AssertionArgument[] replArgs = new AssertionArgument[args_.length];
		for (int i = 0; i < args_.length; i++) {
			if (args_[i].equals(original))
				replArgs[i] = replacement;
			else
				replArgs[i] = args_[i];
		}
		// Replacing the relation
		AssertionArgument replPredicate = (relation_.equals(original)) ? replacement
				: relation_;
		PartialAssertion replaced = new PartialAssertion(replPredicate,
				microtheory_, getProvenance(), replArgs);
		for (PartialAssertion sub : getSubAssertions())
			replaced.addSubAssertion(sub.replaceArg(original, replacement));
		return replaced;
	}

	@Override
	public String toPrettyString() {
		StringBuilder builder = new StringBuilder();
		if (relation_ == null)
			builder.append("<null>");
		else
			builder.append(super.toPrettyString());

		// Sub assertions
		if (subAssertions_ != null) {
			for (PartialAssertion subAssertion : subAssertions_) {
				String subString = subAssertion.toPrettyString();
				builder.append("\n"
						+ INCREMENT_PATTERN.matcher(subString)
								.replaceAll(" $1"));
			}
		}
		return builder.toString();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (relation_ == null)
			builder.append("<null>");
		else
			builder.append(super.toString());

		// Sub assertions
		if (subAssertions_ != null) {
			for (PartialAssertion subAssertion : subAssertions_) {
				String subString = subAssertion.toString();
				builder.append("\n"
						+ INCREMENT_PATTERN.matcher(subString)
								.replaceAll(" $1"));
			}
		}
		return builder.toString();
	}

	public static Collection<PartialAssertion> flattenHierarchy(
			PartialAssertion assertion) {
		ArrayList<PartialAssertion> flattened = new ArrayList<>();
		flattened.add(assertion);
		for (PartialAssertion subPartial : assertion.getSubAssertions())
			flattened.addAll(flattenHierarchy(subPartial));
		return flattened;
	}
}
