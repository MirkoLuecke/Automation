package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotView;
import org.eclipse.ui.PlatformUI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

/**
 * Verifies that the Properties view Config section is automatically expanded
 * with real content (not an empty dummy line) when a step is selected.
 */
public class PropertiesViewExpansionTest {

    private static SWTWorkbenchBot bot;
    private static WorkflowRepository repo;
    private static final String WF_ID = "props-expansion-test";

    @BeforeClass
    public static void setUp() throws Exception {
        bot = new SWTWorkbenchBot();
        IStringVariableManager svm = VariablesPlugin.getDefault().getStringVariableManager();
        String path = svm.performStringSubstitution(AutomationPreferences.getWorkflowStoragePath());
        repo = new WorkflowRepository(new File(path));

        // Create a workflow with a step that has config properties (projectName)
        Workflow wf = new Workflow(WF_ID, "Props Expansion Test", "");
        Step step = new Step("refresh-project");
        step.getConfig().put("projectName", "test-project");
        wf.getSteps().add(step);
        repo.save(wf);

        // Open Automation view and load the workflow
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        loadWorkflow("Props Expansion Test");

        // Ensure Properties view is open
        PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
            try {
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                    .showView("org.eclipse.ui.views.PropertySheet");
            } catch (Exception ignored) {}
        });
        bot.sleep(200);
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

    @Test
    public void configSection_expandedWithRealContent_onStepSelection() {
        SWTBotView automationView = bot.viewById("com.example.automation.view");
        SWTBotTable table = automationView.bot().table();

        // Click the step — triggers the Properties view to update
        table.click(0, 1);

        SWTBotView propsView = bot.viewById("org.eclipse.ui.views.PropertySheet");

        // Wait for the Properties view tree to populate with category items
        bot.waitUntil(new DefaultCondition() {
            @Override public boolean test() {
                return propsView.bot().tree().getAllItems().length > 0;
            }
            @Override public String getFailureMessage() {
                return "Properties view tree never populated";
            }
        }, 5000);

        // Find the Config category
        SWTBotTreeItem configItem = null;
        for (SWTBotTreeItem item : propsView.bot().tree().getAllItems()) {
            if ("Config".equals(item.getText())) {
                configItem = item;
                break;
            }
        }
        assertNotNull("'Config' category not found in Properties view", configItem);

        // Wait for it to be expanded (our asyncExec must have run)
        final SWTBotTreeItem configItemFinal = configItem;
        bot.waitUntil(new DefaultCondition() {
            @Override public boolean test() {
                return configItemFinal.isExpanded();
            }
            @Override public String getFailureMessage() {
                return "Config section was not automatically expanded";
            }
        }, 3000);

        // Assert real children are present — not just an empty dummy item.
        // A dummy placeholder used for the expand arrow shows up as an item
        // with empty text. After the fix, the SWT.Expand listener must have
        // fired so real property rows (e.g. "projectName") are present.
        SWTBotTreeItem[] children = configItemFinal.getItems();
        assertTrue("Config section has no children at all", children.length > 0);

        String firstChildText = children[0].getText().trim();
        assertFalse(
            "Config section shows empty dummy child (SWT.Expand listener never fired). "
                + "Expected 'projectName', got: '" + firstChildText + "'",
            firstChildText.isEmpty());
    }
}
