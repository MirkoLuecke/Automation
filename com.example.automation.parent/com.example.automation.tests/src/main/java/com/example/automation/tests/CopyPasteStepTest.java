package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.ui.PlatformUI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class CopyPasteStepTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_ID = "copy-paste-test";
    private static final Gson GSON = new GsonBuilder().create();

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        Workflow wf = new Workflow(WF_ID, "Copy Paste Test", "");
        wf.getSteps().add(new Step("refresh-all"));
        wf.getSteps().add(new Step("refresh-project"));
        repo.save(wf);

        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        loadWorkflow("Copy Paste Test");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        repo.delete(WF_ID);
    }

    private static void loadWorkflow(String displayName) {
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

    private static void setClipboard(String text) {
        PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
            Clipboard cb = new Clipboard(PlatformUI.getWorkbench().getDisplay());
            cb.setContents(new Object[]{text}, new Transfer[]{TextTransfer.getInstance()});
            cb.dispose();
        });
    }

    @Test
    public void copyButton_enabledWhenStepSelected() {
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        table.click(0, 1);
        assertTrue(bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Copy Step(s)").isEnabled());
    }

    @Test
    public void pasteButton_disabledWhenClipboardHasInvalidJson() {
        setClipboard("not json");
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        table.click(0, 1);
        assertFalse(bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Paste Step(s)").isEnabled());
    }

    @Test
    public void copyThenPaste_insertsStepAfterSelection() {
        SWTBotTable table = bot.viewById("com.example.automation.view").bot().table();
        int originalCount = table.rowCount();
        String originalName = table.cell(0, 1);

        // Copy row 0
        table.click(0, 1);
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Copy Step(s)").click();

        // Paste (row 0 still selected) → should insert at index 1
        bot.viewById("com.example.automation.view").bot()
            .toolbarButtonWithTooltip("Paste Step(s)").click();

        assertEquals("Row count must increase by 1", originalCount + 1, table.rowCount());
        assertEquals("Row 0 unchanged",  originalName, table.cell(0, 1));
        assertEquals("Row 1 is the copy", originalName, table.cell(1, 1));
    }
}
