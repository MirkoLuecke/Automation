package com.example.automation.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import com.example.automation.StepOperations;
import com.example.automation.model.Step;

public class StepOperationsTest {

    // isContiguous

    @Test
    public void isContiguous_emptyArray_returnsFalse() {
        assertFalse(StepOperations.isContiguous(new int[]{}));
    }

    @Test
    public void isContiguous_singleElement_returnsTrue() {
        assertTrue(StepOperations.isContiguous(new int[]{3}));
    }

    @Test
    public void isContiguous_consecutiveIndices_returnsTrue() {
        assertTrue(StepOperations.isContiguous(new int[]{2, 3, 4}));
    }

    @Test
    public void isContiguous_gapInIndices_returnsFalse() {
        assertFalse(StepOperations.isContiguous(new int[]{1, 3, 4}));
    }

    @Test
    public void isContiguous_twoElements_consecutive_returnsTrue() {
        assertTrue(StepOperations.isContiguous(new int[]{5, 6}));
    }

    @Test
    public void isContiguous_twoElements_nonConsecutive_returnsFalse() {
        assertFalse(StepOperations.isContiguous(new int[]{5, 7}));
    }

    // deepCopy

    @Test
    public void deepCopy_copiesAllFields() {
        Step src = new Step("my-action");
        src.setName("My Step");
        src.setBold(true);
        src.getConfig().put("key", "value");

        Step copy = StepOperations.deepCopy(src);

        assertEquals("my-action", copy.getActionId());
        assertEquals("My Step", copy.getName());
        assertTrue(copy.isBold());
        assertEquals("value", copy.getConfig().get("key"));
    }

    @Test
    public void deepCopy_configIsIndependent() {
        Step src = new Step("my-action");
        src.getConfig().put("key", "original");

        Step copy = StepOperations.deepCopy(src);
        copy.getConfig().put("key", "modified");

        assertEquals("original", src.getConfig().get("key"));
    }

    @Test
    public void deepCopy_nullNameAndFalse() {
        Step src = new Step("x");
        Step copy = StepOperations.deepCopy(src);
        assertNull(copy.getName());
        assertFalse(copy.isBold());
    }

    // isDirField / isFileField

    @Test
    public void isDirField_keyEndingWithDir_returnsTrue() {
        assertTrue(StepOperations.isDirField("workingDir"));
        assertTrue(StepOperations.isDirField("repoDir"));
        assertTrue(StepOperations.isDirField("targetDir"));
    }

    @Test
    public void isDirField_otherKey_returnsFalse() {
        assertFalse(StepOperations.isDirField("filePath"));
        assertFalse(StepOperations.isDirField("url"));
    }

    @Test
    public void isFileField_keyEndingWithFileOrPath_returnsTrue() {
        assertTrue(StepOperations.isFileField("filePath"));
        assertTrue(StepOperations.isFileField("settingsFile"));
        assertTrue(StepOperations.isFileField("pomPath"));
    }

    @Test
    public void isFileField_otherKey_returnsFalse() {
        assertFalse(StepOperations.isFileField("workingDir"));
        assertFalse(StepOperations.isFileField("goals"));
    }
}
