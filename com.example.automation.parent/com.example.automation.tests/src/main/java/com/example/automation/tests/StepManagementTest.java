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

public class StepManagementTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;

    private static final String WF_EMPTY      = "step-mgmt-empty";
    private static final String WF_TWO_STEPS  = "step-mgmt-two-steps";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        repo.save(new Workflow(WF_EMPTY, "Step Mgmt Empty", ""));

        Workflow twoStep = new Workflow(WF_TWO_STEPS, "Step Mgmt Two Steps", "");
        twoStep.getSteps().add(new Step("refresh-all"));
        twoStep.getSteps().add(new Step("refresh-project"));
        repo.save(twoStep);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_EMPTY);
        repo.delete(WF_TWO_STEPS);
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
    public void addStep_addsRowToTable() {
        loadWorkflow("Step Mgmt Empty");
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Add Step").click();
        // Select the first available action and confirm
        bot.shell("Add Step").bot().table().click(0, 0);
        bot.button("OK").click();
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals("Table must have 1 row after adding a step", 1, table.rowCount());
    }

    @Test
    public void deleteStep_withStepSelected_removesRow() {
        loadWorkflow("Step Mgmt Empty");
        // Add a step first
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Add Step").click();
        bot.shell("Add Step").bot().table().click(0, 0);
        bot.button("OK").click();

        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());

        table.click(0, 1); // select the step
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Delete Step").click();

        assertEquals("Table must be empty after deleting the only step", 0, table.rowCount());
    }

    @Test
    public void moveStepUp_reordersSteps() {
        loadWorkflow("Step Mgmt Two Steps");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(2, table.rowCount());

        String firstName  = table.cell(0, 1); // "Refresh All"
        String secondName = table.cell(1, 1); // "Refresh Project"

        table.click(1, 1); // select row 1
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Up").click();

        assertEquals("Row 0 must be what was row 1", secondName, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 0", firstName,  table.cell(1, 1));
    }

    @Test
    public void moveStepDown_reordersSteps() {
        loadWorkflow("Step Mgmt Two Steps");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(2, table.rowCount());

        String firstName  = table.cell(0, 1);
        String secondName = table.cell(1, 1);

        table.click(0, 1); // select row 0
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Down").click();

        assertEquals("Row 0 must be what was row 1", secondName, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 0", firstName,  table.cell(1, 1));
    }
}
