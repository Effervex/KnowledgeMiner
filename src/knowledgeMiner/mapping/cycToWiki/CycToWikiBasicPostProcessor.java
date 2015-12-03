package knowledgeMiner.mapping.cycToWiki;

import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;
import util.UtilityMethods;
import util.collection.WeightedSet;
import knowledgeMiner.mapping.MappingPostProcessor;

public class CycToWikiBasicPostProcessor extends MappingPostProcessor<Integer> {

	@Override
	public WeightedSet<Integer> process(WeightedSet<Integer> collection,
			WikipediaSocket wmi, OntologySocket ontology) {
		if (collection.isEmpty())
			return collection;
		UtilityMethods.removeNegOnes(collection);
		return collection;
	}

}
