package org.smartbit4all.eclipse.event.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.smartbit4all.eclipse.event.EventActivator;
import org.smartbit4all.eclipse.event.core.EventIndexManager;
import org.smartbit4all.eclipse.event.core.EventLogger;

import java.io.File;

/**
 * Preference page for Event Navigator plugin.
 */
public class EventPluginPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public EventPluginPreferencePage() {
        super(GRID);
        setPreferenceStore(EventPluginPreferences.getStore());
        setDescription("Event Navigator Plugin Settings");
    }

    @Override
    public void init(IWorkbench workbench) {
        // Initialize defaults
        EventPluginPreferences.initializeDefaults();
    }

    @Override
    protected void createFieldEditors() {
        Composite parent = getFieldEditorParent();
        
        // Automatic indexing settings
        Group indexGroup = new Group(parent, SWT.NONE);
        indexGroup.setText("Automatic Indexing");
        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan = 2;
        indexGroup.setLayoutData(gridData);
        
        addField(new BooleanFieldEditor(
            "autoIndexOnSave",
            "Enable automatic indexing on file save",
            indexGroup));
        
        addField(new BooleanFieldEditor(
            "reindexOnBuild",
            "Re-index on project clean/build",
            indexGroup));
        
        // Cache management
        Group cacheGroup = new Group(parent, SWT.NONE);
        cacheGroup.setText("Cache Management");
        GridData cacheGridData = new GridData(GridData.FILL_HORIZONTAL);
        cacheGridData.horizontalSpan = 2;
        cacheGroup.setLayoutData(cacheGridData);
        
        // Cache info label
        Label cacheInfoLabel = new Label(cacheGroup, SWT.WRAP);
        EventIndexManager indexManager = EventIndexManager.getInstance();
        if (indexManager.hasCachedIndex()) {
            cacheInfoLabel.setText("Cache file exists (index is persisted)");
        } else {
            cacheInfoLabel.setText("No cache file found");
        }
        GridData labelData = new GridData(GridData.FILL_HORIZONTAL);
        labelData.horizontalSpan = 2;
        cacheInfoLabel.setLayoutData(labelData);
        
        // Clear cache button
        Button clearCacheButton = new Button(cacheGroup, SWT.PUSH);
        clearCacheButton.setText("Clear Cache and Re-index Workspace");
        clearCacheButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                EventLogger.info("EventPluginPreferencePage: User requested cache clear");
                indexManager.clearCache();
                indexManager.indexWorkspaceIncremental();
                cacheInfoLabel.setText("Cache cleared - re-indexing workspace...");
            }
        });
    }
}
