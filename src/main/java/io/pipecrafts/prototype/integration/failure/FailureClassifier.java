package io.pipecrafts.prototype.integration.failure;

import org.springframework.http.HttpStatusCode;

/**
 * The single source of truth for what a downstream status means, consulted by both the circuit breaker and the
 * status handler so the two can never disagree.
 *
 * <p>Must be a pure function of the status. A classifier bean qualified with a client name (or that client's YAML
 * {@code policy}) replaces the default for that client only; I/O failures are always
 * {@link FailureCategory#DEPENDENCY_FAILURE} and are not classified here.
 */
@FunctionalInterface
public interface FailureClassifier {

    Classification classify(HttpStatusCode status);
}
