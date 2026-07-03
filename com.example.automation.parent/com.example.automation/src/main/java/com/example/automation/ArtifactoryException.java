package com.example.automation;

public class ArtifactoryException extends Exception {
    private static final long serialVersionUID = 1L;
    public ArtifactoryException(String message) { super(message); }
    public ArtifactoryException(String message, Throwable cause) { super(message, cause); }
}
