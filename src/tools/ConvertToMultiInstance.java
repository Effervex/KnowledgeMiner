package tools;

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

import org.apache.commons.lang3.StringUtils;

import util.UtilityMethods;
import weka.core.Instance;

public class ConvertToMultiInstance {

	/**
	 * Takes an ARFF file in, along with attribute equality indices, and outputs
	 * the file in relational format
	 *
	 * @param args
	 *            The input file and attributes
	 */
	public static void main(String[] args) {
		File file = new File(args[0]);
		String[] split = args[1].split(",");
		int[] indices = new int[split.length];
		for (int i = 0; i < split.length; i++)
			indices[i] = Integer.parseInt(split[i]);

		ConvertToMultiInstance cmi = new ConvertToMultiInstance();
		try {
			cmi.process(file, indices);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void process(File file, int[] indices) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(file));
		File tempOut = File.createTempFile("relational", "temp");
		BufferedWriter out = new BufferedWriter(new FileWriter(tempOut));

		Collection<Character> singleQuotes = new ArrayList<>();
		singleQuotes.add('\'');

		// Read every line
		StringBuilder preamble = new StringBuilder();
		String input = null;
		boolean merge = false;
		String instClass = null;
		String[] equality = null;
		int id = 0;
		String relation = null;
		while ((input = in.readLine()) != null) {
			if (relation == null)
				relation = input;
			if (!merge)
				merge = preamble(input, preamble);
			else {
				ArrayList<String> split = UtilityMethods.split(input, ',',
						singleQuotes);
				String thisClass = split.remove(split.size() - 1);
				String[] thisArray = asEqualityArray(split, indices);
				// If an equal instance, add to bag
				String asRelation = StringUtils.join(split, ',');
				asRelation = asRelation.replaceAll("\\\\", "\\\\\\\\");
				asRelation = asRelation.replaceAll("\"", "\\\\\"");
				if (Arrays.equals(thisArray, equality)) {
					out.write("\\n" + asRelation);
				} else {
					// Finish writing the old instance
					if (id > 0) {
						out.write("\"," + instClass + "\n");
					}
					out.write(id++ + ",\"" + asRelation);
					equality = thisArray;
					instClass = thisClass;
				}
			}
		}
		out.write("\"," + instClass + "\n");

		out.close();
		in.close();

		// Move data to final file
		File relFile = new File(file.getParent()
				+ File.separator + "Relational" + file.getName());
		out = new BufferedWriter(new FileWriter(relFile));
		out.write(relation + "_relational\n\n");
		out.write("@attribute id {");
		for (int i = 0; i < id; i++) {
			if (i != 0)
				out.write(",");
			out.write(i + "");
		}
		out.write("}\n");
		out.write(preamble.toString());

		// Read from the temp file
		in = new BufferedReader(new FileReader(tempOut));
		while ((input = in.readLine()) != null)
			out.write(input + "\n");
		in.close();
		out.close();
	}

	/**
	 * Converts a line of input into an equality array for duplication checking.
	 *
	 * @param instance
	 *            The string split instance.
	 * @param indices
	 *            The equality indices to compare on.
	 * @return A String array with the comparable values as arguments.
	 */
	private String[] asEqualityArray(ArrayList<String> instance, int[] indices) {
		String[] equalityArray = new String[indices.length];
		int i = 0;
		for (int index : indices)
			equalityArray[i++] = instance.get(index);
		return equalityArray;
	}

	/**
	 * Write the preamble.
	 *
	 * @param input
	 *            The current line
	 * @param preamble
	 *            The output file writer.
	 * @return True if the preamble is complete.
	 * @throws IOException
	 *             Should something go aery...
	 */
	private boolean preamble(String input, StringBuilder builder)
			throws IOException {
		if (input.startsWith("@relation")) {
			builder.append("@attribute bag relational\n");
		} else if (input.startsWith("@attribute 'class'") || input.startsWith("@attribute class")) {
			builder.append("@end bag\n");
			builder.append(input + "\n\n");
		} else if (input.startsWith("@attribute")) {
			builder.append("  " + input + "\n");
		} else if (input.startsWith("@data")) {
			builder.append(input + "\n");
			return true;
		}
		return false;
	}
}
