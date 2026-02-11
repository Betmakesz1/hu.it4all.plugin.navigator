package org.smartbit4all.eclipse.event.ui.commands;

import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.smartbit4all.eclipse.event.ast.EventAnnotationScanner;
import org.smartbit4all.eclipse.event.core.EventLogger;
import org.smartbit4all.eclipse.event.core.EventSubscriberInfo;

/**
 * Runs EventAnnotationScanner on the selected Java compilation unit.
 */
public class EventAnnotationTestHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ICompilationUnit unit = resolveCompilationUnit(event);
        if (unit == null) {
            EventLogger.info("EventAnnotationTestHandler: No ICompilationUnit selected");
            return null;
        }

        EventAnnotationScanner scanner = new EventAnnotationScanner();
        List<?> results = scanner.scanForSubscribers(unit);

        EventLogger.info("EventAnnotationTestHandler: " + unit.getElementName()
                + " subscribers=" + results.size());

        for (Object item : results) {
            if (item instanceof EventSubscriberInfo) {
                EventLogger.info("EventAnnotationTestHandler: " + item.toString());
            } else {
                EventLogger.info("EventAnnotationTestHandler: Unknown result type: "
                        + item.getClass().getName());
            }
        }

        return null;
    }

    private ICompilationUnit resolveCompilationUnit(ExecutionEvent event) {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }

        Object element = ((IStructuredSelection) selection).getFirstElement();
        if (element instanceof ICompilationUnit) {
            return (ICompilationUnit) element;
        }

        if (element instanceof IJavaElement) {
            IJavaElement javaElement = (IJavaElement) element;
            if (javaElement.getElementType() == IJavaElement.COMPILATION_UNIT) {
                return (ICompilationUnit) javaElement;
            }
        }

        if (element instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) element;
            Object adapted = adaptable.getAdapter(ICompilationUnit.class);
            if (adapted instanceof ICompilationUnit) {
                return (ICompilationUnit) adapted;
            }
        }

        return null;
    }
}
