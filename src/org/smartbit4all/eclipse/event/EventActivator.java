package org.smartbit4all.eclipse.event;

import org.eclipse.core.runtime.ILog;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.smartbit4all.eclipse.event.core.EventIndexManager;
import org.smartbit4all.eclipse.event.core.EventLogger;

/**
 * The activator class controls the plug-in life cycle
 */
public class EventActivator extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "org.smartbit4all.eclipse.event"; //$NON-NLS-1$

    // The shared instance
    private static EventActivator plugin;

    /**
     * The constructor
     */
    public EventActivator() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        
        // Initialize the logger
        EventLogger.info("Event Navigator Plugin Started - Version " + getBundle().getVersion());
        
        // Initialize the EventIndexManager singleton
        EventIndexManager.getInstance();
        EventLogger.info("EventIndexManager initialized");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // Clear the event index
        try {
            EventIndexManager.getInstance().clear();
            EventLogger.info("EventIndexManager cleared");
        } catch (Exception e) {
            EventLogger.error("Error clearing EventIndexManager", e);
        }
        
        // Shutdown the logger
        EventLogger.shutdown();
        
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static EventActivator getDefault() {
        return plugin;
    }

    /**
     * Returns the plugin log
     *
     * @return the plugin log
     */
    public static ILog getPluginLog() {
        return getDefault().getLog();
    }
}
