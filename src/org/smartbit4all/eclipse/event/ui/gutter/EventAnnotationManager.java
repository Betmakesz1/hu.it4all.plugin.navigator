package org.smartbit4all.eclipse.event.ui.gutter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.smartbit4all.eclipse.event.EventActivator;
import org.smartbit4all.eclipse.event.ast.EventAnnotationScanner;
import org.smartbit4all.eclipse.event.ast.EventPublisherScanner;
import org.smartbit4all.eclipse.event.core.EventDefinition;
import org.smartbit4all.eclipse.event.core.EventIndexManager;
import org.smartbit4all.eclipse.event.core.EventPublisherInfo;
import org.smartbit4all.eclipse.event.core.EventSubscriberInfo;
import org.smartbit4all.eclipse.event.ui.gutter.EventGutterAnnotations.EventPublisherAnnotation;
import org.smartbit4all.eclipse.event.ui.gutter.EventGutterAnnotations.EventSubscriberAnnotation;

/**
 * Manages event-related annotations in Eclipse editors.
 * 
 * This singleton class is responsible for:
 * - Creating and updating gutter annotations for publishers and subscribers
 * - Managing the lifecycle of annotations across editor events
 * - Coordinating with EventIndexManager for event relationship data
 * 
 * Thread-safety: All public methods are synchronized to prevent concurrent modification.
 */
public class EventAnnotationManager {

    // Singleton instance
    private static EventAnnotationManager instance;
    
    // Cache of annotations per editor (IEditorPart -> List<Annotation>)
    private Map<IEditorPart, List<Annotation>> editorAnnotationsCache;
    
    /**
     * Private constructor for singleton pattern.
     */
    private EventAnnotationManager() {
        this.editorAnnotationsCache = new HashMap<>();
    }
    
    /**
     * Get the singleton instance.
     * @return EventAnnotationManager instance
     */
    public static synchronized EventAnnotationManager getInstance() {
        if (instance == null) {
            instance = new EventAnnotationManager();
        }
        return instance;
    }
    
    /**
     * Update annotations for the given editor.
     * This is the main entry point called when an editor is opened or modified.
     * 
     * @param editor The editor part to update annotations for
     */
    public synchronized void updateAnnotationsForEditor(IEditorPart editor) {
        if (editor == null) {
            return;
        }
        
        try {
            // Get ICompilationUnit from editor
            ICompilationUnit compilationUnit = getCompilationUnit(editor);
            if (compilationUnit == null) {
                log("No compilation unit found for editor: " + editor.getTitle(), IStatus.WARNING);
                return;
            }
            
            // Get annotation model
            IAnnotationModel annotationModel = getAnnotationModel(editor);
            if (annotationModel == null) {
                log("No annotation model found for editor: " + editor.getTitle(), IStatus.WARNING);
                return;
            }
            
            // Remove old annotations
            removeAnnotationsForEditor(editor, annotationModel);
            
            // Create new annotations
            List<Annotation> newAnnotations = createAnnotations(compilationUnit);
            
            // Add new annotations to model
            for (Annotation annotation : newAnnotations) {
                Position position = getAnnotationPosition(annotation, compilationUnit);
                if (position != null) {
                    annotationModel.addAnnotation(annotation, position);
                }
            }
            
            // Cache the new annotations
            editorAnnotationsCache.put(editor, newAnnotations);
            
            log("Updated annotations for editor: " + editor.getTitle() + " (" + newAnnotations.size() + " annotations)", IStatus.INFO);
            
        } catch (Exception e) {
            log("Error updating annotations for editor: " + editor.getTitle(), e);
        }
    }
    
    /**
     * Remove all annotations for the given editor.
     * Called when an editor is closed.
     * 
     * @param editor The editor part to remove annotations from
     */
    public synchronized void removeAnnotationsForEditor(IEditorPart editor) {
        if (editor == null) {
            return;
        }
        
        try {
            IAnnotationModel annotationModel = getAnnotationModel(editor);
            if (annotationModel != null) {
                removeAnnotationsForEditor(editor, annotationModel);
            }
            
            // Remove from cache
            editorAnnotationsCache.remove(editor);
            
        } catch (Exception e) {
            log("Error removing annotations for editor: " + editor.getTitle(), e);
        }
    }
    
    /**
     * Clear all annotations from all editors.
     * Used for cleanup during plugin shutdown.
     */
    public synchronized void clearAllAnnotations() {
        // Create a copy to avoid concurrent modification
        List<IEditorPart> editors = new ArrayList<>(editorAnnotationsCache.keySet());
        
        for (IEditorPart editor : editors) {
            removeAnnotationsForEditor(editor);
        }
        
        editorAnnotationsCache.clear();
        log("Cleared all event annotations", IStatus.INFO);
    }
    
    // ========================================================================
    // Internal Helper Methods
    // ========================================================================
    
    /**
     * Get ICompilationUnit from editor.
     * @param editor The editor part
     * @return ICompilationUnit or null if not available
     */
    private ICompilationUnit getCompilationUnit(IEditorPart editor) {
        if (!(editor instanceof ITextEditor)) {
            return null;
        }
        
        ITextEditor textEditor = (ITextEditor) editor;
        IJavaElement element = textEditor.getEditorInput().getAdapter(IJavaElement.class);
        
        if (element instanceof ICompilationUnit) {
            return (ICompilationUnit) element;
        }
        
        return null;
    }
    
    /**
     * Get IAnnotationModel from editor.
     * @param editor The editor part
     * @return IAnnotationModel or null if not available
     */
    private IAnnotationModel getAnnotationModel(IEditorPart editor) {
        if (!(editor instanceof ITextEditor)) {
            return null;
        }
        
        ITextEditor textEditor = (ITextEditor) editor;
        IDocumentProvider documentProvider = textEditor.getDocumentProvider();
        
        if (documentProvider == null) {
            return null;
        }
        
        return documentProvider.getAnnotationModel(textEditor.getEditorInput());
    }
    
