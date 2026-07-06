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
     * other registered Eclipse variables (alphabetical), absolute path last.
     *
     * <p>If the target path lies inside a closed project (and no open project
     * covers it), an empty list is returned — the path is inaccessible.</p>
     */
    public static List<Suggestion> compute(
            String absolutePath, IProject[] projects, IStringVariableManager mgr) {
        if (absolutePath == null || absolutePath.isBlank()) return List.of();

        Path target = Paths.get(absolutePath).toAbsolutePath().normalize();

        // Determine whether the target is inside any project (open or closed)
        // and whether it is inside any OPEN project.
        boolean insideClosedProjectOnly = isInsideClosedProjectOnly(target, projects, mgr);
        if (insideClosedProjectOnly) return List.of();

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

        // 2. ${workspace_loc} — workspace root (only when target is not inside any project)
        boolean insideAnyProject = isInsideAnyProject(target, projects, mgr);
        if (!insideAnyProject) {
            try {
                String wsResolved = mgr.performStringSubstitution("${workspace_loc}", false);
                if (wsResolved != null && !wsResolved.isBlank()) {
                    Path wsPath = Paths.get(wsResolved).toAbsolutePath().normalize();
                    String varForm = buildVariableForm("${workspace_loc}", wsPath, target);
                    if (varForm != null)
                        result.add(new Suggestion(varForm, target.toString(), "workspace root"));
                }
            } catch (Exception ignored) {}
        }

        // 3. All other registered dynamic variables (no argument, skipping workspace_loc)
        for (var dynVar : mgr.getDynamicVariables()) {
            String name = dynVar.getName();
            if ("workspace_loc".equals(name)) continue;
            try {
                String resolved = mgr.performStringSubstitution("${" + name + "}", false);
                if (resolved == null || resolved.isBlank()) continue;
                Path varPath = Paths.get(resolved).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${" + name + "}", varPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), name));
            } catch (Exception ignored) {}
        }

        // 4. User-defined value variables
        for (var valVar : mgr.getValueVariables()) {
            try {
                String resolved = mgr.performStringSubstitution(
                    "${" + valVar.getName() + "}", false);
                if (resolved == null || resolved.isBlank()) continue;
                Path varPath = Paths.get(resolved).toAbsolutePath().normalize();
                String varForm = buildVariableForm("${" + valVar.getName() + "}", varPath, target);
                if (varForm != null)
                    result.add(new Suggestion(varForm, target.toString(), valVar.getName()));
            } catch (Exception ignored) {}
        }

        // 5. Absolute path — always last
        result.add(new Suggestion(target.toString(), target.toString(), "absolute path"));

        return result;
    }

    /**
     * Returns true if the target path is inside at least one project from the array,
     * but none of those covering projects is open.
     */
    private static boolean isInsideClosedProjectOnly(
            Path target, IProject[] projects, IStringVariableManager mgr) {
        boolean insideOpen = false;
        boolean insideClosed = false;

        // Check via workspace-root + project-name convention
        try {
            String wsResolved = mgr.performStringSubstitution("${workspace_loc}", false);
            if (wsResolved != null && !wsResolved.isBlank()) {
                Path wsPath = Paths.get(wsResolved).toAbsolutePath().normalize();
                if (target.startsWith(wsPath)) {
                    Path rel = wsPath.relativize(target);
                    if (rel.getNameCount() > 0) {
                        String firstSegment = rel.getName(0).toString();
                        for (IProject project : projects) {
                            if (project.getName().equalsIgnoreCase(firstSegment)) {
                                if (project.isOpen()) insideOpen = true;
                                else insideClosed = true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Also check via IProject.getLocation() for linked/external projects
        for (IProject project : projects) {
            org.eclipse.core.runtime.IPath loc = project.getLocation();
            if (loc == null) continue;
            Path projectPath = loc.toFile().toPath().toAbsolutePath().normalize();
            if (target.startsWith(projectPath)) {
                if (project.isOpen()) insideOpen = true;
                else insideClosed = true;
            }
        }

        return insideClosed && !insideOpen;
    }

    /**
     * Returns true if the target path is inside at least one project (open or closed).
     */
    private static boolean isInsideAnyProject(
            Path target, IProject[] projects, IStringVariableManager mgr) {
        // Check via workspace-root + project-name convention
        try {
            String wsResolved = mgr.performStringSubstitution("${workspace_loc}", false);
            if (wsResolved != null && !wsResolved.isBlank()) {
                Path wsPath = Paths.get(wsResolved).toAbsolutePath().normalize();
                if (target.startsWith(wsPath)) {
                    Path rel = wsPath.relativize(target);
                    if (rel.getNameCount() > 0) {
                        String firstSegment = rel.getName(0).toString();
                        for (IProject project : projects) {
                            if (project.getName().equalsIgnoreCase(firstSegment)) return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Also check via IProject.getLocation()
        for (IProject project : projects) {
            org.eclipse.core.runtime.IPath loc = project.getLocation();
            if (loc == null) continue;
            Path projectPath = loc.toFile().toPath().toAbsolutePath().normalize();
            if (target.startsWith(projectPath)) return true;
        }
        return false;
    }

    /** Returns null if {@code target} does not start with {@code base}. */
    static String buildVariableForm(String variableExpr, Path base, Path target) {
        if (!target.startsWith(base)) return null;
        if (target.equals(base)) return variableExpr;
        Path relative = base.relativize(target);
        return variableExpr + "/" + relative.toString().replace('\\', '/');
    }
}
