/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;
import util.UniqueID;
import util.collection.CacheMap;
import util.collection.WeightedSet;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class MappingHeuristic<Source, Target> extends
		WeightedHeuristic {
	/** The local cache for this mapping heuristic. */
	private CacheMap<CachedMapping<Source, Target>, CachedMapping<Source, Target>> localCache_;

	/**
	 * Constructor for a new MappingHeuristic
	 * 
	 * @param mapper
	 */
	public MappingHeuristic(CycMapper mapper) {
		super(mapper);
		localCache_ = new CacheMap<>(false);
	}

	/**
	 * The actual mapping method for determining the mapping.
	 * 
	 * @param source
	 *            The source to map.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Ontology access.
	 * @return A {@link WeightedSet} of Targets.
	 * @throws IOException
	 *             Should something go awry...
	 */
	protected abstract WeightedSet<Target> mapSourceInternal(Source source,
			WMISocket wmi, OntologySocket ontology) throws Exception;

	/**
	 * Map a Source to one or more Targets. This method is just the accessor
	 * that records the results.
	 * 
	 * @param s
	 *            The source term to map.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            The Ontology access.
	 * @return A {@link WeightedSet} of Targets with weights normalised to sum
	 *         to one.
	 */
	@SuppressWarnings("unchecked")
	public final WeightedSet<Target> mapSourceToTarget(Source s, WMISocket wmi,
			OntologySocket ontology) {
		// Find the ID of the source.
		int id = -1;
		if (s instanceof Integer)
			id = (Integer) s;
		else if (s instanceof UniqueID)
			id = ((UniqueID) s).getID();

		// Load preprocessed or normal if no preprocessed
		WeightedSet<Target> mappedTarget = (WeightedSet<Target>) KnowledgeMiner
				.getInstance().getHeuristicResult(id, getHeuristicName());
		if (mappedTarget != null)
			return mappedTarget;

		try {
			mappedTarget = mapSourceInternal(s, wmi, ontology);
			if (!mappedTarget.isEmpty()
					&& mappedTarget
							.getWeight(mappedTarget.getOrdered().first()) > 1)
				mappedTarget
						.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
			KnowledgeMinerPreprocessor.getInstance().recordData(
					getHeuristicName(), id, mappedTarget);
			return mappedTarget;
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Source: " + s);
		}
		return null;
	}

	public void removeCached(Source s) {
		CachedMapping<Source, Target> c = new CachedMapping<>(s);
		localCache_.remove(c);
	}
}
