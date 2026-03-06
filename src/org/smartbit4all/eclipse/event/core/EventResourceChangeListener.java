package org.smartbit4all.eclipse.event.core;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.smartbit4all.eclipse.event.preferences.EventPluginPreferences;

/**
 * Listens to resource changes and updates the event index.
 * 
 * Handles two types of events:
 * - POST_CHANGE: When files are modified/saved (if preference enabled)
 * - POST_BUILD: When projects are built/cleaned (if preference enabled)
 */
public class EventResourceChangeListener implements IResourceChangeListener, IResourceDeltaVisitor {

    private static final String JAVA_FILE_EXTENSION = ".java";

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        try {
            int eventType = event.getType();
            
            // Handle POST_BUILD events (project clean/build)
            if (eventType == IResourceChangeEvent.POST_BUILD) {
                if (EventPluginPreferences.isReindexOnBuildEnabled()) {
                    handlePostBuild(event);
                }
                return;
            }
            
            // Handle POST_CHANGE events (file save)
            if (eventType == IResourceChangeEvent.POST_CHANGE) {
                if (EventPluginPreferences.isAutoIndexOnSaveEnabled()) {
                    IResourceDelta delta = event.getDelta();
                    if (delta != null) {
                        delta.accept(this);
                    }
                }
                return;
            }
            
        } catch (Exception e) {
            EventLogger.error("EventResourceChangeListener: Error processing resource change event", e);
        }
    }
    
    /**
     * Handles POST_BUILD events - re-indexes affected projects.
     */
    private void handlePostBuild(IResourceChangeEvent event) {
        try {
            EventLogger.info("EventResourceChangeListener: POST_BUILD event detected");
            
            IResourceDelta delta = event.getDelta();
            if (delta == null) {
                return;
            }
            
            // Check which projects were affected by build
            IResourceDelta[] projectDeltas = delta.getAffectedChildren();
            for (IResourceDelta projectDelta : projectDeltas) {
                if (projectDelta.getResource() instanceof IProject) {
                    IProject project = (IProject) projectDelta.getResource();
                    
                    if (!project.isOpen() || !project.hasNature(JavaCore.NATURE_ID)) {
                        continue;
                    }
                    
                    // Check if it's a full build (clean)
                    int flags = projectDelta.getFlags();
                    if ((flags & IResourceDelta.CONTENT) != 0 || (flags & IResourceDelta.REPLACED) != 0) {
                        EventLogger.info("EventResourceChangeListener: Re-indexing project after build: " 
                            + project.getName());
                        
                        IJavaProject javaProject = JavaCore.create(project);
                        EventIndexManager.getInstance().indexProject(javaProject);
                    }
                }
            }
        } catch (Exception e) {
            EventLogger.error("EventResourceChangeListener: Error handling POST_BUILD", e);
        }
    }

    @Override
    public boolean visit(IResourceDelta delta) {
        try {
            // Only process files
            if (delta.getResource() instanceof IFile) {
                IFile file = (IFile) delta.getResource();
                
                // Only process Java files
                if (!file.getName().endsWith(JAVA_FILE_EXTENSION)) {
                    return true;
                }
                
                // Get compilation unit from file
                ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(file);
                boolean isRemoved = delta.getKind() == IResourceDelta.REMOVED;
                if (compilationUnit == null || (!isRemoved && !compilationUnit.exists())) {
                    return true;
                }
                
                EventLogger.debug("EventResourceChangeListener: Processing " + file.getName() 
                    + " (kind=" + kindToString(delta.getKind()) + ")");
                
                // Handle different delta kinds
                switch (delta.getKind()) {
                    case IResourceDelta.ADDED:
                        handleFileAdded(compilationUnit);
                        break;
                    case IResourceDelta.CHANGED:
                        handleFileChanged(compilationUnit);
                        break;
                    case IResourceDelta.REMOVED:
                        handleFileRemoved(file, compilationUnit);
                        break;
                    default:
                        break;
                }
            }
            
            // Continue visiting children
            return true;
        } catch (Exception e) {
            EventLogger.error("EventResourceChangeListener: Error visiting delta", e);
            return true;
        }
    }

    /**
     * Handle newly added Java file.
     * 
     * @param compilationUnit the newly added compilation unit
     */
    private void handleFileAdded(ICompilationUnit compilationUnit) {
        EventLogger.info("EventResourceChangeListener: File added - indexing: " + compilationUnit.getElementName());
        
        // Index the new file
        EventIndexManager indexManager = EventIndexManager.getInstance();
        indexManager.indexCompilationUnitIncremental(compilationUnit);
        
        EventLogger.debug("EventResourceChangeListener: File indexed successfully");
    }

    /**
     * Handle modified Java file.
     * 
     * @param compilationUnit the modified compilation unit
     */
    private void handleFileChanged(ICompilationUnit compilationUnit) {
        EventLogger.info("EventResourceChangeListener: File changed - re-indexing: " + compilationUnit.getElementName());
        
        // Re-index the file (update existing entries)
        EventIndexManager indexManager = EventIndexManager.getInstance();
        indexManager.indexCompilationUnitIncremental(compilationUnit);
        
        EventLogger.debug("EventResourceChangeListener: File re-indexed successfully");
    }

    /**
     * Handle deleted Java file.
     * 
     * @param compilationUnit the deleted compilation unit
     */
    private void handleFileRemoved(IFile file, ICompilationUnit compilationUnit) {
        String unitPath = compilationUnit != null && compilationUnit.getPath() != null
            ? compilationUnit.getPath().toString()
            : (file != null && file.getFullPath() != null ? file.getFullPath().toString() : null);

        EventLogger.info("EventResourceChangeListener: File removed - removing from index: "
            + (unitPath != null ? unitPath : "unknown"));

        EventIndexManager indexManager = EventIndexManager.getInstance();
        if (unitPath != null) {
            indexManager.removeCompilationUnitByPath(unitPath);
        }

        EventLogger.debug("EventResourceChangeListener: Removed entries for deleted file");
    }

    /**
     * Converts delta kind to readable string for logging.
     * 
     * @param kind the delta kind
     * @return string representation
     */
    private String kindToString(int kind) {
        switch (kind) {
            case IResourceDelta.ADDED: return "ADDED";
            case IResourceDelta.CHANGED: return "CHANGED";
            case IResourceDelta.REMOVED: return "REMOVED";
            default: return "UNKNOWN(" + kind + ")";
        }
    }
}
