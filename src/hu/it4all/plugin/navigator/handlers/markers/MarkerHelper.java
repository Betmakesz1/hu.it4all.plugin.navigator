package hu.it4all.plugin.navigator.handlers.markers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class MarkerHelper {
    
    public static final String MARKER_TYPE = "com.company.mdm.navigation.mdmEventMarker";
    public static final String ATTRIB_INTERFACE = "targetInterfaceName";

    public static void createEventMarker(IResource resource, int start, int length, String interfaceName) {
        System.out.println("      [MarkerHelper] Creating marker:");
        System.out.println("      - Resource: " + resource.getName());
        System.out.println("      - Interface: " + interfaceName);
        System.out.println("      - Position: " + start + " length: " + length);
        
        try {
            IMarker marker = resource.createMarker(MARKER_TYPE);
            System.out.println("      [MarkerHelper] Marker created, setting attributes...");
            
            marker.setAttribute(IMarker.CHAR_START, start);
            marker.setAttribute(IMarker.CHAR_END, start + length);
            marker.setAttribute(IMarker.MESSAGE, "MDM Event: " + interfaceName);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
            marker.setAttribute(ATTRIB_INTERFACE, interfaceName);
            
            System.out.println("      [MarkerHelper] Marker created successfully!");
            
        } catch (CoreException e) {
            System.out.println("      [MarkerHelper] ERROR creating marker!");
            e.printStackTrace();
        }
    }

    public static void clearMarkers(IResource resource) {
        try {
            resource.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }
}