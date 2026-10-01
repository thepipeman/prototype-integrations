package io.pipecrafts.prototype.integration.call;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.pipecrafts.prototype.integration.failure.DownstreamBusinessException;

/**
 * An immutable downstream call plus its declared business-outcome mappings.
 *
 * <p>Only {@link DownstreamBusinessException} is ever intercepted: the first matching mapping wins, in declaration
 * order, and an unmatched one is rethrown as the same instance. Dependency failures always propagate. Nothing is
 * logged here; the shared client layer already did.
 */
public final class DownstreamCall<T> {

    private final Supplier<? extends T> call;
    private final List<Mapping<T>> mappings;

    private DownstreamCall(Supplier<? extends T> call, List<Mapping<T>> mappings) {
        this.call = call;
        this.mappings = mappings;
    }

    static <T> DownstreamCall<T> of(Supplier<? extends T> call) {
        return new DownstreamCall<>(requireNonNull(call, "call"), List.of());
    }

    public DownstreamCall<T> onBusiness(int status, Supplier<? extends T> outcome) {
        requireNonNull(outcome, "outcome");
        return onBusiness(ex -> ex.status().value() == status, ex -> outcome.get());
    }

    public DownstreamCall<T> onBusinessCode(String code, Supplier<? extends T> outcome) {
        requireNonNull(code, "code");
        requireNonNull(outcome, "outcome");
        return onBusiness(ex -> code.equals(ex.error().code()), ex -> outcome.get());
    }

    public DownstreamCall<T> onBusiness(Predicate<DownstreamBusinessException> matches,
            Function<DownstreamBusinessException, ? extends T> outcome) {
        List<Mapping<T>> extended = new ArrayList<>(mappings);
        extended.add(new Mapping<>(requireNonNull(matches, "matches"), requireNonNull(outcome, "outcome")));
        return new DownstreamCall<>(call, List.copyOf(extended));
    }

    public T get() {
        try {
            return call.get();
        }
        catch (DownstreamBusinessException ex) {
            for (Mapping<T> mapping : mappings) {
                if (mapping.matches().test(ex)) {
                    return mapping.outcome().apply(ex);
                }
            }
            throw ex;
        }
    }

    private record Mapping<T>(Predicate<DownstreamBusinessException> matches,
            Function<DownstreamBusinessException, ? extends T> outcome) {
    }
}
