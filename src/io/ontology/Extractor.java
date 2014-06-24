/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sam Sarjant - initial API and implementation
 ******************************************************************************/
package io.ontology;

import graph.core.DAGNode;
import io.ResourceAccess;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.UtilityMethods;

/**
 * Extracts the ontology data into textfile form, such that it can be recreated.
 * 
 * @author Sam Sarjant
 */
public class Extractor {
	private static final Pattern PROP_PATTERN = Pattern
			.compile("\"(.+?)\"=\"(.+?)\"( |$)");
	private static final Pattern PRIMITIVE_PATTERN = Pattern
			.compile(" (?:(\"[^\"]*\")|(-?\\d[\\dE+-.]*(?= |\\))))");
	private static final Pattern TRIPLE_CAPTURE = Pattern
			.compile("^addedge \\(((?:(?:(?:\\(.+?\\))|(?:\\S+)) ){2})(\".+?\")\\)$");
	public static File OUTPUT_FILE = new File("DAGrecreate.txt");

	public void run(int port) throws UnknownHostException, IOException {
		ResourceAccess.newInstance(port);
		OntologySocket socket = ResourceAccess.requestOntologySocket();
		socket.querySocket("set /env/pretty true");

		// Set up outputfile
		OUTPUT_FILE.createNewFile();
		BufferedWriter out = new BufferedWriter(new FileWriter(OUTPUT_FILE));

		// Get nodes
		System.out.print("Nodes complete:");
		extractDAGData(socket, out, "nextnode", "addnode", "N");
		System.out.println(" Done.");

		// Get edges
		System.out.print("Edges complete:");
		extractDAGData(socket, out, "nextedge", "addedge", "E");
		System.out.println(" Done.");

		out.close();
	}

	private void extractDAGData(OntologySocket socket, BufferedWriter out,
			String next, String add, String propType) throws IOException {
		int index = 0;
		int count = 0;
		while (index >= 0) {
			String output = socket.querySocket(next + " " + index);
			String[] split = output.split("\\|");

			// Write add<obj>
			int id = Integer.parseInt(split[0]);
			index = id;
			if (id < 0)
				break;

			// Ignoring ANON
			if (output.contains(DAGNode.ANON_TO_STRING))
				continue;

			// Adding ' to primitives
			String name = prefixPrimitives(split[1]);
			name = escapeStrings(name);

			out.write("$0$=" + add + " " + name + "\n");

			// Write properties
			output = socket.querySocket("listprops " + propType + " " + id);
			Matcher m = PROP_PATTERN.matcher(output);
			while (m.find()) {
				out.write("addprop " + propType + " $0$ \"" + m.group(1)
						+ "\" |\\n" + m.group(2) + "\\n|\n");
			}

			count++;
			if ((count % 10000) == 0)
				System.out.print(" " + count);
		}
	}

	private String escapeStrings(String name) {
		// Escape strings
		return name.replaceAll("\"", "\\\"");
	}

	private String prefixPrimitives(String string) {
		Matcher m = PRIMITIVE_PATTERN.matcher(string);
		StringBuilder buffer = new StringBuilder();
		int start = 0;
		while (m.find()) {
			if (m.group(2) != null) {
				buffer.append(string.substring(start, m.start(2)) + "'");
				buffer.append(m.group(2));
				start = m.end(2);
			}
		}
		return buffer.append(string.substring(start, string.length()))
				.toString();
	}

	public void fixExtraction(File file) throws Exception {
		BufferedReader in = new BufferedReader(new FileReader(file));
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(
				"output.txt")));

		String input = null;
		while ((input = in.readLine()) != null) {
			// Replace primitives
			input = prefixPrimitives(input);

			// Escape newlines
			out.write(input + "\n");
		}
		out.close();
		in.close();
	}

	public void convertToDirectScript(File file) throws Exception {
		BufferedReader in = new BufferedReader(new FileReader(file));
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(
				"output.txt")));

		String input = null;
		String add = null;
		String mt = null;
		String creator = null;
		while ((input = in.readLine()) != null) {
			if (input.startsWith("$0$")) {
				if (add != null) {
					StringBuilder writable = new StringBuilder(add);
					if (mt != null)
						writable.append(":" + mt);
					if (creator != null)
						writable.append(" (" + creator + ")");
					writable.append("\n");
					out.write(writable.toString());
					mt = null;
					creator = null;
				}

				// Fix strings
				add = escapeQuote(input.substring(4));
			} else if (input.startsWith("addprop E $0$ \"MT\"")) {
				mt = input.substring(22, input.length() - 3);
			} else if (input.startsWith("addprop E $0$ \"creator\"")
					|| input.startsWith("addprop N $0$ \"creator\"")) {
				creator = input.substring(27, input.length() - 3);
			}
		}
		out.close();
		in.close();
	}

	private String escapeQuote(String input) {
		Matcher m = TRIPLE_CAPTURE.matcher(input);
		StringBuilder out = new StringBuilder();
		if (m.matches()) {
			out.append("addedge (" + m.group(1) + "\"");

			String string = UtilityMethods.shrinkString(m.group(2), 1);
			string = string.replaceAll("(?<!\\\\)\"", "\\\\\"");

			out.append(string + "\")");
			return out.toString();
		} else
			return input;
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Please provide port number.");
			System.exit(0);
		}
		int port = Integer.parseInt(args[0]);
		try {
			if (args.length > 1)
				new Extractor().convertToDirectScript(new File(args[1]));
			else
				new Extractor().run(port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
