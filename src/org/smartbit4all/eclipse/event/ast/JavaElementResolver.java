package org.smartbit4all.eclipse.event.ast;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.Flags;
import org.smartbit4all.eclipse.event.core.EventLogger;

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
            EventLogger.info("resolveConstant: Expression is null, returning null");
            return null;
        }

        try {
            // Try to resolve the binding from the expression
            IVariableBinding binding = null;

            if (expr instanceof QualifiedName) {
                // Handle cases like: MyApi.EVENT_NAME
                QualifiedName qualifiedName = (QualifiedName) expr;
                IBinding resolvedBinding = qualifiedName.resolveBinding();
                if (resolvedBinding instanceof IVariableBinding) {
                    binding = (IVariableBinding) resolvedBinding;
                    EventLogger.debug("resolveConstant: Resolved QualifiedName binding: " + qualifiedName);
                } else {
                    EventLogger.debug("resolveConstant: QualifiedName binding is not IVariableBinding");
                }
            } else if (expr instanceof SimpleName) {
                // Handle cases like: EVENT_NAME (direct variable reference)
                // A resolveBinding megkeresi a statikus importokat és azokat járja be. 
                // Ha megtalálja a változót amihez allokálja, visszaadja az értéket
                SimpleName simpleName = (SimpleName) expr;
                IBinding resolvedBinding = simpleName.resolveBinding();
                if (resolvedBinding instanceof IVariableBinding) {
                    binding = (IVariableBinding) resolvedBinding;
                    EventLogger.debug("resolveConstant: Resolved SimpleName binding: " + simpleName);
                } else {
                    EventLogger.debug("resolveConstant: SimpleName binding is not IVariableBinding");
                }
            } else {
                // Unsupported expression type
                EventLogger.debug("resolveConstant: Unsupported expression type: " + expr.getClass().getSimpleName());
                return null;
            }

            // If no binding could be resolved
            if (binding == null) {
                EventLogger.debug("resolveConstant: Binding is null after resolution");
                return null;
            }

            // Check if it's a final field (constant)
            int modifiers = binding.getModifiers();
            if (!Flags.isFinal(modifiers)) {
                // Not a final field, cannot be a constant value
                EventLogger.debug("resolveConstant: Field is not final: " + binding.getName());
                return null;
            }

            // Get the constant value
            Object constantValue = binding.getConstantValue();
            if (constantValue == null) {
                EventLogger.debug("resolveConstant: No constant value found for: " + binding.getName());
                return null;
            }

            // Return as String
            String result = constantValue.toString();
            EventLogger.debug("resolveConstant: Successfully resolved constant: " + binding.getName() + " = " + result);
            return result;
        } catch (Exception e) {
            EventLogger.error("resolveConstant: Exception during constant resolution", e);
            return null;
        }
    }
    /**
     * Resolve a constant value from an IField directly
     * 
     * @param field JDT IField representing a constant
     * @return String value of the constant, or null if not resolvable
     */
    public static String resolveConstantFromField(IField field) {
        // Null safety check
        if (field == null) {
            EventLogger.info("resolveConstantFromField: IField is null, returning null");
            return null;
        }

        try {
            String fieldName = field.getElementName();
            
            // Check if it's a final field (constant)
            int flags = field.getFlags();
            if (!Flags.isFinal(flags)) {
                // Not a final field, cannot be a constant value
                EventLogger.debug("resolveConstantFromField: Field is not final: " + fieldName);
                return null;
            }

            // Get the constant value from the field's default value
            Object constantValue = field.getConstant();
            if (constantValue == null) {
                EventLogger.debug("resolveConstantFromField: No constant value found for: " + fieldName);
                return null;
            }

            // Return as String
            String result = constantValue.toString();
            EventLogger.debug("resolveConstantFromField: Successfully resolved constant: " + fieldName + " = " + result);
            return result;
        } catch (Exception e) {
            EventLogger.error("resolveConstantFromField: Exception during field constant resolution", e);
            return null;
        }
    }
}
