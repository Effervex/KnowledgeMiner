/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mining;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.meta.ImpliedStandingMiner;
import knowledgeMiner.mining.meta.MetaMiningHeuristic;
import knowledgeMiner.mining.wikipedia.FirstSentenceMiner;
import knowledgeMiner.mining.wikipedia.FirstSentenceParserMiner;
import knowledgeMiner.mining.wikipedia.InfoboxClusterer;
import knowledgeMiner.mining.wikipedia.InfoboxRelationMiner;
import knowledgeMiner.mining.wikipedia.InfoboxTypeMiner;
import knowledgeMiner.mining.wikipedia.ListMiner;
import knowledgeMiner.mining.wikipedia.TitleMiner;

import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * This class is the base class for mining information from a source and adding
 * this information to KnowledgeMiner's 'mind'.
 * 
 * @author Sam Sarjant
 */
public class CycMiner {
	private final static Logger logger_ = LoggerFactory
			.getLogger(CycMiner.class);

	/** A special case miner. */
	private InfoboxClusterer infoboxClusterer_;

	/** The meta-mining heuristics. */
	private List<MetaMiningHeuristic> metaMiningHeuristics_;

	/** The heuristics used for mining information from articles. */
	private ArrayList<MiningHeuristic> miningHeuristics_;

	/** The sentence parser. */
	private SentenceParserHeuristic sentenceParser_;

