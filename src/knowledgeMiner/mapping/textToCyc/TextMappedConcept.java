package knowledgeMiner.mapping.textToCyc;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.mapping.CycMapper;
import util.collection.WeightedSet;
import cyc.MappableConcept;
import cyc.OntologyConcept;

public class TextMappedConcept extends MappableConcept {
	private static final long serialVersionUID = 1L;
	private boolean preProcessText_;
	private boolean allowDirectSearch_;

	public TextMappedConcept(String text, boolean preProcessText,
			boolean allowDirectSearch) {
		super(text);
		// super(NLPToSyntaxModule.convertToAscii(text));
		preProcessText_ = preProcessText;
		allowDirectSearch_ = allowDirectSearch;
	}

	public TextMappedConcept(TextMappedConcept existing) {
		super(existing);
		preProcessText_ = existing.preProcessText_;
	}

	@Override
	protected WeightedSet<OntologyConcept> mapThingInternal(CycMapper mapper,
			WMISocket wmi, OntologySocket ontology) {
		return mapper.mapTextToCyc((String) mappableThing_, false, false,
				preProcessText_, true, wmi, ontology);
	}

	@Override
	public TextMappedConcept clone() {
		return new TextMappedConcept(this);
	}

	@Override
	public String toPrettyString() {
		return toString();
	}

	@Override
	public String getIdentifier() {
		return toString();
	}

	@Override
	public String toString() {
		return "mapText(\"" + mappableThing_ + "\")";
	}

	public static void main(String[] args) {
		// TODO Allow for scripting
	}
}
