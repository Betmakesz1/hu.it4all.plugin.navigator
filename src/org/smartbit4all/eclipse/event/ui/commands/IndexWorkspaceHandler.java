package org.smartbit4all.eclipse.event.ui.commands;

import java.util.Arrays;
import java.util.Comparator;

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
import org.smartbit4all.eclipse.event.core.EventPluginProperties;

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
                    
                    // Sort projects: priority module first (from plugin.properties), then alphabetically
                    String priorityModule = EventPluginProperties.get("indexing.priority.module");
                    if (priorityModule != null && !priorityModule.isEmpty()) {
                        EventLogger.info("IndexJob: Priority module configured: " + priorityModule);
                        sortProjectsWithPriority(projectsToIndex, priorityModule);
                    } else {
                        // No priority module, just sort alphabetically
                        Arrays.sort(projectsToIndex, Comparator.comparing(IProject::getName));
                    }
                    
                    monitor.beginTask("Indexing workspace...", IProgressMonitor.UNKNOWN);
                }
                
                EventIndexManager indexManager = EventIndexManager.getInstance();
                int totalUnits = 0;
                int javaProjects = 0;
                int skippedClosed = 0;
                int skippedNonJava = 0;
                int projectsScanned = 0;
                
                for (IProject project : projectsToIndex) {
                    projectsScanned++;
                    if (monitor.isCanceled()) {
                        EventLogger.info("IndexJob: Indexing cancelled by user");
                        return Status.CANCEL_STATUS;
                    }
                    
                    if (!project.isOpen()) {
                        skippedClosed++;
                        EventLogger.info("IndexJob: Skipping closed project: " + project.getName());
                        continue;
                    }
                    
                    if (!project.hasNature(JavaCore.NATURE_ID)) {
                        skippedNonJava++;
                        EventLogger.info("IndexJob: Skipping non-Java project: " + project.getName());
                        continue;
                    }
                    
                    javaProjects++;
                    monitor.subTask("Indexing project: " + project.getName());
                    
                    try {
                        IJavaProject javaProject = JavaCore.create(project);
                        int indexed = indexProject(javaProject, indexManager);
                        if (indexed == 0) {
                            EventLogger.info("IndexJob: No source units found for project: " + project.getName());
                        }
                        totalUnits += indexed;
                    } catch (Exception e) {
                        EventLogger.error("IndexJob: Error indexing project " + project.getName(), e);
                    }
                }
                
                EventLogger.info("IndexJob: Projects scanned: " + projectsScanned + ", Java: " + javaProjects
                        + ", closed: " + skippedClosed + ", non-Java: " + skippedNonJava);
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
            int sourcePackages = 0;
            
            try {
                IPackageFragment[] packages = javaProject.getPackageFragments();
                
                for (IPackageFragment pkg : packages) {
                    if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE) {
                        sourcePackages++;
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

            EventLogger.debug("IndexJob: Project " + javaProject.getElementName()
                    + " source packages: " + sourcePackages + ", units indexed: " + count);
            
            return count;
        }
        
        /**
         * Sorts projects array with priority module first, rest alphabetically.
         * Priority module name can contain partial match (e.g., "platform" matches "my-platform-api").
         * 
         * @param projects array to sort (modified in place)
         * @param priorityModule the module name (or partial name) to prioritize
         */
        private void sortProjectsWithPriority(IProject[] projects, String priorityModule) {
            Arrays.sort(projects, new Comparator<IProject>() {
                @Override
                public int compare(IProject p1, IProject p2) {
                    String name1 = p1.getName();
                    String name2 = p2.getName();
                    
                    boolean p1IsPriority = name1.contains(priorityModule);
                    boolean p2IsPriority = name2.contains(priorityModule);
                    
                    // If one is priority and the other isn't, priority comes first
                    if (p1IsPriority && !p2IsPriority) {
                        return -1;
                    }
                    if (!p1IsPriority && p2IsPriority) {
                        return 1;
                    }
                    
                    // Both are priority or both are not: alphabetical order
                    return name1.compareTo(name2);
                }
            });
            
            // Log the sorted order for debugging
            EventLogger.info("IndexJob: Project indexing order:");
            for (int i = 0; i < projects.length && i < 10; i++) {
                EventLogger.info("  " + (i + 1) + ". " + projects[i].getName());
            }
            if (projects.length > 10) {
                EventLogger.info("  ... and " + (projects.length - 10) + " more projects");
            }
        }
    }
}