    /**
     * Remove annotations for editor from the annotation model.
     * @param editor The editor part
     * @param annotationModel The annotation model
     */
    private void removeAnnotationsForEditor(IEditorPart editor, IAnnotationModel annotationModel) {
        List<Annotation> cachedAnnotations = editorAnnotationsCache.get(editor);
        
        if (cachedAnnotations == null || cachedAnnotations.isEmpty()) {
            return;
        }
        
        // Remove all cached annotations from the model
        for (Annotation annotation : cachedAnnotations) {
            annotationModel.removeAnnotation(annotation);
        }
    }
    
    /**
     * Create all annotations for the given compilation unit.
     * This scans for publishers and subscribers and creates annotation objects.
     * 
     * @param compilationUnit The compilation unit to scan
     * @return List of created annotations
     */
    private List<Annotation> createAnnotations(ICompilationUnit compilationUnit) {
        List<Annotation> annotations = new ArrayList<>();
        
        try {
            EventIndexManager indexManager = EventIndexManager.getInstance();
            
            // Scan for publishers (using instance, not static)
            EventPublisherScanner publisherScanner = new EventPublisherScanner();
            List<?> publisherResults = publisherScanner.scanForPublishers(compilationUnit);
            
            for (Object obj : publisherResults) {
                if (obj instanceof EventPublisherInfo) {
                    EventPublisherInfo publisherInfo = (EventPublisherInfo) obj;
                    
                    // Find subscriber count
                    EventDefinition eventDef = new EventDefinition(publisherInfo.getApi(), publisherInfo.getEvent());
                    List<EventSubscriberInfo> subscribers = indexManager.findSubscribers(eventDef);
                    int subscriberCount = subscribers != null ? subscribers.size() : 0;
                    
                    // Create publisher annotation
                    EventPublisherAnnotation annotation = new EventPublisherAnnotation(publisherInfo, subscriberCount);
                    annotations.add(annotation);
                }
            }
            
            // Scan for subscribers (using instance, not static)
            EventAnnotationScanner annotationScanner = new EventAnnotationScanner();
            List<?> subscriberResults = annotationScanner.scanForSubscribers(compilationUnit);
            
            for (Object obj : subscriberResults) {
                if (obj instanceof EventSubscriberInfo) {
                    EventSubscriberInfo subscriberInfo = (EventSubscriberInfo) obj;
                    
                    // Check if publisher exists
                    EventDefinition eventDef = new EventDefinition(subscriberInfo.getApi(), subscriberInfo.getEvent());
                    EventPublisherInfo publisher = indexManager.findPublisher(eventDef);
                    boolean publisherFound = (publisher != null);
                    
                    // Create subscriber annotation
                    EventSubscriberAnnotation annotation = new EventSubscriberAnnotation(subscriberInfo, publisherFound);
                    annotations.add(annotation);
                }
            }
            
        } catch (Exception e) {
            log("Error creating annotations for compilation unit", e);
        }
        
        return annotations;
    }
    
    /**
     * Get the position for an annotation in the editor.
     * This calculates where the annotation should appear in the gutter.
     * 
     * @param annotation The annotation
     * @param compilationUnit The compilation unit
     * @return Position or null if cannot be determined
     */
    private Position getAnnotationPosition(Annotation annotation, ICompilationUnit compilationUnit) {
        try {
            IMethod method = null;
            
            // Determine which method this annotation belongs to
            if (annotation instanceof EventPublisherAnnotation) {
                EventPublisherAnnotation pubAnnotation = (EventPublisherAnnotation) annotation;
                Object methodObj = pubAnnotation.getPublisherInfo().getMethod();
                if (methodObj instanceof IMethod) {
                    method = (IMethod) methodObj;
                }
            } else if (annotation instanceof EventSubscriberAnnotation) {
                EventSubscriberAnnotation subAnnotation = (EventSubscriberAnnotation) annotation;
                Object methodObj = subAnnotation.getSubscriberInfo().getMethod();
                if (methodObj instanceof IMethod) {
                    method = (IMethod) methodObj;
                }
            }
            
            if (method != null) {
                return getMethodPosition(method);
            }
            
        } catch (Exception e) {
            log("Error getting annotation position", e);
        }
        
        return null;
    }
    
    /**
     * Get the position of a method in the source code.
     * @param method The method element
     * @return Position or null if cannot be determined
     */
    private Position getMethodPosition(IMethod method) {
        try {
            if (method == null || !method.exists()) {
                return null;
            }
            
            ISourceRange sourceRange = method.getSourceRange();
            if (sourceRange == null) {
                return null;
            }
            
            int offset = sourceRange.getOffset();
            int length = sourceRange.getLength();
            
            return new Position(offset, length);
            
        } catch (JavaModelException e) {
            log("Error getting method position for: " + method.getElementName(), e);
            return null;
        }
    }
    
    // ========================================================================
    // Logging
    // ========================================================================
    
    /**
     * Log a message.
     * @param message The message
     * @param severity The severity level
     */
    private void log(String message, int severity) {
        EventActivator activator = EventActivator.getDefault();
        if (activator != null) {
            activator.getLog().log(new Status(severity, EventActivator.PLUGIN_ID, message));
        }
    }
    
    /**
     * Log an exception.
     * @param message The message
     * @param exception The exception
     */
    private void log(String message, Exception exception) {
        EventActivator activator = EventActivator.getDefault();
        if (activator != null) {
            activator.getLog().log(new Status(IStatus.ERROR, EventActivator.PLUGIN_ID, message, exception));
        }
    }
}
