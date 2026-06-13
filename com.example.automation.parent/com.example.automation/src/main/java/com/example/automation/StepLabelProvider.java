package com.example.automation;

import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.OwnerDrawLabelProvider;
import org.eclipse.jface.viewers.ViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Event;

import com.example.automation.api.ActionRegistry;
import com.example.automation.api.IAction;
import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;

/**
 * Container for the three {@link org.eclipse.jface.viewers.ColumnLabelProvider}
 * subclasses used in the Automation view table: {@link Status} (coloured square with
 * progress), {@link Name} (action name or step label), and {@link Config}
 * (truncated key=value summary).
 */
public class StepLabelProvider {

    private StepLabelProvider() {}

    /**
     * Owner-draw label provider for the Status column. Paints a coloured square
     * ({@link com.example.automation.model.StepStatus#WHITE} grey,
     * {@link com.example.automation.model.StepStatus#GREEN} green,
     * {@link com.example.automation.model.StepStatus#YELLOW} amber with progress %,
     * {@link com.example.automation.model.StepStatus#RED} red).
     */
    public static class Status extends OwnerDrawLabelProvider {

        private static final int SQUARE = 12;
        private Color colorWhite, colorGreen, colorYellow, colorRed;

        @Override
        protected void initialize(ColumnViewer viewer, ViewerColumn column) {
            super.initialize(viewer, column);
            var d = viewer.getControl().getDisplay();
            colorWhite  = new Color(d, 0xC0, 0xC0, 0xC0);
            colorGreen  = new Color(d, 0x00, 0xAA, 0x00);
            colorYellow = new Color(d, 0xFF, 0xB3, 0x00);
            colorRed    = new Color(d, 0xCC, 0x00, 0x00);
        }

        @Override
        public void dispose() {
            if (colorWhite  != null) { colorWhite.dispose();  colorWhite  = null; }
            if (colorGreen  != null) { colorGreen.dispose();  colorGreen  = null; }
            if (colorYellow != null) { colorYellow.dispose(); colorYellow = null; }
            if (colorRed    != null) { colorRed.dispose();    colorRed    = null; }
            super.dispose();
        }

        @Override
        protected void measure(Event event, Object element) {
            event.height = Math.max(event.height, SQUARE + 4);
        }

        @Override
        protected void erase(Event event, Object element) {
            event.detail &= ~SWT.FOREGROUND;
        }

        @Override
        protected void paint(Event event, Object element) {
            Step step = (Step) element;
            GC gc = event.gc;
            Color prev = gc.getBackground();
            gc.setBackground(colorFor(step.getStatus()));
            if (step.getStatus() == StepStatus.YELLOW) {
                int x = event.x + 4;
                int y = event.y + (event.height - SQUARE) / 2;
                gc.fillRectangle(x, y, SQUARE, SQUARE);
                gc.setBackground(prev);
                String text = step.getProgress() + "%";
                gc.drawText(text, x + SQUARE + 4, y - 1, true);
            } else {
                int x = event.x + (event.width  - SQUARE) / 2;
                int y = event.y + (event.height - SQUARE) / 2;
                gc.fillRectangle(x, y, SQUARE, SQUARE);
                gc.setBackground(prev);
            }
        }

        private Color colorFor(StepStatus status) {
            switch (status) {
                case GREEN:  return colorGreen;
                case YELLOW: return colorYellow;
                case RED:    return colorRed;
                default:     return colorWhite;
            }
        }
    }

    /**
     * Label provider for the Name column. Returns the step's custom name if set,
     * otherwise falls back to the action's display name, then to the raw action ID.
     */
    public static class Name extends ColumnLabelProvider {
        @Override
        public String getText(Object element) {
            Step step = (Step) element;
            if (step.getName() != null && !step.getName().isBlank())
                return step.getName();
            IAction action = ActionRegistry.getInstance().getAction(step.getActionId());
            return action != null ? action.getName() : step.getActionId();
        }
    }

    /**
     * Label provider for the Config column. Returns a comma-separated
     * {@code key=value} summary of the step's config, truncated to 80 characters.
     */
    public static class Config extends ColumnLabelProvider {
        private static final int MAX = 80;

        @Override
        public String getText(Object element) {
            Map<String, String> cfg = ((Step) element).getConfig();
            if (cfg == null || cfg.isEmpty()) return "";
            String text = cfg.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
            return text.length() > MAX ? text.substring(0, MAX - 3) + "..." : text;
        }
    }
}
