package com.example.automation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

public class ShowAutomationViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            HandlerUtil.getActiveWorkbenchWindow(event)
                .getActivePage()
                .showView(AutomationView.ID);
        } catch (PartInitException e) {
            throw new ExecutionException("Failed to open Automation view", e);
        }
        return null;
    }
}
