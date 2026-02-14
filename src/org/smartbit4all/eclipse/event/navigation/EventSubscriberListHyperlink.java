package org.smartbit4all.eclipse.event.navigation;

import java.util.List;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.smartbit4all.eclipse.event.core.EventDefinition;
import org.smartbit4all.eclipse.event.core.EventLogger;
import org.smartbit4all.eclipse.event.core.EventSubscriberInfo;

/**
 * Hyperlink implementation that shows a selection dialog when multiple subscribers exist.
 * 
 * This hyperlink is used when a publisher has multiple subscribers, allowing the user
 * to choose which subscriber to navigate to.
 */
public class EventSubscriberListHyperlink implements IHyperlink {

    private final EventDefinition event;
    private final List<EventSubscriberInfo> subscribers;
    private final IRegion region;

    /**
     * Creates a hyperlink that shows a list of subscribers to choose from.
     * 
     * @param event the event definition
     * @param subscribers list of subscribers for this event
     * @param region the text region where the hyperlink is active
     */
    public EventSubscriberListHyperlink(EventDefinition event, List<EventSubscriberInfo> subscribers, IRegion region) {
        this.event = event;
        this.subscribers = subscribers;
        this.region = region;
    }

    /**
     * Opens a selection dialog and navigates to the chosen subscriber.
     */
    @Override
    public void open() {
        if (subscribers == null || subscribers.isEmpty()) {
            EventLogger.debug("EventSubscriberListHyperlink: No subscribers to display");
            return;
        }

        EventLogger.debug("EventSubscriberListHyperlink: Opening selection dialog for " + subscribers.size() + " subscriber(s)");
        
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        
        // Create selection dialog
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new SubscriberLabelProvider());
        dialog.setTitle("Select Event Subscriber");
        dialog.setMessage("Choose a subscriber for event: " + event.toString());
        dialog.setElements(subscribers.toArray());
        dialog.setMultipleSelection(false);
        
        // Show dialog
        if (dialog.open() == Window.OK) {
            Object result = dialog.getFirstResult();
            if (result instanceof EventSubscriberInfo) {
                EventSubscriberInfo selected = (EventSubscriberInfo) result;
                EventLogger.debug("EventSubscriberListHyperlink: User selected " + selected.getClassName() + "." + selected.getMethodName());
                navigateToSubscriber(selected);
            }
        } else {
            EventLogger.debug("EventSubscriberListHyperlink: User cancelled selection dialog");
        }
    }

    /**
     * Navigates to the selected subscriber method.
     * 
     * @param subscriber the subscriber to navigate to
     */
    private void navigateToSubscriber(EventSubscriberInfo subscriber) {
        Object methodObj = subscriber.getMethod();
        if (methodObj instanceof IMethod) {
            IMethod method = (IMethod) methodObj;
            try {
                String target = subscriber.getClassName() + "." + subscriber.getMethodName() + "()";
                EventLogger.debug("EventSubscriberListHyperlink: Navigating to " + target);
                JavaUI.openInEditor(method);
                EventLogger.info("EventSubscriberListHyperlink: Successfully navigated to " + target);
            } catch (Exception e) {
                // Show error dialog to user
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                MessageDialog.openError(shell, "Navigation Error",
                    "Could not open subscriber: " + subscriber.getClassName() + "." + subscriber.getMethodName());
                EventLogger.error("EventSubscriberListHyperlink: Failed to navigate to subscriber", e);
                e.printStackTrace();
            }
        } else {
            EventLogger.debug("EventSubscriberListHyperlink: Method object is not IMethod, cannot navigate");
        }
    }

    /**
     * Returns the text region where this hyperlink is active.
     */
    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    /**
     * Returns the tooltip text shown when hovering over the hyperlink.
     */
    @Override
    public String getHyperlinkText() {
        if (subscribers == null || subscribers.isEmpty()) {
            return "No subscribers found";
        }
        
        int count = subscribers.size();
        return "Open subscriber (" + count + " subscribers found)";
    }

    @Override
    public String getTypeLabel() {
        return null;
    }

    /**
     * Label provider for subscriber list items in the selection dialog.
     */
    private static class SubscriberLabelProvider extends LabelProvider {
        @Override
        public String getText(Object element) {
            if (element instanceof EventSubscriberInfo) {
                EventSubscriberInfo info = (EventSubscriberInfo) element;
                String className = info.getClassName();
                String methodName = info.getMethodName();
                
                // Format: ClassName.methodName() [channel]
                StringBuilder sb = new StringBuilder();
                sb.append(getSimpleClassName(className));
                sb.append(".");
                sb.append(methodName);
                sb.append("()");
                
                // Add channel if present
                if (info.getChannel() != null && !info.getChannel().isEmpty()) {
                    sb.append(" [channel: ");
                    sb.append(info.getChannel());
                    sb.append("]");
                }
                
                return sb.toString();
            }
            return super.getText(element);
        }
        
        /**
         * Extracts simple class name from fully qualified name.
         * 
         * @param fqn fully qualified class name
         * @return simple class name
         */
        private String getSimpleClassName(String fqn) {
            if (fqn == null) {
                return "";
            }
            int lastDot = fqn.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < fqn.length() - 1) {
                return fqn.substring(lastDot + 1);
            }
            return fqn;
        }
    }
}
