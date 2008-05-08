/*******************************************************************************
 * Copyright (c) 2008 xored software, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Alex Panchenko)
 *     xored software, Inc. - Fixed bug for end of file less whan 4 symbol comments (Andrei Sobolev)
 *******************************************************************************/
package org.eclipse.dltk.compiler.task;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.core.DLTKCore;

public class TodoTaskSimpleParser {

	private final ITaskReporter taskReporter;
	private final boolean caseSensitive;
	private final char[][] tags;
	private final int minTagLength;
	private final int[] priorities;

	public TodoTaskSimpleParser(ITaskReporter taskReporter,
			TodoTaskPreferences preferences) {
		this.taskReporter = taskReporter;
		this.caseSensitive = preferences.isCaseSensitive();
		final List tags = preferences.getTaskTags();
		if (!tags.isEmpty()) {
			final int tagCount = tags.size();
			this.tags = new char[tagCount][];
			this.priorities = new int[tagCount];
			int minTagLength = Integer.MAX_VALUE;
			for (int i = 0; i < tagCount; ++i) {
				final TodoTask task = (TodoTask) tags.get(i);
				String tagName = task.name;
				if (!caseSensitive) {
					tagName = tagName.toUpperCase();
				}
				if (tagName.length() < minTagLength) {
					minTagLength = tagName.length();
				}
				this.tags[i] = tagName.toCharArray();
				if (TodoTask.PRIORITY_HIGH.equals(task.priority)) {
					priorities[i] = IMarker.PRIORITY_HIGH;
				} else if (TodoTask.PRIORITY_LOW.equals(task.priority)) {
					priorities[i] = IMarker.PRIORITY_LOW;
				} else {
					priorities[i] = IMarker.PRIORITY_NORMAL;
				}
			}
			this.minTagLength = minTagLength;
		} else {
			this.tags = null;
			this.minTagLength = 0;
			this.priorities = null;
		}
	}

	public boolean isValid() {
		return tags != null && tags.length > 0;
	}

	private int lineNumber;
	private int contentPos;
	private int contentEnd;

	public void parse(char[] content) {
		lineNumber = 0;
		contentPos = 0;
		contentEnd = content.length;
		while (contentPos < contentEnd) {
			int begin = contentPos;
			final int end = findEndOfLine(content);
			if (begin < end) {
				begin = skipSpaces(content, begin, end);
				if (begin < end && content[begin] == '#') {
					++begin;
					begin = skipSpaces(content, begin, end);
					if (begin + minTagLength <= end) {
						processLine(content, begin, end);
					}
				}
			}
			++lineNumber;
		}
	}

	private final int skipSpaces(char[] content, int pos, final int end) {
		while (pos < end && Character.isWhitespace(content[pos])) {
			++pos;
		}
		return pos;
	}

	private void processLine(char[] content, int begin, final int end) {
		for (int i = 0; i < tags.length; ++i) {
			final char[] tag = tags[i];
			if (begin + tag.length < content.length) {
				char ch = content[begin + tag.length];
				if (begin + tag.length < end && isEnd(ch)
						|| begin + tag.length == end) {
					if (compareTag(content, begin, tag)) {
						final String msg = new String(content, begin, end
								- begin);
						try {
							taskReporter.reportTask(msg, lineNumber,
									priorities[i], begin, contentPos);
						} catch (CoreException e) {
							DLTKCore.error("Error in reportTask()", e);
						}
					}
				}
			}
		}
	}

	private boolean isEnd(char ch) {
		return Character.isWhitespace(ch) || ch == ':' || ch == '('
				|| ch == ';' || ch == ':' || ch == '[' || ch == ']'
				|| ch == ')' || ch == '@' || ch == '!' || ch == '%'
				|| ch == '#' || ch == '*' || ch == '^' || ch == '~'
				|| ch == '&' || ch == '|' || ch == '\\' || ch == '/';
	}

	private boolean compareTag(char[] content, int pos, final char[] tag) {
		if (caseSensitive) {
			for (int j = 0; j < tag.length; ++j) {
				if (content[pos + j] != tag[j]) {
					return false;
				}
			}
		} else {
			for (int j = 0; j < tag.length; ++j) {
				if (Character.toUpperCase(content[pos + j]) != tag[j]) {
					return false;
				}
			}
		}
		return true;
	}

	private int findEndOfLine(char[] content) {
		while (contentPos < contentEnd) {
			if (content[contentPos] == '\r') {
				final int endLine = contentPos;
				++contentPos;
				if (contentPos < contentEnd && content[contentPos] == '\n') {
					++contentPos;
				}
				return endLine;
			} else if (content[contentPos] == '\n') {
				final int endLine = contentPos;
				++contentPos;
				return endLine;
			} else {
				++contentPos;
			}
		}
		return contentPos;
	}

}
