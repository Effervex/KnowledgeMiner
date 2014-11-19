/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner.mapping;

import graph.core.CommonConcepts;
import graph.module.NLPToSyntaxModule;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.WeightedHeuristic;
import knowledgeMiner.mapping.cycToWiki.CycToWikiPostProcessor;
import knowledgeMiner.mapping.cycToWiki.CycToWiki_ContextRelatedSynonyms;
import knowledgeMiner.mapping.cycToWiki.CycToWiki_TitleMatching;
import knowledgeMiner.mapping.cycToWiki.CycToWiki_VoteSynonyms;
import knowledgeMiner.mapping.textToCyc.CleanJunkPreProcessor;
import knowledgeMiner.mapping.textToCyc.JoinNewLinePreProcessor;
import knowledgeMiner.mapping.textToCyc.RemoveAnchorPreProcessor;
import knowledgeMiner.mapping.textToCyc.RemoveBracketsPreProcessor;
import knowledgeMiner.mapping.textToCyc.RemoveSentencePunctuationPreProcessor;
import knowledgeMiner.mapping.textToCyc.RemoveSpacesPreProcessor;
import knowledgeMiner.mapping.textToCyc.ReplaceUnderscorePreProcessor;
import knowledgeMiner.mapping.textToCyc.SplitCommaPreProcessor;
import knowledgeMiner.mapping.textToCyc.SplitNewLinePreProcessor;
import knowledgeMiner.mapping.textToCyc.TextToCyc_AnchorMap;
import knowledgeMiner.mapping.textToCyc.TextToCyc_BasicString;
import knowledgeMiner.mapping.textToCyc.TextToCyc_DateParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_IntervalParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_NumericParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_TextSearch;
import knowledgeMiner.mapping.textToCyc.TextToCyc_TimeContextParse;
import knowledgeMiner.mapping.textToCyc.TextToCyc_WikiSenseSearch;
import knowledgeMiner.mapping.wikiToCyc.WikiToCycPostProcessor;
import knowledgeMiner.mapping.wikiToCyc.WikiToCyc_TitleMatching;
import knowledgeMiner.mapping.wikiToCyc.WikiToCyc_VoteSynonyms;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import util.wikipedia.WikiParser;

import com.google.common.base.Predicate;

import cyc.OntologyConcept;

/**
 * This class is the base class for identifying mappings between Cyc concepts
 * and source information. this is combined somewhat with the CycMiner.
 * 
 * @author Sam Sarjant
 */
public class CycMapper {
	private final static Logger logger_ = LoggerFactory
			.getLogger(CycMapper.class);

	/**
	 * The percentage that a term must be clear by when finding a Cyc term from
	 * an Article.
	 */
	public static final float AMBIGUITY_THRESHOLD = 0.1f;

	/** The string for an ambiguous Cyc term. */
	public static final String AMBIGUOUS_STRING = "---AMBIGUOUS---";

	/** The prefix string for Categories. */
	public static final String CATEGORY_STRING = "Category:";

	/** The number of anchors to use when searching for synonyms. */
	public static final int NUM_RELEVANT_ANCHORS = 6;

	/** The mapping heuristics for Cyc concept to Wiki article. */
	private MappingSuite<OntologyConcept, Integer> cycToWikiMapping_;

	/** The mapping heuristics for plain text to Cyc concept. */
	private MappingSuite<String, OntologyConcept> textToCycMapping_;

	/** The mapping heuristics for Wiki article to Cyc concept. */
	private MappingSuite<Integer, OntologyConcept> wikiToCycMapping_;

	/**
	 * The constructor for the Cyc Mapper.
	 * 
	 * @param knowledgeMiner
	 */
	public CycMapper(KnowledgeMiner knowledgeMiner) {
		super();
		cycToWikiMapping_ = new MappingSuite<>();
		wikiToCycMapping_ = new MappingSuite<>();
		textToCycMapping_ = new MappingSuite<>();

		initialiseHeuristics(knowledgeMiner);
	}

	/**
	 * The constructor for the Cyc Mapper.
	 * 
	 * @param knowledgeMiner
	 */
	public CycMapper() {
		this(null);
	}

