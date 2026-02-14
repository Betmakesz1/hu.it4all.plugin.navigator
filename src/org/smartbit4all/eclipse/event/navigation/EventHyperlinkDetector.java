package org.smartbit4all.eclipse.event.navigation;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.smartbit4all.eclipse.event.ast.JavaElementResolver;
import org.smartbit4all.eclipse.event.core.EventDefinition;
import org.smartbit4all.eclipse.event.core.EventIndexManager;
import org.smartbit4all.eclipse.event.core.EventLogger;
import org.smartbit4all.eclipse.event.core.EventPluginProperties;
import org.smartbit4all.eclipse.event.core.EventPublisherInfo;
import org.smartbit4all.eclipse.event.core.EventSubscriberInfo;

import java.util.List;

/**
 * Hyperlink detector for Ctrl+Click navigation
 * 
 * Detects event publishers and subscribers in Java code and creates
 * hyperlinks for navigation between them.
 */
public class EventHyperlinkDetector implements IHyperlinkDetector {

    private static final String SUBSCRIPTION_ANNOTATION_FQN =
            EventPluginProperties.get("event.subscription.annotation.fqn");
    private static final String SUBSCRIPTION_ANNOTATION_NAME =
            EventPluginProperties.get("event.subscription.annotation.name");
    private static final String PUBLISHER_INVOCATION_API_FQN =
            EventPluginProperties.get("event.publisher.invocation.api.fqn");
    private static final String PUBLISHER_METHOD_NAME =
            EventPluginProperties.get("event.publisher.method.name");
    private static final String SUBSCRIBER_API_PARAM =
            EventPluginProperties.get("event.subscriber.info.api");
    private static final String SUBSCRIBER_EVENT_PARAM =
            EventPluginProperties.get("event.subscriber.info.event");

    /**
     * Detects hyperlinks at the given region in the document.
     * 
     * @param textViewer the text viewer
     * @param region the region where hyperlinks should be detected
     * @param canShowMultipleHyperlinks whether multiple hyperlinks can be shown
     * @return array of hyperlinks or null if none found
     */
    @Override
    public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
        // Check if textViewer is valid
        if (textViewer == null) {
            return null;
        }
        
        // Get the document from the text viewer
        IDocument document = textViewer.getDocument();
        if (document == null) {
            return null;
        }
        
        // Get the compilation unit from the active editor
        ICompilationUnit compilationUnit = getCompilationUnit();
        if (compilationUnit == null) {
            return null;
        }
        
