package com.example.automation.tests;

import static org.junit.Assert.*;
import org.junit.Test;
import com.example.automation.ArtifactoryException;

public class ArtifactoryExceptionTest {

    @Test
    public void constructor_setsMessage() {
        ArtifactoryException ex = new ArtifactoryException("oops");
        assertEquals("oops", ex.getMessage());
    }

    @Test
    public void constructor_withCause_setsBoth() {
        Throwable cause = new RuntimeException("root");
        ArtifactoryException ex = new ArtifactoryException("oops", cause);
        assertEquals("oops", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
