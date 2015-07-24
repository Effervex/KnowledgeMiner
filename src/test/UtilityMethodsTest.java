/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import util.UtilityMethods;

/**
 * 
 * @author Sam Sarjant
 */
public class UtilityMethodsTest {

	@Test
	public void testManipulateStringCapitalisation() {
		// Basic one-word
		Set<String> titles = UtilityMethods
				.manipulateStringCapitalisation("Dog");
		assertEquals(titles.size(), 2);
		assertTrue(titles.contains("Dog"));
		assertTrue(titles.contains("dog"));

		// Basic two-words
		titles = UtilityMethods.manipulateStringCapitalisation("Many Dogs");
		assertEquals(titles.size(), 3);
		assertTrue(titles.contains("Many Dogs"));
		assertTrue(titles.contains("Many dogs"));
		assertTrue(titles.contains("many dogs"));

		// Scoped (One-One)
		titles = UtilityMethods.manipulateStringCapitalisation("Dog (Hairy)");

		assertTrue(titles.contains("Dog (Hairy)"));
		assertTrue(titles.contains("Dog (hairy)"));
		assertTrue(titles.contains("dog (hairy)"));

		// Scoped (One-Two)
		titles = UtilityMethods
				.manipulateStringCapitalisation("Dog (Quite Hairy)");

		assertTrue(titles.contains("Dog (Quite Hairy)"));
		assertTrue(titles.contains("Dog (Quite hairy)"));
		assertTrue(titles.contains("Dog (quite hairy)"));
		assertTrue(titles.contains("dog (quite hairy)"));

		// Scoped (Two-Two)
		titles = UtilityMethods
				.manipulateStringCapitalisation("Many Dogs (Quite Hairy)");

		assertTrue(titles.contains("Many Dogs (Quite Hairy)"));
		assertTrue(titles.contains("Many Dogs (Quite hairy)"));
		assertTrue(titles.contains("Many Dogs (quite hairy)"));
		assertTrue(titles.contains("Many dogs (Quite Hairy)"));
		assertTrue(titles.contains("Many dogs (Quite hairy)"));
		assertTrue(titles.contains("Many dogs (quite hairy)"));
		assertTrue(titles.contains("many dogs (quite hairy)"));

		// Scoped (The)
		titles = UtilityMethods
				.manipulateStringCapitalisation("Dog (The Great)");

		assertTrue(titles.contains("Dog (The Great)"));
		assertTrue(titles.contains("Dog (The great)"));
		assertTrue(titles.contains("Dog (the great)"));
		assertTrue(titles.contains("dog (the great)"));
		assertTrue(titles.contains("Dog (Great)"));
		assertTrue(titles.contains("Dog (great)"));
		assertTrue(titles.contains("dog (great)"));

		// Scoped (The Two or more)
		titles = UtilityMethods
				.manipulateStringCapitalisation("Dog (The Super Great)");

		assertTrue(titles.contains("Dog (The Super Great)"));
		assertTrue(titles.contains("Dog (The super great)"));
		assertTrue(titles.contains("Dog (the super great)"));
		assertTrue(titles.contains("dog (the super great)"));
		assertTrue(titles.contains("Dog (Super Great)"));
		assertTrue(titles.contains("Dog (Super great)"));
		assertTrue(titles.contains("Dog (super great)"));
		assertTrue(titles.contains("dog (super great)"));
	}

}
