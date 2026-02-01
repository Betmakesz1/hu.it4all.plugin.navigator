package hu.it4all.plugin.navigator.handlers.markers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;

public class NavigationResolution implements org.eclipse.ui.IMarkerResolutionGenerator {
    @Override
    public IMarkerResolution[] getResolutions(IMarker marker) {
        return new IMarkerResolution[] {
            new IMarkerResolution() {
                @Override
                public String getLabel() {
                    return "Go to MDM Listeners";
                }
                @Override
                public void run(IMarker marker) {
                    // TODO: Itt hívjuk meg a SearchEngine-t az interfészre :(
                }
            }
        };
    }
}