	/**
	 * Fragments a string into sub-components, mapping each one individually if
	 * necessary.
	 * 
	 * @param text
	 *            The text to fragment & map.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The Ontology access.
	 * @param disallowed
	 *            The disallowed heuristics.
	 * @return A Hierarchical Weighted Set of assertion queues.
	 */
	private HierarchicalWeightedSet<OntologyConcept> fragmentString(
			List<String> words,
			WMISocket wmi,
			OntologySocket ontology,
			Collection<Class<? extends MappingHeuristic<String, OntologyConcept>>> disallowed) {
		int n = words.size();

		// TODO Commas are being used during search here. Be sure to exclude
		// them here or earlier.

		// Map
		String str = StringUtils.join(words, ' ').trim();
		HierarchicalWeightedSet<OntologyConcept> mappings = (HierarchicalWeightedSet<OntologyConcept>) textToCycMapping_
				.mapSourceToTarget(str, wmi, ontology, disallowed);
		if (words.size() > 1) {
			mappings.addLower(fragmentString(words.subList(0, n - 1), wmi,
					ontology, disallowed));
			mappings.addLower(fragmentString(words.subList(1, n), wmi,
					ontology, disallowed));
		}

		return mappings;
	}

	@SuppressWarnings("rawtypes")
	private void initialiseHeuristics(KnowledgeMiner knowledgeMiner) {
		// TODO Set this up as a config file.
		// Initialise Cyc to Wiki heuristics
		cycToWikiMapping_.addHeuristic(new CycToWiki_TitleMatching(this), this);
//		cycToWikiMapping_.addHeuristic(new CycToWiki_CanonicalMatching(this),
//				this);
		cycToWikiMapping_.addHeuristic(new CycToWiki_VoteSynonyms(this), this);
		cycToWikiMapping_.addHeuristic(new CycToWiki_ContextRelatedSynonyms(
				this), this);
		cycToWikiMapping_.addPostProcessor(new CycToWikiPostProcessor());
		if (knowledgeMiner != null)
			for (MappingHeuristic mh : cycToWikiMapping_.getHeuristics())
				knowledgeMiner.registerHeuristic(mh);

		// Initialise Wiki to Cyc heuristics
		wikiToCycMapping_.addHeuristic(new WikiToCyc_TitleMatching(this), this);
		wikiToCycMapping_.addHeuristic(new WikiToCyc_VoteSynonyms(this), this);
		wikiToCycMapping_.addPostProcessor(new WikiToCycPostProcessor());
		if (knowledgeMiner != null)
			for (MappingHeuristic mh : wikiToCycMapping_.getHeuristics())
				knowledgeMiner.registerHeuristic(mh);

		// The various text mapping heuristics
		textToCycMapping_.addHeuristic(new TextToCyc_BasicString(this), this);
		textToCycMapping_.addHeuristic(new TextToCyc_NumericParse(this), this);
		textToCycMapping_.addHeuristic(new TextToCyc_AnchorMap(this), this);
		textToCycMapping_.addHeuristic(new TextToCyc_DateParse(this), this);
		textToCycMapping_.addHeuristic(new TextToCyc_IntervalParse(this), this);
		textToCycMapping_.addHeuristic(new TextToCyc_TextSearch(this), this);
		textToCycMapping_.addHeuristic(new TextToCyc_WikiSenseSearch(this),
				this);
		// textToCycMapping_.addHeuristic(new TextToCyc_WikifySearch(this),
		// this);
		textToCycMapping_.addHeuristic(new TextToCyc_TimeContextParse(this),
				this);
		textToCycMapping_.addPreProcessor(new CleanJunkPreProcessor());
		textToCycMapping_.addPreProcessor(new ReplaceUnderscorePreProcessor());
		textToCycMapping_.addPreProcessor(new SplitNewLinePreProcessor());
		textToCycMapping_.addPreProcessor(new JoinNewLinePreProcessor());
		textToCycMapping_.addPreProcessor(new SplitCommaPreProcessor());
		textToCycMapping_.addPreProcessor(new RemoveBracketsPreProcessor());
		textToCycMapping_.addPreProcessor(new RemoveSpacesPreProcessor());
		textToCycMapping_.addPreProcessor(new RemoveAnchorPreProcessor());
		textToCycMapping_
				.addPreProcessor(new RemoveSentencePunctuationPreProcessor());
		textToCycMapping_.setPreProcessFilter(new Predicate<String>() {
			@Override
			public boolean apply(String target) {
				return !target.trim().isEmpty();
			}
		});
		if (knowledgeMiner != null)
			for (MappingHeuristic mh : textToCycMapping_.getHeuristics())
				knowledgeMiner.registerHeuristic(mh);
	}

