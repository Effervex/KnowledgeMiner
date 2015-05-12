/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package util.wikipedia;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import util.IllegalDelimiterException;
import util.Pair;
import util.UtilityMethods;

/**
 * A class containing several static methods for parsing a string for particular
 * patterns or information.
 * 
 * @author Sam Sarjant
 */
public class WikiParser {
	private static final Pattern COMMENT_PATTERN = Pattern.compile(
			"\\<\\!--(.*?)--\\>", Pattern.DOTALL);
	/** The special opening/closing grouping characters to ignore delimiters. */
	public static final String[][] ALL_DELIMITERS = { { "'''", "'''" },
			{ "''", "''" }, { "(", ")" }, { "{", "}" }, { "[", "]" },
			{ "<", ">" } };
	public static final Pattern ANCHOR_PARSER = Pattern
			.compile("\\[\\[([^\\[\\]]+?)(?:\\|([^\\[\\]]+?))?\\]\\]");
	public static final Pattern ANCHOR_PARSER_WEIGHTED = Pattern
			.compile("\\[\\[([^\\[\\]]+?)(?:\\|([^\\[\\]]+?))?\\]\\]\\{(\\d\\.\\d+)\\}");
	public static final Pattern ANCHOR_PARSER_ROUGH = Pattern
			.compile("[^\\s\\[]*" + WikiParser.ANCHOR_PARSER.pattern()
					+ "[^\\s\\[]*");
	public static final Pattern LIST_PATTERN = Pattern.compile(
			"^(\\*|#|(?:;.+?:)).+?$", Pattern.MULTILINE);
	public static final Pattern MEDIA_LINK_PREFIX = Pattern
			.compile("\\[\\[\\w+:");
	/**
	 * The value returned for a sentence indicating both an individual and a
	 * collection.
	 */
	public static final byte BOTH = 0;
	/** If only brackets should be used as delimiters. */
	public static final String[][] BRACKETS_ONLY = { { "(", ")" },
			{ "{", "}" }, { "[", "]" }, { "<", ">" } };
	/** The value returned for a sentence indicating a collection. */
	public static final byte COLLECTION = -1;
	/** The default individuality value. */
	public static final byte DEFAULT = 7;
	public static final Pattern FUNCTION_PARSER = Pattern
			.compile("\\{\\{([^\\{\\}]+?)\\}\\}");
	/** The parser for a section/subsection heading. */
	public static final Pattern HEADER_PARSER = Pattern.compile(
			"^(['=]+)(.+?)\\1$", Pattern.MULTILINE);
	/** The value returned for a sentence indicating an individual. */
	public static final byte INDIVIDUAL = 1;
	/** The value returned for an unknown standing sentence. */
	public static final byte UNKNOWN_STANDING = -7;

	/**
	 * Checks the string array for a special list of characters, returning the
	 * index of the last character of the pattern if found.
	 * 
	 * @param charArray
	 *            The characters to check.
	 * @param pattern
	 *            The pattern to search for at the index.
	 * @param i
	 *            The index to check at.
	 * @return The index after the matched pattern if found, otherwise the old
	 *         index.
	 */
	private static int isPatternPresent(char[] charArray, String pattern, int i) {
		if (pattern == null)
			return i;
		if (i + pattern.length() >= charArray.length)
			return i;

		for (int j = 0; j < pattern.length(); j++) {
			if (charArray[i + j] != pattern.charAt(j))
				return i;
		}
		return i + pattern.length();
	}

