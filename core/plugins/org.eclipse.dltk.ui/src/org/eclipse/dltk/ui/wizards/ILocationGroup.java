/*******************************************************************************
 * Copyright (c) 2009 xored software, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.ui.wizards;

import java.net.URI;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.dltk.core.environment.IEnvironment;
import org.eclipse.dltk.launching.IInterpreterInstall;

public interface ILocationGroup {

	public interface Listener {
		void update(ILocationGroup locationGroup, Object arg);
	}

	String getProjectName();

	IProject getProjectHandle();

	IEnvironment getEnvironment();

	IPath getLocation();

	/**
	 * @since 2.0
	 */
	URI getLocationURI();

	/**
	 * @since 2.0
	 */
	IInterpreterInstall getInterpreter();

	void addLocationListener(Listener listener);

	boolean getDetect();

	/**
	 * @return
	 * @since 2.0
	 */
	boolean isInWorkspace();

	/**
	 * @return
	 * @since 2.0
	 */
	boolean isSrc();

	/**
	 * @return
	 * @since 2.0
	 */
	String getScriptNature();

}
