/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package cyc;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.OntologySocket;

import java.util.LinkedList;

import knowledgeMiner.KnowledgeMiner;

public enum CycConstants {
	BASE_MICROTHEORY("KnowledgeMinerMt"),
	BASEKB("BaseKB"),
	BIRTH_DATE("birthDate"),
	COLLECTION("Collection"),
	COMMENT("comment"),
	CONCEPT_IMAGE("conceptImage"),
	DATA_MICROTHEORY("KnowledgeMinerDataMt"),
	DBPEDIA_ONTOLOGY("DBPediaOntology"),
	DEATH_DATE("dateOfDeath"),
	DEFAULTED_INDIVIDUAL("DefaultedIndividual"),
	EVERY_WIKI_MICROTHEORY("KnowledgeMinerCollectorMt"),
	EVERYTHING_PSC("EverythingPSC"),
	FUNCTION("Function-Denotational"),
	GENLS("genls"),
	IMPLEMENTATION_MICROTHEORY("KnowledgeMinerImplementationMt"),
	INDIVIDUAL("Individual"),
	INFOBOX_PAIRING("PairedViaInfoboxType"),
	INFOBOX_VALUE_SYNONYM("wikipediaInfoboxValue"),
	ISA("isa"),
	ISA_GENLS("isaGenls"),
	KNOWLEDGE_MINER("KnowledgeMiner"),
	LEXICAL_MICROTHEORY("KnowledgeMinerLexicalMt"),
	MAPPING_CONFIDENCE("mappingConfidence"),
	PREDICATE("Predicate"),
	QUOTEDISA("quotedIsa"),
	QUOTEDISA_PARENT("SubLExpressionType"),
	SYNONYM_RELATION("wikipediaArticleSynonym"),
	SYNONYM_RELATION_CANONICAL("wikipediaArticleName-Canonical"),
	SYNONYMOUS_EXTERNAL_CONCEPT("synonymousExternalConcept"),
	UNCLASSIFIED_CONCEPT("KnowledgeMinerUnclassified"),
	UNIVERSAL_VOCAB_MT("UniversalVocabularyMt"),
	URLFN("URLFn"),
	WIKIPEDIA_COMMENT("wikipediaComment"),
	WIKIPEDIA_URL("wikipediaArticleURL-Expansion");

	public static OntologyConcept WIKI_VERSION;
	private OntologyConcept concept_;

	private CycConstants(String constName) {
		int id = ResourceAccess.requestOntologySocket()
				.createConcept(constName);
		concept_ = new OntologyConcept(constName, id);
	}

	public OntologyConcept getConcept() {
		return concept_;
	}

	public String getConceptName() {
		return getConcept().getConceptName();
	}

	public int getID() {
		if (concept_ == null)
			return -1;
		return concept_.getID();
	}

	@Override
	public String toString() {
		return getID() + "";
	}

	private static void lexicalPredicate(OntologyConcept predicate,
			String comment, Object genls, Object arg1Isa, OntologySocket cyc)
			throws Exception {
		assertID(cyc.createAndAssert(predicate.getConceptName(),
				CommonConcepts.PREDICATE.getID(), comment));
		assertID(cyc.assertToOntology(BASEKB.getConceptName(),
				CommonConcepts.GENLPREDS.getID(), predicate.getID(), genls));
		assertID(cyc.assertToOntology(BASEKB.getConceptName(),
				CommonConcepts.ISA.getID(), predicate.getID(),
				CommonConcepts.BINARY_PREDICATE.getID()));
		assertID(cyc.assertToOntology(BASEKB.getConceptName(),
				CommonConcepts.ARITY.getID(), predicate.getID(), "'2"));
		assertID(cyc.assertToOntology(BASEKB.getConceptName(),
				CommonConcepts.ARG1ISA.getID(), predicate.getID(), arg1Isa));
		assertID(cyc.assertToOntology(BASEKB.getConceptName(),
				CommonConcepts.ARG2ISA.getID(), predicate.getID(),
				CommonConcepts.CHARACTER_STRING.getID()));
	}

