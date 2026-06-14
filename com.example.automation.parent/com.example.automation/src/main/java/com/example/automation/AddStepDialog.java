package com.example.automation;

import java.util.HashMap;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.model.Step;

/**
 * Modal dialog for selecting an action type when adding a new step to a workflow.
 * Displays all registered actions in a two-column table (name + description);
 * the OK button is enabled only when a row is selected.
 */
public class AddStepDialog extends TitleAreaDialog {

    private final ActionRegistry registry;
    private TableViewer viewer;
    private Step result;
    private TableColumn nameColumn;
    private TableColumn descColumn;

    public AddStepDialog(Shell parent, ActionRegistry registry) {
        super(parent);
        this.registry = registry;
    }

    @Override
    public void create() {
        super.create();
        setTitle("Add Step");
        setMessage("Select an action type for the new step.");
        getButton(OK).setEnabled(false);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Add Step");
    }

    @Override
    protected boolean isResizable() { return true; }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        viewer = new TableViewer(area, SWT.FULL_SELECTION | SWT.BORDER | SWT.SINGLE);
        viewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        TableViewerColumn nameCol = new TableViewerColumn(viewer, SWT.NONE);
        nameColumn = nameCol.getColumn();
        nameColumn.setText("Name");
        nameColumn.setWidth(180);
        nameCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return ((IAction) element).getName();
            }
        });

        TableViewerColumn descCol = new TableViewerColumn(viewer, SWT.NONE);
        descColumn = descCol.getColumn();
        descColumn.setText("Description");
        descColumn.setWidth(350);
        descCol.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                String desc = ((IAction) element).getDescription();
                return desc == null ? "" : desc;
            }
        });

        // Single persistent comparator — reads sort state from the table widget each call.
        // SelectionListeners only update the widget's sort state then call refresh().
        viewer.setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer v, Object o1, Object o2) {
                IAction a1 = (IAction) o1;
                IAction a2 = (IAction) o2;
                boolean byName = viewer.getTable().getSortColumn() != descColumn;
                String s1 = byName ? nvl(a1.getName()) : nvl(a1.getDescription());
                String s2 = byName ? nvl(a2.getName()) : nvl(a2.getDescription());
                int cmp = s1.compareToIgnoreCase(s2);
                return viewer.getTable().getSortDirection() == SWT.DOWN ? -cmp : cmp;
            }
        });

        nameColumn.addSelectionListener(SelectionListener.widgetSelectedAdapter(
            e -> { updateSort(nameColumn); viewer.refresh(); }));
        descColumn.addSelectionListener(SelectionListener.widgetSelectedAdapter(
            e -> { updateSort(descColumn); viewer.refresh(); }));

        viewer.setInput(registry.getAllActions());

        viewer.getTable().setSortColumn(nameColumn);
        viewer.getTable().setSortDirection(SWT.UP);
        viewer.refresh();

        viewer.addSelectionChangedListener(e ->
            getButton(OK).setEnabled(!viewer.getStructuredSelection().isEmpty()));

        viewer.addDoubleClickListener(e -> {
            if (!viewer.getStructuredSelection().isEmpty()) okPressed();
        });

        return area;
    }

    private void updateSort(TableColumn col) {
        org.eclipse.swt.widgets.Table t = viewer.getTable();
        if (t.getSortColumn() == col) {
            t.setSortDirection(t.getSortDirection() == SWT.UP ? SWT.DOWN : SWT.UP);
        } else {
            t.setSortColumn(col);
            t.setSortDirection(SWT.UP);
        }
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    @Override
    protected void okPressed() {
        IAction action = (IAction) ((IStructuredSelection) viewer.getSelection()).getFirstElement();
        result = createStep(action);
        super.okPressed();
    }

    /**
     * Returns the new step created from the selected action, or {@code null} if cancelled.
     *
     * @return the created step, pre-populated with the action's default configuration
     */
    public Step getResult() { return result; }

    /**
     * Creates a new {@link Step} for the given action, pre-populated with default configuration.
     *
     * @param action the action to create a step for; must not be null
     * @return a new step with {@code actionId} set and config initialised to defaults
     */
    public static Step createStep(IAction action) {
        Step step = new Step(action.getId());
        step.setConfig(new HashMap<>(action.getDefaultConfig()));
        return step;
    }
}
