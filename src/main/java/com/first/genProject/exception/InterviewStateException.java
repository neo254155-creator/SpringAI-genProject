package com.first.genProject.exception;

public class InterviewStateException extends RuntimeException {
    public InterviewStateException() { super("This interview session is completed"); }
}
