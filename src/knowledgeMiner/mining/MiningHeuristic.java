/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.ontology.OntologySocket;
import io.resources.WMISocket;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.wikipedia.WikipediaMappedConcept;
import knowledgeMiner.preprocessing.KnowledgeMinerPreprocessor;

import org.slf4j.LoggerFactory;

import cyc.AssertionArgument;

/**
 * 
 * @author Sam Sarjant
 */
public abstract class MiningHeuristic extends WeightedHeuristic {

	/** The information this heuristic produces. */
	private final int informationProduced_;

	/** The weights of each infoType held by this heuristic. */
	private final double[] infoTypeWeights_;

	protected final HeuristicProvenance basicProvenance_;

	protected final CycMiner miner_;

	protected boolean partitionInformation_;

	/**
	 * Constructor for a new MiningHeuristic
	 * 
	 * @param mapper
	 *            The mapping class.
	 * @param miner
	 *            The mining class.
	 */
	public MiningHeuristic(boolean usePrecomputed, CycMapper mapper,
			CycMiner miner) {
		super(usePrecomputed, mapper);
		miner_ = miner;
		basicProvenance_ = new HeuristicProvenance(this, null);

		boolean[] infoTypes = new boolean[InformationType.values().length];
		infoTypeWeights_ = new double[infoTypes.length];
		setInformationTypes(infoTypes);
		int bitwise = 0;
		for (int i = 0; i < infoTypes.length; i++) {
			if (infoTypes[i]) {
				bitwise += 1 << i;
				infoTypeWeights_[i] = INITIAL_WEIGHT;
			}
		}

		informationProduced_ = bitwise;
	}

	/**
	 * Partitions the mined information into separate parts, such that the only
	 * information returned is that which concerns the current article. Also, if
	 * performing precomputation, all partitioned information is added to its
	 * respective article.
	 *
	 * @param info
	 *            The information to partition up.
	 * @param article
	 *            The current article to partition to.
	 * @return All information concerning the current article from info. Should
	 *         be all of it, but some cases might split it.
	 * @throws Exception
	 */
	private MinedInformation partitionInformation(MinedInformation info,
			Integer article) throws Exception {
		// No data? No need to partition
		if (!partitionInformation_ || !info.isModified())
			return info;

		// Separate the assertions
		Map<Integer, MinedInformation> partitions = new HashMap<>();
		for (PartialAssertion assertion : info.getAssertions()) {
			// Split by each arg
			for (int i = 0; i < assertion.getArgs().length; i++) {
				if (assertion.isHierarchical() && i != 0)
					continue;
				AssertionArgument aa = assertion.getArgs()[i];
				if (aa instanceof WikipediaMappedConcept) {
					WikipediaMappedConcept wmc = (WikipediaMappedConcept) aa;
					MinedInformation artInfo = partitions.get(wmc.getArticle());
					if (artInfo == null) {
						artInfo = getInfo(wmc.getArticle());
						partitions.put(wmc.getArticle(), artInfo);
					}
					artInfo.addAssertion(assertion);
				}
			}
		}

		// Add other info to the core article
		MinedInformation coreInfo = partitions.get(article);
		if (coreInfo == null)
			coreInfo = new MinedInformation(article);
		coreInfo.addStandingInformation(info.getStanding());
		for (DefiniteAssertion concrete : info.getConcreteAssertions())
			coreInfo.addAssertion(concrete);
		coreInfo.setInfoboxTypes(info.getInfoboxTypes());
		coreInfo.addMinedInfoType(info.getMinedInformation());
		// Exit now with the core info if no precomputation
		if (!isPrecomputed())
			return coreInfo;

		// Record mined info for all referenced article
		for (Map.Entry<Integer, MinedInformation> entry : partitions.entrySet()) {
			MinedInformation artInfo = entry.getValue();
			artInfo.addMinedInfoType(info.getMinedInformation());
			writeInfo(artInfo);
		}
		return coreInfo;
	}

	/**
	 * Reweights the information returned by this mining heuristic mining
	 * process.
	 * 
	 * @param info
	 *            The info to reweight
	 */
	private void reweightInfo(MinedInformation info) {
		double weight = getWeight();
		if (weight == 1)
			return;

		// Otherwise, reweight the assertions
		for (PartialAssertion pa : info.getAssertions())
			pa.setWeight(pa.getWeight() * weight);
		// TODO Reweight the standing? It's normalised anyway, so it wouldn't
		// matter too much.
//		info.getStanding()
	}

	protected synchronized MinedInformation getInfo(int article) {
		// Load up the information, if it exists
		MinedInformation info = null;
		try {
			info = (MinedInformation) KnowledgeMinerPreprocessor.getInstance()
					.getLoadHeuristicResult(getHeuristicName(), article);
		} catch (Exception e) {
			System.err.println("Error while deserialising " + article);
			e.printStackTrace();
		}
		if (info == null) {
			info = new MinedInformation(article);
			KnowledgeMinerPreprocessor.getInstance().recordData(
					getHeuristicName(), article, info);
		}

		return info;
	}

	/**
	 * If the given infoType is requested.
	 * 
	 * @param infoRequested
	 *            The total information requested.
	 * @param infoType
	 *            The type of information being checked.
	 * @return True if the information was requested.
	 */
	protected final boolean informationRequested(int infoRequested,
			InformationType infoType) {
		// If this has what is wanted.
		if ((infoRequested & (1 << infoType.ordinal())) > 0)
			return true;
		return false;
	}

