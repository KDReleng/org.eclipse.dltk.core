/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.internal.core.structure;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.DLTKLanguageManager;
import org.eclipse.dltk.core.IDLTKLanguageToolkit;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.IScriptFolder;
import org.eclipse.dltk.core.ISourceElementParser;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ISourceModuleInfoCache;
import org.eclipse.dltk.core.ISourceModuleInfoCache.ISourceModuleInfo;
import org.eclipse.dltk.core.search.IDLTKSearchScope;
import org.eclipse.dltk.core.search.SearchDocument;
import org.eclipse.dltk.core.search.indexing.AbstractIndexer;
import org.eclipse.dltk.core.search.indexing.InternalSearchDocument;
import org.eclipse.dltk.core.search.indexing.SourceIndexerRequestor;
import org.eclipse.dltk.internal.core.ModelManager;

public class StructureIndexer extends AbstractIndexer {
	private static class ParserInput implements
			org.eclipse.dltk.compiler.env.ISourceModule {

		private final SearchDocument document;

		public ParserInput(SearchDocument document) {
			this.document = document;
		}

		public char[] getContentsAsCharArray() {
			return document.getCharContents();
		}

		public IModelElement getModelElement() {
			return null;
		}

		public IPath getScriptFolder() {
			return new Path(document.getPath()).removeLastSegments(1);
		}

		public String getSourceContents() {
			return document.getContents();
		}

		public char[] getFileName() {
			return document.getPath().toCharArray();
		}

	}

	private final ISourceModule sourceModule;
	static long maxWorkTime = 0;

	public StructureIndexer(SearchDocument document, ISourceModule module) {
		super(document);
		this.sourceModule = module;
	}

	public void indexDocument() {
		long started = System.currentTimeMillis();
		IDLTKLanguageToolkit toolkit = this.document.getToolkit();
		if (toolkit == null) {
			toolkit = DLTKLanguageManager.findToolkit(new Path(this.document
					.getPath()));
		}
		if (toolkit == null) {
			return;
		}
		SourceIndexerRequestor requestor = ((InternalSearchDocument) this.document).requestor;
		if (requestor == null) {
			requestor = ModelManager.getModelManager().indexManager
					.getSourceRequestor(sourceModule.getScriptProject());
		}
		requestor.setIndexer(this);
		if (!this.document.isExternal()) {
			String pkgName = ""; //$NON-NLS-1$
			IScriptFolder folder = (IScriptFolder) sourceModule.getParent();
			pkgName = folder.getElementName();
			requestor.setPackageName(pkgName);
		} else {
			IPath path = new Path(this.document.getPath());
			String ppath = path.toString();
			String pkgName = (new Path(ppath.substring(ppath
					.indexOf(IDLTKSearchScope.FILE_ENTRY_SEPARATOR) + 1))
					.removeLastSegments(1)).toString();
			requestor.setPackageName(pkgName);
		}
		ISourceElementParser parser = ((InternalSearchDocument) this.document)
				.getParser();
		if (parser == null) {
			parser = DLTKLanguageManager.getSourceElementParser(sourceModule);
		}
		ISourceModuleInfoCache cache = ModelManager.getModelManager()
				.getSourceModuleInfoCache();
		ISourceModuleInfo info = cache.get(sourceModule);
		parser.setRequestor(requestor);
		parser.parseSourceModule(new ParserInput(document), info);
		long ended = System.currentTimeMillis();

		if (ended - started > maxWorkTime) {
			maxWorkTime = ended - started;
			if (DLTKCore.VERBOSE) {
				System.err.println("Max indexDocument() work time " //$NON-NLS-1$
						+ maxWorkTime + " on " + document.getPath()); //$NON-NLS-1$
			}
		}
	}
}
