package test;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import util.collection.WeightedSet;
import cyc.OntologyConcept;

public class TextMappedConceptTest {

	private static OntologySocket ontology_;
	private static WMISocket wmi_;
	private static CycMapper mapper_;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		KnowledgeMiner km = KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
		mapper_ = km.getMapper();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		TextMappedConcept tmt = new TextMappedConcept("small", false, false);
		WeightedSet<OntologyConcept> result = tmt.mapThing(mapper_, wmi_,
				ontology_);
		System.out.println(result);
	}

}
