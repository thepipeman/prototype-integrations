package io.pipecrafts.prototype.integration.web;

import org.springframework.http.ProblemDetail;

public final class ApiProblems {

    public static final String CODE_PROPERTY = "code";

    private ApiProblems() {
    }

    public static ProblemDetail of(ApiErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.detail());
        problem.setTitle(code.title());
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }
}
