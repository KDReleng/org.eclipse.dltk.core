/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *******************************************************************************/
package org.eclipse.dltk.internal.core.index2;

import java.util.Collection;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.environment.EnvironmentPathUtils;
import org.eclipse.dltk.core.environment.IFileHandle;
import org.eclipse.dltk.core.index2.IIndexer;
import org.eclipse.dltk.core.search.indexing.AbstractJob;
import org.eclipse.dltk.internal.core.util.Util;

/**
 * Abstract request for performing operation on index.
 * 
 * @author michael
 * 
 */
public abstract class AbstractIndexRequest extends AbstractJob {

	AbstractProjectIndexer projectIndexer;
	ProgressJob progressJob;

	public AbstractIndexRequest(AbstractProjectIndexer indexer,
			ProgressJob progressJob) {
		this.projectIndexer = indexer;
		this.progressJob = progressJob;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((projectIndexer == null) ? 0 : projectIndexer.hashCode());
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractIndexRequest other = (AbstractIndexRequest) obj;
		if (projectIndexer == null) {
			if (other.projectIndexer != null)
				return false;
		} else if (!projectIndexer.equals(other.projectIndexer))
			return false;
		return true;
	}

	/**
	 * Analyzes source modules changes, and fills collections with source
	 * modules that need to be removed/re-indexed.
	 * 
	 * @param containerPath
	 *            Container path
	 * @param sourceModules
	 *            Existing source modules under container path
	 * @param toRemove
	 *            Result collection of source modules that need to be removed
	 *            from index
	 * @param toReindex
	 *            Result collection of source modules that need to be re-indexed
	 * @param toolkit
	 *            Language toolkit
	 */
	public void analyzeSourceModuleChanges(IPath containerPath,
			Collection<ISourceModule> sourceModules,
			Collection<String> toRemove, Collection<ISourceModule> toReindex) {

		IIndexer indexer = IndexerManager.getIndexer();
		if (indexer == null) {
			return;
		}

		Map<String, Long> documentNames = indexer.getDocuments(containerPath);
		if (documentNames == null) {
			toReindex.addAll(sourceModules);
			return;
		}

		toRemove.addAll(documentNames.keySet());

		for (ISourceModule sourceModule : sourceModules) {
			String relativePath = Util.relativePath(sourceModule.getPath(),
					containerPath.segmentCount());
			IFileHandle handle = EnvironmentPathUtils.getFile(sourceModule);

			if (toRemove.remove(relativePath)) {
				if (documentNames.get(relativePath) < handle.lastModified()) {
					toReindex.add(sourceModule);
				}
			}
		}
	}
}
