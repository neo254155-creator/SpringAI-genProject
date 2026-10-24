package com.first.genProject.support;

public final class FeedbackFixtures {
    private FeedbackFixtures() {
    }

    public static final String VALID_FEEDBACK = """
            {
              "correctPoints": ["Identifies dependency injection"],
              "missingPoints": ["Explain constructor injection"],
              "explanation": "Spring supplies dependencies; constructor injection makes them explicit.",
              "rubric": {
                "correctness": {"score": 4, "reason": "Minor technical imprecision"},
                "completeness": {"score": 2, "reason": "One key point missing"},
                "clarity": {"score": 1, "reason": "Understandable but imprecise"}
              }
            }
            """;

    public static final String VALID_REQUEST = """
            {"track":"Java","topic":"Spring Boot","level":"intermediate",
             "question":"How does dependency injection work?",
             "answer":"Spring supplies dependencies to objects."}
            """;
}
