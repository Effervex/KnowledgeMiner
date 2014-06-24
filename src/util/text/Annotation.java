/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.text;

import java.io.IOException;

import util.Weighted;

/**
 * An annotation to text that replaces a given string with an 'annotated' one.
 * 
 * @author Sam Sarjant
 */
public abstract class Annotation implements Comparable<Annotation>, Weighted {
	private int end_;
	private int start_;
	private String text_;
	private double weight_;

	/**
	 * Constructor for a new Annotation with a defined start point.
	 * 
	 * @param annotation
	 *            The previous {@link Annotation} to use.
	 * @param start
	 *            The new start point to use.
	 */
	public Annotation(Annotation annotation, int start) {
		text_ = annotation.text_;
		start_ = start;
		end_ = start_ + text_.length();
		weight_ = annotation.weight_;
	}

	/**
	 * Constructor for a cloned Annotation.
	 * 
	 * @param annotation
	 *            The previous {@link Annotation} to use.
	 * @param start
	 *            The new start point to use.
	 */
	public Annotation(Annotation annotation) {
		text_ = annotation.text_;
		start_ = annotation.start_;
		end_ = annotation.end_;
		weight_ = annotation.weight_;
	}

	/**
	 * Constructor for a new Annotation
	 * 
	 * @param text
	 *            The text being annotated.
	 * @param weight
	 *            The weight of the annotation.
	 */
	public Annotation(String text, double weight) {
		text_ = text;
		start_ = -1;
		end_ = -1;
		weight_ = weight;
	}

	/**
	 * A locational constructor for a new Annotation
	 * 
	 * @param text
	 *            The text being annotated.
	 * @param weight
	 *            The weight of the annotation.
	 */
	public Annotation(String text, int start, int end, double weight) {
		text_ = text;
		start_ = start;
		end_ = end;
		weight_ = weight;
	}

	/**
	 * Applies this annotation to the text.
	 * 
	 * @param text
	 *            The text to annotate.
	 * @return The modified text with the original text replaced by the label
	 *         link.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public String applyLabel(String text) throws IOException {
		StringBuilder buffer = new StringBuilder(text.substring(0, start_));
		buffer.append(toString());
		buffer.append(text.substring(end_));
		return buffer.toString();
	}

	@Override
	public int compareTo(Annotation arg0) {
		int result = Double.compare(start_, arg0.start_);
		if (result != 0)
			return -result;

		result = Double.compare(end_, arg0.end_);
		if (result != 0)
			return -result;

		return text_.compareTo(arg0.text_);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Annotation other = (Annotation) obj;
		if (end_ != other.end_)
			return false;
		if (start_ != other.start_)
			return false;
		if (text_ == null) {
			if (other.text_ != null)
				return false;
		} else if (!text_.equals(other.text_))
			return false;
		return true;
	}

	public String getText() {
		return text_;
	}

	public int getStart() {
		return start_;
	}

	public int getEnd() {
		return end_;
	}

	@Override
	public double getWeight() {
		return weight_;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + end_;
		result = prime * result + start_;
		result = prime * result + ((text_ == null) ? 0 : text_.hashCode());
		return result;
	}

	/**
	 * If this annotation supercedes another.
	 * 
	 * @param annotation
	 *            The other annotation being compared.
	 * @return 1 if this annotation is used, -1 if the other is used, 0 if they
	 *         do not overlap.
	 */
	public int overlaps(Annotation annotation) {
		int result = Double.compare(text_.length(), annotation.text_.length());
		if (result == 0)
			result = -Double.compare(weight_, annotation.weight_);

		if (!(end_ <= annotation.start_ || start_ >= annotation.end_))
			return result;
		if (!(annotation.end_ <= start_ || annotation.start_ >= end_))
			return result;
		return 0;
	}

	@Override
	public void setWeight(double weight) {
		weight_ = weight;
	}

	@Override
	public String toString() {
		return text_;
	}
}
