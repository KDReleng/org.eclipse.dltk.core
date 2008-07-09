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
package org.eclipse.dltk.debug.core;

import org.eclipse.dltk.debug.core.IDebugOptions.BooleanOption;

public class DebugOption {

	/**
	 * All commands should be treated as asynchronous
	 */
	public static final BooleanOption DBGP_ASYNC = new BooleanOption(
			"DBGP_ASYNC", true);

}
