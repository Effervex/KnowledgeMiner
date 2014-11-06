/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.HeuristicProvenance;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.WeightedStanding;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class InfoboxMiner extends WikipediaArticleMiningHeuristic {
	/** The delimiter used for the mappings file. */
	public static final String MAPPING_DELIMITER = "\t";

	/** The file to save/load mappings to/from. */
	private File mappingsFile_;

	/** The mapping between infobox Strings and TermStanding. */
	private Map<String, WeightedStanding> standingMap_;

	private ReentrantLock mapLock_;

	/**
	 * Constructor for a new InfoboxMiner.
	 * 
	 * @param mapper
	 *            The Mapping class.
	 * @param heuristicName
	 *            The name of the subclass infobox miner.
	 */
	public InfoboxMiner(boolean usePrecomputed, CycMapper mapper, CycMiner miner, String heuristicName,
			File mappingsFilename) {
		super(usePrecomputed, mapper, miner);
		try {
			mappingsFile_ = mappingsFilename;
			if (!mappingsFilename.exists())
				mappingsFilename.createNewFile();
			standingMap_ = initialiseInfoboxMappings(mappingsFilename);
			mapLock_ = new ReentrantLock();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads the data from the infobox relation mapping into local memory.
	 * 
	 * @param mappingsFilename
	 *            The filename to read the mappings from.
	 * @throws Exception
	 *             Should something go awry...
	 */
	private Map<String, WeightedStanding> initialiseInfoboxMappings(
			File mappingsFilename) throws Exception {
		Map<String, WeightedStanding> mappings = new HashMap<>();
		if (mappingsFilename != null && !mappingsFilename.exists())
			throw new FileNotFoundException("Infobox mappings file '"
					+ mappingsFilename + "' doesn't exist!");

		FileReader reader = new FileReader(mappingsFilename);
		BufferedReader in = new BufferedReader(reader);
		String input = null;
		// Read the mappings file, line by line
		while ((input = in.readLine()) != null) {
			// Skip the headings
			if (input.startsWith("RELATION"))
				continue;
			TermStanding[] ts = TermStanding.values();
			input.replaceAll(" ", "");
			String[] split = input.split(MAPPING_DELIMITER);
			if (split.length < ts.length + 1) {
				in.close();
				throw new InputMismatchException("Expected at least "
						+ (ts.length + 1) + " elements! Was '" + input
						+ "' instead.");
			}

			WeightedStanding standing = new WeightedStanding();
			for (int i = 0; i < ts.length; i++)
				standing.addStanding(null, ts[i],
						Double.parseDouble(split[i + 1]));
			mappings.put(split[0].trim(), standing);

			if (split.length > 2)
				readAdditionalInput(split);
		}

		in.close();
		reader.close();

		return mappings;
	}

	/**
	 * Reads any additional input.
	 * 
	 * @param split
	 *            The line that was read in, split by tab.
	 */
	protected abstract void readAdditionalInput(String[] split);

	/**
	 * Records the standing for a given infobox-related key.
	 * 
	 * @param key
	 *            The key for the standing.
	 * @param actualStanding
	 *            The standing being recorded.
	 */
	protected final void recordStanding(String key, TermStanding actualStanding) {
		try {
			mapLock_.lock();
			WeightedStanding standing = standingMap_.get(key);
			if (standing == null) {
				standing = new WeightedStanding();
				standingMap_.put(key, standing);
			}
			standing.addStanding(new HeuristicProvenance(this, key),
					actualStanding, getWeight());
		} finally {
			mapLock_.unlock();
		}
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		informationProduced[InformationType.STANDING.ordinal()] = true;
	}

	/**
	 * Compiles any additional output to write for a given infobx term.
	 * 
	 * @param infoboxTerm
	 *            The infobox term to write additional output for.
	 * @return A String[] of additional output, to be separated by tabs.
	 */
	protected abstract String[] writeAdditionalOutput(String infoboxTerm);

	@Override
	public void printHeuristicState() throws Exception {
		super.printHeuristicState();
		mappingsFile_.createNewFile();
		BufferedWriter out = new BufferedWriter(new FileWriter(mappingsFile_));

		List<String> ordered = null;
		try {
			mapLock_.lock();
			ordered = new ArrayList<>(standingMap_.keySet());
		} finally {
			mapLock_.unlock();
		}
		Collections.sort(ordered);
		out.write("RELATION\t" + TermStanding.values()[0] + "\t"
				+ TermStanding.values()[1] + "\t" + TermStanding.values()[2]
				+ "\n");
		for (String mapping : ordered) {
			out.write(mapping);
			TermStanding[] ts = TermStanding.values();
			for (int i = 0; i < ts.length; i++)
				out.write("\t"
						+ standingMap_.get(mapping).getActualWeight(ts[i]));
			String[] additional = writeAdditionalOutput(mapping);
			for (String add : additional)
				out.write("\t" + add);
			out.write("\n");
		}
		out.close();
	}

	/**
	 * Votes for an article's standing based on what infobox it uses.
	 * 
	 * @param input
	 *            The input for determining standing.
	 * @return A standing determined by the input.
	 */
	public final WeightedStanding getStanding(String input) {
		WeightedStanding standing = standingMap_.get(input);
		if (standing != null)
			return standing;
		return new WeightedStanding();
	}
}
