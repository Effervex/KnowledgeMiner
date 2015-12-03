package tools;

import io.ResourceAccess;
import io.ontology.OntologySocket;
import io.resources.WikipediaSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

import cyc.OntologyConcept;
import knowledgeMiner.KnowledgeMiner;

public class SalienceCreator {
	private static final File RAND_ARTS = new File("articleListRand.txt");
	private static final File OUT_FOLDER = new File("outlinks");
	private static final String TEXT = "text";
	private int threshold_;
	private int numFiles_;
	private WikipediaSocket wmi_;
	private OntologySocket ontology_;

	public SalienceCreator(int threshold, int numFiles) {
		threshold_ = threshold;
		numFiles_ = numFiles;
		wmi_ = ResourceAccess.requestWikipediaSocket();
		ontology_ = ResourceAccess.requestOntologySocket();
		OUT_FOLDER.mkdir();
	}

	public static void main(String[] args) {
		KnowledgeMiner.newInstance("Enwiki_20110722");
		int threshold = Integer.parseInt(args[0]);
		int numFiles = Integer.parseInt(args[1]);
		SalienceCreator sc = new SalienceCreator(threshold, numFiles);
		try {
			sc.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void execute() throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(RAND_ARTS));
		int numFiles = 0;
		String input = null;
		ExtractArticleText eat = new ExtractArticleText();
		while ((input = in.readLine()) != null && numFiles < numFiles_) {
			Collection<Integer> outlinks = wmi_.getOutLinks(Integer
					.parseInt(input));
			int artID = Integer.parseInt(input);
			String type = wmi_.getPageType(artID);
			if (outlinks.size() >= threshold_
					&& type.equals(WikipediaSocket.TYPE_ARTICLE)) {
				File file = new File(OUT_FOLDER, input + ".txt");
				file.mkdirs();
				BufferedWriter out = new BufferedWriter(new FileWriter(file));
				try {
					String title = WikipediaSocket.singular(wmi_.getArtTitle(true,
							artID));
					if (eat.extract(artID, title, false, false)) {
						numFiles++;
						System.out.println(title);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Find ontology disambiguation for the outlink
				for (Integer outlink : outlinks)
					disambiguate(outlink, out);
				out.close();
//				numFiles++;
			}
		}

		in.close();
	}

	/**
	 * Disambiguate an outlink to one or more collections.
	 *
	 * @param outlink
	 *            The outlins being disambiguated.
	 * @param out
	 *            The output writer.
	 * @throws IOException
	 */
	private void disambiguate(Integer outlink, BufferedWriter out)
			throws IOException {
		OntologyConcept concept = KnowledgeMiner.getConceptMapping(outlink,
				ontology_);
		if (concept != null)
			out.write(concept.getConceptName() + "\n");
	}
}
