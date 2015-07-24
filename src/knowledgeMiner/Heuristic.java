/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package knowledgeMiner;

/**
 * An interface representing a heuristic for some aspect of the KnowledgeMiner.
 * 
 * @author Sam Sarjant
 */
public interface Heuristic {
	/**
	 * Records an instance in the appropriate file.
	 * 
	 * @param instance
	 *            The instance being recorded.
	 */
	public void recordInstance(Object instance);

	/**
	 * Formats the prior query as a training instance to be used for data mining
	 * purposes.
	 * 
	 * @return An instance line, in comma-separated ARFF format.
	 */
	public String asTrainingInstance(Object instance)
			throws IllegalArgumentException;

	/**
	 * Gets the ARFF header string for this heuristic.
	 * 
	 * @return The header for the ARFF file.
	 */
	public String getARFFHeader(String file);

	/**
	 * Prints the state of the {@link Heuristic}.
	 */
	public void printHeuristicState() throws Exception;
}
