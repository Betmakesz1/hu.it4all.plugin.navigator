package org.smartbit4all.eclipse.event.ast;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.Flags;

/**
 * Java element resolution utilities
 */
public class JavaElementResolver {

    /**
     * Resolve a constant value from an Expression (e.g., MyApi.EVENT_NAME)
     * 
     * @param expr AST Expression node (typically QualifiedName or SimpleName)
     * @return String value of the constant, or null if not resolvable
     */
    public static String resolveConstant(Expression expr) {
        // Null safety check
        if (expr == null) {
            return null;
        }

        // Try to resolve the binding from the expression
        IVariableBinding binding = null;

        if (expr instanceof QualifiedName) {
            // Handle cases like: MyApi.EVENT_NAME
            QualifiedName qualifiedName = (QualifiedName) expr;
            IBinding resolvedBinding = qualifiedName.resolveBinding();
            if (resolvedBinding instanceof IVariableBinding) {
                binding = (IVariableBinding) resolvedBinding;
            }
        } else if (expr instanceof SimpleName) {
            // Handle cases like: EVENT_NAME (direct variable reference)
        	// A resolveBinding megkeresi a statikus importokat és azokat járja be. 
        	// Ha megtalálja a változót amihez allokálja, visszaadja az értéket
            SimpleName simpleName = (SimpleName) expr;
            IBinding resolvedBinding = simpleName.resolveBinding();
            if (resolvedBinding instanceof IVariableBinding) {
                binding = (IVariableBinding) resolvedBinding;
            }
        } else {
            // Unsupported expression type
            return null;
        }

        // If no binding could be resolved
        if (binding == null) {
            return null;
        }

        // Check if it's a final field (constant)
        int modifiers = binding.getModifiers();
        if (!Flags.isFinal(modifiers)) {
            // Not a final field, cannot be a constant value
            return null;
        }

        // Get the constant value
        Object constantValue = binding.getConstantValue();
        if (constantValue == null) {
            return null;
        }

        // Return as String
        return constantValue.toString();
    }
    
}
