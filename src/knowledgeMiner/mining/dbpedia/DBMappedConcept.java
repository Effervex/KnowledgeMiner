package knowledgeMiner.mining.dbpedia;

import graph.core.CommonConcepts;
import graph.core.PrimitiveNode;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextToCyc_DateParse;
import knowledgeMiner.mining.TextMappedConcept;
import knowledgeMiner.mining.wikipedia.WikipediaMappedConcept;

import org.apache.commons.lang3.StringUtils;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.util.NodeFactoryExtra;
import com.hp.hpl.jena.sparql.util.Utils;

import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.MappableConcept;
import cyc.OntologyConcept;
import cyc.PrimitiveConcept;
import cyc.StringConcept;

/**
 * A class for mapping DBpedia resources to ontological concepts. Either uses
 * WikiMapped to return article mappings, TextMapped to map textual properties,
 * or DBpedia mappings for other resources.
 *
 * @author Sam Sarjant
 */
public class DBMappedConcept extends MappableConcept {
	private static final long serialVersionUID = 1L;
	private boolean predicate_ = false;

	/** A cache of mappings from DBpedia URIs to concepts. */
	public static final Map<String, WeightedSet<OntologyConcept>> predMappings_ = new HashMap<>();

	public DBMappedConcept(RDFNode resource, boolean predicate) {
		super(resource);
		predicate_ = predicate;
	}

	public DBMappedConcept(DBMappedConcept existing) {
		super(existing);
	}

	@Override
	protected WeightedSet<OntologyConcept> mapThingInternal(CycMapper mapper,
			WMISocket wmi, OntologySocket ontology) {
		RDFNode rdfMappable = (RDFNode) mappableThing_;
		// Resource
		if (rdfMappable.isResource()) {
			Resource res = rdfMappable.asResource();

			// Check if it's an article
			String namespace = res.getNameSpace();
			if (predicate_) {
				return mapToPredicate(res, mapper, wmi, ontology);
			} else if (namespace.equals(DBPediaNamespace.DBPEDIA.getURI())) {
				// Look up the wikiPageID (if any)
				RDFNode artID = DBPediaAlignmentMiner.askSingularQuery(
						"?id",
						"<"
								+ res.getURI()
								+ "> "
								+ DBPediaNamespace.format(
										DBPediaNamespace.DBPEDIAOWL,
										"wikiPageID") + " ?id");
				if (artID != null) {
					int article = artID.asLiteral().getInt();
					try {
						if (article != -1) {
							WikipediaMappedConcept wmc = new WikipediaMappedConcept(
									article);
							return wmc.mapThing(mapper, wmi, ontology);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else {
				// TODO Map the other thing, possibly by following a chain.
				// System.out.println("MapC: " + mappableThing_);
			}
		} else if (rdfMappable.isLiteral()) {
			Literal lit = rdfMappable.asLiteral();
			// Hand off to Text mapping to resolve.
			String property = lit.getValue().toString();
			TextMappedConcept tmc = new TextMappedConcept(property, true, true);
			WeightedSet<OntologyConcept> results = tmc.mapThing(mapper, wmi,
					ontology);
			// Add DBpedia's literal definitions
			parsePrimitive(lit, results, mapper, wmi, ontology);
			return results;
		}
		// Otherwise, return empty
		return new WeightedSet<>(0);
	}

	/**
	 * Parses a primitive literal value and adds it to the set of results with
	 * weight = 1.
	 *
	 * @param lit
	 *            The literal to parse.
	 * @param results
	 *            The results to add to.
	 * @param mapper
	 *            The mapper access.
	 * @param wmi
	 *            The wmi access.
	 * @param ontology
	 *            The ontology access.
	 */
	private void parsePrimitive(Literal lit,
			WeightedSet<OntologyConcept> results, CycMapper mapper,
			WMISocket wmi, OntologySocket ontology) {
		Object value = lit.getValue();
		PrimitiveNode primitiveNode = PrimitiveNode.parseNode(value.toString());
		if (primitiveNode != null)
			results.set(new PrimitiveConcept(primitiveNode.getPrimitive()), 1);
		else {
			// Some non-primitive value.
			if (value instanceof String)
				results.set(new StringConcept(value.toString()), 1);
			else if (value instanceof XSDDateTime) {
				String dateString = lit.getString();
				dateString = dateString.substring(0, dateString.indexOf('+'));
				results.setAll(mapper.mapViaHeuristic(dateString,
						TextToCyc_DateParse.class, wmi, ontology));
			}
		}
	}

	/**
	 * Maps a resource to a predicate, creating a new predicate if necessary.
	 * 
	 * @param res
	 *            The resource to map to predicate.
	 * @param mapper
	 *            The mapper access.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return A weighted set of predicates that are mappings.
	 */
	private synchronized WeightedSet<OntologyConcept> mapToPredicate(
			Resource res, CycMapper mapper, WMISocket wmi,
			OntologySocket ontology) {
		WeightedSet<OntologyConcept> results;
		// First check if mappings are already known
		WeightedSet<OntologyConcept> mappings = predMappings_.get(res.getURI());
		if (mappings != null)
			return mappings;

		// Map the predicate
		String relation = res.getLocalName();
		if (relation.equals("type")) {
			results = new WeightedSet<>(1);
			results.add(CycConstants.ISA_GENLS.getConcept());
			predMappings_.put(res.getURI(), results);
			return results;
		}

		results = mapper.mapRelationToPredicate(relation, wmi, ontology);

		// Add a lower ranked created relation
		WeightedSet<OntologyConcept> newRelationSet = new WeightedSet<>(1);
		try {
			OntologyConcept newRelation = createNewRelation(res, ontology);
			if (newRelation != null) {
				newRelationSet.add(newRelation);
				((HierarchicalWeightedSet) results).addLower(newRelationSet);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		predMappings_.put(res.getURI(), results);
		return results;
	}

	/**
	 * Creates a new refinable relation to be incorporated in the mapping
	 * process.
	 *
	 * @param res
	 *            The relation URI.
	 * @param ontology
	 *            The ontology access.
	 * @return The new ontology relation, or null.
	 * @throws Exception
	 */
	private OntologyConcept createNewRelation(Resource res,
			OntologySocket ontology) throws Exception {
		String name = StringUtils.uncapitalize(res.getLocalName());
		if (ontology.inOntology(name))
			return null;
		int id = ontology
				.createAndAssert(
						name,
						CycConstants.REFINABLE_PREDICATE.getID(),
						"A refinable relation imported from DBPedia. This relation is equivalent to the DBPedia relation: ["
								+ res.getURI() + "]");
		if (id == -1)
			return null;
		// Arity always 2
		ontology.assertToOntology(
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARITY.getID(), id, "'2");
		// DBpedia links
		ontology.assertToOntology(
				CycConstants.IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID(), id,
				CycConstants.DBPEDIA_ONTOLOGY.getID(),
				new StringConcept(res.getURI()));
		return new OntologyConcept(res.getLocalName(), id);
	}

	public RDFNode getResource() {
		return (RDFNode) mappableThing_;
	}

	@Override
	public AssertionArgument clone() {
		return new DBMappedConcept(this);
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
		return "mapDB(" + mappableThing_.toString() + ")";
	}
}
