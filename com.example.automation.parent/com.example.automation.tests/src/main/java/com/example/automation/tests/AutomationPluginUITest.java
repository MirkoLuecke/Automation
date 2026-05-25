package com.example.automation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.junit.BeforeClass;
import org.junit.Test;

public class AutomationPluginUITest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = new SWTWorkbenchBot();
        try {
            bot.viewByTitle("Welcome").close();
        } catch (WidgetNotFoundException e) {
            // Welcome view not open in this Eclipse instance, ignore
        }
    }

    @Test
    public void automationMenuItemIsPresent() {
        SWTBotMenu item = bot.menu("Project").menu("Automation");
        assertTrue("Automation menu item should be enabled", item.isEnabled());
    }

    @Test
    public void automationMenuItemOpensAutomationView() {
        bot.menu("Project").menu("Automation").click();
        SWTBotView view = bot.viewByTitle("Automation");
        assertNotNull("Automation view should be open", view);
    }

}
