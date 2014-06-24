/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import io.resources.WMISocket;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Compares two articles, ordering the more popular one (by inlinks) first.
 * 
 * @author Sam Sarjant
 */
public class PopularityComparator implements Comparator<Integer> {
	private Map<Integer, Integer> inLinks_;

	public PopularityComparator(Collection<Integer> articles, WMISocket wmi) {
		try {
			Integer[] articleArray = articles.toArray(new Integer[articles
					.size()]);
			List<Integer[]> numLinks = wmi.getNumLinks(articleArray);
			Iterator<Integer[]> iter = numLinks.iterator();
			inLinks_ = new HashMap<>();
			for (int i = 0; i < articleArray.length; i++) {
				inLinks_.put(articleArray[i], iter.next()[0]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public int compare(Integer o1, Integer o2) {
		int result = Double.compare(inLinks_.get(o1), inLinks_.get(o2));
		if (result != 0)
			return -result;
		return Double.compare(o1, o2);
	}
}
