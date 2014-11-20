package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.MappingPreProcessor;

public class JoinNewLinePreProcessor extends MappingPreProcessor<String> {

	@Override
	public String processSingle(String input, WMISocket wmi,
			OntologySocket ontology) {
		return input.replaceAll(" *\n *", " ");
	}

}
