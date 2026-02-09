package hu.it4all.plugin.navigator.handlers.search;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

/**
 * Segédosztály interface implementációk kereséséhez a workspace-ben.
 */
public class InterfaceSearchEngine {

    /**
     * Megkeresi az adott interface összes implementációját a projektben.
     * 
     * @param interfaceName Az interface teljes neve (pl. "com.example.MyListener")
     * @param project A projekt, ahol keresünk
     * @param monitor Progress monitor
     * @return Az implementáló típusok listája
     */
    public List<IType> findImplementations(String interfaceName, IProject project, IProgressMonitor monitor) {
        List<IType> results = new ArrayList<>();
        
        try {
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject == null || !javaProject.exists()) {
                return results;
            }

            IType interfaceType = javaProject.findType(interfaceName);
            if (interfaceType == null || !interfaceType.exists()) {
                return results;
            }

            IJavaSearchScope scope = SearchEngine.createWorkspaceScope();

            SearchPattern pattern = SearchPattern.createPattern(
                interfaceType,
                IJavaSearchConstants.IMPLEMENTORS,
                SearchPattern.R_EXACT_MATCH
            );

            if (pattern == null) {
                return results;
            }

            SearchRequestor requestor = new SearchRequestor() {
                @Override
                public void acceptSearchMatch(SearchMatch match) throws CoreException {
                    if (match.getElement() instanceof IType) {
                        IType type = (IType) match.getElement();
                        results.add(type);
                    }
                }
            };

            SearchEngine searchEngine = new SearchEngine();
            searchEngine.search(
                pattern,
                new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                scope,
                requestor,
                monitor
            );

        } catch (CoreException e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Megkeresi az adott interface összes implementációját,
     * és visszaadja a compilation unit-okat.
     */
    public List<ICompilationUnit> findImplementationCompilationUnits(String interfaceName, IProject project, IProgressMonitor monitor) {
        List<ICompilationUnit> compilationUnits = new ArrayList<>();
        List<IType> types = findImplementations(interfaceName, project, monitor);

        for (IType type : types) {
            ICompilationUnit cu = type.getCompilationUnit();
            if (cu != null && cu.exists()) {
                compilationUnits.add(cu);
            }
        }

        return compilationUnits;
    }

    /**
     * Megkeresi azokat a metódusokat, amelyek az adott interface-t implementálják
     * és subscriber annotációval vannak ellátva.
     */
    public List<MethodLocation> findSubscriberMethods(String interfaceName, IProject project, IProgressMonitor monitor) {
        List<MethodLocation> methods = new ArrayList<>();
        List<IType> implementations = findImplementations(interfaceName, project, monitor);

        for (IType type : implementations) {
            //TODO Annotációk vizsgálata
            methods.add(new MethodLocation(type, type.getCompilationUnit()));
        }

        return methods;
    }

    /**
     * Egyszerű adatstruktúra a metódus helyek tárolására.
     */
    public static class MethodLocation {
        private final IType type;
        private final ICompilationUnit compilationUnit;

        public MethodLocation(IType type, ICompilationUnit compilationUnit) {
            this.type = type;
            this.compilationUnit = compilationUnit;
        }

        public IType getType() {
            return type;
        }

        public ICompilationUnit getCompilationUnit() {
            return compilationUnit;
        }

        public String getDisplayName() {
            return type.getFullyQualifiedName();
        }
    }
}