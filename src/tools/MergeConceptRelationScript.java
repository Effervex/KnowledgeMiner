package tools;

import graph.core.CommonConcepts;
import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import knowledgeMiner.KnowledgeMiner;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import tools.util.ArticleMetrics;
import tools.util.TripleMetrics;
import util.collection.MultiMap;

public class MergeConceptRelationScript {
	private static final String[] DUMMY_STRING = new String[] { "dummy" };
	private MultiMap<String, String[]> conceptData_;
	private WMISocket wmi_;
	private OntologySocket ontology_;

	public MergeConceptRelationScript(int port) {
		ResourceAccess.newInstance(port);
		KnowledgeMiner.newInstance("Enwiki_20110722");
		ontology_ = ResourceAccess.requestOntologySocket();
		wmi_ = ResourceAccess.requestWMISocket();
	}

	public static void main(String[] args) {
		int port = -1;
		File conceptFile = null;
		File tripleFile = null;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-p"))
				port = Integer.parseInt(args[++i]);
			else if (conceptFile == null)
				conceptFile = new File(args[i]);
			else if (tripleFile == null)
				tripleFile = new File(args[i]);
		}
		try {
			MergeConceptRelationScript mcrs = new MergeConceptRelationScript(
					port);
			mcrs.processFile(conceptFile, tripleFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void processFile(File conceptFile, File tripleFile) throws Exception {
		conceptData_ = readConcepts(conceptFile);

		// Process each relation triple
		processRelations(tripleFile);
	}

	protected void processRelations(File tripleFile)
			throws FileNotFoundException, IOException, IllegalAccessException {
		BufferedReader in = new BufferedReader(new FileReader(tripleFile));
		BufferedWriter out = new BufferedWriter(new FileWriter("WIKI"
				+ tripleFile.getName()));
		out.write(WordUtils.capitalize(StringUtils.join(TripleMetrics.values(),
				'\t').toLowerCase())
				+ "\n");

		String input = null;
		while ((input = in.readLine()) != null) {
			String[] output = new String[TripleMetrics.values().length];
			Arrays.fill(output, "");
			String[] split = input.split("\t");
			// Basic constant text domain and range
			output[TripleMetrics.DOMAIN_TEXT.ordinal()] = split[2];
			output[TripleMetrics.RANGE_TEXT.ordinal()] = split[3];
			output[TripleMetrics.RELATION.ordinal()] = split[4];
			output[TripleMetrics.TEXT_TRIPLE.ordinal()] = split[2] + "#"
					+ split[4] + "#" + split[3];

			// Get domains
			List<String[]> domains = conceptData_
					.getList(output[TripleMetrics.DOMAIN_TEXT.ordinal()]);
			// Ensure non-empty (add dummy)
			if (domains == null)
				domains = new ArrayList<>();
			if (domains.isEmpty())
				domains.add(DUMMY_STRING);

			// Get ranges
			List<String[]> ranges = conceptData_
					.getList(output[TripleMetrics.RANGE_TEXT.ordinal()]);
			// Ensure non-empty (add dummy)
			if (ranges == null)
				ranges = new ArrayList<>();
			if (ranges.isEmpty())
				ranges.add(DUMMY_STRING);

			// Iterate through domains and ranges, adding output lines
			for (String[] domainArt : domains) {
				String[] domainClone = Arrays.copyOf(output, output.length);
				if (domainArt.length < ArticleMetrics.values().length)
					domainArt = DUMMY_STRING;
				if (domainArt != DUMMY_STRING) {
					processKnownConcept(domainArt, "DOMAIN", domainClone);
				}

				for (String[] rangeArt : ranges) {
					String[] rangeClone = Arrays.copyOf(domainClone,
							domainClone.length);
					if (rangeArt.length < ArticleMetrics.values().length)
						rangeArt = DUMMY_STRING;
					if (rangeArt != DUMMY_STRING) {
						processKnownConcept(rangeArt, "RANGE", rangeClone);

						if (domainArt != DUMMY_STRING) {
							processKnownDomainRange(rangeClone);
						}
					}

					out.write(StringUtils.join(rangeClone, '\t') + "\n");
				}
			}
		}

		in.close();
		out.close();
	}

	/**
	 * Processes a single concept, noting the relevant metrics.
	 * 
	 * @param conceptSplit
	 *            The concept details.
	 * @param type
	 *            The type of concept.
	 * @param outputArray
	 *            The output array.
	 */
	protected void processKnownConcept(String[] conceptSplit, String type,
			String[] outputArray) {
		// Output solo topic statistics
		outputArray[TripleMetrics.valueOf(type + "_ARTICLE").ordinal()] = conceptSplit[ArticleMetrics.WIKI_ARTICLE
				.ordinal()];
		outputArray[TripleMetrics.valueOf(type + "_CYC_MAPPED").ordinal()] = conceptSplit[ArticleMetrics.CYC_MAPPING
				.ordinal()];
		outputArray[TripleMetrics.valueOf(type + "_WEIGHT_FAMILY_CONTEXT")
				.ordinal()] = conceptSplit[ArticleMetrics.WMI_RELATEDNESS_FAMILY
				.ordinal()];
		outputArray[TripleMetrics.valueOf(type + "_WEIGHT_FREQ_CONTEXT")
				.ordinal()] = conceptSplit[ArticleMetrics.ARTICLE_PROB_CONTEXT
				.ordinal()];
	}

	/**
	 * Takes measurements when the domain and range are mapped to articles.
	 * 
	 * @param outputArray
	 *            The output array
	 * @throws IOException
	 */
	protected void processKnownDomainRange(String[] outputArray)
			throws IOException {
		// Output shared stats
		int domID = wmi_
				.getArticleByTitle(outputArray[TripleMetrics.DOMAIN_ARTICLE
						.ordinal()]);
		int rangeID = wmi_
				.getArticleByTitle(outputArray[TripleMetrics.RANGE_ARTICLE
						.ordinal()]);
		// Direct relatedness
		outputArray[TripleMetrics.DOMAIN_RANGE_RELATEDNESS.ordinal()] = wmi_
				.getRelatednessList(domID, rangeID).get(0) + "";

		// Family-based relatedness
		Collection<Integer> domOutlinks = wmi_.getOutLinks(domID);
		Collection<Integer> ranOutlinks = wmi_.getOutLinks(rangeID);

		List<Double> relatedness = wmi_.getRelatednessList(rangeID,
				domOutlinks.toArray(new Integer[domOutlinks.size()]));
		double sum = 0;
		for (Double d : relatedness)
			sum += d;
		sum /= relatedness.size();
		outputArray[TripleMetrics.DOMAIN_RANGE_RELATEDNESS_DOM_CONTEXT
				.ordinal()] = sum + "";

		relatedness = wmi_.getRelatednessList(domID,
				ranOutlinks.toArray(new Integer[ranOutlinks.size()]));
		sum = 0;
		for (Double d : relatedness)
			sum += d;
		sum /= relatedness.size();
		outputArray[TripleMetrics.DOMAIN_RANGE_RELATEDNESS_RAN_CONTEXT
				.ordinal()] = sum + "";

		// Check Cyc validity if possible
		if (!outputArray[TripleMetrics.DOMAIN_CYC_MAPPED.ordinal()].isEmpty()
				&& !outputArray[TripleMetrics.RANGE_CYC_MAPPED.ordinal()]
						.isEmpty()
				&& outputArray[TripleMetrics.RELATION.ordinal()].equals("is_a")) {
			int edgeID = ontology_.assertToOntology(null,
					CommonConcepts.GENLS.getID(),
					outputArray[TripleMetrics.DOMAIN_CYC_MAPPED.ordinal()],
					outputArray[TripleMetrics.RANGE_CYC_MAPPED.ordinal()]);
			if (edgeID == -1) {
				// Try with isa
				edgeID = ontology_.assertToOntology(null,
						CommonConcepts.ISA.getID(),
						outputArray[TripleMetrics.DOMAIN_CYC_MAPPED.ordinal()],
						outputArray[TripleMetrics.RANGE_CYC_MAPPED.ordinal()]);
				if (edgeID == -1) {
					outputArray[TripleMetrics.VALID_CYC.ordinal()] = "F";
					return;
				}
			}

			ontology_.unassert(null, edgeID);
			outputArray[TripleMetrics.VALID_CYC.ordinal()] = "T";
		}
	}

	private MultiMap<String, String[]> readConcepts(File conceptFile)
			throws Exception {
		MultiMap<String, String[]> conceptMap = MultiMap.createListMultiMap();
		BufferedReader in = new BufferedReader(new FileReader(conceptFile));
		// Skip the header
		in.readLine();

		String input = null;
		while ((input = in.readLine()) != null) {
			String[] split = input.split("\t");
			conceptMap.put(split[ArticleMetrics.TEXT_TERM.ordinal()], split);
		}

		in.close();
		return conceptMap;
	}
}