	public void clearMappings() {
		cycToWikiMapping_.clearMappings();
	}

	public MappingSuite<OntologyConcept, Integer> getCycToWikiMappingSuite() {
		return cycToWikiMapping_;
	}

	public MappingSuite<String, OntologyConcept> getTextToCycMappingSuite() {
		return textToCycMapping_;
	}

	public MappingSuite<Integer, OntologyConcept> getWikiToCycMappingSuite() {
		return wikiToCycMapping_;
	}

	public void initialise() {
	}

	/**
	 * Maps a Cyc term to a weighted set of probable Wikipedia articles.
	 * 
	 * @param cycTerm
	 *            The Cyc term used for mapping.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Ontology access.
	 * @return The possible Wikipedia articles, weighted by their likelihood.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public WeightedSet<Integer> mapCycToWikipedia(
			OntologyConcept cycTerm,
			Collection<Class<? extends MappingHeuristic<OntologyConcept, Integer>>> excludedHeuristics,
			WMISocket wmi, OntologySocket ontology) {
		return cycToWikiMapping_.mapSourceToTarget(cycTerm, wmi, ontology,
				excludedHeuristics);
	}

	/**
	 * Maps an infobox relation to a weighted set of probable Cyc predicates.
	 * 
	 * @param relation
	 *            The article used for mapping.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Cyc access.
	 * @return The possible Cyc predicates, weighted by their likelihood.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public HierarchicalWeightedSet<OntologyConcept> mapRelationToPredicate(
			String relation, WMISocket wmi, OntologySocket ontology) {
		HierarchicalWeightedSet<OntologyConcept> textMapped = mapTextToCyc(
				relation, false, false, true, true, wmi, ontology);
		WeightedSet<OntologyConcept> flattened = textMapped.flattenHierarchy();
		HierarchicalWeightedSet<OntologyConcept> predicates = new HierarchicalWeightedSet<>();
		// For every argument.
		for (OntologyConcept arg : flattened) {
			if (arg instanceof OntologyConcept) {
				OntologyConcept cycTerm = arg;
				try {
					if (ontology.isa(cycTerm.getIdentifier(),
							CommonConcepts.PREDICATE.getID()))
						predicates.add(cycTerm, flattened.getWeight(cycTerm));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return predicates;
	}

	/**
	 * Maps plain text to a weighted set of probable Cyc terms.
	 * 
	 * @param text
	 *            The text used for mapping.
	 * @param allowStrings
	 *            If the text can simply be returned as a StringNode .
	 * @param fragmentText
	 *            If the text should be fragmented up.
	 * @param preProcessText
	 *            If the text being parsed should be preprocessed.
	 * @param allowDirectSearch
	 *            If text can be mapped directly (search ontology by text).
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Cyc access.
	 * @return The possible Cyc terms, weighted by their likelihood.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public HierarchicalWeightedSet<OntologyConcept> mapTextToCyc(String text,
			boolean allowStrings, boolean fragmentText, boolean preProcessText,
			boolean allowDirectSearch, WMISocket wmi, OntologySocket ontology) {
		text = WikiParser.cleanupUselessMarkup(text);
		text = NLPToSyntaxModule.convertToAscii(text).trim();
		if (text.isEmpty())
			return new HierarchicalWeightedSet<>();
		logger_.debug("mapTextToCyc: {}", text);

		// Building disallowed
		Collection<Class<? extends MappingHeuristic<String, OntologyConcept>>> excludedHeuristics = new ArrayList<>();
		if (!allowStrings)
			excludedHeuristics.add(TextToCyc_BasicString.class);
		if (!allowDirectSearch)
			excludedHeuristics.add(TextToCyc_TextSearch.class);
		if (excludedHeuristics.isEmpty())
			excludedHeuristics = null;

		if (!fragmentText) {
			if (preProcessText)
				return textToCycMapping_
						.mapSourcesToTargets(textToCycMapping_
								.preProcessSource(text, wmi, ontology), wmi,
								ontology, excludedHeuristics);
			return (HierarchicalWeightedSet<OntologyConcept>) textToCycMapping_
					.mapSourceToTarget(text, wmi, ontology, excludedHeuristics);
		}

		// Split strings up, creating layered collections of assertions
		ArrayList<String> listSplit = WikiParser.split(text.trim(), " ");
		return fragmentString(listSplit, wmi, ontology, excludedHeuristics);
	}

	/**
	 * Maps using a specific heuristic.
	 * 
	 * @param input
	 *            The input to map.
	 * @param heuristic
	 *            The heuristic class to use.
	 * @param wmi
	 *            The WMI access.
	 * @param ontology
	 *            The ontology access.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public WeightedSet mapViaHeuristic(Object input,
			Class<? extends MappingHeuristic> heuristic, WMISocket wmi,
			OntologySocket ontology) {
		MappingHeuristic<Object, Object> heuristicInst = (MappingHeuristic<Object, Object>) KnowledgeMiner
				.getInstance().getHeuristicByString(
						WeightedHeuristic.generateHeuristicName(heuristic));
		if (heuristicInst != null)
			return heuristicInst.mapSourceToTarget(input, wmi, ontology);
		return null;
	}

	/**
	 * Maps a Wikipedia article to a weighted set of probable Cyc terms.
	 * 
	 * @param articleID
	 *            The article used for mapping.
	 * @param wmi
	 *            WMI access.
	 * @param ontology
	 *            Cyc access.
	 * @return The possible Cyc terms, weighted by their likelihood.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public WeightedSet<OntologyConcept> mapWikipediaToCyc(int articleID,
			WMISocket wmi, OntologySocket ontology) {
		return wikiToCycMapping_.mapSourceToTarget(articleID, wmi, ontology,
				null);
	}

	/**
	 * Runs a mapping algorithm.
	 * 
	 * @param args
	 *            Probably empty.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String[] args) {
		CycMapper mapper = KnowledgeMiner.getInstance().getMapper();
		WMISocket wmi = ResourceAccess.requestWMISocket();
		OntologySocket ontology = ResourceAccess.requestOntologySocket();
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String input = null;
		do {
			System.out
					.println("Select an option:\n\t'1' (concept-article), '2' (article-concept), "
							+ "'3' (text-concept), '4' (text-predicate),\n"
							+ "\tOr 'exit'");
			try {
				input = in.readLine().trim();
				// Set the suite
				MappingSuite suite = null;
				int type = 0;
				if (input.equals("1")) {
					suite = mapper.cycToWikiMapping_;
					type = 1;
				} else if (input.equals("2")) {
					suite = mapper.wikiToCycMapping_;
					type = 2;
				} else if (input.equals("3")) {
					suite = mapper.textToCycMapping_;
					type = 3;
				} else if (input.equals("4")) {
					suite = mapper.textToCycMapping_;
					type = 4;
				}

				// Create the source
				System.out.println("Enter the source of the mapping.");
				input = in.readLine().trim();
				Object source = null;
				switch (type) {
				case 1:
					source = new OntologyConcept(input);
					break;
				case 2:
					source = wmi.getArticleByTitle(input);
					break;
				case 3:
				case 4:
					source = input;
					break;
				}

				// Select the mapping algorithm
				WeightedSet result = null;
				if (type == 1 || type == 2 || type == 3) {
					System.out.println("Select mapping heuristic:");
					int i = 1;
					for (Object heuristic : suite.getHeuristics()) {
						System.out.println("\t" + i++ + ": "
								+ heuristic.getClass().getSimpleName());
					}
					System.out.println("\tOr " + i + " for ALL.");
					int j = Integer.parseInt(in.readLine().trim());

					if (j == i)
						result = suite.mapSourceToTarget(source, wmi, ontology,
								null);
					else
						result = ((MappingHeuristic) suite.getHeuristics().get(
								j - 1))
								.mapSourceToTarget(source, wmi, ontology);

					// Convert article numbers to names
					if (type == 1) {
						List<String> articleNames = wmi.getPageTitle(true,
								(Integer[]) result.toArray(new Integer[result
										.size()]));
						WeightedSet<String> nameSet = new WeightedSet(
								result.size());
						Iterator<Integer> iter = result.iterator();
						for (String articleName : articleNames) {
							nameSet.set(articleName,
									result.getWeight(iter.next()));
						}
						result = nameSet;
					}
				} else if (type == 4) {
					result = mapper
							.mapRelationToPredicate(input, wmi, ontology);
					result.normaliseWeightTo1(KnowledgeMiner.CUTOFF_THRESHOLD);
				}
				System.out.println(result);

				System.out.println();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} while (!input.equals("exit"));
		System.exit(0);
	}
}
