/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import knowledgeMiner.KnowledgeMiner;
import util.collection.CacheMap;
import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;

import com.google.common.base.Predicate;

/**
 * A suite of tools for mapping Source to Target.
 * 
 * @author Sam Sarjant
 */
public class MappingSuite<Source, Target> {
	/** The mapping heuristics used. */
	private ArrayList<MappingHeuristic<Source, Target>> mappingHeuristics_;

	/** The post processors to apply to mappings. */
	private Collection<MappingPostProcessor<Target>> postProcessors_;

	/** The pre processors to apply to the source. */
	private List<MappingPreProcessor<Source>> preProcessors_;

	/** The pre mapped info source to apply to the mappings process. */
	private CacheMap<Source, HierarchicalWeightedSet<Target>> cachedMappings_;

	/** The non-recursive preprocessors that always modify the source. */
	private List<MappingPreProcessor<Source>> nonRecursivePreprocessors_;

	private Predicate<Source> preProcessFilter_;

	/**
	 * Constructor for a new MappingSuite
	 */
	public MappingSuite() {
		cachedMappings_ = new CacheMap<>(KnowledgeMiner.CACHE_SIZES, true);
		mappingHeuristics_ = new ArrayList<>();
		postProcessors_ = new ArrayList<>();
		preProcessors_ = new ArrayList<>();
		nonRecursivePreprocessors_ = new ArrayList<>();
	}

	/**
	 * Adds a mapping heuristic to the suite of mapping tools.
	 * 
	 * @param heuristic
	 *            The heuristic to add.
	 * @param mapper
	 *            The mapper root.
	 */
	public void addHeuristic(MappingHeuristic<Source, Target> heuristic,
			CycMapper mapper) {
		if (!mappingHeuristics_.contains(heuristic)) {
			mappingHeuristics_.add(heuristic);
		}
	}

	/**
	 * Adds a post processor to this {@link MappingSuite}.
	 * 
	 * @param postProcessor
	 *            The post processor to add
	 */
	public void addPostProcessor(MappingPostProcessor<Target> postProcessor) {
		postProcessors_.add(postProcessor);
	}

	/**
	 * Adds a pre processor to this {@link MappingSuite}.
	 * 
	 * @param preProcessor
	 *            The pre processor to add
	 */
	public void addPreProcessor(MappingPreProcessor<Source> preProcessor) {
		if (preProcessor.requiresRecurse())
			preProcessors_.add(preProcessor);
		else
			nonRecursivePreprocessors_.add(preProcessor);
	}

	public void setPreProcessFilter(Predicate<Source> filter) {
		preProcessFilter_ = filter;
	}

	/**
	 * Attempts to find a mapping between the Source and the Target using
	 * multiple heuristics, returning a {@link WeightedSet} of results.
	 * 
	 * @param source
	 *            The source being mapped.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Ontology access
	 * @param disabledHeuristics
	 *            The heuristics that are not allowed to be used.
	 * @return A {@link WeightedSet} of Target mappings.
	 * @throws Exception
	 *             If something goes awry.
	 */
	public WeightedSet<Target> mapSourceToTarget(
			Source source,
			WMISocket wmi,
			OntologySocket ontology,
			Collection<Class<? extends MappingHeuristic<Source, Target>>> disabledHeuristics) {
		// If the term is already mapped, return the article.
		// TODO Try disabling this for memory and to see if it makes much of a
		// performance difference.
		WeightedSet<Target> knownMapping = getAddCachedMapping(source, null);
		if (knownMapping != null)
			return knownMapping;

		HierarchicalWeightedSet<Target> mappings = new HierarchicalWeightedSet<>();
		mappings.setAll(mapSourceToTargetInternal(source, wmi, ontology,
				disabledHeuristics));
		// TODO Refine this to only remap the heuristics needed.
		if (disabledHeuristics == null)
			getAddCachedMapping(source, mappings);
		return mappings;
	}

