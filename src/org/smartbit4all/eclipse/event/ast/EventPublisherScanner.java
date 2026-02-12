package org.smartbit4all.eclipse.event.ast;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.smartbit4all.eclipse.event.core.EventLogger;
import org.smartbit4all.eclipse.event.core.EventPluginProperties;
import org.smartbit4all.eclipse.event.core.EventPublisherInfo;

/**
 * Scanner for invocationApi.publisher() pattern
 */
public class EventPublisherScanner {

    private static final String INVOCATION_API_FQN =
            EventPluginProperties.get("event.publisher.invocation.api.fqn");
    private static final String PUBLISHER_METHOD_NAME =
            EventPluginProperties.get("event.publisher.method.name");

    /**
     * Scan compilation unit for publisher patterns
     */
    public List<?> scanForPublishers(ICompilationUnit unit) {
        List<Object> result = new ArrayList<>();

        if (unit == null) {
            EventLogger.info("scanForPublishers: ICompilationUnit is null");
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
            cu.accept(new EventPublisherVisitor(result));
            
            EventLogger.info("scanForPublishers: Found " + result.size() + " publisher(s)");
        } catch (Exception e) {
            EventLogger.error("scanForPublishers: Exception during AST scan", e);
        }

        return result;
    }

    private static class EventPublisherVisitor extends ASTVisitor {
        private final List<Object> publishers;

        EventPublisherVisitor(List<Object> publishers) {
            this.publishers = publishers;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            if (isPublisherInvocation(node)) {
                Object publisherInfo = extractPublisherInfo(node);
                if (publisherInfo != null) {
                    publishers.add(publisherInfo);
                }
            }
            return true;
        }
    }

    private static boolean isPublisherInvocation(MethodInvocation node) {
        // Check method name: "publisher"
        if (PUBLISHER_METHOD_NAME == null) {
            return false;
        }
        if (!PUBLISHER_METHOD_NAME.equals(node.getName().getIdentifier())) {
            return false;
        }

        // Check expression type: InvocationApi
        Expression expression = node.getExpression();
        if (expression == null) {
            return false;
        }

        ITypeBinding binding = expression.resolveTypeBinding();
        if (binding != null && INVOCATION_API_FQN != null) {
            return INVOCATION_API_FQN.equals(binding.getQualifiedName());
        }

        return false;
    }

    private static Object extractPublisherInfo(MethodInvocation node) {
        // Parse and validate arguments (exactly 3 expected)
        List<?> arguments = node.arguments();
        if (arguments == null || arguments.size() != 3) {
            EventLogger.debug("extractPublisherInfo: Expected 3 arguments, got " +
                    (arguments == null ? 0 : arguments.size()));
            return null;
        }

        // Arg 0: Publisher API class (TypeLiteral)
        Object arg0 = arguments.get(0);
        if (!(arg0 instanceof Expression)) {
            EventLogger.debug("extractPublisherInfo: Arg 0 is not an Expression");
            return null;
        }

        Expression arg0Expr = (Expression) arg0;
        String publisherApi = extractTypeFromTypeLiteral(arg0Expr);
        if (publisherApi == null) {
            EventLogger.debug("extractPublisherInfo: Failed to extract Publisher API from Arg 0");
            return null;
        }

        // Arg 1: Subscriber API class (TypeLiteral)
        Object arg1 = arguments.get(1);
        if (!(arg1 instanceof Expression)) {
            EventLogger.debug("extractPublisherInfo: Arg 1 is not an Expression");
            return null;
        }

        Expression arg1Expr = (Expression) arg1;
        String subscriberApi = extractTypeFromTypeLiteral(arg1Expr);
        if (subscriberApi == null) {
            EventLogger.debug("extractPublisherInfo: Failed to extract Subscriber API from Arg 1");
            return null;
        }

        // Arg 2: Event név konstans (JavaElementResolver.resolveConstant())
        Object arg2 = arguments.get(2);
        if (!(arg2 instanceof Expression)) {
            EventLogger.debug("extractPublisherInfo: Arg 2 is not an Expression");
            return null;
        }

        Expression arg2Expr = (Expression) arg2;
        String eventName = resolveEventName(arg2Expr);
        if (eventName == null) {
            EventLogger.debug("extractPublisherInfo: Failed to resolve event name from Arg 2");
            return null;
        }

        // Befoglaló metódus megtalálása
        MethodDeclaration enclosingMethod = findEnclosingMethod(node);
        if (enclosingMethod == null) {
            EventLogger.debug("extractPublisherInfo: Could not find enclosing method");
            return null;
        }

        // IMethod kinyerése binding-ból
        IMethodBinding methodBinding = enclosingMethod.resolveBinding();
        IMethod method = null;
        if (methodBinding != null) {
            IJavaElement element = methodBinding.getJavaElement();
            if (element instanceof IMethod) {
                method = (IMethod) element;
            }
        }

        // EventPublisherInfo objektum összeállítása
        String className = resolveDeclaringClassName(enclosingMethod, methodBinding);
        String methodName = enclosingMethod.getName().getIdentifier();

        EventLogger.debug("extractPublisherInfo: Publisher found - API: " + publisherApi + 
                ", Event: " + eventName + ", Method: " + className + "." + methodName);

        return new EventPublisherInfo(publisherApi, eventName, className, methodName, method, node);
    }

    private static String resolveEventName(Expression expr) {
        // Try direct StringLiteral first
        if (expr instanceof StringLiteral) {
            return ((StringLiteral) expr).getLiteralValue();
        }

        // Try to resolve as constant
        return JavaElementResolver.resolveConstant(expr);
    }

    private static String extractTypeFromTypeLiteral(Expression expr) {
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

    private static MethodDeclaration findEnclosingMethod(MethodInvocation node) {
        if (node == null) {
            return null;
        }

        org.eclipse.jdt.core.dom.ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration) {
                return (MethodDeclaration) current;
            }
            current = current.getParent();
        }

        return null;
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
        //Rekurzívan megkeressük a parentet
        while (current != null) {
            if (current instanceof TypeDeclaration) {
                return (TypeDeclaration) current;
            }
            current = current.getParent();
        }

        return null;
    }
}