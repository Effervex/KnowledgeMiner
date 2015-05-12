package tools;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import graph.module.NLPToStringModule;
import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.resources.WMISocket;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.ConceptModule;
import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.textToCyc.TextMappedConcept;
import knowledgeMiner.mapping.wikiToCyc.WikipediaMappedConcept;
import knowledgeMiner.mining.CycMiner;
import knowledgeMiner.mining.MinedInformation;
import knowledgeMiner.mining.PartialAssertion;
import knowledgeMiner.mining.SentenceParserHeuristic;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import tools.util.DisambiguatedTriple;
import util.text.OpenNLP;
import util.text.StanfordNLP;
import util.wikipedia.WikiParser;
import cyc.AssertionArgument;
import cyc.CycConstants;
import cyc.OntologyConcept;

public class ConvertToAssignment {
	private static final Pattern SQUASHED_PATTERN = Pattern
			.compile("([a-z,.])(?=[A-Z(])");
	private static final Pattern PROVENANCE_PATTERN = Pattern
			.compile("^([^+]+)\\+(a )?(.+)$");
	private static final Pattern FUNCTION_PATTERN = Pattern
			.compile("^\\(\\S+ (.+?)\\)$");

	public ConvertToAssignment() {
	}

	public static void main(String[] args) {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ConvertToAssignment cta = new ConvertToAssignment();
		File rootFolder = new File(args[0]);
		if (!rootFolder.exists()) {
			System.out.println("Root folder could not be found");
			System.exit(1);
		}
		cta.execute(rootFolder);
	}

