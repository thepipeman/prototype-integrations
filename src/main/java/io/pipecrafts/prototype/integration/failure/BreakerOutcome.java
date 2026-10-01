package io.pipecrafts.prototype.integration.failure;

public enum BreakerOutcome {

    SUCCESS,

    FAILURE,

    /** Released without being recorded: says nothing about the dependency's availability. */
    IGNORED
}
