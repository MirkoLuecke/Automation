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

public class StepNameEditTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_NAMED   = "step-name-named";
    private static final String WF_DEFAULT = "step-name-default";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow named = new Workflow(WF_NAMED, "Step Name Named", "");
        Step s1 = new Step("refresh-all");
        s1.setName("My Custom Name");
        named.getSteps().add(s1);
        repo.save(named);

        Workflow def = new Workflow(WF_DEFAULT, "Step Name Default", "");
        def.getSteps().add(new Step("refresh-all"));
        repo.save(def);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_NAMED);
        repo.delete(WF_DEFAULT);
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
    public void customName_showsInTableNameColumn() {
        loadWorkflow("Step Name Named");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        assertEquals("My Custom Name", table.cell(0, 1));
    }

    @Test
    public void noCustomName_showsActionDisplayName() {
        loadWorkflow("Step Name Default");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        assertEquals("Refresh All", table.cell(0, 1));
    }
}