	/**
	 * Converts Amal's OntoCMaps data into assignment data
	 *
	 * @param rootFolder
	 *            The folder containing the Topics and Entities folders,
	 *            themselves containing folder of the extractions.
	 */
	public void execute(File rootFolder) {
		// Topics and Entities
		File[] artFolders = rootFolder.listFiles();
		ExecutorService executor = Executors.newFixedThreadPool(Runtime
				.getRuntime().availableProcessors());
		WMISocket wmi = ResourceAccess.requestWMISocket();
		for (File artFolder : artFolders) {
			// First check if the article can be disambiguated
			String artName = convertArtName(artFolder.getName(), wmi);
			if (artName == null)
				continue;

			Runnable worker = new ConvertFile(artFolder, artName);
			executor.execute(worker);
		}
		executor.shutdown();
		try {
			executor.awaitTermination(24, TimeUnit.HOURS);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Converts the squashed article name into a fully expanded art name.
	 *
	 * @param name
	 *            The name to expand.
	 * @return The name of the article
	 */
	private String convertArtName(String name, WMISocket wmi) {
		// Search directly
		try {
			int artId = wmi.getArticleByTitle(name);
			if (artId != -1)
				return name;
		} catch (IOException e) {
			e.printStackTrace();
		}

		Matcher m = SQUASHED_PATTERN.matcher(name);
		if (m.find()) {
			String artName = m.replaceAll("$1 ");
			String lowerName = StringUtils.capitalize(artName.toLowerCase());
			try {
				List<Integer> arts = wmi.getArticleByTitle(artName, lowerName);
				if (arts.get(0) != -1)
					return artName;
				if (arts.get(1) != -1)
					return lowerName;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.err.println("Could not parse article: " + name);

		return null;
	}

	private class ConvertFile implements Runnable {
		private File folder_;
		private Collection<DisambiguatedTriple> triples_;
		private String artName_;
		private TripleDisambiguator tripleDisam_;

		private DAGSocket ontology_;
		private WMISocket wmi_;

		public ConvertFile(File artFolder, String artName) {
			folder_ = artFolder;
			artName_ = artName;
			triples_ = new ArrayList<>();
		}

		@Override
		public void run() {
			ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
			wmi_ = ResourceAccess.requestWMISocket();
			tripleDisam_ = new TripleDisambiguator(artName_);
			try {
				// Open Amal's file, store appropriate triples, and disambiguate
				// others.
				recordAmalsData();

				// Process article with KM heuristics
				recordKMData();

				// Write the final file
				writeAssignmentFile(artName_);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * Extracts Amal's outputs and disambiguates them, recording the
		 * results.
		 * 
		 * @throws Exception
		 */
		private void recordAmalsData() throws Exception {
			for (File f : folder_.listFiles()) {
				// Find the xls file
				if (f.getName().endsWith(".xls")
						&& f.getName().startsWith("Extractions")) {
					Collection<DisambiguatedTriple> allTriples = tripleDisam_
							.parseXLSSheet(f);
					// Remove the non-subject triples
					Collection<DisambiguatedTriple> subjTriples = new ArrayList<>();
					for (DisambiguatedTriple dt : allTriples) {
						if (correctSubject(dt.getDomain().getText())) {
							subjTriples.add(dt);
						}
					}

					triples_ = tripleDisam_.disambiguateTriples(subjTriples,
							null);
				}
			}
		}

		/**
		 * Checks if the text triple uses the correct subject.
		 *
		 * @param subjectValue
		 *            The string in the subject column.
		 * @return True if the string is (probably) useful, false otherwise.
		 */
		private boolean correctSubject(String subjectValue) {
			String lowerArt = artName_.toLowerCase();
			String noContext = (lowerArt.contains("(")) ? artName_.replaceAll(
					"\\(.+?\\)", "") : lowerArt;
			String lowerSubj = subjectValue.replaceAll("_", " ").toLowerCase();
			String[] brokenSubj = lowerSubj.split("\\s+");

			// If A is substring of B or vice versa
			if (lowerArt.contains(lowerSubj))
				return true;
			// Split subs
			if (brokenSubj.length > 1) {
				boolean contained = true;
				for (String broken : brokenSubj) {
					if (!lowerArt.contains(broken)) {
						contained = false;
						break;
					}
				}
				if (contained)
					return true;
			}

			// Art in lower
			if (lowerSubj.contains(noContext))
				return true;
			return false;
		}

		/**
		 * Runs KMs mining heuristics on the article, recording the raw text
		 * information and the disambiguated information.
		 * 
		 * @throws Exception
		 *             Should something go awry...
		 */
		private void recordKMData() throws Exception {
			int artID = wmi_.getArticleByTitle(artName_);
			CycMiner miner = KnowledgeMiner.getInstance().getMiner();
			ConceptModule cm = new ConceptModule(artID);
			miner.mineArticle(cm, MinedInformation.ALL_TYPES, wmi_, ontology_);
			// Record the partial assertions as Disambiguated Triples
			for (PartialAssertion assertion : cm.getAssertions()) {
				// Ignore comments
				if (assertion
						.getRelation()
						.toPrettyString()
						.equals(CycConstants.WIKIPEDIA_COMMENT.getConceptName())
						|| assertion.getRelation().toPrettyString()
								.equals(CycConstants.COMMENT.getConceptName()))
					continue;

				// Parse a relation from provenance if possible
				String provenanceDetails = assertion.getProvenance()
						.getDetails();
				String relation = null;
				String range = null;
				if (provenanceDetails != null && !provenanceDetails.isEmpty()) {
					Matcher m = PROVENANCE_PATTERN.matcher(provenanceDetails);
					if (m.find()) {
						relation = (m.group(2) != null) ? m.group(1) + " "
								+ m.group(2) : m.group(1);
						relation = relation.trim();
						range = m.group(3);
					}
				}

				// Figure out the value for the range.
				AssertionArgument aaRange = assertion.getArgs()[1];
				String disamRange = null;
				if (aaRange instanceof TextMappedConcept) {
					String mapText = ((TextMappedConcept) aaRange).getText();
					if (range == null)
						range = mapText;
					Matcher m = WikiParser.ANCHOR_PARSER.matcher(mapText);
					if (m.matches())
						disamRange = m.group(1);
				} else if (aaRange instanceof WikipediaMappedConcept) {
					String pageTitle = wmi_.getPageTitle(
							((WikipediaMappedConcept) aaRange).getArticle(),
							true);
					if (range == null)
						range = pageTitle;
					disamRange = pageTitle;
				}
				if (range == null) {
					range = aaRange.toPrettyString();
					if (aaRange instanceof OntologyConcept) {
						Matcher m = FUNCTION_PATTERN.matcher(aaRange
								.toPrettyString());
						if (m.matches()) {
							range = m.group(1);
							if (range.startsWith("'"))
								range = range.substring(1);
							disamRange = range;
						}
					}
				}
				range = WikiParser.cleanAllMarkup(range);
				range = range.replaceAll("\"", "");
				range = range.replaceAll("\\s*\\n\\s*", " ");
				if (disamRange != null)
					disamRange = disamRange.replaceAll("\\s*\\n\\s*", " ");

				// Appropriate relation name
				if (relation == null) {
					AssertionArgument relationArg = assertion.getRelation();
					if (relationArg instanceof TextMappedConcept)
						relation = ((TextMappedConcept) relationArg).getText();
					else if (relationArg.equals(CycConstants.ISA_GENLS
							.getConcept()))
						relation = "is a";
					else if (relationArg.equals(CycConstants.SYNONYM_RELATION
							.getConcept())
							|| relationArg
									.equals(CycConstants.SYNONYM_RELATION_CANONICAL
											.getConcept()))
						relation = "synonym";
					else
						relation = NLPToStringModule.conceptToPlainText(
								assertion.getRelation().toPrettyString())
								.toLowerCase();
				} else if (SentenceParserHeuristic.isCopula(relation))
					relation = "is a";

				// Create the triple
				relation = relation.replaceAll("\\s+", "_");
				DisambiguatedTriple dt = new DisambiguatedTriple(relation,
						artName_.toLowerCase(), range.toLowerCase());

				// Add disambiguation data
				if (disamRange != null)
					dt.getRange().setDisambiguation(disamRange,
							assertion.getWeight());
				if (!triples_.contains(dt))
					triples_.add(dt);
			}
		}

		/**
		 * Writes the assignment file based on the information collected.
		 * 
		 * @param artName
		 *            The name of the Wikipedia article
		 * @throws IOException
		 */
		private void writeAssignmentFile(String artName) throws IOException {
			// CSV initialisation
			File outFile = new File(folder_, folder_.getName() + ".csv");
			outFile.createNewFile();
			BufferedWriter csvOut = new BufferedWriter(new FileWriter(outFile));

			// XLS initialisation
			File xlsFile = new File(folder_, folder_.getName() + ".xls");
			xlsFile.createNewFile();
			FileOutputStream xlsOut = new FileOutputStream(xlsFile);
			Workbook wb = new HSSFWorkbook();

			// Preamble
			csvOut.write("Wikipedia article:," + artName + "\n");
			csvOut.write("\n");

			// Extractions
			Sheet s1 = wb.createSheet("Part 1 EXTRACTIONS");
			writeRow(s1, 0, csvOut, "TASK 1a", "EXTRACTIONS");
			csvOut.write(",,,,,");
			Sheet s2 = wb.createSheet("Part 2 DISAMBIGUATIONS");
			writeRow(s2, 0, csvOut, "TASK 2a", "DISAMBIGUATIONS");
			csvOut.write("\n");
			writeRow(s1, 1, csvOut, "First arg", "Relation", "Second arg",
					"Is correct?", "Reason?");
			csvOut.write(",,");
			writeRow(s2, 1, csvOut, "First disambiguation", "Relation",
					"Second disambiguation", "Is second correct?", "Comments");
			csvOut.write("\n");

			// Output all the triples
			int rowNum = 2;
			for (DisambiguatedTriple triple : triples_) {
				String domain = triple.getDomain().getText()
						.replaceAll(",", "").replaceAll("_", " ");
				domain = OpenNLP.lemmatise(domain, false);
				String relation = triple.getRelation().getText()
						.replaceAll(",", "").replaceAll("_", " ");
				String range = triple.getRange().getText().replaceAll(",", "")
						.replaceAll("_", " ");
				range = OpenNLP.lemmatise(range, false);
				writeRow(s1, rowNum, csvOut, domain, relation, range);
				csvOut.write(",,,,");

				// Disam line
				String disamStr = null;
				Object disam = triple.getRange().getDisambiguated();
				if (disam != null && !disam.toString().startsWith("\"")
						&& !StringUtils.isNumeric(disam.toString())) {
					disamStr = "[[" + triple.getRange().getDisambiguated()
							+ "]]";
				} else
					disamStr = "'"
							+ triple.getRange().getText().replaceAll(",", "")
							+ "'";
				writeRow(s2, rowNum, csvOut, "[[" + artName_ + "]]", triple
						.getRelation().getText(), disamStr);
				csvOut.write("\n");
				rowNum++;
			}
			csvOut.write("\n");

			// Your extractions
			s1.createRow(rowNum);
			s2.createRow(rowNum++);
			writeRow(s1, rowNum, csvOut, "TASK 1b", "YOUR EXTRACTIONS");
			csvOut.write(",,,,,");
			writeRow(s2, rowNum++, csvOut, "TASK 2b", "YOUR DISAMBIGUATIONS");
			csvOut.write("\n");
			writeRow(s1, rowNum, csvOut, "First arg", "Relation", "Second arg",
					"Reason?");
			csvOut.write(",,,");
			writeRow(s2, rowNum++, csvOut, "First disambiguation", "Relation",
					"Second disambiguation", "Comments");
			csvOut.write("\n");
			for (int i = 0; i < 5; i++) {
				writeRow(s1, rowNum, csvOut, "<Enter triple #" + (i + 1) + ">");
				csvOut.write(",,,,,,");
				writeRow(s2, rowNum++, csvOut, "<Enter disambiguation #"
						+ (i + 1) + ">");
				csvOut.write("\n");
			}
			csvOut.write("\n");

			// Alternative source
			s1.createRow(rowNum++);
			writeRow(s1, rowNum++, csvOut, "TASK 1c", "ALTERNATIVE SOURCE");
			csvOut.write("\n");
			writeRow(s1, rowNum++, csvOut, "<Enter URL>");
			csvOut.write("\n");

			wb.write(xlsOut);
			csvOut.close();
			xlsOut.close();
			wb.close();
		}

		/**
		 * Writes a series of values to a row in a sheet. Also simultaneously
		 * writes to an output csv file, splitting each value by comma.
		 *
		 * @param s
		 *            The sheet to create the row for.
		 * @param rowNum
		 *            The number of the row being created.
		 * @param values
		 *            The values to add to the row.
		 * @throws IOException
		 *             Should something go awry...
		 */
		private void writeRow(Sheet s, int rowNum, BufferedWriter csvOut,
				String... values) throws IOException {
			Row r = s.createRow(rowNum);
			for (int i = 0; i < values.length; i++) {
				Cell c = r.createCell(i);
				c.setCellValue(values[i]);
				if (i != 0)
					csvOut.write(",");
				csvOut.write(values[i]);
			}
		}

	}
}
