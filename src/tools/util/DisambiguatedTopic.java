package tools.util;

import io.ontology.OntologySocket;
import io.resources.WMISocket;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import knowledgeMiner.mapping.CycMapper;
import knowledgeMiner.mining.wikipedia.WikipediaMappedConcept;
import tools.TripleDisambiguator;
import util.Pair;
import util.collection.WeightedSet;
import util.text.TermWeight;
import cyc.OntologyConcept;

/**
 * A class for representing disambiguated text in a wrapped object, with
 * disambiguations and mappings included. Created via a factory such that each
 * member is individual.
 *
 * @author Sam Sarjant
 */
public class DisambiguatedTopic {
	/** The uniquely keyed instances. */
	private static Map<String, DisambiguatedTopic> instanceMap_ = new HashMap<>();

	private static final byte TYPE_WEIGHTED_ARTICLES = 2;

	public static final byte TYPE_ARTICLE = 1;

	private static final byte TYPE_CONCEPT = 3;

	private static final OntologyConcept NO_MAPPING = new OntologyConcept(
			"NO_MAPPING");

	private Object disambiguation_;
	private byte disamType_;
	private OntologyConcept mappedConcept_;

	private OntologySocket ontology_;

	private String text_;

	private WMISocket wmi_;

	private boolean isCreated_ = false;

	private double disamWeight_;

	private DisambiguatedTopic(String text) {
		text_ = text;
	}

	/**
	 * Finds disambiguations via the outlinks family-relatedness method.
	 * 
	 * @param cleanText
	 *            The text to use for outlink search.
	 * @param wmi
	 *            The WMI access.
	 * @param parent
	 *            The parent access.
	 */
	private double disambiguateViaOutlinks(String cleanText,
			TripleDisambiguator parent, WMISocket wmi) throws IOException {
		WeightedSet<Integer> articles = wmi.getWeightedArticles(cleanText);
		WeightedSet<Integer> outlinkWeights = new WeightedSet<Integer>();
		for (Integer art : articles) {
			List<Double> familyRelatedness = wmi
					.getRelatednessList(
							art,
							parent.relatedArticles_
									.toArray(new Integer[parent.relatedArticles_
											.size()]));
			double avRel = 0;
			for (Double d : familyRelatedness)
				avRel += d;
			avRel /= familyRelatedness.size();
			outlinkWeights.add(art, avRel);
		}
		disambiguation_ = outlinkWeights;
		disamType_ = TYPE_WEIGHTED_ARTICLES;
		return 1;
	}

	/**
	 * Disambiguates this term using the given disambiguation strategy.
	 *
	 * @param termStrategy
	 *            The strategy for disambiguation.
	 * @param textToArticleMappings_
	 * @throws IOException
	 */
	public Object disambiguate(TermWeight termStrategy,
			TripleDisambiguator parent, WMISocket wmi) throws IOException {
		String cleanText = text_.replaceAll("_", " ").trim();
		switch (termStrategy) {
		case DBPEDIA:
		case WIKIFICATION:
			Pair<String, Double> weightedArticle = parent.textToArticleMappings_
					.get(cleanText);
			if (weightedArticle != null) {
				disambiguation_ = weightedArticle.objA_;
				disamWeight_ = weightedArticle.objB_;
				disamType_ = TYPE_ARTICLE;
			}
			break;
		case OUTLINKS:
			return disambiguateViaOutlinks(cleanText, parent, wmi);
		case FREQ_WEIGHTED:
			WeightedSet<Integer> freqArticles = wmi.getWeightedArticles(
					cleanText, 0, parent.relatedArticles_);
			if (freqArticles != null && !freqArticles.isEmpty()) {
				disambiguation_ = freqArticles;
				disamType_ = TYPE_WEIGHTED_ARTICLES;
			}
			break;
		}
		return disambiguation_;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DisambiguatedTopic other = (DisambiguatedTopic) obj;
		if (text_ == null) {
			if (other.text_ != null)
				return false;
		} else if (!text_.equals(other.text_))
			return false;
		return true;
	}

	public OntologyConcept getConcept(CycMapper mapper) throws IOException {
		if (mappedConcept_ == null) {
			mappedConcept_ = getMappedOntology(getDisambiguated(), mapper,
					wmi_, ontology_);
			if (mappedConcept_ == null)
				mappedConcept_ = NO_MAPPING;
		}
		if (mappedConcept_ == NO_MAPPING)
			return null;
		return mappedConcept_;
	}

