package com.example.automation;

import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.example.automation.PathCellEditor.PathType;
import com.example.automation.PathVariableSuggestions.Suggestion;

public class PathPickerDialog extends TitleAreaDialog {

    private final String initialValue;
    private final PathType pathType;
    private Text textField;
    private TableViewer suggestionsViewer;
    private String result;

    public PathPickerDialog(Shell parent, String initialValue, PathType pathType) {
        super(parent);
        this.initialValue = initialValue != null ? initialValue : "";
        this.pathType = pathType;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    public void create() {
        super.create();
        setTitle("Select Path");
        setMessage("Browse for a path or type it directly. "
            + "Click a suggestion to use a variable form.");
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Select Path");
    }

    @Override
    protected boolean isResizable() { return true; }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        // Row: text field + Browse button
        Composite row = new Composite(area, SWT.NONE);
        GridLayout rowLayout = new GridLayout(2, false);
        rowLayout.marginWidth = 5;
        rowLayout.marginHeight = 5;
        row.setLayout(rowLayout);
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        textField = new Text(row, SWT.BORDER | SWT.SINGLE);
        textField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        textField.setText(initialValue);

        Button browseButton = new Button(row, SWT.PUSH);
        browseButton.setText("Browse…");
        browseButton.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                String picked = openNativeDialog();
                if (picked != null) textField.setText(picked);
            }
        });

        // Suggestions table
        suggestionsViewer = new TableViewer(area,
            SWT.FULL_SELECTION | SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData tableGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableGd.heightHint = 150;
        suggestionsViewer.getTable().setLayoutData(tableGd);
        suggestionsViewer.getTable().setHeaderVisible(true);
        suggestionsViewer.getTable().setLinesVisible(true);
        suggestionsViewer.setContentProvider(ArrayContentProvider.getInstance());

        TableViewerColumn varCol = new TableViewerColumn(suggestionsViewer, SWT.NONE);
        varCol.getColumn().setText("Variable Form");
        varCol.getColumn().setWidth(370);
        varCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((Suggestion) element).variableForm;
            }
        });

        TableViewerColumn descCol = new TableViewerColumn(suggestionsViewer, SWT.NONE);
        descCol.getColumn().setText("Description");
        descCol.getColumn().setWidth(160);
        descCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((Suggestion) element).description;
            }
        });

        suggestionsViewer.addSelectionChangedListener(e -> {
            IStructuredSelection sel = suggestionsViewer.getStructuredSelection();
            if (!sel.isEmpty())
                textField.setText(((Suggestion) sel.getFirstElement()).variableForm);
        });

        textField.addModifyListener(ev -> refreshSuggestions(textField.getText()));

        refreshSuggestions(initialValue);
        return area;
    }

    private void refreshSuggestions(String text) {
        String absolute = resolveToAbsolute(text);
        List<Suggestion> suggestions = absolute != null
            ? PathVariableSuggestions.compute(absolute)
            : List.of();
        suggestionsViewer.setInput(suggestions);
    }

    private String resolveToAbsolute(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String resolved = EclipseVariables.resolve(value);
            return Paths.get(resolved).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            try {
                java.io.File f = new java.io.File(value);
                return f.isAbsolute() ? f.getAbsolutePath() : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String openNativeDialog() {
        String filterPath = resolveToAbsolute(textField.getText());
        if (pathType == PathType.FILE) {
            if (filterPath != null) {
                java.io.File f = new java.io.File(filterPath);
                if (f.isFile()) filterPath = f.getParent();
            }
            FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            return dialog.open();
        } else {
            DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.NONE);
            if (filterPath != null) dialog.setFilterPath(filterPath);
            return dialog.open();
        }
    }

    @Override
    protected void okPressed() {
        result = textField.getText();
        super.okPressed();
    }

    /** Returns the path value entered by the user, or {@code null} if cancelled. */
    public String getResult() { return result; }
}
