package com.first.genProject.exception;

public class InterviewNotFoundException extends RuntimeException {
    public InterviewNotFoundException(String resource) { super(resource + " was not found"); }
}
