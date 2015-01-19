/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.mining.DefiniteAssertion;
import knowledgeMiner.mining.MinedAssertion;
import knowledgeMiner.mining.wikipedia.InfoboxRelationMiner;
import knowledgeMiner.mining.wikipedia.InfoboxTypeMiner;
import cyc.OntologyConcept;

/**
 * The class to handle all of the IO stuff.
 * 
 * @author Sam Sarjant
 */
public class IOManager {

	private static IOManager instance_;

	public static final String ASSERTIONS_FILE = "assertions.txt";
	public static final String CYC_BLOCKED = "cycBlocked.txt";
	public static final String FIRST_SENTENCE_UNDETERMINEDS = "firstSentences.txt";
	public static final String MAPPINGS_FILE = "mappings.txt";
	public static final String MAPPING_CHAIN = "mappingChain.txt";
	public static final String REMOVED_FILE = "removedConstants.txt";
	public static final String CYC_OPERATIONS = "cycOperations.txt";

	/** The removed constants. */
	private BufferedWriter assertions_;

	/** The output writer for assertions blocked by Cyc. */
	private BufferedWriter blocked_;

	/** The output writer for sentences that couldn't be regexp matched. */
	private BufferedWriter firstSentenceOut_;
	/** The mappings file. */
	private BufferedWriter mappings_;

	/** The removed constants. */
	private BufferedWriter removed_;

	/** The mapping chain file. */
	private BufferedWriter mappingChain_;

	private Set<Integer> writtenSentences_;

	/** The file for Cyc operations. */
	private BufferedWriter cycOperations_;

	/**
	 * Placeholder constructor for an idle IOManager.
	 */
	private IOManager() {
	}

	/**
	 * Initialises the output files.
	 * 
	 * @param assertions
	 *            The assertions output file.
	 * @param blocked
	 *            The blocked assertions output file.
	 * @param firstSentenceOut
	 *            The undetermined first sentence file.
	 * @param mappings
	 *            The mappings output file.
	 * @param removed
	 *            The removed constants file.
	 * @param cycOperations
	 *            The file containing Cyc altering operations.
	 * @param mappingChain
	 *            TODO
	 * @throws IOException
	 *             Should something go awry...
	 */
	private IOManager(String assertions, String blocked,
			String firstSentenceOut, String infoboxTypes,
			String infoboxRelations, String mappings, String removed,
			String cycOperations, String mappingChain) throws IOException {
		File f = new File(assertions);
		f.createNewFile();
		assertions_ = new BufferedWriter(new FileWriter(f));

		f = new File(blocked);
		f.createNewFile();
		blocked_ = new BufferedWriter(new FileWriter(f));

		f = new File(firstSentenceOut);
		f.createNewFile();
		firstSentenceOut_ = new BufferedWriter(new FileWriter(f));
		writtenSentences_ = new HashSet<>(10000000);

		f = new File(mappings);
		f.createNewFile();
		mappings_ = new BufferedWriter(new FileWriter(f));

		f = new File(removed);
		f.createNewFile();
		removed_ = new BufferedWriter(new FileWriter(f));

		f = new File(cycOperations);
		f.createNewFile();
		cycOperations_ = new BufferedWriter(new FileWriter(f));

		f = new File(mappingChain);
		f.createNewFile();
		mappingChain_ = new BufferedWriter(new FileWriter(f));
	}

	/**
	 * Closes all open files.
	 * 
	 * @throws IOException
	 *             Should something go awry...
	 */
	public void close() throws IOException {
		if (assertions_ == null)
			return;
		assertions_.close();
		blocked_.close();
		firstSentenceOut_.close();
		mappings_.close();
		removed_.close();
	}

	/**
	 * Flushes all buffers.
	 * 
	 * @throws IOException
	 *             Should something go awry...
	 */
	public void flush() throws IOException {
		if (assertions_ != null)
			assertions_.flush();
		if (blocked_ != null)
			blocked_.flush();
		if (firstSentenceOut_ != null)
			firstSentenceOut_.flush();
		if (mappings_ != null)
			mappings_.flush();
		if (removed_ != null)
			removed_.flush();
		if (mappingChain_ != null)
			mappingChain_.flush();
	}

