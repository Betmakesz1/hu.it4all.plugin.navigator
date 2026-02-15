package org.smartbit4all.eclipse.event;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.smartbit4all.eclipse.event.core.EventIndexManager;
import org.smartbit4all.eclipse.event.core.EventLogger;
import org.smartbit4all.eclipse.event.core.EventResourceChangeListener;
import org.smartbit4all.eclipse.event.navigation.EventHyperlinkDetector;

/**
 * The activator class controls the plug-in life cycle
 */
public class EventActivator extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "org.smartbit4all.eclipse.event"; //$NON-NLS-1$

    // The shared instance
    private static EventActivator plugin;
    
    // Resource change listener for automatic index refresh
    private EventResourceChangeListener resourceChangeListener;
    
    // BundleContext for OSGi service registration
    private BundleContext bundleContext;

    /**
     * The constructor
     */
    public EventActivator() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        this.bundleContext = context;
        
        // Initialize the logger
        EventLogger.info("Event Navigator Plugin Started - Version " + getBundle().getVersion());
        
        // Initialize the EventIndexManager singleton
        EventIndexManager.getInstance();
        EventLogger.info("EventIndexManager initialized");
        
        // Register resource change listener for automatic index refresh
        try {
            resourceChangeListener = new EventResourceChangeListener();
            ResourcesPlugin.getWorkspace().addResourceChangeListener(
                resourceChangeListener,
                IResourceChangeEvent.POST_CHANGE
            );
            EventLogger.info("EventResourceChangeListener registered");
        } catch (Exception e) {
            EventLogger.error("Error registering EventResourceChangeListener", e);
        }
        
        // EventHyperlinkDetector is registered via extension point in plugin.xml
        EventLogger.info("EventHyperlinkDetector registered via extension point");
        
        // Perform initial workspace indexing in background
        initializeWorkspaceIndex();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // Unregister resource change listener
        try {
            if (resourceChangeListener != null) {
                ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
                EventLogger.info("EventResourceChangeListener unregistered");
            }
        } catch (Exception e) {
            EventLogger.error("Error unregistering EventResourceChangeListener", e);
        }
        
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
     * Initializes the event index for the current workspace.
     * 
     * This method is called at plugin startup to build the initial event index.
     * In a background job to avoid blocking the UI.
     */
    private void initializeWorkspaceIndex() {
        try {
            EventLogger.info("EventActivator: Starting workspace index initialization");

            Job indexJob = new Job("Event Navigator - Initial Workspace Index") {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    try {
                        monitor.beginTask("Indexing workspace", IProgressMonitor.UNKNOWN);
                        EventIndexManager.getInstance().indexWorkspace();
                        EventLogger.info("EventActivator: Workspace index initialization complete");
                        return Status.OK_STATUS;
                    } catch (Exception e) {
                        EventLogger.error("EventActivator: Error during workspace index initialization", e);
                        return Status.CANCEL_STATUS;
                    } finally {
                        monitor.done();
                    }
                }
            };

            indexJob.setUser(false);
            indexJob.schedule();
        } catch (Exception e) {
            EventLogger.error("EventActivator: Error during workspace index initialization", e);
        }
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

    /**
     * NOTE: EventHyperlinkDetector is now registered via extension point in plugin.xml
     * instead of OSGi service registration to ensure proper priority handling.
     */
    @SuppressWarnings("unused")
    private void registerHyperlinkDetector_DEPRECATED(BundleContext context) {
        try {
            EventHyperlinkDetector detector = new EventHyperlinkDetector();
            context.registerService(IHyperlinkDetector.class.getName(), detector, null);
            EventLogger.info("EventHyperlinkDetector successfully registered as OSGi service");
        } catch (Exception e) {
            EventLogger.error("Failed to register EventHyperlinkDetector service", e);
            throw new RuntimeException("Could not register EventHyperlinkDetector service", e);
        }
    }
}
