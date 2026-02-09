package hu.it4all.plugin.navigator.handlers.nature;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Action ami lehetővé teszi az MDM Event Nature hozzáadását/eltávolítását
 * egy projektről a kontextus menüből.
 */
public class AddRemoveNatureAction implements IObjectActionDelegate {

    private IProject selectedProject;

    @Override
    public void run(IAction action) {
        if (selectedProject != null) {
            try {
                if (MDMEventNature.hasNature(selectedProject)) {
                    
                    MDMEventNature.removeNature(selectedProject);
                    System.out.println("MDM Event Nature removed from: " + selectedProject.getName());
                } else {
                    
                    MDMEventNature.addNature(selectedProject);
                    System.out.println("MDM Event Nature added to: " + selectedProject.getName());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        selectedProject = null;
        
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object element = structuredSelection.getFirstElement();
            
            if (element instanceof IProject) {
                selectedProject = (IProject) element;
            } else if (element instanceof IAdaptable) {
                IAdaptable adaptable = (IAdaptable) element;
                selectedProject = adaptable.getAdapter(IProject.class);
            }
        }

        if (action != null && selectedProject != null) {
            if (MDMEventNature.hasNature(selectedProject)) {
                action.setText("Remove MDM Event Nature");
            } else {
                action.setText("Add MDM Event Nature");
            }
        }
    }

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }
}
