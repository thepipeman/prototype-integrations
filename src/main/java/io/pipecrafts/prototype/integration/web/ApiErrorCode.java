package io.pipecrafts.prototype.integration.web;

import org.springframework.http.HttpStatus;

/**
 * Every error code this API can return. Titles and details are constants: no response body is ever built from an
 * exception message or downstream content.
 */
public enum ApiErrorCode {

    DEPENDENCY_FAILED(HttpStatus.BAD_GATEWAY, "Dependency failed",
            "A required service is currently unavailable. Please try again later."),
    DOWNSTREAM_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Request rejected",
            "The request could not be processed."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request", "The request is invalid."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profile not found", "No profile exists for the given id."),
    VERIFICATION_CODE_INCORRECT(HttpStatus.UNPROCESSABLE_CONTENT, "Verification code incorrect",
            "The verification code is incorrect."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Verification code expired",
            "The verification code has expired.");

    private final HttpStatus status;
    private final String title;
    private final String detail;

    ApiErrorCode(HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