	/**
	 * An initialisation constructor for the mining aspect of KnowledgeMiner.
	 */
	public CycMiner(KnowledgeMiner knowledgeMiner, CycMapper mapper) {
		sentenceParser_ = new SentenceParserHeuristic(mapper, this);

		miningHeuristics_ = new ArrayList<>();
		miningHeuristics_.add(new TitleMiner(mapper, this));
		miningHeuristics_.add(new FirstSentenceMiner(mapper, this));
		miningHeuristics_.add(new FirstSentenceParserMiner(mapper, this));
		miningHeuristics_.add(new InfoboxTypeMiner(mapper, this));
		miningHeuristics_.add(new InfoboxRelationMiner(mapper, this));
//		miningHeuristics_.add(new CategoryChildMiner(mapper, this));
		miningHeuristics_.add(new ListMiner(mapper, this));
//		miningHeuristics_.add(new SubCategoryMiner(mapper, this));
		// miningHeuristics_.add(new CategoryMembershipMiner(mapper, this));
		if (knowledgeMiner != null)
			for (MiningHeuristic heuristic : miningHeuristics_)
				knowledgeMiner.registerHeuristic(heuristic);

		metaMiningHeuristics_ = new ArrayList<>();
		metaMiningHeuristics_.add(new ImpliedStandingMiner(mapper, this));
		if (knowledgeMiner != null)
			for (MiningHeuristic heuristic : metaMiningHeuristics_)
				knowledgeMiner.registerHeuristic(heuristic);

		infoboxClusterer_ = new InfoboxClusterer(mapper, this);
		if (knowledgeMiner != null)
			knowledgeMiner.registerHeuristic(infoboxClusterer_);

		try {
			CycConstants.initialiseAssertions(ResourceAccess
					.requestOntologySocket());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Collection<MiningHeuristic> getMiningHeuristics() {
		return miningHeuristics_;
	}

	public Map<MiningHeuristic, Long> heuristicTime = new HashedMap<>();

	/**
	 * The base method for mining information from an Article as well as using
	 * the Article as a base to create further Cyc concepts.
	 * 
	 * @param conceptModule
	 *            The mapping between a Cyc term and a Wikipedia Article.
	 * @param informationRequested
	 *            The information types to mine.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            The Cyc access.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public void mineArticle(ConceptModule conceptModule,
			int informationRequested, WMISocket wmi, OntologySocket ontology)
			throws Exception {
		int article = conceptModule.getArticle();

		// Apply every mining heuristic to extracting information from the
		// article.
		MinedInformation info = new MinedInformation(article);
		for (MiningHeuristic mh : miningHeuristics_) {
			logger_.info("Mining {} with {}", info, mh.toString());
			MinedInformation mined = mh.mineArticle(
					conceptModule,
					informationRequested
							& conceptModule.getUnminedInformation(), wmi,
					ontology);
			ConceptMiningTask.interactiveInterface_.interactiveMining(mined,
					mh, wmi, ontology);
			info.mergeInformation(mined);
		}
		conceptModule.mergeInformation(info);
		conceptModule.addMinedInfoType(informationRequested);
	}

	public void mineSentence(String sentence, MinedInformation info,
			MiningHeuristic heuristic, OntologySocket cyc, WMISocket wmi) {
		logger_.trace("mineSentence: " + info.getArticle());
		try {
			WikipediaMappedConcept focusConcept = new WikipediaMappedConcept(
					info.getArticle());
			Collection<PartialAssertion> results = sentenceParser_
					.extractAssertions(sentence, focusConcept, wmi, cyc,
							heuristic);
			for (PartialAssertion assertion : results)
				info.addAssertion(assertion);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Notes an infobox down in the infobox clusterer and returns true (is a
	 * child) if the example is positive, or the infobox is the same as the
	 * statistically significant collection of other examples.
	 * 
	 * @param concept
	 *            The concept being checked.
	 * @param infobox
	 *            The infobox of the article.
	 * @param isPositive
	 *            If this example is a positive child.
	 * @param parentArticle
	 *            The parent article to record infobox cluster data for.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public boolean noteInfoboxChild(ConceptModule concept, String infobox,
			boolean isPositive, int parentArticle) throws Exception {
		return infoboxClusterer_.noteInfoboxChild(concept, infobox,
				parentArticle, isPositive);
	}

	public void printCurrentHeuristicStates() {
		for (MiningHeuristic mh : miningHeuristics_) {
			try {
				mh.printHeuristicState();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Performs verbose mining of an article, with prompts for a human to enter
	 * data into.
	 */
	public static void verbose() {
		CycMiner miner = KnowledgeMiner.getInstance().getMiner();
		WMISocket wmi = ResourceAccess.requestWMISocket();
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String input = null;
		do {
			try {
				// Create the source
				System.out.println("Enter the source article to mine:");
				input = in.readLine().trim();
				int article = wmi.getArticleByTitle(input);

				System.out.println("Enter the mapped concept (or null):");
				input = in.readLine().trim();
				OntologyConcept cycTerm = null;

				ConceptModule conceptModule = null;
				cycTerm = new OntologyConcept(input);
				conceptModule = new ConceptModule(cycTerm, article, 1.0, true);

				// Select the mapping algorithm
				System.out.println("Select mining heuristic:");
				int i = 1;
				for (MiningHeuristic heuristic : miner.miningHeuristics_) {
					System.out.println("\t" + i++ + ": "
							+ heuristic.getClass().getSimpleName());
				}
				System.out.println("\tOr " + i + " for ALL.");
				int j = Integer.parseInt(in.readLine().trim());

				if (j == i) {
					miner.mineArticle(conceptModule,
							MinedInformation.ALL_TYPES, wmi, ontology);
				} else
					conceptModule.mergeInformation(miner.miningHeuristics_.get(
							j - 1).mineArticle(conceptModule,
							MinedInformation.ALL_TYPES, wmi, ontology));

				System.out.println("Mined information (undisambiguated):");
				for (MinedAssertion aq : conceptModule.getAssertions())
					System.out.println(aq);

				conceptModule.disambiguateAssertions(ontology);
				System.out.println("Disambiguated:");
				for (MinedAssertion assertion : conceptModule
						.getConcreteAssertions())
					System.out.println(assertion);
				System.out.println();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} while (!input.equals("exit"));
		System.exit(0);
	}
}
