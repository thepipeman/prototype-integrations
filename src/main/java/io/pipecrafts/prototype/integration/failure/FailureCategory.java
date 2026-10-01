package io.pipecrafts.prototype.integration.failure;

public enum FailureCategory {

    SUCCESS,

    /** The dependency is healthy and said no: a typed outcome the caller may handle. */
    BUSINESS,

    /** The dependency is unavailable, slow, throttling or broken. */
    DEPENDENCY_FAILURE,

    /** Our own credentials or configuration were refused; the customer cannot fix this. */
    DEPENDENCY_REJECTED
}
