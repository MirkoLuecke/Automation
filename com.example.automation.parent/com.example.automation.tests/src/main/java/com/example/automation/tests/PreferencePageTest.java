package com.example.automation.tests;

import static org.junit.Assert.*;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.*;

public class PreferencePageTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
    }

    @After
    public void closePreferencesIfOpen() {
        try {
            bot.shell("Preferences").bot().button("Cancel").click();
        } catch (Exception ignored) {}
    }

    @Test
    public void preferencePageHasBothFields() {
        openAutomationPreferences();
        SWTBotShell shell = bot.shell("Preferences");
        assertNotNull(shell.bot().textWithLabel("Default working directory:"));
        assertNotNull(shell.bot().textWithLabel("Workflow storage location:"));
    }

    @Test
    public void deployButtonHasTooltip() {
        openAutomationPreferences();
        SWTBotShell shell = bot.shell("Preferences");
        String tooltip = shell.bot().button("Deploy bundled workflows").getToolTipText();
        assertTrue("Tooltip should mention copying workflows",
            tooltip.contains("Copies the workflows"));
    }

    @Test
    public void defaultWorkingDirContainsWorkspaceVar() {
        openAutomationPreferences();
        SWTBotShell shell = bot.shell("Preferences");
        String value = shell.bot().textWithLabel("Default working directory:").getText();
        assertTrue("Default should contain ${workspace_loc}", value.contains("${workspace_loc}"));
    }

    private void openAutomationPreferences() {
        bot.menu("Window").menu("Preferences").click();
        bot.shell("Preferences").bot().tree().getTreeItem("Automation").select();
    }
}
