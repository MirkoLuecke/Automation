package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class MultiSelectMoveTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF = "multi-move-test";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF, "Multi Move Test", "");
        wf.getSteps().add(new Step("refresh-all"));
        wf.getSteps().add(new Step("refresh-project"));
        wf.getSteps().add(new Step("shell-command"));
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
    public void moveUpBlock_shiftsContiguousSelectionUp() {
        loadWorkflow("Multi Move Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(3, table.rowCount());

        String name0 = table.cell(0, 1);
        String name1 = table.cell(1, 1);
        String name2 = table.cell(2, 1);

        // Select rows 1 and 2 as a multi-selection and notify listeners so button state updates
        table.widget.getDisplay().syncExec(() -> {
            table.widget.setSelection(new int[]{1, 2});
            table.widget.notifyListeners(SWT.Selection, new Event());
        });

        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Up").click();

        assertEquals("Row 0 must be what was row 1", name1, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 2", name2, table.cell(1, 1));
        assertEquals("Row 2 must be what was row 0", name0, table.cell(2, 1));
    }

    @Test
    public void moveDownBlock_shiftsContiguousSelectionDown() {
        loadWorkflow("Multi Move Test");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(3, table.rowCount());

        String name0 = table.cell(0, 1);
        String name1 = table.cell(1, 1);
        String name2 = table.cell(2, 1);

        // Select rows 0 and 1 as a multi-selection and notify listeners so button state updates
        table.widget.getDisplay().syncExec(() -> {
            table.widget.setSelection(new int[]{0, 1});
            table.widget.notifyListeners(SWT.Selection, new Event());
        });

        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Move Step Down").click();

        assertEquals("Row 0 must be what was row 2", name2, table.cell(0, 1));
        assertEquals("Row 1 must be what was row 0", name0, table.cell(1, 1));
        assertEquals("Row 2 must be what was row 1", name1, table.cell(2, 1));
    }
}
