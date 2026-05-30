package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.*;
import com.example.automation.model.*;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class MultiLineCommandTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
    }

    @Test
    public void multiLineCommandRoundTripsWithNewlines() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String storagePath = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        WorkflowRepository repo = new WorkflowRepository(new File(storagePath));

        Workflow wf = new Workflow("multi-line-test", "Multi Line Test", "");
        Step step = new Step("shell-command");
        step.getConfig().put("command", "echo line1\necho line2");
        step.getConfig().put("workingDir", "");
        wf.getSteps().add(step);
        repo.save(wf);

        try {
            Workflow loaded = repo.load("multi-line-test");
            String command = loaded.getSteps().get(0).getConfig().get("command");
            assertTrue("Saved command should contain a newline character", command.contains("\n"));
            assertEquals("echo line1\necho line2", command);
        } finally {
            repo.delete("multi-line-test");
        }
    }
}
