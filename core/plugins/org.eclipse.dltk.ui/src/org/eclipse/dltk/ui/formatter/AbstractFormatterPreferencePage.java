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
package org.eclipse.dltk.ui.formatter;

import org.eclipse.core.resources.IProject;
import org.eclipse.dltk.core.DLTKContributionExtensionManager;
import org.eclipse.dltk.internal.ui.editor.ScriptSourceViewer;
import org.eclipse.dltk.internal.ui.formatter.profiles.ProfileManager;
import org.eclipse.dltk.internal.ui.preferences.ScriptSourcePreviewerUpdater;
import org.eclipse.dltk.internal.ui.text.DLTKColorManager;
import org.eclipse.dltk.ui.DLTKUILanguageManager;
import org.eclipse.dltk.ui.IDLTKUILanguageToolkit;
import org.eclipse.dltk.ui.formatter.internal.AbstractFormatterSelectionBlock;
import org.eclipse.dltk.ui.preferences.AbstractConfigurationBlockPropertyAndPreferencePage;
import org.eclipse.dltk.ui.preferences.AbstractOptionsBlock;
import org.eclipse.dltk.ui.preferences.PreferenceKey;
import org.eclipse.dltk.ui.text.IColorManager;
import org.eclipse.dltk.ui.text.ScriptSourceViewerConfiguration;
import org.eclipse.dltk.ui.util.IStatusChangeListener;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.IOverviewRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.ITextEditor;

public abstract class AbstractFormatterPreferencePage extends
		AbstractConfigurationBlockPropertyAndPreferencePage {

	protected class FormatterSelectionBlock extends
			AbstractFormatterSelectionBlock {

		private IColorManager fColorManager;

		public FormatterSelectionBlock(IStatusChangeListener context,
				IProject project, IWorkbenchPreferenceContainer container) {
			super(context, project, getFormatterPreferenceKey(), getNatureId(),
					container);
			fColorManager = new DLTKColorManager(false);
		}

		public void dispose() {
			fColorManager.dispose();
			super.dispose();
		}

		protected DLTKContributionExtensionManager getExtensionManager() {
			return ScriptFormatterManager.getInstance();
		}

		protected IFormatterModifyDialogOwner createDialogOwner() {
			return new FormatterModifyDialogOwner();
		}

		private class FormatterModifyDialogOwner implements
				IFormatterModifyDialogOwner {

			public ISourceViewer createPreview(Composite composite) {
				return FormatterSelectionBlock.this.createPreview(composite);
			}

			public Shell getShell() {
				return AbstractFormatterPreferencePage.this.getShell();
			}

			public IDialogSettings getDialogSettings() {
				return AbstractFormatterPreferencePage.this.getDialogSettings();
			}

			public IProfileManager getProfileManager() {
				return FormatterSelectionBlock.this.getProfileManager();
			}

		}

		/**
		 * @param composite
		 */
		public SourceViewer createPreview(Composite composite) {
			IPreferenceStore generalTextStore = EditorsUI.getPreferenceStore();
			IPreferenceStore store = new ChainedPreferenceStore(
					new IPreferenceStore[] { getPreferenceStore(),
							generalTextStore });
			SourceViewer fPreviewViewer = createPreviewViewer(composite, null,
					null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER,
					store);
			if (fPreviewViewer == null) {
				return null;
			}
			ScriptSourceViewerConfiguration configuration = createSimpleSourceViewerConfiguration(
					fColorManager, store, null, false);
			fPreviewViewer.configure(configuration);
			if (fPreviewViewer.getTextWidget().getTabs() == 0) {
				fPreviewViewer.getTextWidget().setTabs(4);
			}
			new ScriptSourcePreviewerUpdater(fPreviewViewer, configuration,
					store);
			fPreviewViewer.setEditable(false);
			IDocument document = new Document();
			IDLTKUILanguageToolkit toolkit = DLTKUILanguageManager
					.getLanguageToolkit(getNatureId());
			toolkit.getTextTools().setupDocumentPartitioner(document,
					toolkit.getPartitioningId());
			fPreviewViewer.setDocument(document);
			return fPreviewViewer;
		}

		/**
		 * @param parent
		 * @param verticalRuler
		 * @param overviewRuler
		 * @param showAnnotationsOverview
		 * @param styles
		 * @param store
		 * @return
		 */
		private ProjectionViewer createPreviewViewer(Composite parent,
				IVerticalRuler verticalRuler, IOverviewRuler overviewRuler,
				boolean showAnnotationsOverview, int styles,
				IPreferenceStore store) {
			return new ScriptSourceViewer(parent, verticalRuler, overviewRuler,
					showAnnotationsOverview, styles, store);
		}

		protected String getPreferenceLinkMessage() {
			return FormatterMessages.FormatterPreferencePage_settingsLink;
		}

		protected PreferenceKey getSavedContributionKey() {
			return AbstractFormatterPreferencePage.this
					.getFormatterPreferenceKey();
		}

		protected void updatePreview() {
			if (fPreviewViewer != null) {
				IScriptFormatterFactory factory = getSelectedExtension();
				ProfileManager manager = getProfileManager(factory);
				FormatterPreviewUtils.updatePreview(fPreviewViewer, factory
						.getPreviewContent(), factory, manager.getSelected()
						.getSettings());
			}
		}

	}

	protected AbstractOptionsBlock createOptionsBlock(
			IStatusChangeListener newStatusChangedListener, IProject project,
			IWorkbenchPreferenceContainer container) {
		return new FormatterSelectionBlock(newStatusChangedListener, project,
				container);
	}

	/**
	 * @param colorManager
	 * @param store
	 * @param object
	 * @param b
	 * @return
	 */
	protected abstract ScriptSourceViewerConfiguration createSimpleSourceViewerConfiguration(
			IColorManager colorManager, IPreferenceStore preferenceStore,
			ITextEditor editor, boolean configureFormatter);

	protected abstract String getNatureId();

	protected abstract PreferenceKey getFormatterPreferenceKey();

	protected abstract IDialogSettings getDialogSettings();

	protected String getHelpId() {
		return null;
	}

	protected void setDescription() {
		// empty
	}

	protected String getPreferencePageId() {
		return null;
	}

	protected String getProjectHelpId() {
		return null;
	}

	protected String getPropertyPageId() {
		return null;
	}

}