	/**
	 * Checks if this matching character currently matches this character. This
	 * is simply an equals in many cases, but in other cases, the existing
	 * counts and surrounding context can make a difference.
	 * 
	 * @param charArray
	 *            The char array of the string being parsed.
	 * @param charIndex
	 *            The current char index.
	 * @param matchGroup
	 *            The matching group index.
	 * @param counts
	 *            The current counts for the matching groups.
	 * @param groupDelimiters
	 * @param matchingString
	 *            The matching String to check.
	 * @param opening
	 *            If the group is opening.
	 * @return The end of the matching group index if true or the original index
	 *         if false.
	 */
	private static int matchesGrouper(char[] charArray, int charIndex,
			int matchGroup, int groupSideIndex, int[] counts,
			String[][] groupDelimiters) {
		// Special case for apostrophes
		String matchingString = groupDelimiters[matchGroup][groupSideIndex];
		int j = isPatternPresent(charArray, matchingString, charIndex);
		if (j != charIndex) {
			// Special case for apostrophes
			if (matchingString.charAt(0) == '\'') {
				if (counts[matchGroup] == 0)
					counts[matchGroup]++;
				else
					counts[matchGroup]--;
			} else if (groupSideIndex == 0)
				counts[matchGroup]++;
			else
				counts[matchGroup]--;
			return j;
		}
		return charIndex;
	}

	/**
	 * Searches for the closest prior header by scanning a string backwards for
	 * a header.
	 * 
	 * @param priorText
	 *            The text to scan.
	 * @return The header found.
	 */
	public static String backSearchHeader(String priorText) {
		// Search backwards for a heading
		String reverseMarkup = new StringBuilder(priorText).reverse()
				.toString();
		Matcher m2 = HEADER_PARSER.matcher(reverseMarkup);
		if (m2.find())
			return new StringBuilder(m2.group(2)).reverse().toString();
		return null;
	}

	/**
	 * Removes all markup from a string and replaces non-unicode characters for
	 * unicode characters.
	 * 
	 * @param string
	 *            The string to pretty.
	 * @return The pretty-fied string.
	 */
	public static String cleanAllMarkup(String string) {
		string = cleanupUselessMarkup(string);
		string = cleanupExternalLinksAndStyling(string);
		// Fix nested anchors
		String oldStr = string;
		do {
			oldStr = string;
			string = string.replaceAll(
					"\\[\\[(?:[^\\[\\]]+\\|)?([^\\[\\]]+)\\]\\]", "$1");
		} while (!oldStr.equals(string));
		// Replace external links
		string = string.replaceAll("(?<!\\[)\\[[^\\[]\\S+ ([^\\]]+)\\]", "$1");

		// Remove bold/italic
		string = string.replaceAll("'{2,}", "");
		// Fix floating punctuation
		string = string.replaceAll(" (-\\S)", "$1");
		string = string.replaceAll(" ([,;:?])", "$1");
		string = string.replaceAll("\\([,;:?-] ?", "\\(");
		string = string.replaceAll("\\([,;:?-] ?", "");
		string = string.replaceAll("([(\\[{]) ", "$1");
		string = string.replaceAll(" ([)\\]}])", "$1");
		return string;
	}

	/**
	 * Removes all particular bracketed expression from a string.
	 * 
	 * @param string
	 *            The String to clean.
	 * @param startPoint
	 *            The point to start cleaning.
	 * @param leftBracket
	 *            The left bracket.
	 * @param rightBracket
	 *            The right bracket.
	 * @param onlyOnce
	 *            If only one pair of brackets should be removed.
	 * @param ungroupedOnly
	 *            If only ungrouped brackets should be cleaned.
	 * @return The cleaned String.
	 */
	public static String cleanBrackets(String string, int startPoint,
			char leftBracket, char rightBracket, boolean onlyOnce,
			boolean ungroupedOnly) {
		StringBuilder buffer = new StringBuilder();
		Pair<Integer, Integer> bracketGroup;
		while ((!ungroupedOnly && (bracketGroup = findBrackets(string,
				startPoint, leftBracket, rightBracket)) != null)
				|| (ungroupedOnly && (bracketGroup = findUngroupedBrackets(
						string, startPoint, leftBracket, rightBracket)) != null)) {
			buffer.append(string.substring(startPoint, bracketGroup.objA_));
			startPoint = bracketGroup.objB_ + 1;
			if (onlyOnce)
				break;
		}
		buffer.append(string.substring(startPoint));
		return buffer.toString();
	}

