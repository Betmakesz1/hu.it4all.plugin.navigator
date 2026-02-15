package org.smartbit4all.eclipse.event.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.smartbit4all.eclipse.event.core.EventIndexManager;
import org.smartbit4all.eclipse.event.core.EventLogger;

/**
 * Handler for indexing the entire workspace or selected projects.
 * 
 * This handler scans all Java compilation units in the workspace
 * and builds the event index for publishers and subscribers.
 */
public class IndexWorkspaceHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        EventLogger.info("IndexWorkspaceHandler: Starting workspace indexing");
        
        // Get selected project if available
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        IProject selectedProject = null;
        
        if (selection instanceof IStructuredSelection) {
            Object element = ((IStructuredSelection) selection).getFirstElement();
            if (element instanceof IProject) {
                selectedProject = (IProject) element;
                EventLogger.info("IndexWorkspaceHandler: Project selected: " + selectedProject.getName());
            }
        }
        
        // Start indexing in background job
        IndexJob indexJob = new IndexJob(selectedProject);
        indexJob.schedule();
        
        return null;
    }
    
    /**
     * Background job for workspace indexing.
     * 
     * Prevents UI blocking while scanning all compilation units.
     */
    private static class IndexJob extends Job {
        private final IProject selectedProject;
        
        public IndexJob(IProject selectedProject) {
            super("Event Navigator - Indexing Workspace");
            this.selectedProject = selectedProject;
            setUser(true);
        }
        
        @Override
        protected IStatus run(IProgressMonitor monitor) {
            try {
                EventLogger.info("IndexJob: Starting background indexing");
                
                EventIndexManager indexManager = EventIndexManager.getInstance();
                
                if (selectedProject != null) {
                    // Index only selected project (without clearing the entire index)
                    monitor.beginTask("Indexing project: " + selectedProject.getName(), IProgressMonitor.UNKNOWN);
                    
                    if (!selectedProject.isOpen()) {
                        EventLogger.info("IndexJob: Project is closed: " + selectedProject.getName());
                        return Status.CANCEL_STATUS;
                    }
                    
                    if (!selectedProject.hasNature(JavaCore.NATURE_ID)) {
                        EventLogger.info("IndexJob: Not a Java project: " + selectedProject.getName());
                        return Status.CANCEL_STATUS;
                    }
                    
                    IJavaProject javaProject = JavaCore.create(selectedProject);
                    indexManager.indexProject(javaProject);
                    
                    EventLogger.info("IndexJob: Project indexing complete: " + selectedProject.getName());
                } else {
                    // Index entire workspace (clear and rebuild)
                    monitor.beginTask("Indexing workspace...", IProgressMonitor.UNKNOWN);
                    
                    // Use the indexWorkspace method which clears and rebuilds the entire index
                    indexManager.indexWorkspace();
                    
                    EventLogger.info("IndexJob: Workspace indexing complete!");
                }
                
                monitor.done();
                
                return Status.OK_STATUS;
            } catch (Exception e) {
                EventLogger.error("IndexJob: Unexpected error during indexing", e);
                return Status.CANCEL_STATUS;
            }
        }
    }
}
