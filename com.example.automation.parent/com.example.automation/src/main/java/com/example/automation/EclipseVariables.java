package com.example.automation;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.VariablesPlugin;

/**
 * Utility for resolving Eclipse string variable references (e.g. {@code ${workspace_loc}})
 * in configuration values before passing them to file I/O operations.
 */
public final class EclipseVariables {

    private EclipseVariables() {}

    /**
     * Resolves Eclipse string variables in {@code value} and returns the substituted result.
     * Values without variable references are returned unchanged. {@code null} or blank
     * values are returned as-is.
     *
     * @param value the string to resolve; may be null or blank
     * @return the resolved string
     * @throws CoreException if a referenced variable is undefined or resolution fails
     */
    public static String resolve(String value) throws CoreException {
        if (value == null || value.isBlank()) return value;
        return VariablesPlugin.getDefault().getStringVariableManager()
            .performStringSubstitution(value);
    }
}
