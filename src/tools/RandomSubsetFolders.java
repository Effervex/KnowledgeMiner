package tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.commons.io.FileUtils;

public class RandomSubsetFolders {
	private static final File OUTPUTS = new File("subsets");
	private Random random_;

	public RandomSubsetFolders() {
		OUTPUTS.mkdir();
		random_ = new Random();
	}

	public static void main(String[] args) {
		RandomSubsetFolders rsf = new RandomSubsetFolders();
		File folder = new File(args[0]);
		int numSubsets = Integer.parseInt(args[1]);
		int perSubset = Integer.parseInt(args[2]);
		rsf.execute(folder, numSubsets, perSubset);
	}

	public void execute(File folder, int numSubsets, int perSubset) {
		File entitiesFolder = new File(folder, "Entities" + File.separator
				+ "Pick15");
		File topicsFolder = new File(folder, "Topics" + File.separator
				+ "Pick15");
		if (!folder.exists() || !entitiesFolder.exists()
				|| !topicsFolder.exists()) {
			System.err.println("Cannot find folder");
			System.exit(1);
		}

		File[] entityFiles = entitiesFolder.listFiles();
		File[] topicFiles = topicsFolder.listFiles();
		ArrayList<Integer> entityIndices = new ArrayList<>();
		ArrayList<Integer> topicIndices = new ArrayList<>();
		for (int n = 0; n < numSubsets; n++) {
			File outputFolder = new File(OUTPUTS, n + "");
			outputFolder.mkdirs();
			int numEntities = ((n % 2) == 0) ? perSubset / 2 : perSubset
					- perSubset / 2;

			try {
				copyRandomFiles(entityFiles, entityIndices, numEntities,
						outputFolder);
				copyRandomFiles(topicFiles, topicIndices, perSubset
						- numEntities, outputFolder);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Copy files over to the subset folder, pulling randomly (nut ensuring the
	 * random distribution is as balanced as possible).
	 *
	 * @param files
	 *            The files to copy.
	 * @param indices
	 *            The indices to pull.
	 * @param numPulls
	 *            The total number of pulls.
	 * @param subsetDir
	 * @throws IOException
	 *             Should something go awry...
	 */
	private void copyRandomFiles(File[] files, ArrayList<Integer> indices,
			int numPulls, File subsetDir) throws IOException {
		Set<Integer> pulled = new HashSet<>();
		for (int i = 0; i < numPulls;) {
			// Fill up the indices
			if (indices.isEmpty()) {
				for (int f = 0; f < files.length; f++)
					indices.add(f);
			}

			// Pull random index and copy file over
			int randI = random_.nextInt(indices.size());
			int index = indices.get(randI);
			if (pulled.add(index)) {
				indices.remove(randI);
				File destFolder = new File(subsetDir, files[index].getName());
				FileUtils.copyDirectory(files[index], destFolder);
				i++;
			}
		}
	}
}
