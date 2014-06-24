/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import graph.module.NLPToSyntaxModule;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.AssertionQueue;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.WeightedInformation;
import knowledgeMiner.mining.WeightedStanding;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import util.collection.HierarchicalWeightedSet;
import util.collection.MultiMap;
import util.collection.WeightedSet;
import util.wikipedia.InfoboxData;
import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * A mining heuristic that extracts information from the relations of a
 * Wikipedia article's infobox (if one exists).
 * 
 * @author Sam Sarjant
 */
public class InfoboxRelationMiner extends InfoboxMiner {
	/** The chance that an article will be added to the examples. */
	private static final double EXAMPLE_CHANCE = 0.01;

	private static final String WIKIPEDIA_IMAGE_URL = "http://en.wikipedia.org/wiki/File:";

	/** The infobox relation Cyc mappings file. */
	public static final File INFOBOX_RELATION_FILE = new File(
			"infoboxRelationMaps.txt");

	/** The maximum number of examples per relation. */
	public static final int MAX_EXAMPLES = 10;

	private static final Pattern NUMBER_SUFFIX = Pattern.compile("(.+?)\\d+");

	private static final Pattern IMAGE_URL = Pattern.compile("(?:.+?:)?(\\S+\\.\\w+)");

	/** Example articles per relation. */
	private MultiMap<String, Integer> exampleArticles_ = MultiMap
			.createListMultiMap();

	/** The infobox mappings to Cyc predicates. */
	private Map<String, String> infoboxRelationMappings_ = new HashMap<>();

	private static boolean creatingRelations_ = false;

	/**
	 * Constructor for a new InfoboxRelationMiner.
	 * 
	 * @param mapper
	 *            The Mapping class.
	 */
	public InfoboxRelationMiner(CycMapper mapper, CycMiner miner) {
		super(mapper, miner, "infoboxRelationMining", INFOBOX_RELATION_FILE);
	}

	/**
	 * Simple recursive method for adding assertion queues. Includes safeguards
	 * for empty assertion queues with sub-queues.
	 * 
	 * @param info
	 *            The info to add to.
	 * @param assertionQueue
	 *            The queue to potentially add.
	 * @return If an assertion was added.
	 */
	private boolean addAssertionQueue(MinedInformation info,
			AssertionQueue assertionQueue) {
		boolean result = false;
		if (assertionQueue.isEmpty()) {
			// Check for sub queues
			for (AssertionQueue subQueue : assertionQueue
					.getSubAssertionQueues()) {
				result |= addAssertionQueue(info, subQueue);
			}
		} else {
			info.addAssertion(assertionQueue);
			return true;
		}
		return result;
	}

