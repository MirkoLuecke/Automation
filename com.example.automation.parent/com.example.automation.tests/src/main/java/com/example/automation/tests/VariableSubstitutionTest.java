package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.*;
import com.example.automation.preferences.AutomationPreferences;

public class VariableSubstitutionTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
    }

    @Test
    public void workspaceLocVariableResolvesToExistingPath() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String resolved = svm.performStringSubstitution("${workspace_loc}");
        assertNotNull(resolved);
        assertFalse("Variable should resolve to a real path, not the literal expression",
            resolved.contains("${"));
        assertTrue("Resolved workspace path should exist on disk", new File(resolved).exists());
    }

    @Test
    public void defaultWorkingDirResolvesToExistingPath() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String raw = AutomationPreferences.getDefaultWorkingDir();
        String resolved = svm.performStringSubstitution(raw);
        assertFalse("Resolved working dir should not contain ${", resolved.contains("${"));
        File resolvedFile = new File(resolved).getCanonicalFile();
        assertTrue("Default working dir should exist on disk: " + resolvedFile, resolvedFile.exists());
    }

    @Test
    public void workflowStoragePathResolvesToString() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String raw = AutomationPreferences.getWorkflowStoragePath();
        String resolved = svm.performStringSubstitution(raw);
        assertFalse("Resolved storage path should not contain ${", resolved.contains("${"));
        assertFalse("Resolved storage path should not be blank", resolved.isBlank());
    }
}
