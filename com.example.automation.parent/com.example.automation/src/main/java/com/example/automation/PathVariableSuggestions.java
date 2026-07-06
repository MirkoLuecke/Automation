package com.example.automation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

public final class PathVariableSuggestions {

    public static final class Suggestion {
        public final String variableForm;
        public final String resolvedPath;
        public final String description;

        public Suggestion(String variableForm, String resolvedPath, String description) {
            this.variableForm = variableForm;
            this.resolvedPath = resolvedPath;
            this.description = description;
        }
    }

    private PathVariableSuggestions() {}

    /** Convenience overload that uses the live Eclipse registry and all open projects. */
    public static List<Suggestion> compute(String absolutePath) {
        IStringVariableManager mgr = VariablesPlugin.getDefault().getStringVariableManager();
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        return compute(absolutePath, projects, mgr);
    }

    /**
     * Computes ranked variable-form suggestions for {@code absolutePath}.
     * Order: per-project workspace matches (most-specific first), workspace root,
     * user-defined value variables, absolute path last.
     *
     * <p>Only {@code IValueVariable} instances (user-defined stored values) are
     * enumerated dynamically. Built-in {@code IDynamicVariable}s (other than
     * {@code workspace_loc}) are intentionally skipped: many require a UI selection
     * context and would open modal dialogs when resolved outside that context.</p>
     */
    public static List<Suggestion> compute(
            String absolutePath, IProject[] projects, IStringVariableManager mgr) {
        if (absolutePath == null || absolutePath.isBlank()) return List.of();

        Path target = Paths.get(absolutePath).toAbsolutePath().normalize();
        List<Suggestion> result = new ArrayList<>();

        // 1. Per-project ${workspace_loc:/Name} — most specific (open projects only)
        List<Suggestion> projectSuggestions = new ArrayList<>();
        for (IProject project : projects) {
            if (!project.isOpen()) continue;
            org.eclipse.core.runtime.IPath loc = project.getLocation();
            if (loc == null) continue;
            Path projectPath = loc.toFile().toPath().toAbsolutePath().normalize();
            String varForm = buildVariableForm(
                "${workspace_loc:/" + project.getName() + "}", projectPath, target);
            if (varForm != null)
                projectSuggestions.add(new Suggestion(
                    varForm, target.toString(), "project " + project.getName()));
        }
        // Sort by variable form length descending: longer = more specific prefix
        projectSuggestions.sort((a, b) -> b.variableForm.length() - a.variableForm.length());
        result.addAll(projectSuggestions);

        // 2. ${workspace_loc} — workspace root
        try {
            String wsResolved = mgr.performStringSubstitution("${workspace_loc}", false);
            if (wsResolved != null && !wsResolved.isBlank()) {
                Path wsPath = Paths.get(wsResolved).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${workspace_loc}", wsPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), "workspace root"));
            }
        } catch (Exception ignored) {}

        // 3. User-defined value variables (stored values — safe to resolve without UI context)
        for (var valVar : mgr.getValueVariables()) {
            try {
                String value = valVar.getValue();
                if (value == null || value.isBlank()) continue;
                Path varPath = Paths.get(value).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${" + valVar.getName() + "}", varPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), valVar.getName()));
            } catch (Exception ignored) {}
        }

        // 4. Absolute path — always last
        result.add(new Suggestion(target.toString(), target.toString(), "absolute path"));

        return result;
    }

    /** Returns null if {@code target} does not start with {@code base}. */
    static String buildVariableForm(String variableExpr, Path base, Path target) {
        if (!target.startsWith(base)) return null;
        if (target.equals(base)) return variableExpr;
        Path relative = base.relativize(target);
        return variableExpr + "/" + relative.toString().replace('\\', '/');
    }
}