	/**
	 * Finds the first instance of a given set of bracket types and returns the
	 * starting and ending indices (inclusive).
	 * 
	 * @param string
	 *            The string to search.
	 * @param startPoint
	 *            The start point in the string.
	 * @param leftBracket
	 *            The left bracket to find.
	 * @param rightBracket
	 *            The right bracket to find.
	 * @return The starting and ending indices of the brackets (inclusive) or
	 *         null if not found.
	 */
	public static Pair<Integer, Integer> findBrackets(String string,
			int startPoint, char leftBracket, char rightBracket) {
		char[] charArray = string.toCharArray();
		int bracketCount = 0;
		int bracketStart = 0;
		boolean escaped = false;
		for (int i = startPoint; i < charArray.length; i++) {
			char c = charArray[i];
			if (escaped) {
				escaped = false;
				continue;
			} else if (c == '\\') {
				escaped = true;
				continue;
			}

			if (c == leftBracket) {
				if (bracketCount == 0)
					bracketStart = i;
				bracketCount++;
			} else if (c == rightBracket) {
				bracketCount--;
				if (bracketCount < 0)
					bracketCount = 0;
				else if (bracketCount == 0)
					return new Pair<Integer, Integer>(bracketStart, i);
			}
		}
		return null;
	}

	/**
	 * Find the first occurrence of ungrouped brackets.
	 * 
	 * @param string
	 *            The string to search.
	 * @param startPoint
	 *            The start point to search.
	 * @param leftBracket
	 *            The left bracket to search for.
	 * @param rightBracket
	 *            The right bracket to search for.
	 * @return The left and right indexes of the bracketed expression.
	 */
	public static Pair<Integer, Integer> findUngroupedBrackets(String string,
			int startPoint, char leftBracket, char rightBracket) {
		String[][] groupDelimiters = new String[3][2];
		int i = 0;
		for (String[] bracketSet : BRACKETS_ONLY) {
			if (bracketSet[0].charAt(0) != leftBracket)
				groupDelimiters[i++] = bracketSet;
		}
		try {
			int startBracket = first(string, startPoint,
					new String[] { leftBracket + "" }, new String[0],
					groupDelimiters).length()
					+ startPoint - 1;
			return findBrackets(string, startBracket, leftBracket, rightBracket);
		} catch (Exception e) {
		}
		return null;
	}

	/**
	 * Removes pre-existing anchors from a text and replaces them with
	 * whitespace.
	 * 
	 * @param text
	 *            The text to remove anchors from.
	 * @return The text with pre-existing anchors removed.
	 */
	public static String replaceBracketedByWhitespace(String text,
			char leftBracket, char rightBracket) {
		int startPoint = 0;
		Pair<Integer, Integer> leftRight = null;
		StringBuilder buffer = new StringBuilder();
		do {
			leftRight = findUngroupedBrackets(text, startPoint, leftBracket,
					rightBracket);
			if (leftRight == null)
				break;
			// Shift left until whitespace
			int left = leftRight.objA_;
			int right = leftRight.objB_ + 1;
			while (left > 0 && !Character.isWhitespace(text.charAt(left - 1)))
				left--;
			while (right < text.length()
					&& !Character.isWhitespace(text.charAt(right)))
				right++;

			// Replace by whitespace
			buffer.append(text.substring(startPoint, left)
					+ StringUtils.repeat(' ', right - left));
			startPoint = right;
		} while (leftRight != null);
		buffer.append(text.substring(startPoint));
		return buffer.toString();
	}

