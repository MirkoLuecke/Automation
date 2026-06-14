package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class NewWorkflowDialogUITest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (WidgetNotFoundException e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @After
    public void closeDialogIfOpen() {
        try { bot.shell("New Workflow").bot().button("Cancel").click(); } catch (Exception ignored) {}
    }

    private void openFreshView() {
        try { bot.viewById("com.example.automation.view").close(); } catch (Exception ignored) {}
        bot.menu("Project").menu("Automation").click();
    }

    @Test
    public void newWorkflowButton_opensDialogWithTitle() {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        assertNotNull("Shell 'New Workflow' must open", bot.shell("New Workflow"));
    }

    @Test
    public void dialog_okDisabledWhenNameBlank() {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        assertFalse("OK must be disabled when name is empty",
            bot.shell("New Workflow").bot().button("OK").isEnabled());
    }

    @Test
    public void dialog_createWorkflow_appearsInPicker() throws Exception {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        bot.shell("New Workflow").bot().textWithLabel("Name:").setText("UI Test Workflow");
        bot.shell("New Workflow").bot().button("OK").click();

        // The derived ID for "UI Test Workflow" is "ui-test-workflow"
        try {
            bot.viewById("com.example.automation.view").bot()
               .toolbarButtonWithTooltip("Open Workflow").click();
            SWTBotTable picker = bot.shell("Open Workflow").bot().table();
            boolean found = false;
            for (int i = 0; i < picker.rowCount(); i++) {
                if ("UI Test Workflow".equals(picker.cell(i, 0))) { found = true; break; }
            }
            bot.button("Cancel").click();
            assertTrue("Newly created workflow must appear in picker", found);
        } finally {
            repo.delete("ui-test-workflow");
        }
    }

    @Test
    public void dialog_cancel_doesNotCreateWorkflow() {
        openFreshView();
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("New Workflow").click();
        bot.shell("New Workflow").bot().textWithLabel("Name:").setText("Cancel Test Workflow");
        bot.shell("New Workflow").bot().button("Cancel").click();

        bot.viewById("com.example.automation.view").bot()
           .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        boolean found = false;
        for (int i = 0; i < picker.rowCount(); i++) {
            if ("Cancel Test Workflow".equals(picker.cell(i, 0))) { found = true; break; }
        }
        bot.button("Cancel").click();
        assertFalse("Cancelled workflow must NOT appear in picker", found);
    }
}