	public void writeAssertion(OntologyConcept concept,
			DefiniteAssertion assertion) throws IOException {
		if (assertions_ != null)
			assertions_.write(concept.getConceptName() + "\t"
					+ assertion.getRelation() + "\t"
					+ assertion.toPrettyString() + "\t"
					+ assertion.getProvenance() + "\t" + assertion.getStatus()
					+ "\n");
	}

	public void writeBlockedAssertion(MinedAssertion blockedAssertion)
			throws IOException {
		if (blocked_ != null)
			blocked_.write(blockedAssertion.toString() + "\n");
	}

	public void writeFirstSentence(int article, String firstSentence)
			throws IOException {
		if (writtenSentences_ == null || writtenSentences_.contains(article))
			return;
		if (firstSentenceOut_ != null)
			firstSentenceOut_.write(article + "\t" + firstSentence + "\n");
		writtenSentences_.add(article);
	}

	/**
	 * Writes a Cyc operation to file.
	 * 
	 * @param operation
	 *            The operation that altered Cyc in some way.
	 * @throws IOException
	 */
	public void writeCycOperation(String operation) throws IOException {
		if (cycOperations_ != null)
			cycOperations_.write(operation + "\n");
	}

	/**
	 * Writes a mapping out to the mappings output file.
	 * 
	 * @param cycId
	 *            The id of the Cyc term
	 * @param cycTerm
	 *            The Cyc term being mapped.
	 * @param result
	 *            The mapped article.
	 * @return True if write was successful.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public void writeMapping(ConceptModule concept, String articleTitle)
			throws Exception {
		OntologyConcept cycTerm = concept.getConcept();
		System.out.println(concept.toPrettyString() + " ("
				+ concept.getConcreteAssertions().size() + " assertions).");
		if (mappings_ != null) {
			StringBuilder buffer = new StringBuilder();
			if (!concept.isCreatedConcept())
				buffer.append(cycTerm.getIdentifier());
			else
				buffer.append("NEW");
			buffer.append("\t" + cycTerm + "\t" + articleTitle + "\t");
			buffer.append("\t" + concept.getModuleWeight());
			mappings_.write(buffer.toString() + "\n");
		}
	}

	public void writeMappingChain(String chain) throws IOException {
		if (mappingChain_ != null)
			mappingChain_.write(chain + "\n");
	}

	public void writeRemovedConstant(String constant) throws IOException {
		if (removed_ != null)
			removed_.write(constant + "\n");
	}

	/**
	 * Gets the IOManager instance or creates a new placeholder one if
	 * necessary.
	 * 
	 * @return The instance of IOManager.
	 */
	public static IOManager getInstance() {
		if (instance_ == null)
			instance_ = new IOManager();

		return instance_;
	}

	public static void newInstance() {
		if (instance_ != null)
			return;
		try {
			instance_ = new IOManager(ASSERTIONS_FILE, CYC_BLOCKED,
					FIRST_SENTENCE_UNDETERMINEDS,
					InfoboxTypeMiner.INFOBOX_TYPE_FILE.getName(),
					InfoboxRelationMiner.INFOBOX_RELATION_FILE.getName(),
					MAPPINGS_FILE, REMOVED_FILE, CYC_OPERATIONS, MAPPING_CHAIN);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void newInstance(String assertions, String blocked,
			String cyc2Wiki, String wiki2Cyc, String firstSentenceOut,
			String infoboxTypes, String infoboxRelations, String mappings,
			String removed, String cycOperations, String mappingChain) {
		if (instance_ != null)
			return;
		try {
			instance_ = new IOManager(assertions, blocked, firstSentenceOut,
					infoboxTypes, infoboxRelations, mappings, removed,
					cycOperations, mappingChain);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
