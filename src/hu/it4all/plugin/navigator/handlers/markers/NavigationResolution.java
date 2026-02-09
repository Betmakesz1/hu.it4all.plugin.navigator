package hu.it4all.plugin.navigator.handlers.markers;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator;
import org.eclipse.ui.PartInitException;

import hu.it4all.plugin.navigator.handlers.search.InterfaceSearchEngine;
import hu.it4all.plugin.navigator.handlers.search.InterfaceSearchEngine.MethodLocation;

public class NavigationResolution implements IMarkerResolutionGenerator {
    
    @Override
    public IMarkerResolution[] getResolutions(IMarker marker) {
        try {
            String interfaceName = (String) marker.getAttribute(MarkerHelper.ATTRIB_INTERFACE);
            if (interfaceName == null) {
                return new IMarkerResolution[0];
            }

            IProject project = marker.getResource().getProject();

            return new IMarkerResolution[] {
                new NavigateToListenersResolution(interfaceName, project)
            };
            
        } catch (Exception e) {
            e.printStackTrace();
            return new IMarkerResolution[0];
        }
    }

    /**
     * Marker resolution ami megnyitja a listener implementációkat.
     */
    private static class NavigateToListenersResolution implements IMarkerResolution {
        
        private final String interfaceName;
        private final IProject project;

        public NavigateToListenersResolution(String interfaceName, IProject project) {
            this.interfaceName = interfaceName;
            this.project = project;
        }

        @Override
        public String getLabel() {
            return "Go to MDM Listeners (" + getSimpleName(interfaceName) + ")";
        }

        @Override
        public void run(IMarker marker) {
            InterfaceSearchEngine searchEngine = new InterfaceSearchEngine();
            List<MethodLocation> listeners = searchEngine.findSubscriberMethods(
                interfaceName, 
                project, 
                new NullProgressMonitor()
            );

            if (listeners.isEmpty()) {
                System.out.println("No implementations found for: " + interfaceName);
                return;
            }

            if (listeners.size() == 1) {
                openInEditor(listeners.get(0).getCompilationUnit());
            } else {
                showSelectionDialog(listeners);
            }
        }

        /**
         * Megnyit egy compilation unit-ot az editorban.
         */
        private void openInEditor(ICompilationUnit cu) {
            try {
                JavaUI.openInEditor(cu);
            } catch (PartInitException | org.eclipse.jdt.core.JavaModelException e) {
                e.printStackTrace();
            }
        }

        /**
         * Megmutat egy választó dialógust több implementáció esetén.
         */
        private void showSelectionDialog(List<MethodLocation> listeners) {
            org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
                org.eclipse.swt.widgets.Shell shell = org.eclipse.ui.PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getShell();
                hu.it4all.plugin.navigator.handlers.ui.ListenerSelectionDialog.open(
                    shell, listeners, interfaceName);
            });
        }

        /**
         * Segédmetódus az egyszerű név kinyeréséhez.
         */
        private String getSimpleName(String fullyQualifiedName) {
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
        }
    }
}
