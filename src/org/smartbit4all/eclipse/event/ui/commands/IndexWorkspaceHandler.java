package org.smartbit4all.eclipse.event.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ICompilationUnit;
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
                
                IProject[] projectsToIndex;
                
                if (selectedProject != null) {
                    // Index only selected project
                    projectsToIndex = new IProject[] { selectedProject };
                    monitor.beginTask("Indexing project: " + selectedProject.getName(), IProgressMonitor.UNKNOWN);
                } else {
                    // Index all projects in workspace
                    projectsToIndex = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                    monitor.beginTask("Indexing workspace...", IProgressMonitor.UNKNOWN);
                }
                
                EventIndexManager indexManager = EventIndexManager.getInstance();
                int totalUnits = 0;
                
                for (IProject project : projectsToIndex) {
                    if (monitor.isCanceled()) {
                        EventLogger.info("IndexJob: Indexing cancelled by user");
                        return Status.CANCEL_STATUS;
                    }
                    
                    if (!project.isOpen()) {
                        continue;
                    }
                    
                    if (!project.hasNature(JavaCore.NATURE_ID)) {
                        continue;
                    }
                    
                    monitor.subTask("Indexing project: " + project.getName());
                    
                    try {
                        IJavaProject javaProject = JavaCore.create(project);
                        totalUnits += indexProject(javaProject, indexManager);
                    } catch (Exception e) {
                        EventLogger.error("IndexJob: Error indexing project " + project.getName(), e);
                    }
                }
                
                EventLogger.info("IndexJob: Indexing complete! Total compilation units indexed: " + totalUnits);
                monitor.done();
                
                return Status.OK_STATUS;
            } catch (Exception e) {
                EventLogger.error("IndexJob: Unexpected error during indexing", e);
                return Status.CANCEL_STATUS;
            }
        }
        
        /**
         * Indexes all compilation units in a Java project.
         * 
         * @param javaProject the project to index
         * @param indexManager the index manager
         * @return number of units indexed
         */
        private int indexProject(IJavaProject javaProject, EventIndexManager indexManager) {
            int count = 0;
            
            try {
                IPackageFragment[] packages = javaProject.getPackageFragments();
                
                for (IPackageFragment pkg : packages) {
                    if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE) {
                        ICompilationUnit[] units = pkg.getCompilationUnits();
                        
                        for (ICompilationUnit unit : units) {
                            try {
                                if (unit.exists()) {
                                    indexManager.indexCompilationUnit(unit);
                                    count++;
                                    
                                    if (count % 10 == 0) {
                                        EventLogger.debug("IndexJob: Indexed " + count + " units so far...");
                                    }
                                }
                            } catch (Exception e) {
                                EventLogger.error("IndexJob: Error indexing unit " + unit.getElementName(), e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                EventLogger.error("IndexJob: Error processing project " + javaProject.getElementName(), e);
            }
            
            return count;
        }
    }
}
