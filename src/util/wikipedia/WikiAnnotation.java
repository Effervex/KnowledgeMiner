/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import io.resources.WMISocket;
import util.text.Annotation;

/**
 * A class for recording annotations in a set of text. Each annotation replaces
 * a given segment of text with an internal link.
 * 
 * @author Sam Sarjant
 */
public class WikiAnnotation extends Annotation {
	private String label_;
	private WMISocket wmi_;

	/**
	 * Constructor for a new WikiAnnotation
	 * 
	 * @param text
	 *            The text being annotated.
	 * @param label
	 *            The annotation text.
	 * @param weight
	 *            The weight of the annotation.
	 * @param wmi
	 *            The WMI Access.
	 */
	public WikiAnnotation(String text, String label, double weight,
			WMISocket wmi) {
		super(text, weight);
		label_ = label;
		wmi_ = wmi;
	}

	/**
	 * Constructor for a new WikiAnnotation with a defined start point.
	 * 
	 * @param annotation
	 *            The previous {@link WikiAnnotation} to use.
	 * @param start
	 *            The new start point to use.
	 * @param wmi
	 *            The WMI Access.
	 */
	public WikiAnnotation(WikiAnnotation annotation, int start, WMISocket wmi) {
		super(annotation, start);
		label_ = annotation.label_;
		wmi_ = wmi;
	}

	/**
	 * A fully defined constructor for a new Annotation.
	 * 
	 * @param text
	 *            The text to replace.
	 * @param label
	 *            The label replacing the text.
	 * @param start
	 *            The start index.
	 * @param end
	 *            The end index.
	 * @param weight
	 *            The weight of the annotation.
	 * @param wmi
	 *            The WMI Access.
	 */
	public WikiAnnotation(String text, String label, int start, int end,
			double weight, WMISocket wmi) {
		super(text, start, end, weight);
		label_ = label;
		wmi_ = wmi;
	}

	@Override
	public String toString() {
		if (label_.charAt(0) == '[')
			return label_;

		String text = getText();
		try {
			// Append the link
			if (text.equals(label_)
					|| wmi_.getArticleByTitle(text) == wmi_
							.getArticleByTitle(label_))
				return "[[" + text + "]]";
			else
				return "[[" + label_ + "|" + text + "]]";
		} catch (Exception e) {
		}
		return "[[" + label_ + "|" + text + "]]";
	}
}
