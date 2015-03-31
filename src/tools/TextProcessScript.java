package tools;

import io.ResourceAccess;
import io.ontology.DAGSocket;
import io.resources.WMISocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import knowledgeMiner.KnowledgeMiner;
import knowledgeMiner.mapping.CycMapper;

import org.apache.commons.lang3.StringUtils;

import tools.util.ArticleMetrics;
import util.collection.HierarchicalWeightedSet;
import util.collection.WeightedSet;
import util.wikipedia.WikiAnnotation;
import cyc.CycConstants;
import cyc.OntologyConcept;

public class TextProcessScript {
	private static final int NUM_SAMPLES = 3;
	private static final int NUM_TEXT_FIELDS = 3;
	private static final Pattern VAR_PATTERN = Pattern
			.compile("\\?X/\"?(\\d+)\"?");
	private static Integer INTELLIGENT_AGENT_ART;
	private static Integer INTELLIGENT_AGENT_CYC;
	private CycMapper mapper_;
	private DAGSocket ontology_;
	private WMISocket wmi_;
	private Map<String, WeightedSet<Integer>> wikifyMap_;

	public TextProcessScript(int port, boolean wikify) {
		ResourceAccess.newInstance(port);
		KnowledgeMiner.newInstance("Enwiki_20110722");
		wmi_ = ResourceAccess.requestWMISocket();
		ontology_ = (DAGSocket) ResourceAccess.requestOntologySocket();
		mapper_ = new CycMapper();

		try {
			CycConstants.initialiseAssertions(ontology_);
			INTELLIGENT_AGENT_ART = wmi_.getArticleByTitle("Intelligent agent");
			INTELLIGENT_AGENT_CYC = ontology_.getConceptID("IntelligentAgent");
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (wikify) {
			try {
				wikifyMap_ = new HashMap<>();
				SortedSet<WikiAnnotation> topics = wmi_.getTopics(wmi_
						.getMarkup(INTELLIGENT_AGENT_ART));
				for (WikiAnnotation topic : topics) {
					WeightedSet<Integer> articles = new WeightedSet<>();
					articles.add(
							wmi_.getArticleByTitle(topic.getArticleName()),
							topic.getWeight());
					wikifyMap_.put(topic.getText(), articles);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void mapToCyc(String input, BufferedWriter out, boolean relation,
			Collection<Integer> relatedArticles) throws Exception {
		HierarchicalWeightedSet<OntologyConcept> results = null;
		if (relation)
			results = mapper_.mapRelationToPredicate(input, wmi_, ontology_);
		else
			results = mapper_.mapTextToCyc(input, false, false, true, true,
					wmi_, ontology_);

		WeightedSet<OntologyConcept> flattened = results.flattenHierarchy();
		for (OntologyConcept concept : flattened) {
			Object[] output = new Object[ArticleMetrics.values().length];
			Arrays.fill(output, "");
			output[ArticleMetrics.TEXT_TERM.ordinal()] = input;
			output[ArticleMetrics.CYC_MAPPING.ordinal()] = concept
					.getConceptName();
			output[ArticleMetrics.ARTICLE_PROB.ordinal()] = flattened
					.getWeight(concept) + "";
			output[ArticleMetrics.ARTICLE_PROB_CONTEXT.ordinal()] = output[ArticleMetrics.ARTICLE_PROB
					.ordinal()];

			// Find mapped wiki article
			String result = ontology_.query(null,
					CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID(),
					concept.getID(),
					CycConstants.WIKI_VERSION.getConceptName(), "?X");
			Matcher m = VAR_PATTERN.matcher(result);
			int linkedArt = -1;
			if (m.find()) {
				linkedArt = Integer.parseInt(m.group(1));
				if (linkedArt != -1) {
					output[ArticleMetrics.WIKI_ARTICLE.ordinal()] = wmi_
							.getPageTitle(linkedArt, true);

					// Relatedness calculations
					computeArticleWeights(linkedArt, output, relatedArticles);
				}
			}

			// Cyc relatedness
			String similarity = ontology_.command("similarity",
					INTELLIGENT_AGENT_CYC + " " + concept.getID(), true);
			output[ArticleMetrics.CYC_RELATEDNESS.ordinal()] = similarity;

			out.write(StringUtils.join(output, '\t') + "\n");
		}
		if (flattened.isEmpty())
			out.write(input + "\n");
	}

	private void mapToWikipedia(String input,
			Collection<Integer> relatedArticles, BufferedWriter out)
			throws Exception {
		String clean = input.replaceAll("_", " ");
		WeightedSet<Integer> articles = getWeightedArticleMappings(clean);
		if (articles == null || articles.isEmpty()) {
			out.write(input + "\n");
			return;
		}
		WeightedSet<Integer> contextArts = null;
		if (wikifyMap_ == null) {
			contextArts = WMISocket.singular(wmi_.getWeightedArticles(
					relatedArticles, 0, clean));
			contextArts.normaliseSumTo1();
		}

		Object[][] metrics = new Object[articles.size()][ArticleMetrics
				.values().length];
		int i = 0;
		for (Integer art : articles) {
			metrics[i][0] = input;
			metrics[i][1] = wmi_.getPageTitle(art, true);
			metrics[i][2] = "";

			// Mapped Cyc article
			String result = ontology_.query(null,
					CycConstants.SYNONYMOUS_EXTERNAL_CONCEPT.getID(), "?X",
					CycConstants.WIKI_VERSION.getConceptName(), "\"" + art
							+ "\"");
			Matcher m = VAR_PATTERN.matcher(result);
			int linkedCyc = -1;
			if (m.find()) {
				linkedCyc = Integer.parseInt(m.group(1));
				metrics[i][2] = ontology_.findConceptByID(linkedCyc);
			}

			// Art weights
			metrics[i][ArticleMetrics.ARTICLE_PROB.ordinal()] = articles
					.getWeight(art);
			if (wikifyMap_ == null)
				metrics[i][ArticleMetrics.ARTICLE_PROB_CONTEXT.ordinal()] = contextArts
						.getWeight(art);
			else
				metrics[i][ArticleMetrics.ARTICLE_PROB_CONTEXT.ordinal()] = metrics[i][ArticleMetrics.ARTICLE_PROB
						.ordinal()];

			// Relatedness
			computeArticleWeights(art, metrics[i], relatedArticles);

			// Cyc relatedness
			metrics[i][ArticleMetrics.CYC_RELATEDNESS.ordinal()] = 0d;
			if (linkedCyc != -1)
				metrics[i][ArticleMetrics.CYC_RELATEDNESS.ordinal()] = Double
						.parseDouble(ontology_.command("similarity",
								INTELLIGENT_AGENT_CYC + " " + linkedCyc, true));

			i++;
		}

		printTopXSamples(metrics, out);
	}

	protected void computeArticleWeights(Integer art, Object[] metrics,
			Collection<Integer> relatedArticles) throws IOException {
		// WMI relatedness
		metrics[ArticleMetrics.WMI_RELATEDNESS.ordinal()] = wmi_
				.getRelatednessPair(INTELLIGENT_AGENT_ART, art).get(0);

		// Wikirelatedness (family)
		List<Double> familyRelatedness = wmi_.getRelatednessList(art,
				relatedArticles.toArray(new Integer[relatedArticles.size()]));
		double avRel = 0;
		for (Double d : familyRelatedness)
			avRel += d;
		avRel /= familyRelatedness.size();
		metrics[ArticleMetrics.WMI_RELATEDNESS_FAMILY.ordinal()] = avRel;

		// Wikirelatedness (family reversed)
		Collection<Integer> artFamily = getRelated(art);
		familyRelatedness = wmi_.getRelatednessList(INTELLIGENT_AGENT_ART,
				artFamily.toArray(new Integer[artFamily.size()]));
		avRel = 0;
		for (Double d : familyRelatedness)
			avRel += d;
		avRel /= familyRelatedness.size();
		metrics[ArticleMetrics.WMI_RELATEDNESS_REVERSE_FAMILY.ordinal()] = avRel;
	}

	protected WeightedSet<Integer> getWeightedArticleMappings(String textInput)
			throws IOException {
		if (wikifyMap_ != null)
			return wikifyMap_.get(textInput);
		return wmi_.getWeightedArticles(textInput);
	}

	protected void printTopXSamples(Object[][] metrics, BufferedWriter out)
			throws IOException {
		// Print out top threes
		Map<String, Object[]> samples = new HashMap<>();
		for (sortIndex_ = NUM_TEXT_FIELDS; sortIndex_ < ArticleMetrics.values().length; sortIndex_++) {
			Arrays.sort(metrics, new Comparator<Object[]>() {
				@Override
				public int compare(Object[] o1, Object[] o2) {
					return Double.compare((Double) o1[sortIndex_],
							(Double) o2[sortIndex_]);
				}
			});

			// Take the X highest values
			for (int j = 0; j < NUM_SAMPLES; j++) {
				// TODO Take the top three, storing them in a map
				int index = metrics.length - 1 - j;
				if (index < 0)
					break;
				samples.put((String) metrics[index][1], metrics[index]);
			}
		}

		// Print the map
		for (Map.Entry<String, Object[]> entry : samples.entrySet()) {
			out.write(StringUtils.join(entry.getValue(), '\t') + "\n");
		}
	}

	private int sortIndex_;

	public void processFile(File inputFile, boolean relation, boolean wikiBased)
			throws Exception {
		BufferedReader in = new BufferedReader(new FileReader(inputFile));
		BufferedWriter out = null;
		if (wikiBased)
			out = new BufferedWriter(new FileWriter(new File("WIKI"
					+ inputFile.getName())));
		else
			out = new BufferedWriter(new FileWriter(new File("CYC"
					+ inputFile.getName())));
		out.write(StringUtils.join(ArticleMetrics.values(), '\t').toLowerCase()
				+ "\n");

		Collection<Integer> relatedArticles = getRelated(INTELLIGENT_AGENT_ART);

		String input = null;
		while ((input = in.readLine()) != null) {
			if (input.isEmpty())
				continue;

			// Map to Wikipedia
			if (wikiBased)
				mapToWikipedia(input, relatedArticles, out);
			else
				mapToCyc(input, out, relation, relatedArticles);
		}

		out.close();
		in.close();
	}

	protected Collection<Integer> getRelated(Integer centreArticle)
			throws IOException {
		Collection<Integer> relatedArticles = wmi_.getOutLinks(centreArticle);
		// relatedArticles.addAll(wmi_.getOutLinks(centreArticle));
		return relatedArticles;
	}

	public static void main(String[] args) {
		int port = -1;
		File inputFile = null;
		boolean relation = false;
		boolean wikify = false;
		boolean cycBased = false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-p"))
				port = Integer.parseInt(args[++i]);
			else if (args[i].equals("-r"))
				relation = true;
			else if (args[i].equals("-c"))
				cycBased = true;
			else if (args[i].equals("-w"))
				wikify = true;
			else
				inputFile = new File(args[i]);
		}
		try {
			TextProcessScript tps = new TextProcessScript(port, wikify);
			tps.processFile(inputFile, relation, !cycBased);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