	/**
	 * Makes some base assertions before the Cyc object is used.
	 * 
	 * @param ontologySocket
	 * 
	 * @throws Exception
	 */
	private static void setUpAssertions(OntologySocket ontologySocket)
			throws Exception {
		// Creating the default collection
		assertID(ontologySocket
				.createAndAssert(DEFAULTED_INDIVIDUAL.getConceptName(),
						CommonConcepts.COLLECTION.getID(),
						"The collection containing constants that were defaulted to individuals."));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ISA.getID(), DEFAULTED_INDIVIDUAL.getID(),
				QUOTEDISA_PARENT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.GENLS.getID(), DEFAULTED_INDIVIDUAL.getID(),
				"CycLTerm"));

		// Creating the infobox pairing collection
		assertID(ontologySocket
				.createAndAssert(
						INFOBOX_PAIRING.getConceptName(),
						CommonConcepts.COLLECTION.getID(),
						"The collection containing constants that were created by matching infobox types to known siblings."));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ISA.getID(), INFOBOX_PAIRING.getID(),
				QUOTEDISA_PARENT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.GENLS.getID(), INFOBOX_PAIRING.getID(),
				"CycLTerm"));

		// Setting up the Wikipedia version constant
		WIKI_VERSION = new OntologyConcept(ontologySocket.createAndAssert(
				KnowledgeMiner.wikiVersion_, CommonConcepts.INDIVIDUAL.getID(),
				"A version of Wikipedia being used during Knowledge Mining."));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ISA.getID(), KnowledgeMiner.wikiVersion_,
				"IndexedInformationSource"));

		// Wikipedia Comment
		assertID(ontologySocket
				.createAndAssert(
						WIKIPEDIA_COMMENT.getConceptName(),
						CommonConcepts.BINARY_PREDICATE.getID(),
						"A [[DocumentationPredicate]] that is used to represent the comment of the mapped Wikipedia article for this concept. This mapping may be marked up (contain links to other concepts) or just be plain text. In any case, it's superpredicate [[comment]] should take precedence over this predicate."));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ISA.getID(), WIKIPEDIA_COMMENT.getID(),
				"DocumentationPredicate"));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.GENLPREDS.getID(), WIKIPEDIA_COMMENT.getID(),
				COMMENT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARITY.getID(), WIKIPEDIA_COMMENT.getID(), "'2"));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARG1ISA.getID(), WIKIPEDIA_COMMENT.getID(),
				CommonConcepts.THING.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARG2ISA.getID(), WIKIPEDIA_COMMENT.getID(),
				CommonConcepts.CHARACTER_STRING.getID()));

		// Setting up the various synonyms
		lexicalPredicate(
				SYNONYM_RELATION.getConcept(),
				"Denotes a String synonym found by KnowledgeMiner for the concept.",
				CommonConcepts.TERM_STRING.getID(),
				CommonConcepts.THING.getID(), ontologySocket);
		lexicalPredicate(
				SYNONYM_RELATION_CANONICAL.getConcept(),
				"Denotes the canonical String given to a concept found by KnowledgeMiner.",
				CommonConcepts.PRETTY_STRING_CANONICAL.getID(),
				CommonConcepts.THING.getID(), ontologySocket);
		lexicalPredicate(
				INFOBOX_VALUE_SYNONYM.getConcept(),
				"Denotes a Wikipedia specific infobox value name that is synonymous with this predicate.",
				CommonConcepts.TERM_STRING.getID(),
				CommonConcepts.PREDICATE.getID(), ontologySocket);
		assertID(ontologySocket
				.createAndAssert(
						MAPPING_CONFIDENCE.getConceptName(),
						CommonConcepts.BINARY_PREDICATE.getID(),
						"A numerical value between 0 and 1 representing the confidence with which a mapping between THING and the synonymous Wikipedia article is true."));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ISA.getID(), MAPPING_CONFIDENCE.getID(),
				"NumericIntervalSlot"));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ISA.getID(), MAPPING_CONFIDENCE.getID(),
				"StrictlyFunctionalSlot"));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARG1ISA.getID(), MAPPING_CONFIDENCE.getID(),
				CommonConcepts.THING.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARG2ISA.getID(), MAPPING_CONFIDENCE.getID(),
				"Real0-1"));

		// Image URL predicate
		assertID(ontologySocket.createAndAssert(CONCEPT_IMAGE.getConceptName(),
				CommonConcepts.BINARY_PREDICATE.getID(),
				"Defines an image link using a [[UniformResourceLocator]] "
						+ "for a given concept."));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARG1ISA.getID(), CONCEPT_IMAGE.getID(),
				CommonConcepts.THING.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(),
				CommonConcepts.ARG2ISA.getID(), CONCEPT_IMAGE.getID(),
				"UniformResourceLocator"));

		// Strictly Functional Slots
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(), ISA.getID(),
				MAPPING_CONFIDENCE.getID(),
				CommonConcepts.STRICTLY_FUNCTIONAL_SLOT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(), ISA.getID(),
				SYNONYM_RELATION_CANONICAL.getID(),
				CommonConcepts.STRICTLY_FUNCTIONAL_SLOT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(), ISA.getID(),
				WIKIPEDIA_URL.getID(),
				CommonConcepts.STRICTLY_FUNCTIONAL_SLOT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(), ISA.getID(),
				CONCEPT_IMAGE.getID(),
				CommonConcepts.STRICTLY_FUNCTIONAL_SLOT.getID()));
		assertID(ontologySocket.assertToOntology(
				IMPLEMENTATION_MICROTHEORY.getConceptName(), ISA.getID(),
				WIKIPEDIA_COMMENT.getID(),
				CommonConcepts.STRICTLY_FUNCTIONAL_SLOT.getID()));
	}

	private static void assertID(int returnedID) throws Exception {
		if (returnedID < 0)
			throw new Exception("Assertion was not successful!");
	}

	/**
	 * Sets up the microtheories used within the algorithm.
	 * 
	 * @param ontologySocket2
	 * 
	 * @throws Exception
	 *             If something goes awry...
	 */
	private static void setUpMicrotheories(OntologySocket ontologySocket2)
			throws Exception {
		LinkedList<String> genlsMt = new LinkedList<String>();
		// Base microtheory
		genlsMt.add("CurrentWorldDataCollectorMt");
		ontologySocket2
				.createMicrotheory(
						BASE_MICROTHEORY.getConceptName(),
						"The base microtheory for the Wikipedia-Cyc project under which all other Wiki-Cyc microtheories are children.",
						"ProjectMicrotheory", genlsMt);

		genlsMt = new LinkedList<String>();
		genlsMt.add(BASE_MICROTHEORY.getConceptName());
		// Implementation microtheory
		ontologySocket2
				.createMicrotheory(
						IMPLEMENTATION_MICROTHEORY.getConcept()
								.getConceptName(),
						"The microtheory that records all the implementation assertions made during the Wiki-Cyc algorithm.",
						"ProjectMicrotheory", genlsMt);
		// Data microtheory
		genlsMt = new LinkedList<String>();
		genlsMt.add(BASE_MICROTHEORY.getConceptName());
		ontologySocket2
				.createMicrotheory(
						DATA_MICROTHEORY.getConceptName(),
						"The microtheory that records all the data assertions made during the Wiki-Cyc algorithm.",
						"ProjectMicrotheory", genlsMt);
		// Lexical microtheory
		genlsMt = new LinkedList<String>();
		genlsMt.add(BASE_MICROTHEORY.getConceptName());
		genlsMt.add("EnglishMt");
		ontologySocket2
				.createMicrotheory(
						LEXICAL_MICROTHEORY.getConceptName(),
						"The microtheory that records all the lexical assertions made during the Wiki-Cyc algorithm.",
						"ProjectMicrotheory", genlsMt);

		genlsMt = new LinkedList<String>();
		genlsMt.add(IMPLEMENTATION_MICROTHEORY.getConceptName());
		genlsMt.add(DATA_MICROTHEORY.getConceptName());
		genlsMt.add(LEXICAL_MICROTHEORY.getConceptName());
		// All Mts microtheory
		ontologySocket2
				.createMicrotheory(
						EVERY_WIKI_MICROTHEORY.getConceptName(),
						"The microtheory that records all assertions made during the Wiki-Cyc algorithm.",
						"ProjectMicrotheory", genlsMt);
	}

	/**
	 * Initialises the microtheories and assertions that will be used when
	 * asserting.
	 * 
	 * @throws Exception
	 *             Should something go awry...
	 */
	public static void initialiseAssertions(OntologySocket ontologySocket)
			throws Exception {
		KNOWLEDGE_MINER.concept_.setID(ontologySocket.createAndAssert(
				KNOWLEDGE_MINER.getConceptName(), "Cyclist",
				"The KnowledgeMiner cyclist represents "
						+ "the algorithm that automatically extracts "
						+ "and asserts information from various resources."));
		setUpMicrotheories(ontologySocket);
		setUpAssertions(ontologySocket);
	}
}
