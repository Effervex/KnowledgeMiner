/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining.wikipedia;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.TermStanding;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.InformationType;
import knowledgeMiner.mining.MinedInformation;
import util.wikipedia.InfoboxData;

/**
 * A mining heuristic that extracts information solely from the type of infobox
 * a Wikipedia article uses. This heuristic produces term standing.
 * 
 * @author Sam Sarjant
 */
public class InfoboxTypeMiner extends InfoboxMiner {
	/** The file for infobox type standings. */
	public static final File INFOBOX_TYPE_FILE = new File("infoboxTypes.txt");

	/**
	 * Constructor for a new InfoboxMiner.
	 * 
	 * @param mapper
	 *            The Mapping class.
	 * @param miner
	 */
	public InfoboxTypeMiner(CycMapper mapper, CycMiner miner) {
		super(false, mapper, miner, "infoboxTypeMining", INFOBOX_TYPE_FILE);
	}

	@Override
	protected void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket cyc)
			throws IOException {
		// Cluster infobox types to assign parentage
		List<InfoboxData> infoboxData = wmi.getInfoboxData(info.getArticle());
		if (infoboxData.isEmpty())
			return;

		List<String> infoboxTypes = new ArrayList<>();
		for (InfoboxData infobox : infoboxData) {
			infoboxTypes.add(infobox.getInfoboxType());
			try {
				info.addStandingInformation(
						getStanding(infobox.getInfoboxType()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		info.setInfoboxTypes(infoboxTypes);
		return;
	}

	@Override
	protected void readAdditionalInput(String[] split) {
	}

	@Override
	protected void setInformationTypes(boolean[] informationProduced) {
		super.setInformationTypes(informationProduced);
		// While not necessarily always true, infoboxes can indirectly cluster
		// to create parents.
		informationProduced[InformationType.TAXONOMIC.ordinal()] = true;
		informationProduced[InformationType.STANDING.ordinal()] = true;
	}

	@Override
	protected String[] writeAdditionalOutput(String infoboxTerm) {
		return new String[0];
	}

	@Override
	public void updateGlobal(MinedInformation info, WMISocket wmi) {
		super.updateGlobal(info, wmi);

		// Note the infobox type against the standing
		List<String> infoTypes = info.getInfoboxTypes();
		ConceptModule cm = (ConceptModule) info;
		TermStanding actualStanding = cm.getConceptStanding();
		if (infoTypes != null)
			for (String infoboxType : infoTypes)
				recordStanding(infoboxType, actualStanding);
	}
}
