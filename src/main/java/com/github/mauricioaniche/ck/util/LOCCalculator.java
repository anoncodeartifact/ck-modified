package com.github.mauricioaniche.ck.util;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LOCCalculator {

	public static class LocAndComments {
		public final int loc;
		public final int commentLines;

		public LocAndComments(int loc, int commentLines) {
			this.loc = loc;
			this.commentLines = commentLines;
		}
	}

	private static Logger log = Logger.getLogger(LOCCalculator.class);
	
	public static LocAndComments calculateWithComments(String sourceCode) {
		try {
			InputStream is = IOUtils.toInputStream(sourceCode);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			return getNumberOfLinesAndComments(reader);
		} catch (IOException e) {
			log.error("Error when counting lines", e);
			return new LocAndComments(0, 0);
		}
	}



	// Code extracted from https://gist.github.com/shiva27/1432290

	/**
	 * This class  counts the number of source code lines by excluding comments, in a Java file
	 * The pseudocode is as below
	 *
	 * Initial: Set count = 0, commentBegan = false
	 * Start: Read line
	 * Begin: If line is not null, goto Check, else goto End
	 * Check: If line is a trivial line(after trimming, either begins with // or is ""), goto Start
	 *        If commentBegan = true
	 *             if comment has not ended in line
	 *                goto Start
	 *              else
	 *                line = what remains in the line after comment ends
	 *                commenBegan = false
	 *                if line is trivial
	 *                   goto Start
	 * 		  If line is a valid source code line, count++
	 *        If comment has begun in the line, set commentBegan = true
	 *        goto Start
	 * End: print count
	 */
	private static LocAndComments getNumberOfLinesAndComments(BufferedReader bReader)
        throws IOException {

			int loc = 0;
			int commentLines = 0;

			boolean commentBegan = false;
			String line;

			while ((line = bReader.readLine()) != null) {
				String original = line;
				line = line.trim();

				if ("".equals(line)) {
					continue;
				}

				// Line comment-only
				if (!commentBegan && line.startsWith("//")) {
					commentLines++;
					continue;
				}

				// If we are currently inside a block comment, this line is a comment line
				// unless it ends the comment and has code afterwards.
				if (commentBegan) {
					if (commentEnded(line)) {
						int end = line.indexOf("*/");
						String after = line.substring(end + 2).trim();
						commentBegan = false;

						// The part before */ is comment content => count this line as comment line
						// If there is no code after, it's purely a comment line.
						if ("".equals(after) || after.startsWith("//")) {
							commentLines++;
							continue;
						} else {
							// Mixed line: comment ends, then code continues
							// Count as LOC (and NOT as comment-only line)
							if (isSourceCodeLine(after)) {
								loc++;
							}
							// The remainder might begin another block comment
							if (commentBegan(after)) {
								commentBegan = true;
							}
							continue;
						}
					} else {
						// Entire line is within block comment
						commentLines++;
						continue;
					}
				}

				// Not inside a block comment here

				// Handle block comment starting at beginning of line (comment-only)
				if (line.startsWith("/*")) {
					// If it also ends on the same line, check if there's code outside the comment
					if (commentEnded(line)) {
						// If comment starts at 0, treat as comment-only unless code exists after */
						int end = line.indexOf("*/");
						String after = line.substring(end + 2).trim();
						if ("".equals(after) || after.startsWith("//")) {
							commentLines++;
							continue;
						} else {
							if (isSourceCodeLine(after)) {
								loc++;
							}
							if (commentBegan(after)) {
								commentBegan = true;
							}
							continue;
						}
					} else {
						// Block comment begins and continues
						commentLines++;
						commentBegan = true;
						continue;
					}
				}

				// Normal code line (may contain trailing comments)
				if (isSourceCodeLine(line)) {
					loc++;
				}

				// If this line begins a block comment that doesn't end, we enter comment mode
				if (commentBegan(line)) {
					commentBegan = true;
				}

				// If it contains // anywhere after code, that's not a comment-only line, so we do NOT count it.
				// If you want "lines containing comments" as well, that's a separate metric.
			}

			return new LocAndComments(loc, commentLines);
		}

	/**
	 *
	 * @param line
	 * @return This method checks if in the given line a comment has begun and has not ended
	 */
	private static boolean commentBegan(String line) {
		// If line = /* */, this method will return false
		// If line = /* */ /*, this method will return true
		int index = line.indexOf("/*");
		if (index < 0) {
			return false;
		}
		int quoteStartIndex = line.indexOf("\"");
		if (quoteStartIndex != -1 && quoteStartIndex < index) {
			while (quoteStartIndex > -1) {
				line = line.substring(quoteStartIndex + 1);
				int quoteEndIndex = line.indexOf("\"");
				line = line.substring(quoteEndIndex + 1);
				quoteStartIndex = line.indexOf("\"");
			}
			return commentBegan(line);
		}
		return !commentEnded(line.substring(index + 2));
	}

	/**
	 *
	 * @param line
	 * @return This method checks if in the given line a comment has ended and no new comment has not begun
	 */
	private static boolean commentEnded(String line) {
		// If line = */ /* , this method will return false
		// If line = */ /* */, this method will return true
		int index = line.indexOf("*/");
		if (index < 0) {
			return false;
		} else {
			String subString = line.substring(index + 2).trim();
			if ("".equals(subString) || subString.startsWith("//")) {
				return true;
			}
			if(commentBegan(subString))
			{
				return false;
			}
			else
			{
				return true;
			}
		}
	}

	/**
	 *
	 * @param line
	 * @return This method returns true if there is any valid source code in the given input line. It does not worry if comment has begun or not.
	 * This method will work only if we are sure that comment has not already begun previously. Hence, this method should be called only after {@link #commentBegan(String)} is called
	 */
	private static boolean isSourceCodeLine(String line) {
		boolean isSourceCodeLine = false;
		line = line.trim();
		if ("".equals(line) || line.startsWith("//")) {
			return isSourceCodeLine;
		}
		if (line.length() == 1) {
			return true;
		}
		int index = line.indexOf("/*");
		if (index != 0) {
			return true;
		} else {
			while (line.length() > 0) {
				line = line.substring(index + 2);
				int endCommentPosition = line.indexOf("*/");
				if (endCommentPosition < 0) {
					return false;
				}
				if (endCommentPosition == line.length() - 2) {
					return false;
				} else {
					String subString = line.substring(endCommentPosition + 2)
							.trim();
					if ("".equals(subString) || subString.indexOf("//") == 0) {
						return false;
					} else {
						if (subString.startsWith("/*")) {
							line = subString;
							continue;
						}
						return true;
					}
				}

			}
		}
		return isSourceCodeLine;
	}

}
