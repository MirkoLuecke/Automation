package com.example.automation.tests;

import static org.junit.Assert.*;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.junit.Test;

import com.example.automation.EclipseVariables;

public class EclipseVariablesTest {

    @Test
    public void resolve_noVariables_returnsUnchanged() throws Exception {
        String path = "/absolute/path/to/file.xml";
        assertEquals(path, EclipseVariables.resolve(path));
    }

    @Test
    public void resolve_workspaceLoc_returnsAbsolutePath() throws Exception {
        String wsRoot = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        assertEquals(wsRoot, EclipseVariables.resolve("${workspace_loc}"));
    }

    @Test
    public void resolve_workspaceLocSubpath_prependsWorkspaceRoot() throws Exception {
        String wsRoot = ResourcesPlugin.getWorkspace().getRoot()
            .getLocation().toOSString();
        String resolved = EclipseVariables.resolve("${workspace_loc}/sub/path");
        assertTrue("resolved path must start with workspace root",
            resolved.replace('\\', '/').startsWith(wsRoot.replace('\\', '/')));
    }

    @Test
    public void resolve_null_returnsNull() throws Exception {
        assertNull(EclipseVariables.resolve(null));
    }

    @Test
    public void resolve_blankString_returnsUnchanged() throws Exception {
        assertEquals("", EclipseVariables.resolve(""));
    }

    @Test(expected = CoreException.class)
    public void resolve_unknownVariable_throwsCoreException() throws Exception {
        EclipseVariables.resolve("${undefined_xyz_abc_no_such_variable_123}");
    }
}
