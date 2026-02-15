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
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
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
        EventLogger.debug("EventHyperlinkDetector: detectHyperlinks() called at offset=" + region.getOffset() 
            + ", length=" + region.getLength());
        EventLogger.debug("EventHyperlinkDetector: Properties - PUBLISHER_METHOD_NAME=" + PUBLISHER_METHOD_NAME 
            + ", PUBLISHER_INVOCATION_API_FQN=" + PUBLISHER_INVOCATION_API_FQN
            + ", SUBSCRIPTION_ANNOTATION_FQN=" + SUBSCRIPTION_ANNOTATION_FQN);
        
        // Check if textViewer is valid
        if (textViewer == null) {
            EventLogger.debug("EventHyperlinkDetector: textViewer is null");
            return null;
        }
        
        // Get the document from the text viewer
        IDocument document = textViewer.getDocument();
        if (document == null) {
            EventLogger.debug("EventHyperlinkDetector: document is null");
            return null;
        }
        
        // Get the compilation unit from the active editor
        ICompilationUnit compilationUnit = getCompilationUnit();
        if (compilationUnit == null) {
            EventLogger.debug("EventHyperlinkDetector: compilation unit is null");
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: Compilation unit: " + compilationUnit.getElementName());
        
        // Parse the AST from the compilation unit
        ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
        parser.setSource(compilationUnit);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);
        
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        if (ast == null) {
            EventLogger.debug("EventHyperlinkDetector: AST is null");
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: AST parsed successfully");
        
        // Find the AST node at the cursor position using NodeFinder
        ASTNode node = NodeFinder.perform(ast, region.getOffset(), region.getLength());
        if (node == null) {
            EventLogger.debug("EventHyperlinkDetector: No AST node found at cursor position");
            return null;
        }
        
        EventLogger.debug("EventHyperlinkDetector: Found AST node: " + node.getClass().getSimpleName() 
            + " at offset " + node.getStartPosition());
        
        // Check if the node or its parent is an event subscription annotation
        ASTNode currentNode = node;
        int depth = 0;
        while (currentNode != null) {
            EventLogger.debug("EventHyperlinkDetector: Checking node at depth " + depth + ": " 
                + currentNode.getClass().getSimpleName());
            
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
            depth++;
        }
        
        EventLogger.debug("EventHyperlinkDetector: No event-related construct found in AST hierarchy");
        
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
        
        EventLogger.debug("EventHyperlinkDetector.isEventSubscriptionAnnotation: Checking annotation");
        
        // Try to resolve via type binding (most reliable)
        ITypeBinding typeBinding = annotation.resolveTypeBinding();
        if (typeBinding != null && SUBSCRIPTION_ANNOTATION_FQN != null) {
            String annotationFqn = typeBinding.getQualifiedName();
            EventLogger.debug("EventHyperlinkDetector.isEventSubscriptionAnnotation: Resolved FQN: " 
                + annotationFqn + ", expected: " + SUBSCRIPTION_ANNOTATION_FQN);
            if (SUBSCRIPTION_ANNOTATION_FQN.equals(annotationFqn)) {
                return true;
            }
        }
        
        // Fallback: check simple name from getTypeName()
        String annotationName = annotation.getTypeName().getFullyQualifiedName();
        EventLogger.debug("EventHyperlinkDetector.isEventSubscriptionAnnotation: Simple name: " + annotationName);
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
        String methodName = invocation.getName().getIdentifier();
        
        EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: Checking method invocation: " + methodName);
        
        // Check method name: "publisher"
        if (PUBLISHER_METHOD_NAME == null) {
            EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: PUBLISHER_METHOD_NAME is null");
            return false;
        }
        if (!PUBLISHER_METHOD_NAME.equals(methodName)) {
            EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: Method name '" + methodName 
                + "' does not match expected '" + PUBLISHER_METHOD_NAME + "'");
            return false;
        }
        
        EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: Method name matches!");
        
        // Check expression type: InvocationApi
        Expression expression = invocation.getExpression();
        if (expression == null) {
            EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: Expression is null");
            return false;
        }
        
        ITypeBinding binding = expression.resolveTypeBinding();
        if (binding != null && PUBLISHER_INVOCATION_API_FQN != null) {
            String typeName = binding.getQualifiedName();
            EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: Expression type: " + typeName 
                + ", expected: " + PUBLISHER_INVOCATION_API_FQN);
            return PUBLISHER_INVOCATION_API_FQN.equals(typeName);
        }
        
        if (binding == null) {
            EventLogger.debug("EventHyperlinkDetector.isPublisherInvocation: Could not resolve type binding for expression");
        }
        
        return false;
    }
    
    /**
     * Creates hyperlinks from a publisher invocation to its publisher implementation.
     * 
     * @param node the MethodInvocation node (publisher call)
     * @param region the text region for the hyperlink
     * @return array of hyperlinks or null if no publisher found
     */
    private IHyperlink[] createPublisherToSubscribersHyperlinks(ASTNode node, IRegion region) {
        EventLogger.debug("createPublisherToSubscribersHyperlinks: Starting hyperlink creation for publisher");
        
        // Find the actual MethodInvocation node if this isn't one already
        ASTNode methodNode = node;
        int depth = 0;
        while (methodNode != null && !(methodNode instanceof MethodInvocation) && depth < 5) {
            methodNode = methodNode.getParent();
            depth++;
        }
        
        if (methodNode == null || !(methodNode instanceof MethodInvocation)) {
            EventLogger.debug("createPublisherToSubscribersHyperlinks: Could not find MethodInvocation node");
            return null;
        }
        
        MethodInvocation invocation = (MethodInvocation) methodNode;
        EventLogger.debug("createPublisherToSubscribersHyperlinks: Method invocation: " + invocation.getName().getIdentifier());
        
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

        // Calculate hyperlink region - must encompass all possible click positions within publisher() call
        // The region should cover from method name start through at least the opening parenthesis and first argument
        int regionOffset = node.getStartPosition();
        int regionLength = 50;  // Extend to cover "publisher(...)" with all arguments
        
        if (node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            SimpleName methodName = mi.getName();
            if (methodName != null) {
                regionOffset = methodName.getStartPosition();
                regionLength = 50;  // Fixed length to cover entire invocation
            }
        }
        
        IRegion hyperlinkRegion = new Region(regionOffset, regionLength);
        EventLogger.debug("EventHyperlinkDetector: Region offset=" + hyperlinkRegion.getOffset() 
            + ", length=" + hyperlinkRegion.getLength());

        if (subscribers.size() == 1) {
            EventLogger.debug("EventHyperlinkDetector: Creating direct hyperlink for single subscriber");
            Object methodObj = subscribers.get(0).getMethod();
            if (methodObj instanceof IMethod) {
                IMethod method = (IMethod) methodObj;
                EventLogger.debug("EventHyperlinkDetector: Hyperlink target: " 
                    + method.getDeclaringType().getElementName() + "." + method.getElementName());
                JavaElementHyperlink hyperlink = new JavaElementHyperlink(method, hyperlinkRegion);
                EventLogger.debug("EventHyperlinkDetector: RETURNING hyperlink array with region offset=" + hyperlinkRegion.getOffset() 
                    + ", region length=" + hyperlinkRegion.getLength());
                return new IHyperlink[] { hyperlink };
            }
        }

        EventLogger.debug("EventHyperlinkDetector: Creating selection dialog hyperlink for " + subscribers.size() + " subscribers");
        EventSubscriberListHyperlink multiLink = new EventSubscriberListHyperlink(eventDef, subscribers, hyperlinkRegion);
        EventLogger.debug("EventHyperlinkDetector: RETURNING multi-subscriber hyperlink array with region offset=" + hyperlinkRegion.getOffset() 
            + ", region length=" + hyperlinkRegion.getLength());
        return new IHyperlink[] { multiLink };
    }
    
    /**
     * Extracts event definition from a publisher invocation.
     * 
     * @param invocation the publisher method invocation
     * @return EventDefinition or null if extraction fails
     */
    private EventDefinition extractEventFromPublisher(MethodInvocation invocation) {
        EventLogger.debug("extractEventFromPublisher: Starting event extraction from publisher invocation");
        
        // Parse arguments (exactly 3 expected: publisherApi, subscriberApi, eventName)
        @SuppressWarnings("unchecked")
        List<Expression> arguments = invocation.arguments();
        if (arguments == null || arguments.size() != 3) {
            EventLogger.debug("extractEventFromPublisher: Expected 3 arguments, got " 
                + (arguments == null ? "null" : arguments.size()));
            return null;
        }
        
        EventLogger.debug("extractEventFromPublisher: Found 3 arguments, extracting types");
        
        // Arg 0: Publisher API class (TypeLiteral)
        Expression arg0 = arguments.get(0);
        EventLogger.debug("extractEventFromPublisher: Arg0 type: " + arg0.getClass().getSimpleName());
        String publisherApi = extractTypeFromTypeLiteral(arg0);
        if (publisherApi == null) {
            EventLogger.debug("extractEventFromPublisher: Failed to extract publisher API from arg0");
            return null;
        }
        EventLogger.debug("extractEventFromPublisher: Publisher API extracted: " + publisherApi);
        
        // Arg 2: Event name constant
        Expression arg2 = arguments.get(2);
        EventLogger.debug("extractEventFromPublisher: Arg2 type: " + arg2.getClass().getSimpleName());
        String eventName = resolveEventName(arg2);
        if (eventName == null) {
            EventLogger.debug("extractEventFromPublisher: Failed to resolve event name from arg2");
            return null;
        }
        EventLogger.debug("extractEventFromPublisher: Event name resolved: " + eventName);
        
        EventLogger.debug("extractEventFromPublisher: Successfully created EventDefinition: " 
            + publisherApi + ":" + eventName);
        return new EventDefinition(publisherApi, eventName);
    }
    
    /**
     * Extracts the FQN from a TypeLiteral expression.
     * 
     * @param expr the expression (should be TypeLiteral)
     * @return FQN of the type or null
     */
    private String extractTypeFromTypeLiteral(Expression expr) {
        EventLogger.debug("extractTypeFromTypeLiteral: Expression type: " + expr.getClass().getSimpleName());
        
        if (!(expr instanceof TypeLiteral)) {
            EventLogger.debug("extractTypeFromTypeLiteral: Expression is not TypeLiteral");
            return null;
        }
        
        TypeLiteral typeLit = (TypeLiteral) expr;
        ITypeBinding binding = typeLit.resolveTypeBinding();
        if (binding != null) {
            String fqn = binding.getQualifiedName();
            EventLogger.debug("extractTypeFromTypeLiteral: Resolved FQN (raw): " + fqn);
            
            // Handle java.lang.Class<T> - extract the actual type argument
            if (fqn.startsWith("java.lang.Class") && binding.getTypeArguments().length > 0) {
                ITypeBinding[] typeArgs = binding.getTypeArguments();
                String actualType = typeArgs[0].getQualifiedName();
                EventLogger.debug("extractTypeFromTypeLiteral: Extracted actual type from Class<T>: " + actualType);
                return actualType;
            }
            
            EventLogger.debug("extractTypeFromTypeLiteral: Resolved FQN: " + fqn);
            return fqn;
        }
        
        EventLogger.debug("extractTypeFromTypeLiteral: Could not resolve type binding");
        return null;
    }
    
    /**
     * Resolves event name from an expression (String literal or constant).
     * 
     * @param expr the expression
     * @return event name or null
     */
    private String resolveEventName(Expression expr) {
        EventLogger.debug("resolveEventName: Expression type: " + expr.getClass().getSimpleName());
        
        // Try direct StringLiteral first
        if (expr instanceof StringLiteral) {
            String value = ((StringLiteral) expr).getLiteralValue();
            EventLogger.debug("resolveEventName: Resolved from StringLiteral: " + value);
            return value;
        }
        
        // Try to resolve as constant using JavaElementResolver
        EventLogger.debug("resolveEventName: Attempting to resolve as constant reference");
        String resolved = JavaElementResolver.resolveConstant(expr);
        if (resolved != null) {
            EventLogger.debug("resolveEventName: Resolved from constant: " + resolved);
        } else {
            EventLogger.debug("resolveEventName: Failed to resolve constant");
        }
        return resolved;
    }
    
    /**
     * Creates hyperlinks from a subscriber annotation to its publisher.
     * 
     * @param node the Annotation node (EventSubscription)
     * @param region the text region for the hyperlink
     * @return array of hyperlinks or null if no publisher found
     */
    private IHyperlink[] createSubscriberToPublisherHyperlinks(ASTNode node, IRegion region) {
        EventLogger.debug("createSubscriberToPublisherHyperlinks: Starting hyperlink creation for subscriber");
        
        // Find the actual Annotation node if this isn't one already
        ASTNode annotationNode = node;
        int depth = 0;
        while (annotationNode != null && !(annotationNode instanceof Annotation) && depth < 5) {
            annotationNode = annotationNode.getParent();
            depth++;
        }
        
        if (annotationNode == null || !(annotationNode instanceof Annotation)) {
            EventLogger.debug("createSubscriberToPublisherHyperlinks: Could not find Annotation node");
            return null;
        }
        
        Annotation annotation = (Annotation) annotationNode;
        EventLogger.debug("createSubscriberToPublisherHyperlinks: Annotation type: " + annotation.getTypeName().getFullyQualifiedName());
        
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
        
        // Use the actual annotation name position for the hyperlink region
        int regionOffset = annotationNode.getStartPosition();
        int regionLength = 10;
        
        if (annotation.getTypeName() instanceof SimpleName) {
            SimpleName typeName = (SimpleName) annotation.getTypeName();
            regionOffset = typeName.getStartPosition();
            regionLength = typeName.getIdentifier().length();
        }
        
        IRegion hyperlinkRegion = new Region(regionOffset, regionLength);
        EventLogger.debug("EventHyperlinkDetector: Region offset=" + hyperlinkRegion.getOffset() 
            + ", length=" + hyperlinkRegion.getLength());
        
        // Publisher found: create hyperlink
        Object methodObj = publisher.getMethod();
        if (methodObj instanceof IMethod) {
            IMethod method = (IMethod) methodObj;
            EventLogger.debug("EventHyperlinkDetector: Creating hyperlink to publisher: " 
                + method.getDeclaringType().getElementName() + "." + method.getElementName());
            return new IHyperlink[] {
                new JavaElementHyperlink(method, hyperlinkRegion)
            };
        }
        
        EventLogger.debug("EventHyperlinkDetector: Publisher method is not IMethod instance");
        return null;
    }
    
    /**
     * Extracts event definition from a subscriber annotation.
     * 
     * @param annotation the EventSubscription annotation
     * @return EventDefinition or null if extraction fails
     */
    private EventDefinition extractEventFromSubscriber(Annotation annotation) {
        EventLogger.debug("extractEventFromSubscriber: Starting event extraction from subscriber annotation");
        EventLogger.debug("extractEventFromSubscriber: Annotation type: " + annotation.getClass().getSimpleName());
        EventLogger.debug("extractEventFromSubscriber: Looking for parameters - api: " + SUBSCRIBER_API_PARAM 
            + ", event: " + SUBSCRIBER_EVENT_PARAM);
        
        // Extract annotation parameters: api, event
        String api = resolveAnnotationValue(annotation, SUBSCRIBER_API_PARAM);
        EventLogger.debug("extractEventFromSubscriber: API parameter value: " + api);
        
        String event = resolveAnnotationValue(annotation, SUBSCRIBER_EVENT_PARAM);
        EventLogger.debug("extractEventFromSubscriber: Event parameter value: " + event);
        
        if (api == null || event == null) {
            EventLogger.debug("extractEventFromSubscriber: Failed to extract api or event from annotation");
            return null;
        }
        
        EventLogger.debug("extractEventFromSubscriber: Successfully created EventDefinition: " 
            + api + ":" + event);
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
        EventLogger.debug("getAnnotationValue: Getting parameter '" + paramName + "' from " 
            + annotation.getClass().getSimpleName());
        
        if (paramName == null || paramName.isEmpty()) {
            EventLogger.debug("getAnnotationValue: Parameter name is null or empty");
            return null;
        }
        
        if (annotation instanceof MarkerAnnotation) {
            EventLogger.debug("getAnnotationValue: MarkerAnnotation has no parameters");
            return null;
        }
        
        if (annotation instanceof SingleMemberAnnotation) {
            if ("value".equals(paramName)) {
                EventLogger.debug("getAnnotationValue: Getting 'value' from SingleMemberAnnotation");
                return ((SingleMemberAnnotation) annotation).getValue();
            }
            EventLogger.debug("getAnnotationValue: SingleMemberAnnotation only has 'value' parameter");
            return null;
        }
        
        if (annotation instanceof NormalAnnotation) {
            NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
            @SuppressWarnings("unchecked")
            List<MemberValuePair> values = normalAnnotation.values();
            EventLogger.debug("getAnnotationValue: NormalAnnotation has " + values.size() + " parameters");
            for (MemberValuePair pair : values) {
                String pairName = pair.getName().getIdentifier();
                EventLogger.debug("getAnnotationValue: Checking parameter '" + pairName + "'");
                if (paramName.equals(pairName)) {
                    EventLogger.debug("getAnnotationValue: Found matching parameter '" + paramName + "'");
                    return pair.getValue();
                }
            }
            EventLogger.debug("getAnnotationValue: Parameter '" + paramName + "' not found in NormalAnnotation");
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
        EventLogger.debug("resolveAnnotationValue: Resolving parameter '" + paramName + "'");
        
        Expression valueExpr = getAnnotationValue(annotation, paramName);
        if (valueExpr == null) {
            EventLogger.debug("resolveAnnotationValue: Value expression is null for parameter '" + paramName + "'");
            return null;
        }
        
        EventLogger.debug("resolveAnnotationValue: Value expression type: " + valueExpr.getClass().getSimpleName());
        
        if (valueExpr instanceof StringLiteral) {
            String value = ((StringLiteral) valueExpr).getLiteralValue();
            EventLogger.debug("resolveAnnotationValue: Resolved from StringLiteral: " + value);
            return value;
        }
        
        EventLogger.debug("resolveAnnotationValue: Attempting to resolve as constant");
        String resolved = JavaElementResolver.resolveConstant(valueExpr);
        if (resolved != null) {
            EventLogger.debug("resolveAnnotationValue: Resolved from constant: " + resolved);
        } else {
            EventLogger.debug("resolveAnnotationValue: Failed to resolve constant");
        }
        return resolved;
    }
}
