/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io.ontology;

import graph.core.CommonConcepts;
import graph.core.DAGNode;
import graph.core.cli.DAGPortHandler;
import graph.inference.CommonQuery;
import io.IOManager;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.UtilityMethods;
import cyc.CycConstants;
import cyc.OntologyConcept;

public class DAGSocket extends OntologySocket {
	/** The default port number for the DAG. */
	public static final int DAG_PORT = 2425;
	private static final String DELIMITER = "!Y^e#";
	private Logger logger_ = LoggerFactory.getLogger(DAGSocket.class);

	public DAGSocket(DAGAccess access) {
		this(access, DAG_PORT);
	}

	public DAGSocket(DAGAccess kmAccess, int port) {
		super(kmAccess, port);
	}

	private String noNewLine(String str) {
		return str.replaceAll(" ?\n ?", " ");
	}

	private void setEdgeFlags() {
		// Define edge flags
		StringBuilder eFlags = new StringBuilder("F");
		if (ephemeral_)
			eFlags.append("T");
		else
			eFlags.append("F");
		if (forceConstraints_)
			eFlags.append("T");
		else
			eFlags.append("F");

		// Define node flags
		String nFlags = (ephemeral_) ? "FT" : "FF";

		// Set
		try {
			command("set", DAGPortHandler.EDGE_FLAGS + " " + eFlags, false);
			command("set", DAGPortHandler.NODE_FLAGS + " " + nFlags, false);
		} catch (Exception e) {
			logger_.error("setEdgeFlags: {}",
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				setEdgeFlags();
				canRestart_ = true;
			}
		}
	}

