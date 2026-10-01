package io.pipecrafts.prototype.integration.call;

import java.util.function.Supplier;

/** Entry point for facades that declare expected business outcomes of a downstream call instead of catching. */
public final class Downstream {

    private Downstream() {
    }

    /** Wraps the call lazily; nothing runs until {@link DownstreamCall#get()}. Map success inside the supplier. */
    public static <T> DownstreamCall<T> call(Supplier<? extends T> call) {
        return DownstreamCall.of(call);
    }
}
