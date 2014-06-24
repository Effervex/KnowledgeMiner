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

	/**
	 * Constructor for a new InfoboxMiner.
	 * 
	 * @param mapper
	 *            The Mapping class.
	 * @param heuristicName
	 *            The name of the subclass infobox miner.
	 */
	public InfoboxMiner(CycMapper mapper, CycMiner miner, String heuristicName,
			File mappingsFilename) {
		super(mapper, miner);
		try {
			mappingsFile_ = mappingsFilename;
			if (!mappingsFilename.exists())
				mappingsFilename.createNewFile();
			standingMap_ = initialiseInfoboxMappings(mappingsFilename);
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
			input.replaceAll(" ", "");
			String[] split = input.split(MAPPING_DELIMITER);
			if (split.length < 2) {
				in.close();
				throw new InputMismatchException(
						"Expected at least two elements! Was '" + input
								+ "' instead.");
			}

			mappings.put(split[0].trim(),
					new WeightedStanding(TermStanding.valueOf(split[1].trim())));

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
		WeightedStanding standing = standingMap_.get(key);
		if (standing == null) {
			standing = new WeightedStanding(actualStanding,
					new HeuristicProvenance(this, key));
			standingMap_.put(key, standing);
		} else
			standing.addStanding(new HeuristicProvenance(this, key),
					actualStanding);
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

		List<String> ordered = new ArrayList<>(standingMap_.keySet());
		Collections.sort(ordered);
		for (String mapping : ordered) {
			out.write(mapping + "\t"
					+ standingMap_.get(mapping).toParsableString());
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
	public final TermStanding voteStanding(String input) {
		WeightedStanding standing = standingMap_.get(input);
		if (standing != null)
			return standing.getStanding();
		return TermStanding.UNKNOWN;
	}
}
