package knowledgeMiner.mapping.wikiToCyc;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.IOException;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import util.collection.WeightedSet;
import cyc.AssertionArgument;
import cyc.MappableConcept;
import cyc.OntologyConcept;

public class WikipediaMappedConcept extends MappableConcept {
	private static final long serialVersionUID = 1L;

	public WikipediaMappedConcept(int article) {
		super(article);
	}

	public WikipediaMappedConcept(WikipediaMappedConcept existing) {
		super(existing);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapThingInternal(CycMapper mapper,
			WMISocket wmi, OntologySocket ontology) {
		// Get the mapped article (if one exists).
		WeightedSet<OntologyConcept> mapping = new WeightedSet<>(1);
		OntologyConcept concept = KnowledgeMiner.getConceptMapping(
				(int) mappableThing_, ontology);
		if (concept != null)
			mapping.add(concept);
		return mapping;
	}
	
	public int getArticle() {
		return (int) mappableThing_;
	}

	@Override
	public String toPrettyString() {
		try {
			return "mapArt('"
					+ ResourceAccess.requestWMISocket().getPageTitle(
							(int) mappableThing_, true) + "')";
		} catch (IOException e) {
			e.printStackTrace();
		}
		return toString();
	}

	@Override
	public String getIdentifier() {
		return toString();
	}

	@Override
	public String toString() {
		return "mapArt('" + mappableThing_ + "')";
	}

	@Override
	public AssertionArgument clone() {
		return new WikipediaMappedConcept(this);
	}
}
