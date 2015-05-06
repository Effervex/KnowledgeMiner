package knowledgeMiner.mapping.cycToWiki;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.MappingPostProcessor;
import util.collection.WeightedSet;

public class CycToWikiRemoveDisambiguationPostProcessor extends
		MappingPostProcessor<Integer> {

	@Override
	public WeightedSet<Integer> process(WeightedSet<Integer> collection,
			WMISocket wmi, OntologySocket ontology) {
		if (collection.isEmpty())
			return collection;

		// Remove disambiguation pages
		WeightedSet<Integer> newSet = new WeightedSet<>();
		try {
			Integer[] pages = collection
					.toArray(new Integer[collection.size()]);
			int index = 0;
			// Get the page type for every result
			for (String pageType : wmi.getPageType(pages)) {
				if (pageType == null)
					continue;
				int artID = pages[index];
				double weight = collection.getWeight(artID);
				// If not a disambiguation article
				if (!pageType.equals(WMISocket.TYPE_DISAMBIGUATION)) {
					// Keep the article
					newSet.set(artID, weight);
				}
				index++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newSet;
	}

}