	/**
	 * Removes markup that may be useful elsewhere, but for the first sentence
	 * (and other places) is of no use. E.g. external links and internal
	 * styling.
	 * 
	 * @param string
	 *            The string to cleanup.
	 * @return The cleaned String.
	 */
	public static String cleanupExternalLinksAndStyling(String string) {
		// Remove Wiki syntax links
		string = cleanBrackets(string, 0, '{', '}', false, false);
		// Remove Wiki internal links
		string = string.replaceAll("(?!=\\[)\\[[^\\[\\] ]+\\](?!\\])", "");
		// Remove floating empty brackets
		string = string.replaceAll(" ?\\((([^()]+ )|(\\W*))\\) ?", " ");
		// Remove listed styling
		string = LIST_PATTERN.matcher(string).replaceAll("");
		// Close punctuation gaps
		string = string.replaceAll(" ([,;:.?!])", "$1");
		// Remove image/file links (file:X|Y|Z|W)
		StringBuilder buffer = new StringBuilder();
		Matcher m = MEDIA_LINK_PREFIX.matcher(string);
		int startPoint = 0;
		while (m.find(startPoint)) {
			// Found media link
			int bracketStart = m.start();
			Pair<Integer, Integer> location = findBrackets(string,
					bracketStart, '[', ']');
			if (location != null) {
				buffer.append(string.substring(startPoint, location.objA_));
				startPoint = location.objB_ + 1;
			} else {
				buffer.append(string.substring(startPoint, bracketStart + 1));
				startPoint = bracketStart + 1;
			}
		}
		buffer.append(string.substring(startPoint));
		string = buffer.toString();
		return string;
	}

	/**
	 * Cleans up the markup by removing unnecessary comments and such. Wikilinks
	 * and other informative markup things are still left behind.
	 * 
	 * @param markup
	 *            The markup to clean.
	 * @return The cleaned markup.
	 */
	public static String cleanupUselessMarkup(String markup) {
		// Remove commented stuff (across multiple lines)
		markup = COMMENT_PATTERN.matcher(markup).replaceAll("");
		// Fixing line breaks.
		markup = markup.replaceAll("<br ?/?>", "\n");
		// Fixing endashes/emdashes
		markup = markup.replaceAll("&ndash;", "-");
		markup = markup.replaceAll("&mdash;", " - ");
		// Fixing spaces
		markup = markup.replaceAll("&nbsp;", " ");
		// Removing sole html tags
		markup = markup.replaceAll("<[^>]+/>", "");
		// Removing html groups.
		Pattern p = Pattern.compile("<(\\w+).+?/\\1>", Pattern.DOTALL);
		markup = p.matcher(markup).replaceAll("");
		// Removing colon prefixed lines and italicised lines
		markup = markup.replaceAll("(^|\\n):.+?\\n", "");
		markup = markup.replaceAll("(^|\\n)''\\w.+?''\\n", "");
		// Condensing multiple spaces into one.
		markup = markup.replaceAll("  +", " ");
		// Remove underscored items
		markup = markup.replaceAll("_+\\w+_+", "");
		return markup;
	}

	/**
	 * Finds the first substring that occurs before the given delimiter(s). The
	 * delimiter is not allowed to be within brackets or other markup.
	 * 
	 * @param string
	 *            The string to search.
	 * @param startPoint
	 *            teh starting point to search from.
	 * @param safeDelimiters
	 *            The delimiter(s) to break on safely.
	 * @param exceptionDelimiters
	 *            The delimiter(s) that cause an exception if found within
	 *            brackets.
	 * @return The substring from startPoint to one of the delimiters.
	 * @throws IllegalDelimiterException
	 *             If the break delimiter is found.
	 */
	public static String first(String string, int startPoint,
			String[] safeDelimiters, String[] exceptionDelimiters)
			throws IllegalDelimiterException {
		return first(string, startPoint, safeDelimiters, exceptionDelimiters,
				ALL_DELIMITERS);
	}

