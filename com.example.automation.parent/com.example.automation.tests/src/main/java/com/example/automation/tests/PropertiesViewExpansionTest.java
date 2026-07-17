package com.example.automation.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.example.automation.model.Step;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;
import com.example.automation.preferences.AutomationPreferences;

/**
 * Verifies that the AutoExpandPropertySheetPage (returned from AutomationView's
 * getAdapter hook) auto-expands the Config category and populates it with real
 * property rows when selectionChanged() is called with a Step selection.
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

        // Open Automation view and load the workflow so StepAdapterFactory is registered
        try {
            bot.viewById("com.example.automation.view").show();
        } catch (Exception e) {
            bot.menu("Project").menu("Automation").click();
        }
        loadWorkflow("Props Expansion Test");
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
        Display display = PlatformUI.getWorkbench().getDisplay();
        String[] failure = {null};

        display.syncExec(() -> {
            Shell testShell = null;
            try {
                IWorkbenchPage wbPage = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();

                // Get the AutoExpandPropertySheetPage from the Automation view adapter.
                // AutomationView.createPartControl() registered StepAdapterFactory, so
                // IPropertySource adapters for Step will be found by the property sheet.
                IViewPart automationPart = wbPage.findView("com.example.automation.view");
                if (automationPart == null) { failure[0] = "Automation view not found"; return; }

                IPropertySheetPage propPage = (IPropertySheetPage)
                    automationPart.getAdapter(IPropertySheetPage.class);
                if (propPage == null) { failure[0] = "No IPropertySheetPage adapter"; return; }

                // Host the page in a standalone shell — this bypasses the workbench selection
                // mechanism that is unreliable in SWTBot tests. We test the page directly.
                testShell = new Shell(display, SWT.SHELL_TRIM);
                testShell.setLayout(new FillLayout());
                propPage.createControl(testShell);
                testShell.setSize(400, 300);
                testShell.open();

                // Verify the page created a Tree control
                if (propPage.getControl() == null) { failure[0] = "Page control is null after createControl()"; return; }
                if (!(propPage.getControl() instanceof Tree)) {
                    failure[0] = "Page control is not a Tree: " + propPage.getControl().getClass(); return;
                }
                Tree tree = (Tree) propPage.getControl();

                // Simulate: user selected the step in the Automation view.
                // AutoExpandPropertySheetPage.selectionChanged() calls super (populates tree)
                // and schedules an asyncExec to fire SWT.Expand on each category.
                Step step = new Step("refresh-project");
                step.getConfig().put("projectName", "test-project");
                propPage.selectionChanged(automationPart, new StructuredSelection(step));

                // Flush all pending asyncExec calls so the SWT.Expand / setExpanded logic runs.
                while (display.readAndDispatch()) {}

                // ── Assertion 1: tree has category items ──────────────────────────────────
                if (tree.getItemCount() == 0) {
                    failure[0] = "Tree has no items after selectionChanged() — IPropertySource adapter may not be registered"; return;
                }

                // ── Assertion 2: Config category exists ───────────────────────────────────
                TreeItem configItem = null;
                for (TreeItem item : tree.getItems()) {
                    if ("Config".equals(item.getText())) { configItem = item; break; }
                }
                if (configItem == null) {
                    failure[0] = "'Config' category not found. Items: "
                        + Arrays.stream(tree.getItems()).map(TreeItem::getText).collect(Collectors.joining(", "));
                    return;
                }

                // ── Assertion 3: Config is expanded ───────────────────────────────────────
                // asyncExec fired SWT.Expand and called setExpanded(true)
                if (!configItem.getExpanded()) {
                    failure[0] = "Config category was not automatically expanded (asyncExec did not run)"; return;
                }

                // ── Assertion 4: real property rows, not the SWT dummy placeholder ────────
                TreeItem[] children = configItem.getItems();
                if (children.length == 0) { failure[0] = "Config has no children at all"; return; }
                String firstText = children[0].getText().trim();
                if (firstText.isEmpty()) {
                    failure[0] = "Config shows empty dummy child — SWT.Expand listener never fired to replace it"; return;
                }

            } catch (Exception e) {
                failure[0] = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (testShell != null && !testShell.isDisposed()) testShell.dispose();
            }
        });

        assertNull(failure[0], failure[0]);
    }
}
