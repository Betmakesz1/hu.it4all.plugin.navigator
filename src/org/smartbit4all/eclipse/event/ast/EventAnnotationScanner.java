package org.smartbit4all.eclipse.event.ast;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.smartbit4all.eclipse.event.core.EventSubscriberInfo;
import org.smartbit4all.eclipse.event.core.EventPluginProperties;
import org.smartbit4all.eclipse.event.core.EventLogger;

/**
 * Scanner for @EventSubscription annotations
 */
public class EventAnnotationScanner {

    private static final String SUBSCRIPTION_ANNOTATION_FQN =
            EventPluginProperties.get("event.subscription.annotation.fqn");
        private static final String SUBSCRIPTION_ANNOTATION_NAME =
            EventPluginProperties.get("event.subscription.annotation.name");
        private static final String SUBSCRIBER_API_PARAM =
            EventPluginProperties.get("event.subscriber.info.api");
        private static final String SUBSCRIBER_EVENT_PARAM =
            EventPluginProperties.get("event.subscriber.info.event");
        private static final String SUBSCRIBER_CHANNEL_PARAM =
            EventPluginProperties.get("event.subscriber.info.channel");

    /**
     * Scan compilation unit for @EventSubscription annotations
     */
    public List<?> scanForSubscribers(ICompilationUnit unit) {
        List<Object> result = new ArrayList<>();

        if (unit == null) {
            EventLogger.info("scanForSubscribers: ICompilationUnit is null");
            return result;
        }

        try {
            ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(unit);
            parser.setResolveBindings(true);
            parser.setBindingsRecovery(true);
            parser.setStatementsRecovery(true);

            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            cu.accept(new EventSubscriptionVisitor(result));
        } catch (Exception e) {
            EventLogger.error("scanForSubscribers: Exception during AST scan", e);
        }
        return result;
    }

    private static class EventSubscriptionVisitor extends ASTVisitor {
        private final List<Object> subscribers;

        EventSubscriptionVisitor(List<Object> subscribers) {
            this.subscribers = subscribers;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            List<?> modifiers = node.modifiers();
            for (Object modifier : modifiers) {
                if (modifier instanceof Annotation) {
                    Annotation annotation = (Annotation) modifier;
                    if (isEventSubscriptionAnnotation(annotation)) {
                        EventSubscriberInfo info = extractSubscriberInfo(node, annotation);
                        if (info != null) {
                            subscribers.add(info);
                        }
                    }
                }
            }
            return true;
        }
    }

    private static boolean isEventSubscriptionAnnotation(Annotation annotation) {
        if (annotation == null) {
            return false;
        }

        ITypeBinding binding = annotation.resolveTypeBinding();
        if (binding != null && SUBSCRIPTION_ANNOTATION_FQN != null) {
            return SUBSCRIPTION_ANNOTATION_FQN.equals(binding.getQualifiedName());
        }

        String typeName = annotation.getTypeName().getFullyQualifiedName();
        if (SUBSCRIPTION_ANNOTATION_FQN != null && SUBSCRIPTION_ANNOTATION_FQN.equals(typeName)) {
            return true;
        }

        if (SUBSCRIPTION_ANNOTATION_NAME == null) {
            return false;
        }

        return SUBSCRIPTION_ANNOTATION_NAME.equals(typeName);
    }

    private static EventSubscriberInfo extractSubscriberInfo(MethodDeclaration node, Annotation annotation) {
        String api = resolveAnnotationValue(annotation, SUBSCRIBER_API_PARAM);
        String event = resolveAnnotationValue(annotation, SUBSCRIBER_EVENT_PARAM);
        String channel = resolveAnnotationValue(annotation, SUBSCRIBER_CHANNEL_PARAM);

        if (api == null || event == null) {
            EventLogger.debug("extractSubscriberInfo: Missing api or event value");
            return null;
        }

        IMethodBinding methodBinding = node.resolveBinding();
        IMethod method = null;
        if (methodBinding != null) {
            IJavaElement element = methodBinding.getJavaElement();
            if (element instanceof IMethod) {
                method = (IMethod) element;
            }
        }

        String className = resolveDeclaringClassName(node, methodBinding);
        String methodName = node.getName().getIdentifier();

        return new EventSubscriberInfo(api, event, channel, className, methodName, method);
    }
    private static Expression getAnnotationValue(Annotation annotation, String paramName) {
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
            List<?> values = normalAnnotation.values();
            for (Object value : values) {
                if (value instanceof MemberValuePair) {
                    MemberValuePair pair = (MemberValuePair) value;
                    if (paramName.equals(pair.getName().getIdentifier())) {
                        return pair.getValue();
                    }
                }
            }
        }

        return null;
    }

    private static String resolveAnnotationValue(Annotation annotation, String paramName) {
        Expression valueExpr = getAnnotationValue(annotation, paramName);
        if (valueExpr == null) {
            return null;
        }

        if (valueExpr instanceof StringLiteral) {
            return ((StringLiteral) valueExpr).getLiteralValue();
        }

        return JavaElementResolver.resolveConstant(valueExpr);
    }

    private static String resolveDeclaringClassName(MethodDeclaration node, IMethodBinding binding) {
        if (binding != null) {
            ITypeBinding typeBinding = binding.getDeclaringClass();
            if (typeBinding != null) {
                return typeBinding.getQualifiedName();
            }
        }

        TypeDeclaration typeDecl = findEnclosingType(node);
        if (typeDecl != null && typeDecl.getName() != null) {
            return typeDecl.getName().getIdentifier();
        }

        return null;
    }

    private static TypeDeclaration findEnclosingType(MethodDeclaration node) {
        if (node == null) {
            return null;
        }

        org.eclipse.jdt.core.dom.ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof TypeDeclaration) {
                return (TypeDeclaration) current;
            }
            current = current.getParent();
        }

        return null;
    }

}
