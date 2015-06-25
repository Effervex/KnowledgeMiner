package knowledgeMiner.mining;

import graph.core.CommonConcepts;
import graph.inference.CommonQuery;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import knowledgeMiner.mapping.CycMapper;

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

	private static final double NO_CONSTRAINT_REWEIGHT = 0.001;

	/** Optional sub-assertions for hierarchically structured mined information. */
	private Collection<PartialAssertion> subAssertions_;

	/** A reweighted cache of the relations during expansion. */
	private transient Map<OntologyConcept, Double> reweightCache_;

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
		reweightCache_ = new HashMap<>();
		return recurseArguments(expandedRelation, expandedArgs, ontology);
	}

	/**
	 * Recurses through the arguments, calling a recurse through the relations
	 * as well.
	 *
	 * @param expandedRelation
	 *            The relations to recurse through.
	 * @param expandedArgs
	 *            The arguments to recurse through.
	 * @param ontology
	 *            The ontology access.
	 * @return The asertion queue containing the combined relations and args,
	 *         organised by argument first.
	 */
	private AssertionQueue recurseArguments(
			WeightedSet<OntologyConcept> expandedRelation,
			WeightedSet<OntologyConcept>[] expandedArgs, OntologySocket ontology) {
		// Recurse the relations, using these arguments
		AssertionQueue aq = recurseRelation(expandedRelation, expandedArgs,
				ontology);

		// Recurse into further arguments
		for (int i = 0; i < expandedArgs.length; i++) {
			// If the expanded args are hierarchical and can recurse, drop down
			if (expandedArgs[i] != null
					&& expandedArgs[i] instanceof HierarchicalWeightedSet
					&& ((HierarchicalWeightedSet) expandedArgs[i]).hasSubSets()) {
				for (WeightedSet<OntologyConcept> lowerSet : ((HierarchicalWeightedSet<OntologyConcept>) expandedArgs[i])
						.getSubSets()) {
					WeightedSet<OntologyConcept>[] lowerArgs = Arrays.copyOf(
							expandedArgs, expandedArgs.length);
					lowerArgs[i] = lowerSet;
					AssertionQueue lowerAQ = recurseArguments(expandedRelation,
							lowerArgs, ontology);
					aq.addLower(lowerAQ);
				}
			}
		}

		return aq;
	}

	/**
	 * Form the assertions with these relations, then recurse lower relations
	 * ONLY (i.e. no recursing arguments).
	 *
	 * @param expandedRelation
	 *            The relations to form assertions with and recurse through.
	 * @param expandedArgs
	 *            The arguments to use (only these, no recursing).
	 * @param ontology
	 *            The ontology access.
	 * @return The assertion queue resulting from this.
	 */
	private AssertionQueue recurseRelation(
			WeightedSet<OntologyConcept> expandedRelation,
			WeightedSet<OntologyConcept>[] expandedArgs, OntologySocket ontology) {
		// Work through relations
		AssertionQueue aq = new AssertionQueue(getProvenance());
		for (OntologyConcept relation : expandedRelation) {
			double weight = expandedRelation.getWeight(relation);
			weight *= constraintFactor(relation, expandedArgs, ontology);
			AssertionArgument[] args = new AssertionArgument[expandedArgs.length];
			buildArguments(relation, args, 0, weight, expandedArgs, aq,
					ontology);
		}

		// Add lower relations
		if (expandedRelation instanceof HierarchicalWeightedSet
				&& ((HierarchicalWeightedSet) expandedRelation).hasSubSets()) {
			Collection<WeightedSet<OntologyConcept>> lowers = ((HierarchicalWeightedSet<OntologyConcept>) expandedRelation)
					.getSubSets();
			for (WeightedSet<OntologyConcept> lower : lowers)
				aq.addLower(recurseRelation(lower, expandedArgs, ontology));
		}

		return aq;
	}

	/**
	 * Returns a coefficient for multiplying a relation's weight by with respect
	 * to the relation's argument constraints.
	 *
	 * @param relation
	 *            The relation to base the reweight off.
	 * @param expandedArgs
	 *            The arguments containing the null assertion for constraint
	 *            checking.
	 * @param ontology
	 *            The ontology access.
	 * @return The reweighting factor to apply to the weight between 0
	 *         (exclusive) and 1 (inclusive).
	 */
	private double constraintFactor(OntologyConcept relation,
			WeightedSet<OntologyConcept>[] expandedArgs, OntologySocket ontology) {
		if (reweightCache_.containsKey(relation))
			return reweightCache_.get(relation);
		if (relation.equals(CycConstants.ISA_GENLS.getConcept()))
			return 1;

		int nullIndex = 0;
		for (int i = 0; i < expandedArgs.length; i++) {
			if (expandedArgs[i] == null) {
				nullIndex = i;
				break;
			}
		}

		// Check if there are constraints
		Collection<OntologyConcept> constraints = ontology.quickQuery(
				CommonQuery.ARGNISA, relation.getIdentifier() + " '"
						+ (nullIndex + 1));
		if (constraints.size() > 1) {
			reweightCache_.put(relation, 1d);
			return 1;
		}
		if (constraints.isEmpty()
				|| constraints.iterator().next().getID() == CommonConcepts.THING
						.getID()) {
			// Check the arg genls
			constraints = ontology.quickQuery(CommonQuery.ARGNGENL,
					relation.getIdentifier() + " '" + (nullIndex + 1));
			if (constraints.size() >= 1) {
				reweightCache_.put(relation, 1d);
				return 1;
			}
			reweightCache_.put(relation, NO_CONSTRAINT_REWEIGHT);
			return NO_CONSTRAINT_REWEIGHT;
		}
		reweightCache_.put(relation, 1d);
		return 1;
	}

	/**
	 * Builds the combinatorial arguments from an array of arguments.
	 *
	 * @param args
	 *            The args to recursively build.
	 * @param i
	 *            The current index to fill.
	 * @param relationWeight
	 *            The weight of the relation.
	 * @param expandedArgs
	 *            The arguments to combine.
	 * @param aq
	 *            The AssertionQueue to add to.
	 * @param ontology
	 *            The ontology access.
	 */
	private void buildArguments(OntologyConcept relation,
			AssertionArgument[] args, int i, double weight,
			WeightedSet<OntologyConcept>[] expandedArgs, AssertionQueue aq,
			OntologySocket ontology) {
		if (i >= expandedArgs.length) {
			aq.add(new PartialAssertion(relation, microtheory_,
					getProvenance(), args), weight);
			return;
		}

		// If null expansion, use args value.
		if (expandedArgs[i] == null) {
			args[i] = args_[i];
			// TODO Reweight based on argument constraint
			buildArguments(relation, args, i + 1, weight, expandedArgs, aq,
					ontology);
		} else {
			// Iterate through every weighted element.
			for (OntologyConcept concept : expandedArgs[i]) {
				if (ontology.isValidArg(relation.getIdentifier(), concept,
						i + 1)) {
					AssertionArgument[] argsClone = Arrays.copyOf(args,
							args.length);
					argsClone[i] = concept;
					buildArguments(relation, argsClone, i + 1,
							expandedArgs[i].getWeight(concept) * weight,
							expandedArgs, aq, ontology);
				}
			}
		}
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
				// TODO Check it is in fact a relation!

				// No relation found.
				// TODO Modify this to allow relation creation (if arg known)
				if (expandedRelation.isEmpty())
					return aq;
			} else
				expandedRelation.add((OntologyConcept) relation_);

			// Expand the args
			WeightedSet<OntologyConcept>[] expandedArgs = new WeightedSet[args_.length];
			for (int i = 0; i < args_.length; i++) {
				if (!excluded.contains(args_[i])) {
					if (args_[i] instanceof MappableConcept) {
						expandedArgs[i] = ((MappableConcept) args_[i])
								.mapThing(mapper, wmi, ontology);
						// TODO Avoid self-referential excluded anchors
						// No concept found.
						if (expandedArgs[i].isEmpty())
							return aq;
					} else if (args_[i] instanceof OntologyConcept) {
						expandedArgs[i] = new WeightedSet<OntologyConcept>(1);
						expandedArgs[i].add((OntologyConcept) args_[i], 1);
					}
				}
			}

			// Combine the expanded args into valid assertions
			aq = compileAssertionQueue(expandedRelation, expandedArgs, ontology);
			double weight = getWeight();
			aq.scaleAll(weight);
		}
		return aq;
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