	/**
	 * The actual mining method. This method extracts and processes information
	 * from an article which is accessible through get methods.
	 * 
	 * @param info
	 *            The mined information to add to (contains skeletal
	 *            information).
	 * @param informationRequested
	 *            The information requested of this heuristic (bitwise).
	 * @param wmi
	 *            The WMI access point.
	 * @param ontology
	 *            The ontology access.
	 * @throws IOException
	 *             Should something go awry...
	 */
	protected abstract void mineArticleInternal(MinedInformation info,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception;

	/**
	 * If this heuristic produces the requested information.
	 * 
	 * @param informationRequested
	 *            The information requested (bitwise).
	 * @return True if this heuristic is able to produce that information.
	 */
	protected final boolean producesRequestedInformation(
			int informationRequested) {
		if ((informationProduced_ & informationRequested) > 0)
			return true;
		return false;
	}

	/**
	 * Set the information types that this mining heuristic produces.
	 * 
	 * @param infoTypes
	 *            The array to set.
	 */
	protected abstract void setInformationTypes(boolean[] infoTypes);

	/**
	 * Writes the info to file (if preprocessing).
	 *
	 * @param artInfo
	 *            The info to write.
	 */
	protected void writeInfo(MinedInformation artInfo) {
		try {
			KnowledgeMinerPreprocessor.getInstance().recordData(
					getHeuristicName(), artInfo.getArticle(), artInfo);
		} catch (Exception e) {
			System.err.println("Error while serialising "
					+ artInfo.getArticle());
			e.printStackTrace();
		}
	}

	public final int getInformationProduced() {
		return informationProduced_;
	}

	/**
	 * Gets the weight of a specific information type produced by this
	 * heuristic. Always <= the heuristic's weight.
	 * 
	 * @param type
	 *            The information type.
	 * @return The weight of this heuristic's specific information type.
	 */
	public final double getInfoTypeWeight(InformationType type) {
		return weight_ * infoTypeWeights_[type.ordinal()];
	}

	/**
	 * Mines an article for information. This can be adding relations, adding
	 * new terms, or assisting in mapping.
	 * 
	 * @param minedInformation
	 *            The {@link MinedInformation} specifying the target mining.
	 * @param informationRequested
	 *            The information requested for this mining operation.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The information that was able to be mined.
	 */
	public MinedInformation mineArticle(ConceptModule minedInformation,
			int informationRequested, WMISocket wmi, OntologySocket ontology) {
		// If this doesn't produce the required information, return empty
		// information.
		if (!producesRequestedInformation(informationRequested))
			return null;
		LoggerFactory.getLogger(getHeuristicName()).info("MINING:\t{}",
				minedInformation.getArticle());

		// No null articles allowed!
		Integer article = minedInformation.getArticle();
		if (article == null || article == -1)
			return null;

		// Get precomputed info.
		MinedInformation info = (MinedInformation) KnowledgeMiner.getInstance()
				.getHeuristicResult(article, this);
		if (info != null
				&& (informationRequested & info.getMinedInformation()) == informationRequested) {
			// System.out.println(getHeuristicName() + " (Pre): "
			// + info.getAssertions());
			info.setModified(true);
			reweightInfo(info);
			return info;
		}

		// If not precomputed yet, compute it, and split it up if saving
		// precomputed
		try {
			info = new MinedInformation(minedInformation.getArticle());
			mineArticleInternal(info, informationRequested, wmi, ontology);
			if (info != null)
				info.addMinedInfoType(informationRequested);

			// Split the data up and save it
			info = partitionInformation(info, article);
			reweightInfo(info);
			return info;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * An alternative accessor for mining Wikipedia articles. Not recommended.
	 * Primarily for tests and debugging.
	 * 
	 * @param article
	 *            The article being mined.
	 * @param informationRequested
	 *            The information requested for this mining operation.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 * @return The information mined from the article.
	 */
	public final MinedInformation mineArticle(int article,
			int informationRequested, WMISocket wmi, OntologySocket ontology) {
		return mineArticle(new ConceptModule(article), informationRequested,
				wmi, ontology);
	}

	/**
	 * Updates the heuristic using entire collections of assertions about a
	 * concept and article. Note that updates on an assertion level should use
	 * the updateViaAssertion method.
	 * 
	 * @param info
	 *            The information to update with.
	 * @param wmi
	 *            The WMI access.
	 */
	public void updateGlobal(MinedInformation info, WMISocket wmi) {
		// Do nothing by default.
	}

	/**
	 * Updates the weight of both this heuristic and the sub-information type
	 * that this heuristic produces.
	 * 
	 * @param assertion
	 *            The assertion to update the heuristic with.
	 * @param details
	 *            The details of the provenance.
	 * @param weight
	 *            The weight to update towards.
	 * @param infoType
	 *            The information type.
	 * @param wmi
	 *            The WMI access.
	 */
	public void updateViaAssertion(WeightedInformation assertion,
			String details, double weight, InformationType infoType,
			WMISocket wmi) {
		// Perform online weight updating.
		if (KnowledgeMiner.onlineWeightUpdating_) {
			infoTypeWeights_[infoType.ordinal()] = WeightedHeuristic
					.updateWeight(infoTypeWeights_[infoType.ordinal()], weight,
							DEFAULT_ALPHA);
			updateWeight(weight);
		}
	}

	public static WikipediaMappedConcept createSelfRefConcept(Object minedObject) {
		return new WikipediaMappedConcept((int) minedObject);
	}
}
