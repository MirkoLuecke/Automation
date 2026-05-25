package com.example.automation;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

public class AutomationView extends ViewPart {

    public static final String ID = "com.example.automation.view";

    @Override
    public void createPartControl(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText("Preview");
    }

    @Override
    public void setFocus() {}
}
