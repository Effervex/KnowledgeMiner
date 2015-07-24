/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.text;

/**
 * 
 * @author Sam Sarjant
 */
public class StringFragment extends Annotation {
	private int startWordIndex_;
	private int endWordIndex_;

	/**
	 * Constructor for a new StringFragment
	 * 
	 * @param text
	 *            The text of the fragment.
	 * @param start
	 *            The start character index.
	 * @param startWordIndex
	 *            The index of the starting word.
	 * @param endWordIndex
	 *            The index of the ending word.
	 */
	public StringFragment(String text, int start, int startWordIndex,
			int endWordIndex) {
		super(text, start, start + text.length(), 1.0);
		startWordIndex_ = startWordIndex;
		endWordIndex_ = endWordIndex;
	}

	/**
	 * Constructor for a new StringFragment
	 * 
	 * @param text
	 */
	public StringFragment(StringFragment text) {
		super(text);
		startWordIndex_ = text.startWordIndex_;
		endWordIndex_ = text.endWordIndex_;
	}

	public int getStartWord() {
		return startWordIndex_;
	}

	public int getEndWord() {
		return endWordIndex_;
	}

	/**
	 * Calculates the word distance between this fragment and another. That is,
	 * the number of words between this derivation and the other (0 if they
	 * overlap). Negative values indicate direction.
	 * 
	 * @param otherString
	 *            The other string fragment.
	 * @return The word distance.
	 */
	public int wordDistance(StringFragment otherString) {
		if (otherString.startWordIndex_ > endWordIndex_)
			return otherString.startWordIndex_ - endWordIndex_;
		if (otherString.endWordIndex_ < startWordIndex_)
			return otherString.endWordIndex_ - startWordIndex_;
		return 0;
	}
}
