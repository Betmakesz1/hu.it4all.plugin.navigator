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
import org.smartbit4all.eclipse.event.ast.EventPublisherScanner;
import org.smartbit4all.eclipse.event.core.EventLogger;
import org.smartbit4all.eclipse.event.core.EventPublisherInfo;

/**
 * Runs EventPublisherScanner on the selected Java compilation unit.
 */
public class EventPublisherTestHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ICompilationUnit unit = resolveCompilationUnit(event);
        if (unit == null) {
            EventLogger.info("EventPublisherTestHandler: No ICompilationUnit selected");
            return null;
        }

        EventPublisherScanner scanner = new EventPublisherScanner();
        List<?> results = scanner.scanForPublishers(unit);

        EventLogger.info("EventPublisherTestHandler: " + unit.getElementName()
                + " publishers=" + results.size());

        for (Object item : results) {
            if (item instanceof EventPublisherInfo) {
                EventLogger.info("EventPublisherTestHandler: " + item.toString());
            } else {
                EventLogger.info("EventPublisherTestHandler: Unknown result type: "
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
