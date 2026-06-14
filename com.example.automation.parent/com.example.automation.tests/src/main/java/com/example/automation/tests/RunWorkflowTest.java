package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class RunWorkflowTest {

    private static SWTWorkbenchBot bot;
    private static final String WF_ID = "run-workflow-test-wf";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        WorkflowRepository repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF_ID, "Run Workflow Test", "");
        wf.getSteps().add(new com.example.automation.model.Step("refresh-all"));
        repo.save(wf);

        // Open the view and load the workflow
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        for (int i = 0; i < picker.rowCount(); i++) {
            if ("Run Workflow Test".equals(picker.cell(i, 0))) { picker.click(i, 0); break; }
        }
        bot.button("OK").click();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        new WorkflowRepository(new File(path)).delete(WF_ID);
    }

    @Test
    public void runWorkflow_withRefreshAllStep_completesWithGreenStatus() throws Exception {
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Run Workflow").click();

        // Poll up to 10 s for the step to reach GREEN status
        StepStatus[] status = {null};
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Display.getDefault().syncExec(() -> {
                org.eclipse.swt.widgets.Table t =
                    bot.viewById("com.example.automation.view").bot().table().widget;
                if (t.getItemCount() > 0) {
                    Object data = t.getItem(0).getData();
                    if (data instanceof Step) status[0] = ((Step) data).getStatus();
                }
            });
            if (StepStatus.GREEN == status[0]) break;
            Thread.sleep(200);
        }

        assertEquals("Step must reach GREEN status within 10 s", StepStatus.GREEN, status[0]);
    }
}
