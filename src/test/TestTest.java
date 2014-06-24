/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Test;

import util.collection.CacheMap;
import edu.stanford.nlp.process.Morphology;

/**
 * 
 * @author Sam Sarjant
 */
public class TestTest {

	@Test
	public void testCacheMap() {
		CacheMap<Integer, Integer> cacheMap = new CacheMap<>(10, false);
		for (int i = 0; i < 20; i++)
			cacheMap.put(i, i);
		assertEquals(cacheMap.size(), 10);
		assertFalse(cacheMap.containsKey(9));
		assertTrue(cacheMap.containsKey(10));
		assertTrue(cacheMap.containsKey(15));
		assertTrue(cacheMap.containsKey(19));

		cacheMap.remove(10);
		cacheMap.put(10, 10);
		cacheMap.put(21, 21);
		assertTrue(cacheMap.containsKey(10));
		assertFalse(cacheMap.containsKey(11));

		cacheMap.put(12, 12);
		cacheMap.put(22, 22);
		assertTrue(cacheMap.containsKey(12));
		assertFalse(cacheMap.containsKey(13));
	}

	@Test
	public void testRetainAll() {
		SortedSet<String> setA = new TreeSet<>();
		SortedSet<String> setB = new TreeSet<>();
		int countA = 1000;
		for (int i = 0; i < countA; i++) {
			setA.add("A" + i);
		}
		for (int i = 0; i < countA * countA; i++) {
			setB.add("A" + i);
		}

		long start = System.currentTimeMillis();
		setB.retainAll(setA);
		long end = System.currentTimeMillis();
		System.out.println(end - start);
	}
	
	@Test
	public void stringCompare() {
		assertTrue("Cata]".compareTo("Catan]") < 0);
	}

	@Test
	public void testReplaceAll() {
		String nestedTest = "(#<(CatFn #<(AnotherFn Dog)>)>)";
		String result = nestedTest.replaceAll("#<(\\(.+?\\))>", "$1");
		assertEquals(result, "((CatFn #<(AnotherFn Dog))>)");

		while (nestedTest.contains("#<"))
			nestedTest = nestedTest.replaceAll("#<(\\(.+?\\))>", "$1");
		assertEquals(nestedTest, "((CatFn (AnotherFn Dog)))");
	}

	@Test
	public void testStemming() {
		System.out.println(Morphology.stemStatic("Ponies", "NNS"));
	}
}
