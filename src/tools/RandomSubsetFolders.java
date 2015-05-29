package tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
		int n = 0;
		try {
			n = customRemoval(entityFiles, entityIndices, topicFiles,
					topicIndices, numSubsets, perSubset);
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (; n < numSubsets; n++) {
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
	 * Custom removal of some files, still producing sets of size M. Returns the
	 * number of sets completed via this custom format.
	 *
	 * @param entityFiles
	 *            The entity files to copy.
	 * @param entityIndices
	 *            The entity indices remaining.
	 * @param topicFiles
	 *            The topic files to copy.
	 * @param topicIndices
	 *            The topic files remaining.
	 * @param numSubsets
	 *            The maximum number of subsets to produce.
	 * @param perSubset
	 *            The size per subset.
	 * @return The number of subsets created in this custom format.
	 * @throws IOException
	 */
	private int customRemoval(File[] entityFiles,
			ArrayList<Integer> entityIndices, File[] topicFiles,
			ArrayList<Integer> topicIndices, int numSubsets, int perSubset)
			throws IOException {
		int n = 0;
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		for (; n < numSubsets; n++) {
			File outputFolder = new File(OUTPUTS, n + "");
			outputFolder.mkdirs();

			System.out.println("Producing set " + n);
			int count = 0;
			while (count < perSubset) {
				fillIndices(entityFiles, entityIndices);
				fillIndices(topicFiles, topicIndices);
				int i = 0;
				for (; i < entityIndices.size(); i++) {
					System.out.println(i + ": "
							+ entityFiles[entityIndices.get(i)]);
				}
				for (int j = 0; j < topicIndices.size(); j++) {
					System.out.println((i + j) + ": "
							+ topicFiles[topicIndices.get(j)]);
				}
				System.out.println("Select number or leave empty to complete.");
				String input = in.readLine();
				if (input.isEmpty()) {
					if (count == 0)
						return n;
					else {
						// Pull a random article from the larger set.
						if (entityIndices.size() > topicIndices.size()) {
							int randI = random_.nextInt(entityIndices.size());
							int index = entityIndices.get(randI);
							copyFolder(entityFiles[index], entityIndices,
									outputFolder, randI);
							System.out.println("Used: " + entityFiles[index]);
							count++;
						} else {
							int randI = random_.nextInt(topicIndices.size());
							int index = topicIndices.get(randI);
							copyFolder(topicFiles[index], topicIndices,
									outputFolder, randI);
							System.out.println("Used: " + topicFiles[index]);
							count++;
						}
					}
				} else {
					int inputInt = Integer.parseInt(input);
					if (inputInt < entityIndices.size()) {
						int randI = inputInt;
						int index = entityIndices.get(randI);
						copyFolder(entityFiles[index], entityIndices,
								outputFolder, randI);
						count++;
					} else {
						int randI = inputInt - entityIndices.size();
						int index = topicIndices.get(randI);
						copyFolder(topicFiles[index], topicIndices,
								outputFolder, randI);
						count++;
					}
				}
			}
		}
		return n;
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
			fillIndices(files, indices);

			// Pull random index and copy file over
			int randI = random_.nextInt(indices.size());
			int index = indices.get(randI);
			if (pulled.add(index)) {
				copyFolder(files[index], indices, subsetDir, randI);
				i++;
			}
		}
	}

	/**
	 * Copies the folder to the subset directory.
	 *
	 * @param file
	 *            The folder to copy.
	 * @param indices
	 *            The indices to remove the element from.
	 * @param subsetDir
	 *            The subset directory to copy to.
	 * @param randI
	 *            The random index to remove from the indices.
	 * @throws IOException
	 *             Should something go awry...
	 */
	private void copyFolder(File file, ArrayList<Integer> indices,
			File subsetDir, int randI) throws IOException {
		indices.remove(randI);
		File destFolder = new File(subsetDir, file.getName());
		FileUtils.copyDirectory(file, destFolder);
	}

	private void fillIndices(File[] files, ArrayList<Integer> indices) {
		if (indices.isEmpty()) {
			for (int f = 0; f < files.length; f++)
				indices.add(f);
		}
	}
}
