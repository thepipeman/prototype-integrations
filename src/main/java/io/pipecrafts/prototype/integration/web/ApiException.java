package io.pipecrafts.prototype.integration.web;

/** A domain outcome that the controller turns into an API error with a fixed code. */
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;

    public ApiException(ApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public ApiErrorCode code() {
        return code;
    }
}
