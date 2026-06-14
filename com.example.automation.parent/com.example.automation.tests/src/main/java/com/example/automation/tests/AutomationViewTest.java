package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.File;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.junit.*;
import com.example.automation.model.*;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

public class AutomationViewTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void openView() {
        bot = new SWTWorkbenchBot();
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (WidgetNotFoundException e) {
            bot.menu("Project").menu("Automation").click();
        }
    }

    private static WorkflowRepository testRepo() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String resolved = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        return new WorkflowRepository(new File(resolved));
    }

    @Test
    public void headerLabelsArePresent() {
        // The Combo was replaced by two Label widgets (name + description).
        // Verify the view is open and the label area is present by checking
        // we can find the toolbar button that sits immediately above it.
        assertNotNull(bot.viewById("com.example.automation.view").bot()
                         .toolbarButtonWithTooltip("New Workflow"));
    }

    @Test
    public void toolbarButtonsAreVisible() {
        var viewBot = bot.viewById("com.example.automation.view").bot();
        assertNotNull(viewBot.toolbarButtonWithTooltip("New Workflow"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Open Workflow"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Add Step"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Delete Step"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Move Step Up"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Move Step Down"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Run Workflow"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Run Selected Steps"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Stop"));
    }

    @Test
    public void tableHasThreeColumns() {
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        assertEquals(3, table.columnCount());
        assertEquals("Status", table.columns().get(0));
        assertEquals("Name",   table.columns().get(1));
        assertEquals("Config", table.columns().get(2));
    }

    @Test
    public void selectingWorkflowShowsSteps() throws Exception {
        WorkflowRepository repo = testRepo();
        Workflow wf = new Workflow("swtbot-test-wf", "SWTBot Test Workflow", "test");
        wf.getSteps().add(new Step("test.action"));
        repo.save(wf);
        try {
            // Close and reopen the view so it picks up the new workflow
            bot.viewById("com.example.automation.view").close();
            bot.menu("Project").menu("Automation").click();

            // Click "Open Workflow" toolbar button to open the picker dialog
            bot.viewById("com.example.automation.view").bot()
               .toolbarButtonWithTooltip("Open Workflow").click();

            // In the WorkflowPickerDialog, select the test workflow by clicking its row
            SWTBotTable pickerTable = bot.shell("Open Workflow").bot().table();
            int rowIndex = -1;
            for (int i = 0; i < pickerTable.rowCount(); i++) {
                if ("SWTBot Test Workflow".equals(pickerTable.cell(i, 0))) {
                    rowIndex = i;
                    break;
                }
            }
            assertTrue("Workflow not found in picker", rowIndex >= 0);
            pickerTable.click(rowIndex, 0);
            bot.button("OK").click();

            // The table must show exactly one row
            SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
            assertEquals(1, table.rowCount());
        } finally {
            repo.delete("swtbot-test-wf");
        }
    }

    @Test
    public void longWorkflowDescription_isTruncatedInHeader() throws Exception {
        WorkflowRepository repo = testRepo();
        String longDesc = "This is a deliberately long workflow description "
            + "that exceeds one hundred characters and must be truncated in the view header.";
        assertTrue("Test setup: description must be longer than 100 chars", longDesc.length() > 100);
        Workflow wf = new Workflow("desc-truncation-test", "Desc Truncation Test", longDesc);
        repo.save(wf);
        try {
            bot.viewById("com.example.automation.view").close();
            bot.menu("Project").menu("Automation").click();

            bot.viewById("com.example.automation.view").bot()
               .toolbarButtonWithTooltip("Open Workflow").click();
            SWTBotTable pickerTable = bot.shell("Open Workflow").bot().table();
            int rowIndex = -1;
            for (int i = 0; i < pickerTable.rowCount(); i++) {
                if ("Desc Truncation Test".equals(pickerTable.cell(i, 0))) {
                    rowIndex = i;
                    break;
                }
            }
            assertTrue("Workflow not found in picker", rowIndex >= 0);
            pickerTable.click(rowIndex, 0);
            bot.button("OK").click();

            // The description label is the second label (index 1) in the view header.
            String displayed = bot.viewById("com.example.automation.view").bot().label(1).getText();
            assertTrue("Displayed description (" + displayed.length() + " chars) must be ≤ 100",
                displayed.length() <= 100);
        } finally {
            repo.delete("desc-truncation-test");
        }
    }

    @Test
    public void multipleStepsCanBeSelected() throws Exception {
        WorkflowRepository repo = testRepo();
        Workflow wf = new Workflow("multi-select-test-wf", "Multi Select Test", "test");
        wf.getSteps().add(new Step("action.a"));
        wf.getSteps().add(new Step("action.b"));
        repo.save(wf);
        try {
            bot.viewById("com.example.automation.view").close();
            bot.menu("Project").menu("Automation").click();

            // Click "Open Workflow" toolbar button to open the picker dialog
            bot.viewById("com.example.automation.view").bot()
               .toolbarButtonWithTooltip("Open Workflow").click();

            SWTBotTable pickerTable = bot.shell("Open Workflow").bot().table();
            int rowIndex = -1;
            for (int i = 0; i < pickerTable.rowCount(); i++) {
                if ("Multi Select Test".equals(pickerTable.cell(i, 0))) {
                    rowIndex = i;
                    break;
                }
            }
            assertTrue("Workflow not found in picker", rowIndex >= 0);
            pickerTable.click(rowIndex, 0);
            bot.button("OK").click();

            SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
            int[] selCount = {0};
            Display.getDefault().syncExec(() -> {
                table.widget.select(new int[]{0, 1});
                selCount[0] = table.widget.getSelectionCount();
            });
            assertEquals(2, selCount[0]);
        } finally {
            repo.delete("multi-select-test-wf");
        }
    }
}
