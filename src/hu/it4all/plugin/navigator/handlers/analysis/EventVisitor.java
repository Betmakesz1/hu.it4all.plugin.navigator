package hu.it4all.plugin.navigator.handlers.analysis;

import java.util.List;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;

/**
 * Végighalad a fájlon
 * invocationApi.publisher(..., Subscriber.class, ...) hívásokat.
 */
public class EventVisitor extends ASTVisitor {

    private final MarkerCreator markerCreator;

    /**
     * @param markerCreator callback ami ténylegesen létrehozza a Markert.
     */
    public EventVisitor(MarkerCreator markerCreator) {
        this.markerCreator = markerCreator;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        String methodName = node.getName().getIdentifier();

        if ("publisher".equals(methodName)) {
            System.out.println("    >>> FOUND 'publisher' method call!");
            
            Expression expression = node.getExpression();
            if (expression != null && expression.toString().contains("invocationApi")) {
                System.out.println("    >>> FOUND invocationApi.publisher()!");
                
                List<?> arguments = node.arguments();
                System.out.println("    >>> Arguments count: " + arguments.size());
                
                if (arguments.size() >= 2) {
                    Object secondArg = arguments.get(1);
                    System.out.println("    >>> Second arg type: " + secondArg.getClass().getSimpleName());
                    
                    if (secondArg instanceof TypeLiteral) {
                        TypeLiteral typeLiteral = (TypeLiteral) secondArg;
                        String interfaceName = typeLiteral.getType().toString();
                        System.out.println("    >>> Interface name: " + interfaceName);
                        
                        int startPosition = node.getStartPosition();
                        int length = node.getLength();
                        int lineNumber = -1;

                        if (node.getRoot() instanceof CompilationUnit) {
                            CompilationUnit cu = (CompilationUnit) node.getRoot();
                            lineNumber = cu.getLineNumber(startPosition);
                        }

                        System.out.println("    >>> CREATING MARKER at line " + lineNumber);
                        markerCreator.addMarker(startPosition, length, interfaceName, lineNumber);
                    }
                }
            }
        }
        return super.visit(node);
    }

    /**
     * Belső interfész a Marker létrehozásának leválasztásához.
     */
    @FunctionalInterface
    public interface MarkerCreator {
        void addMarker(int offset, int length, String targetInterface, int line);
    }
}