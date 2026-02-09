package hu.it4all.plugin.navigator.handlers.builder;

import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import hu.it4all.plugin.navigator.handlers.analysis.EventVisitor;
import hu.it4all.plugin.navigator.handlers.markers.MarkerHelper;

/**
 * Builder ami végigszkenneli a Java fájlokat és létrehozza a markereket
 * az MDM event publisher hívásokhoz.
 */
public class MDMEventBuilder extends IncrementalProjectBuilder {

    public static final String BUILDER_ID = "hu.it4all.plugin.navigator.mdmEventBuilder";

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        System.out.println("========================================");
        System.out.println("MDM BUILDER START - Project: " + getProject().getName());
        System.out.println("========================================");
        
        if (kind == FULL_BUILD) {
            System.out.println(">>> FULL BUILD");
            fullBuild(monitor);
        } else {
            IResourceDelta delta = getDelta(getProject());
            if (delta == null) {
                System.out.println(">>> FULL BUILD (no delta)");
                fullBuild(monitor);
            } else {
                System.out.println(">>> INCREMENTAL BUILD");
                incrementalBuild(delta, monitor);
            }
        }
        
        System.out.println("MDM BUILDER END");
        System.out.println("========================================");
        return null;
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        getProject().accept(new IResourceVisitor() {
            @Override
            public boolean visit(IResource resource) throws CoreException {
                if (resource instanceof IFile && resource.getName().endsWith(".java")) {
                    MarkerHelper.clearMarkers(resource);
                }
                return true;
            }
        });
    }

    /**
     * Teljes build - az összes Java fájlt végigszkenneljük.
     */
    protected void fullBuild(IProgressMonitor monitor) throws CoreException {
        System.out.println(">>> Starting fullBuild - visiting all resources");
        
        getProject().accept(new IResourceVisitor() {
            @Override
            public boolean visit(IResource resource) throws CoreException {
                System.out.println("  Visiting resource: " + resource.getName() + 
                                 " (type: " + resource.getClass().getSimpleName() + ")");
                
                if (resource instanceof IFile && resource.getName().endsWith(".java")) {
                    System.out.println("  >>> FOUND JAVA FILE: " + resource.getFullPath());
                    checkAndMarkFile((IFile) resource);
                }
                return true;
            }
        });
        
        System.out.println(">>> fullBuild finished");
    }

    /**
     * Inkrementális build - csak a megváltozott fájlokat dolgozzuk fel.
     */
    protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
        System.out.println(">>> Starting incrementalBuild");
        
        delta.accept(new IResourceDeltaVisitor() {
            @Override
            public boolean visit(IResourceDelta delta) throws CoreException {
                IResource resource = delta.getResource();
                System.out.println("  Delta resource: " + resource.getName());
                
                if (resource instanceof IFile && resource.getName().endsWith(".java")) {
                    System.out.println("  >>> FOUND CHANGED JAVA FILE: " + resource.getFullPath());
                    switch (delta.getKind()) {
                        case IResourceDelta.ADDED:
                        case IResourceDelta.CHANGED:
                            checkAndMarkFile((IFile) resource);
                            break;
                        case IResourceDelta.REMOVED:
                            System.out.println("  >>> File removed");
                            break;
                    }
                }
                return true;
            }
        });
        
        System.out.println(">>> incrementalBuild finished");
    }

    /**
     * Egy Java fájl ellenőrzése és markerek létrehozása.
     */
    private void checkAndMarkFile(IFile file) {
        try {
            MarkerHelper.clearMarkers(file);

            ICompilationUnit cu = (ICompilationUnit) JavaCore.create(file);
            if (cu == null || !cu.exists()) {
                return;
            }

            ASTParser parser = ASTParser.newParser(AST.JLS17);
            parser.setSource(cu);
            parser.setResolveBindings(true);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);

            CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

            EventVisitor visitor = new EventVisitor((offset, length, interfaceName, line) -> {
                MarkerHelper.createEventMarker(file, offset, length, interfaceName);
            });

            astRoot.accept(visitor);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
