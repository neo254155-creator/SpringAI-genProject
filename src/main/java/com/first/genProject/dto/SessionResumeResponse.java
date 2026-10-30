package com.first.genProject.dto;

/** Read-only state needed to reopen a saved interview without another AI call. */
public record SessionResumeResponse(SessionResponse session, StoredQuestionResponse question,
        StoredAnswerResponse answer, long questionsAnswered) { }
