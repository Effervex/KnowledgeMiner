package tools;

import static java.util.stream.Collectors.toList;
import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import knowledgeMiner.ConceptMiningTask;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import tools.util.DisambiguatedTopic;
import tools.util.DisambiguatedTriple;
import tools.util.FamilyMetrics;
import util.Pair;
import util.collection.MultiMap;
import util.collection.WeightedSet;
import util.text.TermWeight;
import util.wikipedia.WikiParser;
import cyc.CycConstants;
import cyc.OntologyConcept;

/**
 * The primary class for the research incorporating OntoCmaps and CYC.
 * 
 * @author Sam Sarjant
 */
public class TripleDisambiguator {
	private static final int DBPEDIA_URI_INDEX = 28;
	private static final String FAMILY_FILE = "Family.txt";
	private static final int INPUT_DBPEDIA_INDEX = 3;
	private static final int INPUT_STEMMED_INDEX = 1;
	private static final int INPUT_TEXT_INDEX = 0;
	private static final TermWeight PRIMARY_TERMWEIGHT = TermWeight.WIKIFICATION;
	private static final int RELATION_LABEL_INDEX = 3;
	private static final int RELATION_OBJECT_INDEX = 4;
	private static final int RELATION_SUBJECT_INDEX = 2;
	private static final String RELATIONS_BEST = "RelationsBest.txt";
	private static final String TERMS = "Terms.txt";
	private int contextArticle_;
	private CycMapper mapper_;
	private DAGSocket ontology_;
	private WMISocket wmi_;
	public Map<String, Pair<String, Double>> textToArticleMappings_;
	public Collection<Integer> relatedArticles_;

