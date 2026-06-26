package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class BoldStepTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF = "bold-step-test";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF, "Bold Step Test", "");
        Step boldStep = new Step("refresh-all");
        boldStep.setBold(true);
        wf.getSteps().add(boldStep);
        repo.save(wf);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF);
    }

    private void loadWorkflow(String displayName) {
        try { bot.viewById("com.example.automation.view").close(); } catch (Exception ignored) {}
        bot.menu("Project").menu("Automation").click();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        for (int i = 0; i < picker.rowCount(); i++) {
            if (displayName.equals(picker.cell(i, 0))) { picker.click(i, 0); break; }
        }
        bot.button("OK").click();
    }

    @Test
    public void boldStep_persistedAndRendered_doesNotCrash() {
        loadWorkflow("Bold Step Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        assertFalse("step name must not be empty", table.cell(0, 1).isBlank());
    }

    @Test
    public void boldFalseStep_renderedNormally_doesNotCrash() {
        Workflow wf = new Workflow("bold-false-test", "Bold False Test", "");
        Step normal = new Step("refresh-all");
        wf.getSteps().add(normal);
        try {
            repo.save(wf);
            loadWorkflow("Bold False Test");
            SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
            assertEquals(1, table.rowCount());
            assertFalse(table.cell(0, 1).isBlank());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try { repo.delete("bold-false-test"); } catch (Exception ignored) {}
        }
    }
}
