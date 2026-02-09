package hu.it4all.plugin.navigator.handlers.markers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Marker image provider az MDM Event markerekhez.
 */
public class MDMMarkerImageProvider {

    /**
     * Visszaad egy info ikont a markerekhez.
     */
    public static Image getMarkerImage() {
        return PlatformUI.getWorkbench().getSharedImages()
                .getImage(ISharedImages.IMG_OBJS_INFO_TSK);
    }
    
    /**
     * Visszaad egy ImageDescriptor-t a markerekhez.
     */
    public static ImageDescriptor getMarkerImageDescriptor() {
        return PlatformUI.getWorkbench().getSharedImages()
                .getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK);
    }
}