	/**
	 * Constructor for a new TripleDisambiguator. Loads/processes terms to be
	 * used for triples.
	 * 
	 * @param terms
	 *            The terms to use/process then use.
	 */
	public TripleDisambiguator(String contextArticle) {
		wmi_ = ResourceAccess.requestWMISocket();
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		mapper_ = KnowledgeMiner.getInstance().getMapper();

		try {
			if (contextArticle != null) {
				contextArticle_ = wmi_.getArticleByTitle(contextArticle);
				relatedArticles_ = wmi_.getOutLinks(contextArticle_);
				textToArticleMappings_ = new HashMap<>();
			}
			CycConstants.initialiseAssertions(ontology_);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Collection<DisambiguatedTriple> consistencyCheck(
			Collection<DisambiguatedTriple> family) throws IOException {
		// TODO Convert to assertions and run through assertion grid
		// Collection<PartialAssertion> assertions =
		// convertToAssertions(family);
		// AssertionGrid assertionGrid = new AssertionGrid(assertions, null,
		// ontology_, wmi_);
		// Collection<DefiniteAssertion> definiteAssertions = assertionGrid
		// .findMaximalConjoint(ontology_);

		// Check Cyc validity if possible
		ontology_.setForceConstraints(true);
		for (DisambiguatedTriple triple : family) {
			OntologyConcept range = triple.getRange().getConcept(mapper_);
			OntologyConcept domain = triple.getDomain().getConcept(mapper_);
			if (triple.getRelation().getText().equals("is_a")) {
				// If range is known, but domain is not (or is equal to range),
				// create new concept
				if (range != null && (domain == null || domain.equals(range))) {
					String text = triple.getDomain().getText()
							.replaceAll("_", " ");
					TextMappedConcept tmC = new TextMappedConcept(text, false,
							true);
					WeightedSet<OntologyConcept> mappings = tmC.mapThing(
							mapper_, wmi_, ontology_);
					if (mappings.size() == 1) {
						domain = mappings.getOrdered().first();
						triple.getDomain().setConcept(domain);
					} else {
						domain = ConceptMiningTask.createNewCycTermName(text,
								wmi_.getPageTitle(contextArticle_, false),
								ontology_);
						ontology_.createConcept(domain.getConceptName());
						triple.getDomain().markCreated(domain);
					}
				}

				// Testing consistency
				if (domain != null && range != null
						&& triple.isConsistent() == 0) {
					boolean consistent = true;
					int edgeID = ontology_.assertToOntology(null,
							CommonConcepts.GENLS.getID(), domain, range);
					if (edgeID == -1) {
						// Try with isa
						edgeID = ontology_.assertToOntology(null,
								CommonConcepts.ISA.getID(), domain, range);
						if (edgeID == -1)
							consistent = false;
					}

					ontology_.unassert(null, edgeID, true);
					triple.markConsistent(consistent);
				}

				if (domain != null
						&& domain.getID() == DAGSocket.NON_EXISTENT_ID)
					ontology_.removeConcept(domain.getConceptName());
			}
		}
		return family;
	}

	public Collection<DisambiguatedTriple> convertToDisamTriples(File triples)
			throws Exception {
		Collection<DisambiguatedTriple> disamTriples = new ArrayList<>();

		BufferedReader in = new BufferedReader(new FileReader(triples));
		String input = in.readLine();
		while ((input = in.readLine()) != null) {
			String[] split = input.split("\t");
			DisambiguatedTriple dt = new DisambiguatedTriple(
					split[RELATION_LABEL_INDEX], split[RELATION_SUBJECT_INDEX],
					split[RELATION_OBJECT_INDEX]);
			disamTriples.add(dt);
		}
		in.close();

		return disamTriples;
	}

	/**
	 * Parses Amal's XLS triples spreadsheet. Also simultaneously reads in
	 * DBpedia mappings.
	 *
	 * @param f
	 *            The spreadsheet to parse.
	 * @return The triples found in the spreadsheet.
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public Collection<DisambiguatedTriple> parseXLSSheet(File f)
			throws IOException, FileNotFoundException {
		Collection<DisambiguatedTriple> disamTriples = new ArrayList<>();

		// Open and extract the text triples
		POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(f));
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(2);

		int rows = sheet.getPhysicalNumberOfRows();
		// Parse every row, looking for subjects related to the article
		for (int i = 1; i < rows; i++) {
			HSSFRow row = sheet.getRow(i);
			HSSFCell subjCell = row.getCell(RELATION_SUBJECT_INDEX);
			// Record the triple and any necessary info
			DisambiguatedTriple dt = new DisambiguatedTriple(row.getCell(
					RELATION_LABEL_INDEX).getStringCellValue(), row.getCell(
					RELATION_SUBJECT_INDEX).getStringCellValue(), row.getCell(
					RELATION_OBJECT_INDEX).getStringCellValue());
			disamTriples.add(dt);
		}

		// Also read the DBpedia disambiguations
		parseDBpediaMappings(wb.getSheetAt(1));
		wb.close();

		return disamTriples;
	}

	private void parseDBpediaMappings(HSSFSheet termsSheet) {
		int rows = termsSheet.getPhysicalNumberOfRows();
		// Parse every row, looking for subjects related to the article
		for (int i = 1; i < rows; i++) {
			HSSFRow row = termsSheet.getRow(i);
			HSSFCell uriCell = row.getCell(INPUT_DBPEDIA_INDEX);
			if (uriCell != null && !uriCell.getStringCellValue().trim().isEmpty()) {
				String articleName = uriCell.getStringCellValue()
						.substring(DBPEDIA_URI_INDEX).replaceAll("_", " ");
				articleName = articleName.replaceAll("%28", "(");
				articleName = articleName.replaceAll("%29", ")");

				HSSFCell stemCell = row.getCell(INPUT_STEMMED_INDEX);
				String stemString = stemCell.getStringCellValue().replaceAll(
						"_", " ");
				textToArticleMappings_.put(stemString,
						new Pair<String, Double>(articleName, 1d));
			}
		}
	}

	private void printFamilies(
			MultiMap<String, DisambiguatedTriple> familyTriples)
			throws IOException {
		String shortTitle = getContextArticleTitle();
		BufferedWriter out = new BufferedWriter(new FileWriter(shortTitle
				+ PRIMARY_TERMWEIGHT.toString() + FAMILY_FILE));

		// Print the header
		out.write(StringUtils.join(FamilyMetrics.values(), "\t") + "\n");

		List<String> orderedTerms = new ArrayList<>();
		orderedTerms.addAll(familyTriples.keySet());
		Collections.sort(orderedTerms);
		for (String term : orderedTerms) {
			for (DisambiguatedTriple dt : familyTriples.get(term)) {
				String[] results = new String[FamilyMetrics.values().length];
				Arrays.fill(results, "");
				// Basic text
				results[FamilyMetrics.Term.ordinal()] = term;
				results[FamilyMetrics.Triple.ordinal()] = dt.toString(true);

				// DBpedia disambiguated
				if (dt.getDomain().toString().equals(dt.getRange().toString()))
					results[FamilyMetrics.Subject.ordinal()] = StringUtils
							.capitalize(dt.getDomain().toString(true))
							+ " (NEW)";
				else
					results[FamilyMetrics.Subject.ordinal()] = dt.getDomain()
							.toString();
				results[FamilyMetrics.Relation.ordinal()] = dt.getRelation()
						.toString();
				results[FamilyMetrics.Object.ordinal()] = dt.getRange()
						.toString();

				// Cyc mappings
				results[FamilyMetrics.CYC_Subject.ordinal()] = dt.getDomain()
						.getConcept(mapper_) + "";
				if (dt.getDomain().isCreated())
					results[FamilyMetrics.CYC_Subject.ordinal()] += " (NEW)";
				results[FamilyMetrics.CYC_Object.ordinal()] = dt.getRange()
						.getConcept(mapper_) + "";
				if (dt.getRange().isCreated())
					results[FamilyMetrics.CYC_Object.ordinal()] += " (NEW)";

				// Consistency
				if (dt.isConsistent() == 1)
					results[FamilyMetrics.Consistent.ordinal()] += "T";
				if (dt.isConsistent() == -1)
					results[FamilyMetrics.Consistent.ordinal()] += "F";

				// Disambiguation article?
				int subjArticle = dt.getDomain().getArticle();
				int objArticle = dt.getRange().getArticle();
				if (subjArticle != -1 && subjArticle != objArticle)
					results[FamilyMetrics.Is_Subject_Disam.ordinal()] = (wmi_
							.getPageType(subjArticle)
							.equals(WMISocket.TYPE_DISAMBIGUATION)) ? "T" : "F";
				if (objArticle != -1)
					results[FamilyMetrics.Is_Object_Disam.ordinal()] = (wmi_
							.getPageType(objArticle)
							.equals(WMISocket.TYPE_DISAMBIGUATION)) ? "T" : "F";

				// Domain-Range relatedness
				if (subjArticle != -1 && objArticle != -1
						&& subjArticle != objArticle) {
					Double relatedness = wmi_.getRelatednessList(subjArticle,
							objArticle).get(0);
					results[FamilyMetrics.Domain_Range_Relatedness.ordinal()] = relatedness
							+ "";

					// Disjoint categories
					// results[FamilyMetrics.Disjoint_Categories.ordinal()] =
					// (isDisjointCategories(
					// subjArticle, objArticle)) ? "T" : "F";
					// System.out.println(dt.getDomain()
					// + " -- "
					// + dt.getRange()
					// + ": "
					// + results[FamilyMetrics.Disjoint_Categories
					// .ordinal()]);
				}

				out.write(StringUtils.join(results, "\t") + "\n");
			}
		}

		out.close();
	}

	/**
	 * Checks if two article are within the same category hierarchy
	 *
	 * @param subjArticle
	 *            The subject article.
	 * @param objArticle
	 *            The object article.
	 * @return If the two articles are in categories that are children of
	 *         one-another, return false. Else true.
	 * @throws IOException
	 *             Should something go awry...
	 */
	private boolean isDisjointCategories(int subjArticle, int objArticle)
			throws IOException {
		Collection<Integer> subjectCategories = wmi_
				.getArticleCategories(subjArticle);

		Collection<Integer> objectSubCategories = wmi_
				.getPageSubCategories(objArticle);
		objectSubCategories.addAll(wmi_.getPageSuperCategories(objArticle));

		// If the subject categories are in the object sub-categories or vice
		// versa, they are not disjoint.
		if (!CollectionUtils.retainAll(subjectCategories, objectSubCategories)
				.isEmpty())
			return false;
		return true;
	}

	private String getContextArticleTitle() throws IOException {
		String artTitle = wmi_.getPageTitle(contextArticle_, true);
		String shortTitle = WordUtils.capitalize(artTitle)
				.replaceAll("\\s", "");
		return shortTitle;
	}

	// private void readDBPediaMappings(File termFile) throws IOException {
	// BufferedReader in = new BufferedReader(new FileReader(termFile));
	// String input = in.readLine();
	// while ((input = in.readLine()) != null) {
	// String[] split = input.split("\t");
	// if (!split[INPUT_DBPEDIA_INDEX].isEmpty()) {
	// String articleName = split[INPUT_DBPEDIA_INDEX].substring(
	// DBPEDIA_URI_INDEX).replaceAll("_", " ");
	// articleName = articleName.replaceAll("%28", "(");
	// articleName = articleName.replaceAll("%29", ")");
	// textToArticleMappings_.put(split[INPUT_STEMMED_INDEX],
	// new Pair<String, Double>(articleName, 1d));
	// }
	// }
	// in.close();
	// }

	public MultiMap<String, DisambiguatedTriple> checkFamilyConsistency(
			MultiMap<String, DisambiguatedTriple> familyTriples)
			throws IOException {
		// Disambiguate every family
		for (Map.Entry<String, Collection<DisambiguatedTriple>> entry : familyTriples
				.entrySet()) {
			Collection<DisambiguatedTriple> family = entry.getValue();
			try {
				consistencyCheck(family);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		printFamilies(familyTriples);
		return familyTriples;
	}

	public Collection<DisambiguatedTriple> checkGlobalConsistency(
			MultiMap<String, DisambiguatedTriple> familyTriples) {
		// TODO Auto-generated method stub
		return null;
	}

	public Collection<DisambiguatedTriple> disambiguateRelations(
			Collection<DisambiguatedTriple> triples) throws IOException {
		for (DisambiguatedTriple triple : triples) {
			// Disambiguate Domain
			triple.getDomain().disambiguate(PRIMARY_TERMWEIGHT, this, wmi_);

			// Disambiguate Range
			triple.getRange().disambiguate(PRIMARY_TERMWEIGHT, this, wmi_);
		}
		return triples;
	}

	/**
	 * Disambiguates a set of textual triples into disambiguated triples. These
	 * disambiguations can be in a range of forms, from Wikipedia articles, Cyc
	 * concepts, or weighted sets of results.
	 * 
	 * @param termFile
	 *            The terms used in the triples.
	 * @param triples
	 *            The triples to disambiguate.
	 * @param termFile
	 * @return A set of disambiguated triples, probably of the same size as the
	 *         original set.
	 * @throws Exception
	 */
	public Collection<DisambiguatedTriple> disambiguateTriples(
			Collection<DisambiguatedTriple> triples, File termFile)
			throws Exception {
		if (PRIMARY_TERMWEIGHT.equals(TermWeight.WIKIFICATION)) {
			// TODO If using WIKIFICATION, use that instead
			readWikificationMappings();
		}// else if (PRIMARY_TERMWEIGHT.equals(TermWeight.DBPEDIA)) {
			// readDBPediaMappings(termFile);
		// }
		// TODO Convert this to produce PartialAssertions (mapText("Blah"))
		Collection<DisambiguatedTriple> disamTriples = disambiguateRelations(triples);
		return disamTriples;
	}

	private void readWikificationMappings() {
		try {
			String markup = wmi_.getMarkup(contextArticle_);
			String annotated = wmi_.annotate(markup, 0, true);
			Matcher m = WikiParser.ANCHOR_PARSER_WEIGHTED.matcher(annotated);
			while (m.find()) {
				String linkText = (m.group(2) != null) ? m.group(2) : m
						.group(1);
				textToArticleMappings_.put(
						linkText.toLowerCase(),
						new Pair<String, Double>(m.group(1), Double
								.parseDouble(m.group(3))));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Collection<DisambiguatedTriple> filterTriples(
			Collection<DisambiguatedTriple> disamTriples) {
		return disamTriples
				.stream()
				.filter(t -> t.getDomain().getDisambiguated() != null
						|| t.getRange().getDisambiguated() != null)
				.collect(toList());
	}

	/**
	 * Groups the triples into families, ordered by singular textual terms.
	 *
	 * @param filteredTriples
	 *            The triples to group.
	 * @return A MultiMap of triples, grouped by term.
	 */
	public MultiMap<String, DisambiguatedTriple> groupFamilies(
			Collection<DisambiguatedTriple> filteredTriples) {
		MultiMap<String, DisambiguatedTriple> familyMap = MultiMap
				.createListMultiMap();
		for (DisambiguatedTriple dt : filteredTriples) {
			familyMap.put(dt.getDomain().getText(), dt);
			familyMap.put(dt.getRange().getText(), dt);
		}
		return familyMap;
	}

	/**
	 * The primary method for processing triples.
	 * 
	 * @param triples
	 *            The input triples to process.
	 * @throws IOException
	 */
	public void processTriples(File terms, File triples) throws Exception {
		// Convert triples to Object-based disambiguated triples.
		Collection<DisambiguatedTriple> tripleFormat = convertToDisamTriples(triples);

		// Convert the domain and range of triples into sets of articles.
		Collection<DisambiguatedTriple> disamTriples = disambiguateTriples(
				tripleFormat, terms);

		// Filter out empty set triples (both domain and range are empty).
		Collection<DisambiguatedTriple> filteredTriples = filterTriples(disamTriples);

		// Infer the relation constraints for relations

		// Organise triples into family maps
		MultiMap<String, DisambiguatedTriple> familyTriples = groupFamilies(filteredTriples);

		// Check individual family consistency
		familyTriples = checkFamilyConsistency(familyTriples);

		// Check global consistency
		Collection<DisambiguatedTriple> finalTriples = checkGlobalConsistency(familyTriples);

		DisambiguatedTopic.printTerms(
				new File(getContextArticleTitle() + TERMS), wmi_, mapper_);
	}

	public static void main(String[] args) {
		String contextArticle = args[0];
		File triples = new File(args[1]);
		File terms = new File(args[2]);
		ResourceAccess.newInstance(2426);
		KnowledgeMiner km = KnowledgeMiner.newInstance("Enwiki_20110722");
		TripleDisambiguator td = new TripleDisambiguator(contextArticle);
		try {
			td.processTriples(terms, triples);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
