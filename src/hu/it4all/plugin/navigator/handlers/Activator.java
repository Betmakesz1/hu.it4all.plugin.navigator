package hu.it4all.plugin.navigator.handlers;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Plugin activator - kezeli a plugin életciklusát.
 */
public class Activator extends AbstractUIPlugin {

    // Plugin ID
    public static final String PLUGIN_ID = "hu.it4all.plugin.navigator";

    // Singleton instance
    private static Activator plugin;

    /**
     * Constructor
     */
    public Activator() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
    	super.start(context);
        plugin = this;
        System.out.println("=== MDM Event Navigator Plugin STARTED! ===");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    /**
     * Visszaadja a plugin singleton példányát.
     */
    public static Activator getDefault() {
        return plugin;
    }
}