	/**
	 * Finds the first substring that occurs before the given delimiter(s). The
	 * delimiter is not allowed to be within brackets or other markup.
	 * 
	 * @param string
	 *            The string to search.
	 * @param startPoint
	 *            teh starting point to search from.
	 * @param safeDelimiters
	 *            The delimiter(s) to break on safely.
	 * @param exceptionDelimiters
	 *            The delimiter(s) that cause an exception if found within
	 *            brackets.
	 * @param groupDelimiters
	 *            The delimiters for enclosing groups (such as brackets).
	 * @return The substring from startPoint to one of the delimiters.
	 * @throws IllegalDelimiterException
	 *             If the break delimiter is found.
	 */
	public static String first(String string, int startPoint,
			String[] safeDelimiters, String[] exceptionDelimiters,
			String[][] groupDelimiters) throws IllegalDelimiterException {
		if (startPoint >= string.length())
			return "";
		char[] charArray = string.toCharArray();
		int[] counts = new int[groupDelimiters.length];
		boolean escaped = false;
		if (exceptionDelimiters == null)
			exceptionDelimiters = new String[0];

		// Run through character by character.
		for (int i = startPoint; i < charArray.length; i++) {
			char c = charArray[i];
			// Checking for escape character
			if (escaped) {
				escaped = false;
				continue;
			} else if (c == '\\') {
				escaped = true;
				continue;
			}

			// Check for grouping characters
			boolean canDelimit = true;
			boolean usedClosingBracket = false;
			for (int g = 0; g < groupDelimiters.length; g++) {
				boolean bracketFound = false;
				// Opening
				int j = matchesGrouper(charArray, i, g, 0, counts,
						groupDelimiters);
				if (j != i) {
					i = j - 1;
					bracketFound = true;
				} else {
					j = matchesGrouper(charArray, i, g, 1, counts,
							groupDelimiters);
					if (j != i) {
						i = j - 1;
						bracketFound = true;
					}
				}

				// If there is a group, do not delimit
				if (counts[g] > 0)
					canDelimit = false;

				if (bracketFound) {
					if (counts[g] >= 0)
						usedClosingBracket = true;
				}
			}
			if (usedClosingBracket)
				continue;

			if (canDelimit) {
				// Check for safe delimiter
				for (String delimiter : safeDelimiters) {
					int j = isPatternPresent(charArray, delimiter, i);
					if (j != i)
						return string.substring(startPoint, j);
				}
			}
			// Check for exception delimiter
			for (String delimiter : exceptionDelimiters) {
				int j = isPatternPresent(charArray, delimiter, i);
				if (j != i) {
					throw new IllegalDelimiterException(delimiter,
							string.substring(startPoint, j));
				}
			}
		}

		return string.substring(startPoint);
	}

	/**
	 * Checks if there are open brackets in the string.
	 * 
	 * @param string
	 *            The string to check.
	 * @return True if there are any open brackets.
	 */
	public static boolean isOpenBrackets(String string) {
		char[] charArray = string.toCharArray();
		int bracketCount = 0;
		for (char c : charArray) {
			if (c == '(')
				bracketCount++;
			if (c == ')')
				bracketCount--;
			if (bracketCount < 0)
				return true;
		}
		if (bracketCount > 0)
			return true;
		return false;
	}

	/**
	 * Finds the first substring starting from the beginning of the string.
	 * 
	 * @param string
	 *            The string to parse.
	 * @param delimiter
	 *            The delimiter to split the string.
	 * @param breakDelimiter
	 *            The halting group pattern delimiter to use in case the first
	 *            delimiter is not found.
	 * @return The first occurrence of the string finishing on the delimiter or
	 *         the entire string if delimiter not found (outside of brackets and
	 *         markup).
	 * @throws IllegalDelimiterException
	 *             If the break delimiter is found.
	 */
	public static String first(String string, String delimiter,
			String breakDelimiter) throws IllegalDelimiterException {
		return first(string, delimiter, breakDelimiter, ALL_DELIMITERS);
	}