        // Parse the AST from the compilation unit
        ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
        parser.setSource(compilationUnit);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);
        
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        if (ast == null) {
            return null;
        }
        
        // Find the AST node at the cursor position using NodeFinder
        ASTNode node = NodeFinder.perform(ast, region.getOffset(), region.getLength());
        if (node == null) {
            return null;
        }
        
        // Check if the node or its parent is an event subscription annotation
        ASTNode currentNode = node;
        while (currentNode != null) {
            if (isEventSubscriptionAnnotation(currentNode)) {
                EventLogger.debug("EventHyperlinkDetector: Event subscription annotation detected");
                return createSubscriberToPublisherHyperlinks(currentNode, region);
            }
            if (isPublisherInvocation(currentNode)) {
                EventLogger.debug("EventHyperlinkDetector: Publisher invocation detected");
                return createPublisherToSubscribersHyperlinks(currentNode, region);
            }
            // Move up to parent node
            currentNode = currentNode.getParent();
        }
        
        return null;
    }
    
    /**
     * Gets the ICompilationUnit from the active editor.
     * 
     * @return the compilation unit or null if not found
     */
    private ICompilationUnit getCompilationUnit() {
        try {
            // Get the active editor from the workbench
            IEditorPart activeEditor = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage()
                .getActiveEditor();
            
            if (activeEditor == null) {
                return null;
            }
            
            // Get the working copy manager and retrieve the compilation unit
            IWorkingCopyManager manager = JavaUI.getWorkingCopyManager();
            ICompilationUnit unit = manager.getWorkingCopy(activeEditor.getEditorInput());
            
            return unit;
        } catch (Exception e) {
            // Silently fail - editor might not be a Java editor
            return null;
        }
    }
    
    /**
     * Checks if the AST node is an event subscription annotation.
     * 
     * @param node the AST node to check
     * @return true if the node is an EventSubscription annotation
     */
    private boolean isEventSubscriptionAnnotation(ASTNode node) {
        // Check if node is an Annotation
        if (!(node instanceof Annotation)) {
            return false;
        }
        
        Annotation annotation = (Annotation) node;
        
        // Try to resolve via type binding (most reliable)
        ITypeBinding typeBinding = annotation.resolveTypeBinding();
        if (typeBinding != null && SUBSCRIPTION_ANNOTATION_FQN != null) {
            if (SUBSCRIPTION_ANNOTATION_FQN.equals(typeBinding.getQualifiedName())) {
                return true;
            }
        }
        
        // Fallback: check simple name from getTypeName()
        String annotationName = annotation.getTypeName().getFullyQualifiedName();
        if (SUBSCRIPTION_ANNOTATION_FQN != null && SUBSCRIPTION_ANNOTATION_FQN.equals(annotationName)) {
            return true;
        }
        
        // Last resort: check short name if configured
        if (SUBSCRIPTION_ANNOTATION_NAME != null && SUBSCRIPTION_ANNOTATION_NAME.equals(annotationName)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if the AST node is a publisher invocation pattern.
     * 
     * @param node the AST node to check
     * @return true if the node is an invocationApi.publisher(...) invocation
     */
    private boolean isPublisherInvocation(ASTNode node) {
        // Check if node is a MethodInvocation
        if (!(node instanceof MethodInvocation)) {
            return false;
        }
        
        MethodInvocation invocation = (MethodInvocation) node;
        
        // Check method name: "publisher"
        if (PUBLISHER_METHOD_NAME == null) {
            return false;
        }
        if (!PUBLISHER_METHOD_NAME.equals(invocation.getName().getIdentifier())) {
            return false;
        }
        
        // Check expression type: InvocationApi
        Expression expression = invocation.getExpression();
        if (expression == null) {
            return false;
        }
        
        ITypeBinding binding = expression.resolveTypeBinding();
        if (binding != null && PUBLISHER_INVOCATION_API_FQN != null) {
            return PUBLISHER_INVOCATION_API_FQN.equals(binding.getQualifiedName());
        }
        
        return false;
    }
    
    /**
     * Creates hyperlinks from a publisher invocation to its subscribers.
     * 
     * @param node the MethodInvocation node (publisher call)
     * @param region the text region for the hyperlink
     * @return array of hyperlinks or null if no subscribers found
     */
    private IHyperlink[] createPublisherToSubscribersHyperlinks(ASTNode node, IRegion region) {
        if (!(node instanceof MethodInvocation)) {
            return null;
        }
        
        MethodInvocation invocation = (MethodInvocation) node;
        
        // Extract event information from the publisher call
        EventDefinition eventDef = extractEventFromPublisher(invocation);
        if (eventDef == null) {
            EventLogger.debug("EventHyperlinkDetector: Failed to extract event from publisher");
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: Publisher event extracted: " + eventDef.toString());
        
        // Find subscribers for this event
        EventIndexManager indexManager = EventIndexManager.getInstance();
        List<EventSubscriberInfo> subscribers = indexManager.findSubscribers(eventDef);
        
        if (subscribers == null || subscribers.isEmpty()) {
            // No subscribers found
            EventLogger.debug("EventHyperlinkDetector: No subscribers found for event: " + eventDef.toString());
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: Found " + subscribers.size() + " subscriber(s) for event: " + eventDef.toString());
        
        if (subscribers.size() == 1) {
            // Single subscriber: create direct hyperlink
            EventSubscriberInfo subscriber = subscribers.get(0);
            Object methodObj = subscriber.getMethod();
            if (methodObj instanceof IMethod) {
                IMethod method = (IMethod) methodObj;
                return new IHyperlink[] {
                    new JavaElementHyperlink(method, region)
                };
            }
            return null;
        } else {
            // Multiple subscribers: create list hyperlink
            return new IHyperlink[] {
                new EventSubscriberListHyperlink(eventDef, subscribers, region)
            };
        }
    }
    
    /**
     * Extracts event definition from a publisher invocation.
     * 
     * @param invocation the publisher method invocation
     * @return EventDefinition or null if extraction fails
     */
    private EventDefinition extractEventFromPublisher(MethodInvocation invocation) {
        // Parse arguments (exactly 3 expected: publisherApi, subscriberApi, eventName)
        @SuppressWarnings("unchecked")
        List<Expression> arguments = invocation.arguments();
        if (arguments == null || arguments.size() != 3) {
            return null;
        }
        
        // Arg 1: Subscriber API class (TypeLiteral)
        Expression arg1 = arguments.get(1);
        String subscriberApi = extractTypeFromTypeLiteral(arg1);
        if (subscriberApi == null) {
            return null;
        }
        
        // Arg 2: Event name constant
        Expression arg2 = arguments.get(2);
        String eventName = resolveEventName(arg2);
        if (eventName == null) {
            return null;
        }
        
        return new EventDefinition(subscriberApi, eventName);
    }
    
    /**
     * Extracts the FQN from a TypeLiteral expression.
     * 
     * @param expr the expression (should be TypeLiteral)
     * @return FQN of the type or null
     */
    private String extractTypeFromTypeLiteral(Expression expr) {
        if (!(expr instanceof TypeLiteral)) {
            return null;
        }
        
        TypeLiteral typeLit = (TypeLiteral) expr;
        ITypeBinding binding = typeLit.resolveTypeBinding();
        if (binding != null) {
            return binding.getQualifiedName();
        }
        
        return null;
    }
    
    /**
     * Resolves event name from an expression (String literal or constant).
     * 
     * @param expr the expression
     * @return event name or null
     */
    private String resolveEventName(Expression expr) {
        // Try direct StringLiteral first
        if (expr instanceof StringLiteral) {
            return ((StringLiteral) expr).getLiteralValue();
        }
        
        // Try to resolve as constant using JavaElementResolver
        return JavaElementResolver.resolveConstant(expr);
    }
    
    /**
     * Creates hyperlinks from a subscriber annotation to its publisher.
     * 
     * @param node the Annotation node (EventSubscription)
     * @param region the text region for the hyperlink
     * @return array of hyperlinks or null if no publisher found
     */
    private IHyperlink[] createSubscriberToPublisherHyperlinks(ASTNode node, IRegion region) {
        if (!(node instanceof Annotation)) {
            return null;
        }
        
        Annotation annotation = (Annotation) node;
        
        // Extract event information from the annotation
        EventDefinition eventDef = extractEventFromSubscriber(annotation);
        if (eventDef == null) {
            EventLogger.debug("EventHyperlinkDetector: Failed to extract event from subscriber annotation");
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: Subscriber event extracted: " + eventDef.toString());
        
        // Find publisher for this event
        EventIndexManager indexManager = EventIndexManager.getInstance();
        EventPublisherInfo publisher = indexManager.findPublisher(eventDef);
        
        if (publisher == null) {
            // No publisher found
            EventLogger.debug("EventHyperlinkDetector: No publisher found for event: " + eventDef.toString());
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: Found publisher for event: " + eventDef.toString());
        
        // Publisher found: create hyperlink
        Object methodObj = publisher.getMethod();
        if (methodObj instanceof IMethod) {
            IMethod method = (IMethod) methodObj;
            return new IHyperlink[] {
                new JavaElementHyperlink(method, region)
            };
        }
        
        return null;
    }
    
    /**
     * Extracts event definition from a subscriber annotation.
     * 
     * @param annotation the EventSubscription annotation
     * @return EventDefinition or null if extraction fails
     */
    private EventDefinition extractEventFromSubscriber(Annotation annotation) {
        // Extract annotation parameters: api, event
        String api = resolveAnnotationValue(annotation, SUBSCRIBER_API_PARAM);
        String event = resolveAnnotationValue(annotation, SUBSCRIBER_EVENT_PARAM);
        
        if (api == null || event == null) {
            return null;
        }
        
        return new EventDefinition(api, event);
    }
    
    /**
     * Gets the value of an annotation parameter.
     * 
     * @param annotation the annotation
     * @param paramName the parameter name
     * @return Expression or null if not found
     */
    private Expression getAnnotationValue(Annotation annotation, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        
        if (annotation instanceof MarkerAnnotation) {
            return null;
        }
        
        if (annotation instanceof SingleMemberAnnotation) {
            if ("value".equals(paramName)) {
                return ((SingleMemberAnnotation) annotation).getValue();
            }
            return null;
        }
        
        if (annotation instanceof NormalAnnotation) {
            NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
            @SuppressWarnings("unchecked")
            List<MemberValuePair> values = normalAnnotation.values();
            for (MemberValuePair pair : values) {
                if (paramName.equals(pair.getName().getIdentifier())) {
                    return pair.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Resolves annotation parameter value to String.
     * 
     * @param annotation the annotation
     * @param paramName the parameter name
     * @return resolved String value or null
     */
    private String resolveAnnotationValue(Annotation annotation, String paramName) {
        Expression valueExpr = getAnnotationValue(annotation, paramName);
        if (valueExpr == null) {
            return null;
        }
        
        if (valueExpr instanceof StringLiteral) {
            return ((StringLiteral) valueExpr).getLiteralValue();
        }
        
        return JavaElementResolver.resolveConstant(valueExpr);
    }
}
