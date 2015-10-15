package knowledgeMiner.mapping.cycToWiki;

import graph.core.CommonConcepts;
import graph.inference.VariableNode;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.MappingHeuristic;
import util.UtilityMethods;
import util.collection.WeightedSet;
import cyc.OntologyConcept;

/**
 * Searches Wikipedia directly for a concept using the concept's canonical
 * string.
 *
 * @author Sam Sarjant
 */
public class CycToWiki_CanonicalMatching extends
		MappingHeuristic<OntologyConcept, Integer> {
	public static final Pattern VARIABLE_PARSE = Pattern
			.compile("\\?X/\"(.+?)\"");

	public CycToWiki_CanonicalMatching(CycMapper mapper) {
		super(mapper);
	}

	@Override
	protected WeightedSet<Integer> mapSourceInternal(OntologyConcept source,
			WMISocket wmi, OntologySocket ontology) throws Exception {
		String result = ontology.query(false,
				CommonConcepts.PRETTY_STRING_CANONICAL.getID(), source, VariableNode.DEFAULT);

		WeightedSet<Integer> results = new WeightedSet<>();
		Matcher m = VARIABLE_PARSE.matcher(result);
		while (m.find()) {
			Set<String> capitalisations = UtilityMethods
					.manipulateStringCapitalisation(m.group(1));
			List<Integer> arts = wmi.getArticleByTitle(capitalisations
					.toArray(new String[capitalisations.size()]));
			for (Integer artID : arts)
				if (artID != null && artID != -1 && !results.contains(artID))
					results.add(artID);
		}

		return results;
	}
}