	public HierarchicalWeightedSet<Target> mapSourcesToTargets(
			HierarchicalWeightedSet<Source> sources,
			WMISocket wmi,
			OntologySocket ontology,
			Collection<Class<? extends MappingHeuristic<Source, Target>>> disabledHeuristics) {
		HierarchicalWeightedSet<Target> mapping = new HierarchicalWeightedSet<>();
		for (Source src : sources)
			mapping.addAll(
					mapSourceToTarget(src, wmi, ontology, disabledHeuristics),
					sources.getWeight(src));

		// Add lower
		for (WeightedSet<Source> lower : sources.getSubSets()) {
			mapping.addLower(mapSourcesToTargets(
					(HierarchicalWeightedSet<Source>) lower, wmi, ontology,
					disabledHeuristics));
		}
		mapping.cleanEmptyParents();
		return mapping;
	}

	public HierarchicalWeightedSet<Source> preProcessSource(Source source,
			WMISocket wmi, OntologySocket ontology) {
		HierarchicalWeightedSet<Source> result = new HierarchicalWeightedSet<>();
		if (preProcessFilter_ != null && !preProcessFilter_.apply(source))
			return result;
		result.add(source);
		for (MappingPreProcessor<Source> preprocessor : preProcessors_) {
			Collection<Source> processed = preprocessor.process(source, wmi,
					ontology);
			// If the processed output does differ from the input, recurse in
			if (!processed.isEmpty()
					&& (processed.size() > 1 || !processed.iterator().next()
							.equals(source))) {
				// Add each processed source as a sub-value.
				for (Source processedSource : processed) {
					HierarchicalWeightedSet<Source> lower = preProcessSource(
							processedSource, wmi, ontology);
					if (!lower.isEmpty())
						result.addLower(lower);
				}
				break;
			}
		}
		return result;
	}

	private synchronized HierarchicalWeightedSet<Target> getAddCachedMapping(
			Source source, HierarchicalWeightedSet<Target> mappings) {
		if (mappings == null) {
			// Getting mapping
			if (cachedMappings_.containsKey(source))
				return new HierarchicalWeightedSet<Target>(
						cachedMappings_.get(source));
		} else {
			// Putting mapping
			cachedMappings_.put(source, mappings);
		}
		return null;
	}

	/**
	 * Attempts to find a mapping between the Source and the Target using
	 * multiple heuristics, returning a {@link WeightedSet} of results.
	 * 
	 * @param source
	 *            The source being mapped.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Ontology access
	 * @param disabledHeuristics
	 *            The heuristics that are not allowed to be used.
	 * @return A {@link WeightedSet} of Target mappings.
	 * @throws Exception
	 *             If something goes awry.
	 */
	private HierarchicalWeightedSet<Target> mapSourceToTargetInternal(
			Source source,
			WMISocket wmi,
			OntologySocket ontology,
			Collection<Class<? extends MappingHeuristic<Source, Target>>> disabledHeuristics) {
		HierarchicalWeightedSet<Target> mappings = new HierarchicalWeightedSet<>();
		// Loop throutgh every heuristic
		for (MappingHeuristic<Source, Target> heuristic : mappingHeuristics_) {
			// Ignore disable heuristics
			if (disabledHeuristics == null
					|| !disabledHeuristics.contains(heuristic.getClass())) {
				WeightedSet<Target> result = heuristic.mapSourceToTarget(
						source, wmi, ontology);
				if (!result.isEmpty())
					mappings.addAll(result, heuristic.getWeight());
			}
		}

		// Apply any post processors
		mappings = postProcess(mappings, wmi, ontology);

		mappings.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
		return mappings;
	}

	public HierarchicalWeightedSet<Target> postProcess(
			HierarchicalWeightedSet<Target> mappings, WMISocket wmi,
			OntologySocket ontology) {
		for (MappingPostProcessor<Target> pp : postProcessors_) {
			mappings = new HierarchicalWeightedSet<Target>(pp.process(mappings,
					wmi, ontology));
		}
		return mappings;
	}

	public void clearMappings() {
		cachedMappings_.clear();
	}

	public ArrayList<MappingHeuristic<Source, Target>> getHeuristics() {
		return mappingHeuristics_;
	}
}
