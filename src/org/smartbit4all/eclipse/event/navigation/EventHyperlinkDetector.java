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
        
        if (textViewer == null) {
            return null;
        }
        
        IDocument document = textViewer.getDocument();
        if (document == null) {
            return null;
        }
        
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
        
        ASTNode node = NodeFinder.perform(ast, region.getOffset(), region.getLength());
        if (node == null) {
            return null;
        }
        
        ASTNode currentNode = node;
        while (currentNode != null) {
            if (isEventSubscriptionAnnotation(currentNode)) {
                return createSubscriberToPublisherHyperlinks(currentNode, region);
            }
            if (isPublisherInvocation(currentNode)) {
                return createPublisherToSubscribersHyperlinks(currentNode, region);
            }
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
        if (!(node instanceof Annotation)) {
            return false;
        }
        
        Annotation annotation = (Annotation) node;
        ITypeBinding typeBinding = annotation.resolveTypeBinding();
        return typeBinding != null && SUBSCRIPTION_ANNOTATION_FQN != null 
            && SUBSCRIPTION_ANNOTATION_FQN.equals(typeBinding.getQualifiedName());
    }
    
    /**
     * Checks if the AST node is a publisher invocation pattern.
     * 
     * @param node the AST node to check
     * @return true if the node is an invocationApi.publisher(...) invocation
     */
    private boolean isPublisherInvocation(ASTNode node) {
        if (!(node instanceof MethodInvocation)) {
            return false;
        }
        
        MethodInvocation invocation = (MethodInvocation) node;
        if (!PUBLISHER_METHOD_NAME.equals(invocation.getName().getIdentifier())) {
            return false;
        }
        
        Expression expression = invocation.getExpression();
        if (expression == null) {
            return false;
        }
        
        ITypeBinding binding = expression.resolveTypeBinding();
        return binding != null && PUBLISHER_INVOCATION_API_FQN != null 
            && PUBLISHER_INVOCATION_API_FQN.equals(binding.getQualifiedName());
    }
    
    /**
     * Creates hyperlinks from a publisher invocation to its publisher implementation.
     * 
     * @param node the MethodInvocation node (publisher call)
     * @param region the text region for the hyperlink
     * @return array of hyperlinks or null if no publisher found
     */
    private IHyperlink[] createPublisherToSubscribersHyperlinks(ASTNode node, IRegion region) {
        MethodInvocation invocation = (MethodInvocation) node;
        
        EventDefinition eventDef = extractEventFromPublisher(invocation);
        if (eventDef == null) {
            return null;
        }
        
        EventIndexManager indexManager = EventIndexManager.getInstance();
        List<EventSubscriberInfo> subscribers = indexManager.findSubscribers(eventDef);
        
        if (subscribers == null || subscribers.isEmpty()) {
            return null;
        }

        MethodInvocation mi = (MethodInvocation) node;
        SimpleName methodName = mi.getName();
        int regionOffset = methodName != null ? methodName.getStartPosition() : node.getStartPosition();
        int regionLength = 50;
        
        IRegion hyperlinkRegion = new Region(regionOffset, regionLength);

        if (subscribers.size() == 1) {
            Object methodObj = subscribers.get(0).getMethod();
            if (methodObj instanceof IMethod) {
                IMethod method = (IMethod) methodObj;
                JavaElementHyperlink hyperlink = new JavaElementHyperlink(method, hyperlinkRegion);
                return new IHyperlink[] { hyperlink };
            }
        }
        
        EventSubscriberListHyperlink multiLink = new EventSubscriberListHyperlink(eventDef, subscribers, hyperlinkRegion);
        return new IHyperlink[] { multiLink };
    }
    
    /**
     * Extracts event definition from a publisher invocation.
     * 
     * @param invocation the publisher method invocation
     * @return EventDefinition or null if extraction fails
     */
    private EventDefinition extractEventFromPublisher(MethodInvocation invocation) {
        @SuppressWarnings("unchecked")
        List<Expression> arguments = invocation.arguments();
        if (arguments == null || arguments.size() != 3) {
            return null;
        }
        
        String publisherApi = extractTypeFromTypeLiteral(arguments.get(0));
        if (publisherApi == null) {
            return null;
        }
        
        String eventName = resolveEventName(arguments.get(2));
        if (eventName == null) {
            return null;
        }
        
        return new EventDefinition(publisherApi, eventName);
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
        if (binding == null) {
            return null;
        }
        
        String fqn = binding.getQualifiedName();
        
        if (fqn.startsWith("java.lang.Class") && binding.getTypeArguments().length > 0) {
            return binding.getTypeArguments()[0].getQualifiedName();
        }
        
        return fqn;
    }
    
    /**
     * Resolves event name from an expression (String literal or constant).
     * 
     * @param expr the expression
     * @return event name or null
     */
    private String resolveEventName(Expression expr) {
        if (expr instanceof StringLiteral) {
            return ((StringLiteral) expr).getLiteralValue();
        }
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
        Annotation annotation = (Annotation) node;
        
        EventDefinition eventDef = extractEventFromSubscriber(annotation);
        if (eventDef == null) {
            return null;
        }
        
        EventIndexManager indexManager = EventIndexManager.getInstance();
        EventPublisherInfo publisher = indexManager.findPublisher(eventDef);
        
        if (publisher == null) {
            return null;
        }
        
        int regionOffset = node.getStartPosition();
        int regionLength = 10;
        
        if (annotation.getTypeName() instanceof SimpleName) {
            SimpleName typeName = (SimpleName) annotation.getTypeName();
            regionOffset = typeName.getStartPosition();
            regionLength = typeName.getIdentifier().length();
        }
        
        IRegion hyperlinkRegion = new Region(regionOffset, regionLength);
        
        Object methodObj = publisher.getMethod();
        if (methodObj instanceof IMethod) {
            IMethod method = (IMethod) methodObj;
            return new IHyperlink[] {
                new JavaElementHyperlink(method, hyperlinkRegion)
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
        if (paramName == null || paramName.isEmpty() || !(annotation instanceof NormalAnnotation)) {
            return null;
        }
        
        NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
        @SuppressWarnings("unchecked")
        List<MemberValuePair> values = normalAnnotation.values();
        for (MemberValuePair pair : values) {
            if (paramName.equals(pair.getName().getIdentifier())) {
                return pair.getValue();
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