	/**
	 * A method for custom handling some infobox relations/values.
	 * 
	 * @param relation
	 *            The relation being handled.
	 * @param value
	 *            The value being handled.
	 * @param info
	 *            The info to add any assertions to.
	 * @param infoboxType
	 *            The type of infobox.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return True if the relation/value are special cases and need no further
	 *         parsing.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private boolean mapSpecial(String relation, String value,
			MinedInformation info, String infoboxType, WMISocket wmi,
			OntologySocket ontology) throws Exception {
		relation = relation.toLowerCase();
		// Linking image URLs
		if (relation.equals("image")) {
			value = value.replaceAll("\\s+", "_");
			Matcher m = IMAGE_URL.matcher(value);
			if (m.find())
				value = m.group(1);
			int id = ontology.createConcept("(" + CycConstants.URLFN.getID()
					+ " \"" + WIKIPEDIA_IMAGE_URL + value
					+ "\")");
			info.addConcreteAssertion(new MinedAssertion(
					CycConstants.CONCEPT_IMAGE.getConcept(),
					OntologyConcept.PLACEHOLDER, new OntologyConcept(id),
					CycConstants.DATA_MICROTHEORY.getConceptName(),
					new HeuristicProvenance(this, "image")));
			return true;
		}
		// Names (This was too general and caught many false values).
//		if (relation.matches(".*name\\d*$")) {
//		 TODO Strip value of syntax.
//			info.addConcreteAssertion(new MinedAssertion(
//					CycConstants.SYNONYM_RELATION.getConcept(),
//					CycConcept.PLACEHOLDER, new StringConcept(value),
//					CycConstants.DATA_MICROTHEORY.getConceptName(),
//					new HeuristicProvenance(this, "image")));
//			return true;
//		}
		// Website
		if (relation.equals("website")) {
			parseRelation("homepage", value, info, infoboxType, wmi, ontology);
			return true;
		}

		// Ignorable relations
		if (relation.equals("alt") || relation.matches(".*caption\\d*$"))
			return true;
		return false;
	}

	/**
	 * Resolves a set of Cyc predicates and potential values into a number of
	 * assertions to the Cyc term.
	 * 
	 * @param cycPredicates
	 *            The potential predicates being represented.
	 * @param relationValues
	 *            The potential values of the predicates.
	 * @param info
	 *            The information to add to.
	 * @param relation
	 *            The infobox relation.
	 * @param infoboxType
	 *            The infobox type for this relation.
	 * @param ontology
	 *            The ontology access.
	 * @return If a relation was created.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private boolean resolveRelation(WeightedSet<OntologyConcept> cycPredicates,
			HierarchicalWeightedSet<OntologyConcept> relationValues,
			MinedInformation info, String relation, String infoboxType,
			OntologySocket ontology) throws Exception {
		LoggerFactory.getLogger(CycMiner.class).trace("resolveRelation: {}",
				info.getArticle());
		AssertionQueue assertionQueue = null;
		try {
			assertionQueue = MinedAssertion.createAllAssertions(cycPredicates,
					relationValues, new HeuristicProvenance(this, relation),
					ontology);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(cycPredicates + "\n" + relationValues);
			System.exit(1);
		}
		assertionQueue.cleanEmptyParents();
		return addAssertionQueue(info, assertionQueue);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
		LoggerFactory.getLogger(CycMiner.class).trace(
				"infoboxRelationMiner: {}", info.getArticle());
		int article = info.getArticle();
		// Run through each relation, extracting information where possible.
		List<InfoboxData> infoboxTypes = wmi.getInfoboxData(article);
		if (infoboxTypes.isEmpty())
			return;

		for (InfoboxData infobox : infoboxTypes) {
			Map<String, String> infoboxRelations = infobox
					.getInfoboxRelations();

			int[] standing = new int[TermStanding.values().length];
			for (String relation : infoboxRelations.keySet()) {
				// Determine the standing.
				TermStanding relStanding = voteStanding(relation);
				if (relStanding != TermStanding.UNKNOWN) {
					standing[relStanding.ordinal()]++;
					standing[TermStanding.UNKNOWN.ordinal()]++;
				}

				// Extract info from the relation
				parseRelation(relation, infoboxRelations.get(relation), info,
						infobox.getInfoboxType(), wmi, ontology);
			}

			// Determine standing by majority vote
			float total = standing[TermStanding.UNKNOWN.ordinal()];
			if (standing[TermStanding.INDIVIDUAL.ordinal()] / total > WeightedStanding.VOTING_CONFIDENCE) {
				info.setStanding(new WeightedStanding(TermStanding.INDIVIDUAL,
						basicProvenance_));
			} else if (standing[TermStanding.COLLECTION.ordinal()] / total > WeightedStanding.VOTING_CONFIDENCE) {
				info.setStanding(new WeightedStanding(TermStanding.COLLECTION,
						basicProvenance_));
			}
		}
	}

	@Override
	protected void readAdditionalInput(String[] split) {
		if (infoboxRelationMappings_ == null)
			infoboxRelationMappings_ = new HashMap<String, String>();
		// There should be one more element: the Cyc relation it maps to.
		if (!split[2].isEmpty())
			infoboxRelationMappings_.put(split[0], split[2]);
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		super.setInformationTypes(informationProduced);
		informationProduced[InformationType.RELATIONS.ordinal()] = true;
		informationProduced[InformationType.STANDING.ordinal()] = true;
		informationProduced[InformationType.PARENTAGE.ordinal()] = true;
	}

	@Override
	protected String[] writeAdditionalOutput(String infoboxTerm) {
		String[] output = new String[2];
		// TODO Add infotype collection
		if (infoboxRelationMappings_.containsKey(infoboxTerm))
			output[0] = infoboxRelationMappings_.get(infoboxTerm);
		else
			output[0] = "NIL";
		if (exampleArticles_.containsKey(infoboxTerm))
			output[1] = exampleArticles_.get(infoboxTerm) + "";
		else
			output[1] = "[]";
		return output;
	}

	@Override
	public String asTrainingInstance(Object instance) {
		if (!(instance instanceof String[]))
			throw new IllegalArgumentException(
					"Wrong parameters given as training instance.");

		String[] args = (String[]) instance;
		StringBuilder instanceStr = new StringBuilder();
		boolean first = true;
		for (String arg : args) {
			if (!first)
				instanceStr.append(", ");
			instanceStr.append("\"" + arg + "\"");
			first = false;
		}
		return instanceStr.toString();
	}

	/**
	 * Parses an infobox relation by determining the relation that it maps to in
	 * Cyc, and asserting the information.
	 * 
	 * @param relation
	 *            The infobox relation to parse and assert.
	 * @param value
	 *            The unparsed value of the relation.
	 * @param info
	 *            The mined information to add to.
	 * @param infoboxType
	 *            The infobox type for this relation.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The Cyc access.
	 * @return True if a relation was parsed.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public boolean parseRelation(String relation, String value,
			MinedInformation info, String infoboxType, WMISocket wmi,
			OntologySocket ontology) throws Exception {
		if (mapSpecial(relation, value, info, infoboxType, wmi, ontology))
			return true;

		// Remove number suffix
		String originalRelation = relation;
		Matcher m = NUMBER_SUFFIX.matcher(relation);
		if (m.matches())
			relation = m.group(1);

		LoggerFactory.getLogger(CycMiner.class).trace("parseRelation: {}",
				info.getArticle());
		// First, disambiguate the value
		HierarchicalWeightedSet<OntologyConcept> relationValues = mapper_
				.mapTextToCyc(value, false, false, true, wmi, ontology);
		// If no values, then no relation.
		if (relationValues.isEmpty()) {
			return false;
		}

		// Relation mappings
		// TODO Just map text to cyc, but remove individuals
		LoggerFactory.getLogger(CycMiner.class).trace(
				"mapRelationToPredicate: {}", info.getArticle());
		HierarchicalWeightedSet<OntologyConcept> cycPredicate = mapper_
				.mapRelationToPredicate(relation, wmi, ontology);
		if (cycPredicate.isEmpty()) {
			if (relationValues.isEmpty())
				return false;
			else if (creatingRelations_) {
				// TODO Note the unmapped predicates, creating new ones if
				// necessary
				String relationName = NLPToSyntaxModule.textToConcept(relation);
				relationName = StringUtils.uncapitalize(relationName);
				OntologyConcept relationConcept = miner_
						.getUnresolved()
						.createNewRelation(
								relationName,
								"From Wikipedia infoboxtype '"
										+ infoboxType
										+ "'. <i>Requires user-provided description.</i>",
								ontology);
				cycPredicate.add(relationConcept);
				// TODO Everytime a new predicate is used, keep track of the isa
				// and genls to define the parents. Common values will be
				// heavily used.
			}
		}

		// Resolve the mappings and values to produce a relation
		cycPredicate = (HierarchicalWeightedSet<OntologyConcept>) cycPredicate
				.cleanEmptyParents();
		relationValues = (HierarchicalWeightedSet<OntologyConcept>) relationValues
				.cleanEmptyParents();
		return resolveRelation(cycPredicate, relationValues, info, relation,
				infoboxType, ontology);
	}

	@Override
	public void updateGlobal(MinedInformation info, WMISocket wmi) {
		super.updateGlobal(info, wmi);

		// For every asserted made, record it against the standing
		TermStanding actualStanding = info.getStanding();
		// Run through every infobox relation
		try {
			List<InfoboxData> infoTypes = wmi.getInfoboxData(info.getArticle());
			for (InfoboxData infobox : infoTypes) {
				Map<String, String> relations = infobox.getInfoboxRelations();
				for (String relation : relations.keySet()) {
					if (actualStanding != TermStanding.UNKNOWN)
						recordStanding(relation, actualStanding);
					int article = info.getArticle();
					if (article != -1
							&& (!exampleArticles_.containsKey(relation) || (Math
									.random() < EXAMPLE_CHANCE && exampleArticles_
									.get(relation).size() < MAX_EXAMPLES)))
						exampleArticles_.put(relation, info.getArticle());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void updateViaAssertion(WeightedInformation assertion,
			String details, double weight, InformationType infoType,
			WMISocket wmi) {
		super.updateViaAssertion(assertion, details, weight, infoType, wmi);

		// Use the assertion's provenance to record the relation.
		if (assertion instanceof MinedAssertion) {
			int index = details.indexOf('|');
			String relation = details.substring(index + 1);
			infoboxRelationMappings_.put(relation, ((MinedAssertion) assertion)
					.getRelation().toString());
		}
	}
}
