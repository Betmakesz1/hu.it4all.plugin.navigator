package org.smartbit4all.eclipse.event.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.smartbit4all.eclipse.event.EventActivator;

/**
 * Manages preferences for Event Navigator plugin.
 */
public class EventPluginPreferences {

    // Preference keys
    private static final String PREF_AUTO_INDEX_ON_SAVE = "autoIndexOnSave";
    private static final String PREF_REINDEX_ON_BUILD = "reindexOnBuild";
    
    // Default values
    private static final boolean DEFAULT_AUTO_INDEX_ON_SAVE = true;
    private static final boolean DEFAULT_REINDEX_ON_BUILD = false;
    
    private static IPreferenceStore preferenceStore;
    
    /**
     * Gets the preference store.
     */
    private static IPreferenceStore getPreferenceStore() {
        if (preferenceStore == null) {
            preferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, 
                EventActivator.PLUGIN_ID);
        }
        return preferenceStore;
    }
    
    /**
     * Initialize default preference values.
     */
    public static void initializeDefaults() {
        IPreferenceStore store = getPreferenceStore();
        store.setDefault(PREF_AUTO_INDEX_ON_SAVE, DEFAULT_AUTO_INDEX_ON_SAVE);
        store.setDefault(PREF_REINDEX_ON_BUILD, DEFAULT_REINDEX_ON_BUILD);
    }
    
    /**
     * Checks if automatic indexing on file save is enabled.
     * 
     * @return true if enabled
     */
    public static boolean isAutoIndexOnSaveEnabled() {
        return getPreferenceStore().getBoolean(PREF_AUTO_INDEX_ON_SAVE);
    }
    
    /**
     * Sets automatic indexing on file save.
     * 
     * @param enabled true to enable
     */
    public static void setAutoIndexOnSave(boolean enabled) {
        getPreferenceStore().setValue(PREF_AUTO_INDEX_ON_SAVE, enabled);
    }
    
    /**
     * Checks if re-indexing on project build/clean is enabled.
     * 
     * @return true if enabled
     */
    public static boolean isReindexOnBuildEnabled() {
        return getPreferenceStore().getBoolean(PREF_REINDEX_ON_BUILD);
    }
    
    /**
     * Sets re-indexing on project build/clean.
     * 
     * @param enabled true to enable
     */
    public static void setReindexOnBuild(boolean enabled) {
        getPreferenceStore().setValue(PREF_REINDEX_ON_BUILD, enabled);
    }
    
    /**
     * Returns the preference store.
     */
    public static IPreferenceStore getStore() {
        return getPreferenceStore();
    }
}
