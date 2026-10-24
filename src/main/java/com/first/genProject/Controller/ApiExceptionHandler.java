package com.first.genProject.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.first.genProject.exception.InterviewNotFoundException;
import com.first.genProject.exception.InterviewStateException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import com.first.genProject.exception.AiProviderException;
import com.first.genProject.exception.InvalidInputException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidInputException.class)
    public ProblemDetail invalidInput(InvalidInputException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", exception.getMessage());
    }

    @ExceptionHandler(AiProviderException.class)
    public ProblemDetail providerFailure(AiProviderException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", exception.getMessage());
    }

    @ExceptionHandler(InterviewNotFoundException.class)
    public ProblemDetail notFound(InterviewNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InterviewStateException.class)
    public ProblemDetail completedSession(InterviewStateException exception) {
        return problem(HttpStatus.CONFLICT, "SESSION_COMPLETED", exception.getMessage());
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ProblemDetail databaseUnavailable(Exception exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "Interview storage is unavailable. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpectedFailure(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
