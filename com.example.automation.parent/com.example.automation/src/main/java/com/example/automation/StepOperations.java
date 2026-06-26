package com.example.automation;

import java.util.HashMap;

import org.eclipse.core.resources.ResourcesPlugin;

import com.example.automation.model.Step;

public final class StepOperations {

    private StepOperations() {}

    /**
     * Returns true when {@code sortedIndices} forms a contiguous block
     * (e.g. [2,3,4]) with no gaps. An empty array returns false.
     */
    public static boolean isContiguous(int[] sortedIndices) {
        if (sortedIndices.length == 0) return false;
        return sortedIndices[sortedIndices.length - 1] - sortedIndices[0]
               == sortedIndices.length - 1;
    }

    /** Deep-copies a step: same actionId/name/bold, independent config map. */
    public static Step deepCopy(Step src) {
        Step copy = new Step(src.getActionId());
        copy.setName(src.getName());
        copy.setBold(src.isBold());
        copy.setConfig(new HashMap<>(src.getConfig()));
        return copy;
    }

    /**
     * Returns the absolute path of the directory that contains the Eclipse
     * workspace root, or {@code null} if the workspace location is unavailable.
     */
    public static String workspaceParent() {
        org.eclipse.core.runtime.IPath loc =
            ResourcesPlugin.getWorkspace().getRoot().getLocation();
        if (loc == null) return null;
        java.io.File parent = loc.toFile().getParentFile();
        return parent != null ? parent.getAbsolutePath() : null;
    }

    /** Returns true when {@code key} represents a directory config field. */
    public static boolean isDirField(String key) {
        return key.toLowerCase(java.util.Locale.ROOT).endsWith("dir");
    }

    /** Returns true when {@code key} represents a file or path config field. */
    public static boolean isFileField(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith("file") || lower.endsWith("path");
    }
}
