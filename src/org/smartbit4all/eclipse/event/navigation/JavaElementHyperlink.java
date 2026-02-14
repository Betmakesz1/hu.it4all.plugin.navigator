package org.smartbit4all.eclipse.event.navigation;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.PartInitException;
import org.smartbit4all.eclipse.event.core.EventLogger;

/**
 * Hyperlink implementation that navigates to a Java element (method).
 * 
 * This hyperlink is used to jump from event publishers to subscribers
 * and vice versa.
 */
public class JavaElementHyperlink implements IHyperlink {

    private final IMethod method;
    private final IRegion region;

    /**
     * Creates a hyperlink to a Java method.
     * 
     * @param method the target Java method
     * @param region the text region where the hyperlink is active
     */
    public JavaElementHyperlink(IMethod method, IRegion region) {
        this.method = method;
        this.region = region;
    }

    /**
     * Opens the target method in the Java editor.
     */
    @Override
    public void open() {
        if (method == null) {
            EventLogger.debug("JavaElementHyperlink: Method is null, cannot navigate");
            return;
        }
        
        try {
            String target = method.getDeclaringType().getElementName() + "." + method.getElementName() + "()";
            EventLogger.debug("JavaElementHyperlink: Navigating to " + target);
            // Open the method in the Java editor
            JavaUI.openInEditor(method);
            EventLogger.info("JavaElementHyperlink: Successfully navigated to " + target);
        } catch (PartInitException e) {
            EventLogger.error("JavaElementHyperlink: Failed to open editor", e);
            e.printStackTrace();
        } catch (Exception e) {
            EventLogger.error("JavaElementHyperlink: Unexpected error during navigation", e);
            e.printStackTrace();
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
        if (method == null) {
            return "Navigate to method";
        }
        
        String className = method.getDeclaringType().getElementName();
        String methodName = method.getElementName();
        
        return "Open " + className + "." + methodName + "()";
    }

	@Override
	public String getTypeLabel() {
		return null;
	}
}
