package com.example.automation;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;

import com.example.automation.model.Step;

/**
 * Cell editor for file or directory path config keys. Opens a native OS dialog
 * and stores the chosen path as an Eclipse path variable reference so that paths
 * inside the workspace are portable across machines.
 *
 * <ul>
 *   <li>Under the step's project → {@code ${workspace_loc:/ProjectName}/relative/path}</li>
 *   <li>Under workspace root → {@code ${workspace_loc}/relative/path}</li>
 *   <li>Outside workspace → absolute path unchanged</li>
 * </ul>
 */
public class PathCellEditor extends DialogCellEditor {

    public enum PathType { FILE, DIRECTORY }

    private final Step step;
    private final PathType pathType;
    private Label label;

    public PathCellEditor(Composite parent, Step step, PathType pathType) {
        super(parent);
        this.step     = step;
        this.pathType = pathType;
    }

    @Override
    protected Control createContents(Composite cell) {
        label = new Label(cell, SWT.LEFT);
        return label;
    }

    @Override
    protected void updateContents(Object value) {
        label.setText(value instanceof String s ? s : "");
    }

    @Override
    protected Object openDialogBox(Control cellEditorWindow) {
        String current    = (String) getValue();
        String filterPath = resolveToFilterPath(current);

        String result;
        if (pathType == PathType.FILE) {
            FileDialog dialog = new FileDialog(cellEditorWindow.getShell(), SWT.OPEN);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            result = dialog.open();
        } else {
            DirectoryDialog dialog = new DirectoryDialog(cellEditorWindow.getShell(), SWT.NONE);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            result = dialog.open();
        }

        if (result == null) return null;
        return relativize(result, step);
    }

    private String resolveToFilterPath(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String resolved = EclipseVariables.resolve(value);
            if (pathType == PathType.FILE) {
                java.io.File f = new java.io.File(resolved);
                String parent = f.getParent();
                return parent != null ? parent : resolved;
            }
            return resolved;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Relativizes {@code absolutePath} against the step's project or the workspace root,
     * prefixing the result with the appropriate Eclipse path variable.
     * Public to allow direct testing from the test bundle.
     */
    public static String relativize(String absolutePath, Step step) {
        Path selected = Paths.get(absolutePath).toAbsolutePath().normalize();
        String projectName = step.getConfig().get("projectName");

        if (projectName != null && !projectName.isBlank()) {
            IProject project = ResourcesPlugin.getWorkspace().getRoot()
                .getProject(projectName);
            if (project.exists()) {
                IPath projectLoc = project.getLocation();
                if (projectLoc != null) {
                    Path projectPath = projectLoc.toFile().toPath().toAbsolutePath().normalize();
                    try {
                        Path rel = projectPath.relativize(selected);
                        String relStr = rel.toString().replace('\\', '/');
                        if (!relStr.startsWith("../") && !relStr.equals("..")) {
                            return relStr.isEmpty()
                                ? "${workspace_loc:/" + projectName + "}"
                                : "${workspace_loc:/" + projectName + "}/" + relStr;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        IPath wsLoc = ResourcesPlugin.getWorkspace().getRoot().getLocation();
        if (wsLoc != null) {
            Path wsPath = wsLoc.toFile().toPath().toAbsolutePath().normalize();
            try {
                Path rel = wsPath.relativize(selected);
                String relStr = rel.toString().replace('\\', '/');
                if (!relStr.startsWith("../") && !relStr.equals("..")) {
                    return relStr.isEmpty()
                        ? "${workspace_loc}"
                        : "${workspace_loc}/" + relStr;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return absolutePath;
    }
}
