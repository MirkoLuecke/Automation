package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

/**
 * SWTBot tests for column-header sorting in the Add Step dialog.
 *
 * Each test opens a fresh Add Step dialog (guaranteeing initial ascending-by-name
 * state), exercises a column header, verifies row order, then cancels the dialog.
 */
public class AddStepDialogSortingTest {

    private static SWTWorkbenchBot bot;
    private static final String WF_ID = "add-step-sort-test-wf";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();

        // Create a workflow so the Add Step toolbar button is enabled.
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        WorkflowRepository repo = new WorkflowRepository(new File(path));
        repo.save(new Workflow(WF_ID, "Add Step Sort Test", ""));

        // Open Automation view and load the test workflow.
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Open Workflow").click();
        SWTBotTable picker = bot.shell("Open Workflow").bot().table();
        for (int i = 0; i < picker.rowCount(); i++) {
            if ("Add Step Sort Test".equals(picker.cell(i, 0))) {
                picker.click(i, 0);
                break;
            }
        }
        bot.button("OK").click();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        new WorkflowRepository(new File(path)).delete(WF_ID);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SWTBotTable openDialog() {
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Add Step").click();
        return bot.shell("Add Step").bot().table();
    }

    /** Fires a SWT.Selection event on column {@code idx} of the given table. */
    private static void clickHeader(SWTBotTable table, int colIdx) {
        Display.getDefault().syncExec(() -> {
            TableColumn col = table.widget.getColumn(colIdx);
            Event event = new Event();
            event.widget = col;
            col.notifyListeners(SWT.Selection, event);
        });
    }

    private static boolean isAscending(SWTBotTable table, int col) {
        for (int i = 0; i < table.rowCount() - 1; i++) {
            if (table.cell(i, col).compareToIgnoreCase(table.cell(i + 1, col)) > 0)
                return false;
        }
        return true;
    }

    private static boolean isDescending(SWTBotTable table, int col) {
        for (int i = 0; i < table.rowCount() - 1; i++) {
            if (table.cell(i, col).compareToIgnoreCase(table.cell(i + 1, col)) < 0)
                return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void dialogOpens_tableHasTwoColumns() {
        SWTBotTable table = openDialog();
        try {
            assertEquals(2, table.columnCount());
            assertEquals("Name",        table.columns().get(0));
            assertEquals("Description", table.columns().get(1));
        } finally {
            bot.button("Cancel").click();
        }
    }

    @Test
    public void initialState_nameColumnSortedAscending() {
        SWTBotTable table = openDialog();
        try {
            assertTrue("Name column should be sorted ascending on open",
                isAscending(table, 0));
        } finally {
            bot.button("Cancel").click();
        }
    }

    @Test
    public void clickNameHeader_sortsDescending() {
        SWTBotTable table = openDialog();
        try {
            // Dialog opens ascending; first click toggles to descending.
            clickHeader(table, 0);
            assertTrue("Name column should be descending after one click",
                isDescending(table, 0));
        } finally {
            bot.button("Cancel").click();
        }
    }

    @Test
    public void clickNameHeaderTwice_sortsAscendingAgain() {
        SWTBotTable table = openDialog();
        try {
            clickHeader(table, 0);  // → descending
            clickHeader(table, 0);  // → ascending again
            assertTrue("Name column should be ascending after two clicks",
                isAscending(table, 0));
        } finally {
            bot.button("Cancel").click();
        }
    }

    @Test
    public void clickDescriptionHeader_sortsByDescriptionAscending() {
        SWTBotTable table = openDialog();
        try {
            clickHeader(table, 1);
            assertTrue("Description column should be sorted ascending after header click",
                isAscending(table, 1));
        } finally {
            bot.button("Cancel").click();
        }
    }
}