	@SuppressWarnings("unchecked")
	private OntologyConcept getMappedOntology(Object object, CycMapper mapper,
			WMISocket wmi, OntologySocket ontology) throws IOException {
		if (object == null)
			return null;
		if (disamType_ == TYPE_CONCEPT)
			return (OntologyConcept) disambiguation_;
		int article = getArticle();
		if (article == -1)
			return null;

		WikipediaMappedConcept wmC = new WikipediaMappedConcept(article);
		WeightedSet<OntologyConcept> mapped = wmC.mapThing(mapper, wmi,
				ontology);
		if (mapped.isEmpty())
			return null;
		return mapped.getOrdered().first();
	}

	public Object getDisambiguated() {
		return disambiguation_;
	}

	public String getText() {
		return text_;
	}

	public double getWeight(int i) {
		if (disamType_ == TYPE_ARTICLE || disamType_ == TYPE_CONCEPT)
			return disamWeight_;
		else if (disamType_ == TYPE_WEIGHTED_ARTICLES) {
			WeightedSet<Integer> disam = (WeightedSet<Integer>) disambiguation_;
			return disam.getWeight(disam.iterator().next());
		}
		return -1;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((text_ == null) ? 0 : text_.hashCode());
		return result;
	}

	public void setConcept(OntologyConcept concept) {
		mappedConcept_ = concept;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean rawText) {
		if (rawText || disambiguation_ == null)
			return text_;
		if (disamType_ == TYPE_CONCEPT)
			return disambiguation_.toString() + " (NEW)";
		if (disamType_ == TYPE_ARTICLE)
			return disambiguation_.toString();

		int article = getArticle();
		try {
			if (article != -1)
				return wmi_.getPageTitle(article, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * The only method for creating a new DisambiguatedTopic.
	 *
	 * @param text
	 *            The text to create/get the topic for.
	 */
	public static DisambiguatedTopic getCreateDisambiguatedTopic(String text,
			WMISocket wmi, OntologySocket ontology) {
		DisambiguatedTopic dt = instanceMap_.get(text);
		if (dt == null) {
			dt = new DisambiguatedTopic(text);
			dt.wmi_ = wmi;
			dt.ontology_ = ontology;
			instanceMap_.put(text, dt);
		}
		return dt;
	}

	public void markCreated(OntologyConcept createdConcept) {
		isCreated_ = true;
		mappedConcept_ = createdConcept;
		disambiguation_ = createdConcept;
		disamType_ = TYPE_CONCEPT;
		disamWeight_ = 1;
	}

	public boolean isCreated() {
		return isCreated_;
	}

	public static void printTerms(File termFile, WMISocket wmi, CycMapper mapper) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(termFile));

			// Write the header
			out.write("Text\tDisambiguated\tCYC\tCreated?\n");

			// Write every term and its mappings
			for (Map.Entry<String, DisambiguatedTopic> entry : instanceMap_
					.entrySet()) {
				DisambiguatedTopic disTerm = entry.getValue();
				out.write(disTerm.getText() + "\t");
				if (disTerm.disamType_ == TYPE_ARTICLE)
					out.write(disTerm.getDisambiguated().toString());
				else if (disTerm.disamType_ == TYPE_CONCEPT)
					out.write(disTerm.getDisambiguated().toString());
				else if (disTerm.disamType_ == TYPE_WEIGHTED_ARTICLES)
					out.write(wmi.getPageTitle(((WeightedSet<Integer>) disTerm
							.getDisambiguated()).iterator().next(), true));
				out.write("\t" + disTerm.getConcept(mapper) + "\t");
				if (disTerm.isCreated())
					out.write("T");
				else
					out.write("F");
				out.write("\n");
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getArticle() {
		if (disamType_ == TYPE_ARTICLE)
			try {
				return wmi_.getArticleByTitle((String) disambiguation_);
			} catch (IOException e) {
				e.printStackTrace();
			}
		else if (disamType_ == TYPE_WEIGHTED_ARTICLES)
			return ((WeightedSet<Integer>) disambiguation_).iterator().next();
		return -1;
	}

	public void setDisambiguation(String artName, double weight) {
		disambiguation_ = artName;
		disamWeight_ = weight;
		disamType_ = TYPE_ARTICLE;
	}
}
