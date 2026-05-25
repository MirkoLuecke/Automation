package com.example.automation.tests;

import static org.junit.Assert.*;
import java.io.IOException;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.junit.*;
import com.example.automation.model.*;
import com.example.automation.persistence.WorkflowRepository;

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

    @Test
    public void comboIsVisible() {
        assertNotNull(bot.viewById("com.example.automation.view").bot().comboBox());
    }

    @Test
    public void toolbarButtonsAreVisible() {
        var viewBot = bot.viewById("com.example.automation.view").bot();
        assertNotNull(viewBot.toolbarButtonWithTooltip("New Workflow"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Add Step"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Delete Step"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Move Up"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Move Down"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Run"));
        assertNotNull(viewBot.toolbarButtonWithTooltip("Run Selected"));
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
    public void selectingWorkflowShowsSteps() throws IOException {
        WorkflowRepository repo = new WorkflowRepository();
        Workflow wf = new Workflow("swtbot-test-wf", "SWTBot Test Workflow", "test");
        wf.getSteps().add(new Step("test.action"));
        repo.save(wf);
        try {
            // Close and reopen the view so it picks up the new workflow
            bot.viewById("com.example.automation.view").close();
            bot.menu("Project").menu("Automation").click();

            // Select the test workflow from the Combo
            bot.viewById("com.example.automation.view").bot()
               .comboBox().setSelection("SWTBot Test Workflow");

            // The table must show exactly one row
            SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
            assertEquals(1, table.rowCount());
        } finally {
            repo.delete("swtbot-test-wf");
        }
    }
}
