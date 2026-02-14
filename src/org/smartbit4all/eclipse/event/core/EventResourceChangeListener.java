package org.smartbit4all.eclipse.event.core;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;

/**
 * Listens to resource changes and updates the event index.
 * 
 * When Java files are modified, saved, or deleted, this listener
 * automatically refreshes the event index to keep it in sync.
 */
public class EventResourceChangeListener implements IResourceChangeListener, IResourceDeltaVisitor {

    private static final String JAVA_FILE_EXTENSION = ".java";

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        try {
            // Process the delta tree
            IResourceDelta delta = event.getDelta();
            if (delta != null) {
                delta.accept(this);
            }
        } catch (Exception e) {
            EventLogger.error("EventResourceChangeListener: Error processing resource change event", e);
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
                if (compilationUnit == null || !compilationUnit.exists()) {
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
                        handleFileRemoved(compilationUnit);
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
        indexManager.indexCompilationUnit(compilationUnit);
        
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
        indexManager.indexCompilationUnit(compilationUnit);
        
        EventLogger.debug("EventResourceChangeListener: File re-indexed successfully");
    }

    /**
     * Handle deleted Java file.
     * 
     * @param compilationUnit the deleted compilation unit
     */
    private void handleFileRemoved(ICompilationUnit compilationUnit) {
        EventLogger.info("EventResourceChangeListener: File removed - removing from index: " + compilationUnit.getElementName());
        
        // TODO: Implement index cleanup when file is deleted
        // This would require removing all publishers/subscribers from this file
        EventLogger.warn("EventResourceChangeListener: Cleanup for deleted files not yet implemented");
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
