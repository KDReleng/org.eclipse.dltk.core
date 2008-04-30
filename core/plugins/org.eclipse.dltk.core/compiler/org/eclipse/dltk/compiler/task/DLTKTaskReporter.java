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
 *******************************************************************************/
package org.eclipse.dltk.compiler.task;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

/**
 * Default implementation of the {@link ITaskReporter}
 */
public class DLTKTaskReporter implements ITaskReporter {

	private final IResource resource;
	private boolean tasksCleared;

	public DLTKTaskReporter(IResource resource) {
		this.resource = resource;
		tasksCleared = false;
	}

	public void clearTasks() {
		if (!tasksCleared) {
			try {
				resource.deleteMarkers(IMarker.TASK, true,
						IResource.DEPTH_INFINITE);
			} catch (CoreException e) {
				System.err.println(e);
			}
			tasksCleared = true;
		}
	}

	public void reportTask(String message, int lineNumber, int priority,
			int charStart, int charEnd) throws CoreException {
		IMarker m = resource.createMarker(IMarker.TASK);
		m.setAttribute(IMarker.LINE_NUMBER, lineNumber + 1);
		m.setAttribute(IMarker.MESSAGE, message);
		m.setAttribute(IMarker.PRIORITY, priority);
		m.setAttribute(IMarker.CHAR_START, charStart);
		m.setAttribute(IMarker.CHAR_END, charEnd);
		m.setAttribute(IMarker.USER_EDITABLE, Boolean.FALSE);
	}

	public Object getAdapter(Class adapter) {
		return null;
	}

}
