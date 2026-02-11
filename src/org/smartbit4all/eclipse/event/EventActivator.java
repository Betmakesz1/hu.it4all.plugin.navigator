package org.smartbit4all.eclipse.event;

import org.eclipse.core.runtime.ILog;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
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
    }

    @Override
    public void stop(BundleContext context) throws Exception {
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
