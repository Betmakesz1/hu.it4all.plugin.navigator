package hu.it4all.plugin.navigator.handlers.markers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class MarkerHelper {
    
    public static final String MARKER_TYPE = "com.company.mdm.navigation.mdmEventMarker";
    public static final String ATTRIB_INTERFACE = "targetInterfaceName";

    public static void createEventMarker(IResource resource, int start, int length, String interfaceName) {
        try {
            // Létrehozzuk a markert
            IMarker marker = resource.createMarker(MARKER_TYPE);
            marker.setAttribute(IMarker.CHAR_START, start);
            marker.setAttribute(IMarker.CHAR_END, start + length);
            marker.setAttribute(IMarker.MESSAGE, "MDM Event: " + interfaceName);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
            
            // Eltároljuk az interfész nevét, hogy kattintáskor tudjuk, mit kell keresni
            marker.setAttribute(ATTRIB_INTERFACE, interfaceName);
            
        } catch (CoreException e) {
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