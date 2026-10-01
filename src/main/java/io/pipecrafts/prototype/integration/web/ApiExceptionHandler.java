package io.pipecrafts.prototype.integration.web;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.pipecrafts.prototype.integration.failure.DependencyFailedException;
import io.pipecrafts.prototype.integration.failure.DownstreamBusinessException;

/**
 * Maps failures to RFC 9457 problems with a {@code code} property.
 *
 * <p>Downstream exceptions are never read when building a body: every dependency failure yields the same 502 and
 * every unmapped business rejection the same 422, so nothing about the downstream leaks or can be enumerated.
 * Failures were already logged by the client layer.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DependencyFailedException.class)
    ProblemDetail handleDependencyFailed() {
        return ApiProblems.of(ApiErrorCode.DEPENDENCY_FAILED);
    }

    @ExceptionHandler(DownstreamBusinessException.class)
    ProblemDetail handleUnmappedBusiness() {
        return ApiProblems.of(ApiErrorCode.DOWNSTREAM_REJECTED);
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex) {
        return ApiProblems.of(ex.code());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiProblems.of(ApiErrorCode.INTERNAL_ERROR);
    }

    /**
     * Tags Spring MVC's own 400s (validation, missing or mistyped parameters, unreadable body) with a code. The
     * body is usually only built by {@code super}, so the code is added to its result.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && statusCode.value() == HttpStatus.BAD_REQUEST.value()
                && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty(ApiProblems.CODE_PROPERTY, ApiErrorCode.INVALID_REQUEST.name());
        }
        return response;
    }
}