	@Override
	protected void connect() throws UnknownHostException, IOException {
		super.connect();
		try {
			command("set", "/env/singleline true", false);
			command("set", "/env/endmessage ", false);
			command("set", "/env/prompt ", false);
			command("set", DAGPortHandler.PRETTY_RESULTS + " false", false);
			command("set", "/env/time false", false);
			command("set", DAGPortHandler.DYNAMICALLY_ADD_NODES + " false",
					false);
			command("set", "/env/overwriteFunctional false", false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected String getMachineName() {
		return LOCALHOST;
	}

	@Override
	protected int getPort() {
		int port = super.getPort();
		if (port == -1)
			return DAG_PORT;
		return port;
	}

	@Override
	public int assertToOntology(String microtheory, Object... arguments) {
		String edge = null;
		if (arguments.length == 1 && arguments[0] instanceof String
				&& arguments[0].toString().matches("\\(.+?\\)"))
			edge = (String) arguments[0];
		else {
			edge = "(" + StringUtils.join(arguments, " ").trim() + ")";
		}
		edge = noNewLine(edge);

		try {
			String args = null;
			if (microtheory != null)
				args = edge + ":" + microtheory + " ("
						+ CycConstants.KNOWLEDGE_MINER.getID() + ")";
			else
				args = edge + " (" + CycConstants.KNOWLEDGE_MINER.getID() + ")";
			String result = command("addedge", args, false);
			IOManager.getInstance().writeCycOperation("addedge " + args);
			int id = Integer.parseInt(result.split("\\|")[0]);
			if (id < 0)
				System.err.println("addedge " + args + " = " + result);
			clearCachedArticles();
			return id;
		} catch (Exception e) {
			logger_.error("assertToOntology: {}, {}",
					Arrays.toString(arguments),
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = assertToOntology(microtheory, arguments);
				canRestart_ = true;
				return result;
			}
			return -1;
		}
	}

	/**
	 * Sends a batch command string to the DAG. It should be in a standard
	 * recognisable format.
	 *
	 * @param batchCommand
	 *            The command to run in the batch.
	 * @param arguments
	 *            The arguments for each line for the batch command.
	 * @return An array of results for each of the arguments.
	 */
	public String[] batchCommand(String batchCommand, String... arguments) {
		StringBuilder command = new StringBuilder("batch " + batchCommand + " "
				+ DELIMITER + "\n");
		command.append(StringUtils.join(arguments, "\n"));
		command.append("\n" + DELIMITER);
		// String result = querySocket(NLPToSyntaxModule.convertToAscii(command
		// .toString()));
		String result = querySocket(command.toString());
		return result.split(Pattern.quote(DELIMITER));
	}

	/**
	 * Sends a command string to the DAG. It should be in a standard,
	 * recognisable format.
	 * 
	 * @param command
	 *            The command to use.
	 * @param arguments
	 *            The arguments of the command.
	 * @param cache
	 *            If a cache should be used to store/retrieve the command
	 * @return The result String of the command, or an empty string if the
	 *         command was not recognised.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public String command(String command, String arguments, boolean cache)
			throws Exception {
		if (cache) {
			Object cached = access_.getCachedCommand(command, arguments);
			if (cached != null)
				return cached.toString();
		}
		// Remove UTF encoding
		// arguments = NLPToSyntaxModule.convertToAscii(arguments);
		String result = querySocket(command + " " + arguments);
		if (cache)
			access_.cacheCommand(command, arguments, result);
		return result;
	}

	@Override
	public int createConcept(String name) {
		try {
			String args = null;
			// Special case for creating the KnowledgeMiner concept (which
			// throws an Exception).
			if (!name.equals("KnowledgeMiner"))
				args = noNewLine(name) + " (KnowledgeMiner)";
			else
				args = noNewLine(name);
			String output = command("addnode", args, false);
			IOManager.getInstance().writeCycOperation("addnode " + args);
			int pipeIndex = output.indexOf('|');
			if (pipeIndex == -1)
				return -1;
			clearCachedArticles();
			return Integer.parseInt(output.substring(0, pipeIndex));
		} catch (Exception e) {
			logger_.error("createConcept: {}, {}", name,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = createConcept(name);
				canRestart_ = true;
				return result;
			}
			return -1;
		}
	}

	/**
	 * Transforms a DAG object into a plain text string. Object can be a Node,
	 * Edge, Query, or even Marked Up text.
	 *
	 * @param dagObject
	 *            The object to transform.
	 * @param type
	 *            The type of transformation.
	 * @param markup
	 *            If the output should be marked up.
	 * @return A string representation of the object (or null if invalid).
	 */
	@Override
	public String dagToText(Object dagObject, String type, boolean markup) {
		String args = type + " " + dagObject.toString() + " "
				+ ((markup) ? "T" : "F");
		try {
			String result = command("dagtotext", args, false);
			if (result.startsWith("1|")) {
				return result.substring(2);
			}
		} catch (Exception e) {
			logger_.error("dagtotext: {}, {}, {}, {}", dagObject, type, markup,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				String result = dagToText(dagObject, type, markup);
				canRestart_ = true;
				return result;
			}
		}
		return null;
	}

	@Override
	public String findConceptByID(int id) {
		String result;
		try {
			result = command("node", id + "", false);
			String[] split = result.split("\\|");
			if (split[0].equals("-1"))
				return null;
			return split[1];
		} catch (Exception e) {
			e.printStackTrace();
			logger_.error("findConceptByID: {}, {}", id,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				result = findConceptByID(id);
				canRestart_ = true;
				return result;
			}
		}
		return null;
	}

	@Override
	public Collection<OntologyConcept> findConceptByName(String name,
			boolean caseSensitive, boolean exactString, boolean allowAliases) {
		return findFilteredConceptByName(name, caseSensitive, exactString,
				allowAliases);
	}

	@Override
	public String[] findEdgeByID(int id) {
		String result;
		try {
			result = command("edge", id + "", false);
			String[] split = result.split("\\|");
			ArrayList<String> nodes = UtilityMethods.split(
					UtilityMethods.shrinkString(split[1], 1), ' ');
			return nodes.toArray(new String[nodes.size()]);
		} catch (Exception e) {
			logger_.error("findEdgeByID: {}, {}", id,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				String[] result2 = findEdgeByID(id);
				canRestart_ = true;
				return result2;
			}
		}
		return null;
	}

	@Override
	public int findEdgeIDByArgs(Object... edgeArgs) {
		StringBuilder arguments = new StringBuilder();
		for (int i = 0; i < edgeArgs.length; i++)
			if (edgeArgs[i] != null)
				arguments.append(edgeArgs[i] + " (" + (i + 1) + ") ");
		try {
			String result = command("findedges", arguments.toString().trim(),
					true);
			String[] split = result.split("\\|");
			if (split.length == 2 && split[0].equals("1"))
				return Integer.parseInt(split[1]);
		} catch (Exception e) {
			logger_.error("findEdgeIDByArgs: {}, {}", edgeArgs,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = findEdgeIDByArgs(edgeArgs);
				canRestart_ = true;
				return result;
			}
		}
		return -1;
	}

	@Override
	public Collection<String[]> findEdges(Object... indexArgs) {
		Collection<String[]> assertions = new ArrayList<>();
		try {
			String result = command("findedges",
					StringUtils.join(indexArgs, " "), true);
			String[] split = result.split("\\|");
			if (Integer.parseInt(split[0]) <= 0)
				return assertions;
			for (int i = 1; i < split.length; i++) {
				assertions.add(findEdgeByID(Integer.parseInt(split[i])));
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error finding edges with arguments "
					+ indexArgs);
			logger_.error("allAssertions: {}", (Object) indexArgs);
			if (restartConnection()) {
				Collection<String[]> result = findEdges(indexArgs);
				canRestart_ = true;
				return result;
			}
		}
		return assertions;
	}

	public Collection<Integer> findEdgeIDs(String[] indexArgs) {
		Collection<Integer> assertions = new ArrayList<>();
		try {
			String result = command("findedges",
					StringUtils.join(indexArgs, " "), true);
			String[] split = result.split("\\|");
			if (Integer.parseInt(split[0]) <= 0)
				return assertions;
			for (int i = 1; i < split.length; i++) {
				assertions.add(Integer.parseInt(split[i]));
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error finding edges with arguments "
					+ indexArgs);
			logger_.error("allAssertions: {}", (Object) indexArgs);
			if (restartConnection()) {
				Collection<Integer> result = findEdgeIDs(indexArgs);
				canRestart_ = true;
				return result;
			}
		}
		return assertions;
	}

	@Override
	public Collection<OntologyConcept> findFilteredConceptByName(String name,
			boolean caseSensitive, boolean exactString, boolean allowAliases,
			Object... queryArgs) {
		Collection<OntologyConcept> concepts = new HashSet<>();
		if (name.isEmpty())
			return concepts;
		try {
			// Use find node
			StringBuilder buffer = new StringBuilder("\""
					+ noNewLine(name).replaceAll("\"", "\\\\\"") + "\"");
			if (caseSensitive)
				buffer.append(" T");
			else
				buffer.append(" F");
			if (exactString)
				buffer.append(" T");
			else
				buffer.append(" F");

			// Adding the query (if it exists)
			String command = "findnodes";
			if (queryArgs != null && queryArgs.length > 0) {
				buffer.append(" ("
						+ noNewLine(StringUtils.join(queryArgs, ' ')) + ")");
				command = "findnodes*";
			}

			String result = command(command, buffer.toString(), true);
			String[] split = result.split("\\|");
			for (int i = 1; i < split.length; i++) {
				if (!split[i].startsWith("(")
						&& !StringUtils.isNumeric(split[i]))
					continue;

				// Alias check.
				if (allowAliases
						|| split[i].startsWith("(")
						|| findConceptByID(Integer.parseInt(split[i])).equals(
								name)) {
					OntologyConcept concept = OntologyConcept
							.parseArgument(split[i]);
					if (concept != null)
						concepts.add(concept);
				}
			}
		} catch (Exception e) {
			logger_.error("findConceptByName: {}, {}", name,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				Collection<OntologyConcept> result = findFilteredConceptByName(
						name, caseSensitive, exactString, allowAliases,
						queryArgs);
				canRestart_ = true;
				return result;
			}
		}
		return concepts;
	}

	@Override
	public Collection<String[]> getAllAssertions(Object concept, int argPos,
			Object... exceptPredicates) {
		Collection<String[]> assertions = new ArrayList<>();
		StringBuilder arguments = new StringBuilder(concept.toString());
		try {
			if (argPos != -1)
				arguments.append(" (" + argPos + ")");
			for (Object pred : exceptPredicates)
				arguments.append(" " + pred + " (-1)");
			String result = command("findedges",
					noNewLine(arguments.toString()), true);

			String[] split = result.split("\\|");
			if (Integer.parseInt(split[0]) <= 0)
				return assertions;
			for (int i = 1; i < split.length; i++) {
				assertions.add(findEdgeByID(Integer.parseInt(split[i])));
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error getting all assertions for " + concept
					+ " (" + argPos + ")");
			logger_.error("allAssertions: {}:{}, {}", concept, argPos,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				Collection<String[]> result = getAllAssertions(concept, argPos,
						exceptPredicates);
				canRestart_ = true;
				return result;
			}
		}
		return assertions;
	}

	@Override
	public int getConceptID(String term) {
		String result;
		try {
			result = command("node", noNewLine(term), false);
			int index = result.indexOf('|');
			if (index == -1)
				return -13;
			if (result.lastIndexOf('|') == index)
				return NON_EXISTENT_ID;
			return Integer.parseInt(result.substring(0, index));
		} catch (Exception e) {
			logger_.error("getConceptID: {}, {}", term,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result2 = getConceptID(term);
				canRestart_ = true;
				return result2;
			}
		}
		return NON_EXISTENT_ID;
	}

	@Override
	public int getNextEdge(int id) {
		try {
			return Integer.parseInt(command("nextedge", id + "", false));
		} catch (Exception e) {
			logger_.error("getNextEdge: {}, {}", id,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = getNextEdge(id);
				canRestart_ = true;
				return result;
			}
		}
		return -1;
	}

	@Override
	public int getNextNode(int id) {
		try {
			String result = command("nextnode", id + "", false);
			int pipeIndex = result.indexOf('|');
			return Integer.parseInt(result.substring(0, pipeIndex));
		} catch (Exception e) {
			logger_.error("getNextNode: {}, {}", id,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = getNextNode(id);
				canRestart_ = true;
				return result;
			}
		}
		return -1;
	}

	@Override
	public int getNumConstants() {
		try {
			return Integer.parseInt(command("numnodes", "", false));
		} catch (Exception e) {
			logger_.error("getNumConstants: {}",
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = getNumConstants();
				canRestart_ = true;
				return result;
			}
		}
		return -1;
	}

	@Override
	public int getPrevEdge(int id) {
		try {
			return Integer.parseInt(command("prevedge", id + "", false));
		} catch (Exception e) {
			logger_.error("getPrevEdge: {}, {}", id,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = getPrevEdge(id);
				canRestart_ = true;
				return result;
			}
		}
		return -1;
	}

	@Override
	public int getPrevNode(int id) {
		try {
			String result = command("prevnode", id + "", false);
			int pipeIndex = result.indexOf('|');
			return Integer.parseInt(result.substring(0, pipeIndex));
		} catch (Exception e) {
			logger_.error("getPrevNode: {}, {}", id,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				int result = getPrevNode(id);
				canRestart_ = true;
				return result;
			}
		}
		return -1;
	}

	@Override
	public String getProperty(Object nodeEdge, boolean isNode, String propKey) {
		StringBuilder buffer = new StringBuilder();
		if (isNode)
			buffer.append("N");
		else
			buffer.append("E");
		buffer.append(" " + nodeEdge + " \"" + propKey + "\"");

		try {
			String result = command("getprop", noNewLine(buffer.toString()),
					false);
			if (result.startsWith("-2"))
				return null;
			return result.substring(2);
		} catch (Exception e) {
			logger_.error("getProperty: {}:{}, {}", nodeEdge, propKey,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				String result = getProperty(nodeEdge, isNode, propKey);
				canRestart_ = true;
				return result;
			}
		}
		return null;
	}

	@Override
	public Collection<String> getSynonyms(Object term) {
		Collection<OntologyConcept> alias = quickQuery(CommonQuery.ALIAS, term);
		Collection<String> synonyms = new ArrayList<>(alias.size());
		for (OntologyConcept arg : alias)
			synonyms.add(UtilityMethods.shrinkString(arg.toString(), 1));
		return synonyms;
	}

	@Override
	public boolean isValidArg(Object predicate, Object concept, int argNum) {
		if (!super.isValidArg(predicate, concept, argNum))
			return false;

		try {
			return command("validarg",
					predicate + " " + argNum + " " + concept, true).startsWith(
					"1");
		} catch (Exception e) {
			logger_.error("validArg: {}:{}:{}, {}", predicate, concept, argNum,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				boolean result = isValidArg(predicate, concept, argNum);
				canRestart_ = true;
				return result;
			}
		}
		return false;
	}

	@Override
	public List<String> justify(Object... assertionArgs) {
		List<String> justification = new ArrayList<>();
		try {
			String result = command(
					"justify",
					"(" + noNewLine(StringUtils.join(assertionArgs, ' ')) + ")",
					true);
			if (result.startsWith("-1"))
				return justification;
			String[] split = result.split("\\|");
			for (String str : split) {
				if (!str.isEmpty())
					justification.add(str);
			}
		} catch (Exception e) {
			logger_.error("justify: {}, {}", Arrays.toString(assertionArgs),
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				justification = justify(assertionArgs);
				canRestart_ = true;
			}
		}
		return justification;
	}

	@Override
	public boolean parseProofResult(String result) {
		if (result.trim().matches("\\d+\\|T\\|.*"))
			return true;
		return false;
	}

	@Override
	public String query(String microtheory, Object... queryArgs) {
		try {
			// TODO Note that proveFail is always false here.
			return command("query",
					"F (" + noNewLine(StringUtils.join(queryArgs, ' ')) + ")",
					true);
		} catch (Exception e) {
			logger_.error("query: {}, {}", Arrays.toString(queryArgs),
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				String result = query(microtheory, queryArgs);
				canRestart_ = true;
				return result;
			}
		}
		return null;
	}

	@Override
	public Collection<OntologyConcept> quickQuery(CommonQuery cq,
			Object... args) {
		String query = cq.toString() + " "
				+ noNewLine(StringUtils.join(args, " "));
		try {
			String[] split = command("query*", query, true).split("\\|");
			int size = Integer.parseInt(split[0]);
			if (size < 0)
				size = 0;
			Collection<OntologyConcept> results = new ArrayList<>(size);
			for (int i = 1; i <= size; i++) {
				OntologyConcept concept = OntologyConcept
						.parseArgument(split[i]);
				if (concept != null)
					results.add(concept);
			}
			return results;
		} catch (Exception e) {
			logger_.error("quickQuery: {} {}, {}", cq, args,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				Collection<OntologyConcept> result = quickQuery(cq, args);
				canRestart_ = true;
				return result;
			}
		}
		return null;
	}

	@Override
	public boolean removeConcept(Object name) {
		// System.out.println("Attempted removal: " + name);
		// return true;
		try {
			clearCachedArticles();
			String args = noNewLine(name.toString());
			IOManager.getInstance().writeCycOperation("removenode " + args);
			return command("removenode", args, false).startsWith("1");
		} catch (Exception e) {
			logger_.error("removeConcept: {}, {}", name,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				boolean result = removeConcept(name);
				canRestart_ = true;
				return result;
			}
		}
		return false;
	}

	@Override
	public void setEphemeral(boolean b) {
		if (b != ephemeral_) {
			ephemeral_ = b;
			setEdgeFlags();
		}
	}

	@Override
	public void setForceConstraints(boolean b) {
		if (b != forceConstraints_) {
			forceConstraints_ = b;
			setEdgeFlags();
		}
	}

	@Override
	public void setProperty(Object nodeEdge, boolean isNode, String propKey,
			String propValue) {
		StringBuilder buffer = new StringBuilder();
		if (isNode)
			buffer.append("N");
		else
			buffer.append("E");
		buffer.append(" " + noNewLine(nodeEdge.toString()) + " \""
				+ noNewLine(propKey) + "\" |" + System.lineSeparator()
				+ noNewLine(propValue) + System.lineSeparator() + "|");

		try {
			command("addprop", buffer.toString(), false);
		} catch (Exception e) {
			logger_.error("setProperty: {}:{}={}, {}", nodeEdge, propKey,
					propValue, Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				setProperty(nodeEdge, isNode, propKey, propValue);
				canRestart_ = true;
			}
		}
	}

	@Override
	public void removeProperty(Object nodeEdge, boolean isNode, String propKey) {
		StringBuilder buffer = new StringBuilder();
		if (isNode)
			buffer.append("N");
		else
			buffer.append("E");
		buffer.append(" " + noNewLine(nodeEdge.toString()) + " \""
				+ noNewLine(propKey) + "\"");

		try {
			command("removeprop", buffer.toString(), false);
		} catch (Exception e) {
			logger_.error("removeProperty: {}:{}={}, {}", nodeEdge, propKey,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				removeProperty(nodeEdge, isNode, propKey);
				canRestart_ = true;
			}
		}
	}

	@Override
	public boolean unassert(String microtheory, int assertionID,
			boolean forceRemove) {
		try {
			clearCachedArticles();
			String args = assertionID + " ";
			args += (forceRemove) ? "T" : "F";
			IOManager.getInstance().writeCycOperation("removeedge " + args);
			return command("removeedge", args, false).startsWith("1");
		} catch (Exception e) {
			logger_.error("unassert: {}, {}", assertionID,
					Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				boolean result = unassert(microtheory, assertionID, forceRemove);
				canRestart_ = true;
				return result;
			}
		}
		return false;
	}

	/**
	 * Checks if the concept has any children. If so, does not allow assertion
	 * removal.
	 *
	 * @param conceptModule
	 *            The concept to check.
	 * @return True if the concept has children.
	 */
	@Override
	public boolean conceptHasChildren(Object concept) {
		try {
			// Isa
			String result = command("findedges", CommonConcepts.ISA.getID()
					+ " (1) " + concept + " (3) [0,1)", false);
			if (result.startsWith("1"))
				return true;
			// Genls
			result = command("findedges", CommonConcepts.GENLS.getID()
					+ " (1) " + concept + " (3) [0,1)", false);
			if (result.startsWith("1"))
				return true;
		} catch (Exception e) {
			return true;
		}
		return false;
	}

	@Override
	public boolean validConstantName(String cycTerm) {
		return DAGNode.isValidName(cycTerm);
	}

	/**
	 * Searches for all nodes with given property and key values.
	 *
	 * @param property
	 *            The property key.
	 * @param valueRegex
	 *            The regex for value matching.
	 * @return The collection of all concepts with the given key:value pair.
	 */
	public Collection<OntologyConcept> searchNodeProperty(String property,
			String valueRegex) {
		Collection<OntologyConcept> propNodes = new ArrayList<>();
		try {
			String result = command("searchprop", "N " + property + " F\n"
					+ valueRegex, false);
			String[] split = result.split("\\|");

			for (int i = 1; i < split.length; i++) {
				if (!split[i].startsWith("(")
						&& !StringUtils.isNumeric(split[i]))
					continue;
				OntologyConcept concept = OntologyConcept
						.parseArgument(split[i]);
				if (concept != null)
					propNodes.add(concept);
			}
		} catch (Exception e) {
			logger_.error("searchNodeProperty: {}={}, {}", property,
					valueRegex, Arrays.toString(e.getStackTrace()));
			if (restartConnection()) {
				Collection<OntologyConcept> result = searchNodeProperty(
						property, valueRegex);
				canRestart_ = true;
				return result;
			}
		}
		return propNodes;
	}

	public void refinePredicate(int refineEvidence) {
		try {
			command("refine", refineEvidence + " 0.5 T provenance", false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