	/**
	 * Finds the first substring starting from the beginning of the string.
	 * 
	 * @param string
	 *            The string to parse.
	 * @param delimiter
	 *            The delimiter to split the string.
	 * @param breakDelimiter
	 *            The halting group pattern delimiter to use in case the first
	 *            delimiter is not found.
	 * @param groupDelimiters
	 *            The delimiters for enclosing groups (such as brackets).
	 * @return The first occurrence of the string finishing on the delimiter or
	 *         the entire string if delimiter not found (outside of brackets and
	 *         markup).
	 * @throws IllegalDelimiterException
	 *             If the break delimiter is found.
	 */
	public static String first(String string, String delimiter,
			String breakDelimiter, String[][] groupDelimiters)
			throws IllegalDelimiterException {
		if (breakDelimiter == null)
			return first(string, 0, new String[] { delimiter }, null,
					groupDelimiters);
		return first(string, 0, new String[] { delimiter },
				new String[] { breakDelimiter }, groupDelimiters);
	}

	/**
	 * Checks if an Article title begins with List of (which is not a good
	 * candidate article).
	 * 
	 * @param title
	 *            The article title.
	 * @return Trus if the Article is 'List of', false otherwise.
	 */
	public static boolean isAListOf(String title) {
		String regExp = "Lists? of .*";
		Matcher matcher = UtilityMethods.getRegMatcher(title, regExp);
		if (matcher.matches())
			return true;
		return false;
	}

	/**
	 * Replaces all (ungrouped) instances of delimiter with another string.
	 * 
	 * @param string
	 *            The string to modify.
	 * @param target
	 *            The string to replace.
	 * @param replacement
	 *            The replacement string.
	 * @return The replaced string.
	 */
	public static String replaceAll(String string, String target,
			String replacement) {
		if (string.equals(target))
			return replacement;

		string = " " + string + " ";
		StringBuilder resewn = new StringBuilder();
		for (String split : split(string, target)) {
			if (resewn.length() != 0)
				resewn.append(replacement);
			resewn.append(split);
		}
		if (resewn.length() <= 2)
			return string;
		return resewn.toString().substring(1, resewn.length() - 1);
	}

	/**
	 * Splits a string at the given delimiter, but does not split within any
	 * form of brackets.
	 * 
	 * @param string
	 *            The string to split.
	 * @param delimiter
	 *            The delimiter to split by (outside of brackets)
	 * @return An array of strings that do not contain the delimiter except if
	 *         in brackets.
	 */
	public static ArrayList<String> split(String string, String delimiter) {
		ArrayList<String> results = new ArrayList<>();
		if (string.equals(delimiter)) {
			results.add("");
			return results;
		}
		if (string.startsWith(delimiter)) {
			string = string.substring(delimiter.length());
			results.add("");
		}

		String result = null;
		try {
			while (!(result = first(string, delimiter, null)).equals(string)) {
				string = string.substring(result.length());
				results.add(result.substring(0,
						result.length() - delimiter.length()));
			}
		} catch (IllegalDelimiterException e) {
		}

		if (!string.isEmpty()) {
			if (string.endsWith(delimiter)) {
				string = string.substring(0,
						string.length() - delimiter.length());
				results.add(string);
				results.add("");
			} else
				results.add(string);
		}

		return results;
	}

	/**
	 * Gets the index of a given pattern within a string, taking grouping
	 * patterns into acccount.
	 * 
	 * @param string
	 *            The string to search.
	 * @param pattern
	 *            The pattern to search for.
	 * @return The index of the pattern or -1.
	 */
	public static int indexOf(String string, String pattern) {
		String result;
		try {
			result = first(string, pattern, null);
			if (result.endsWith(pattern))
				return result.length() - pattern.length();
		} catch (IllegalDelimiterException e) {
		}
		return -1;
	}
}
