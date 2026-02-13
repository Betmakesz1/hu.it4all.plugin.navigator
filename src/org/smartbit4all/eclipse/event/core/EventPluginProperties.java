package org.smartbit4all.eclipse.event.core;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Central access for plugin.properties values.
 */
public final class EventPluginProperties {

    private static final String BUNDLE_NAME = "plugin";
    private static final ResourceBundle BUNDLE = loadBundle();

    private EventPluginProperties() {
    }

    /**
     * Get a value from plugin.properties, or null if missing.
     */
    public static String get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (BUNDLE == null) {
            return null;
        }
        try {
            if (BUNDLE.containsKey(key)) {
                return BUNDLE.getString(key);
            }
        } catch (MissingResourceException e) {
            EventLogger.error("Missing plugin property: " + key, e);
        }
        return null;
    }
    
    /**
     * Get a boolean value from plugin.properties
     * @param key property key
     * @return true if value is "true", false otherwise
     */
    public static boolean getBoolean(String key) {
        String value = get(key);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static ResourceBundle loadBundle() {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME);
        } catch (MissingResourceException e) {
            EventLogger.error("plugin.properties not found", e);
            return null;
        }
    }
}
