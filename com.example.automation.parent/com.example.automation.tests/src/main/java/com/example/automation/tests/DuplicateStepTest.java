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

public class DuplicateStepTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_SINGLE = "dup-single";
    private static final String WF_MULTI  = "dup-multi";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow single = new Workflow(WF_SINGLE, "Dup Single", "");
        single.getSteps().add(new Step("refresh-all"));
        repo.save(single);

        Workflow multi = new Workflow(WF_MULTI, "Dup Multi", "");
        multi.getSteps().add(new Step("refresh-all"));
        multi.getSteps().add(new Step("refresh-project"));
        repo.save(multi);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_SINGLE);
        repo.delete(WF_MULTI);
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
    public void duplicateSingleStep_insertsOneCopyBelow() {
        loadWorkflow("Dup Single");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(1, table.rowCount());
        String originalName = table.cell(0, 1);

        table.click(0, 1);
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Duplicate Step").click();

        assertEquals("Table must have 2 rows after duplicate", 2, table.rowCount());
        assertEquals("Row 0 must be original", originalName, table.cell(0, 1));
        assertEquals("Row 1 must be the copy", originalName, table.cell(1, 1));
    }

    @Test
    public void duplicateContiguousSelection_insertsAllCopiesBelow() {
        loadWorkflow("Dup Multi");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(2, table.rowCount());

        String name0 = table.cell(0, 1);
        String name1 = table.cell(1, 1);

        table.click(0, 1);
        table.widget.getDisplay().syncExec(() -> {
            table.widget.setSelection(new int[]{0, 1});
            table.widget.notifyListeners(org.eclipse.swt.SWT.Selection, new org.eclipse.swt.widgets.Event());
        });
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Duplicate Step").click();

        assertEquals("Table must have 4 rows after duplicating 2 steps", 4, table.rowCount());
        assertEquals(name0, table.cell(0, 1));
        assertEquals(name1, table.cell(1, 1));
        assertEquals("Copy of row 0 must be at row 2", name0, table.cell(2, 1));
        assertEquals("Copy of row 1 must be at row 3", name1, table.cell(3, 1));
    }
}
