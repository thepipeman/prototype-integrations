package io.pipecrafts.prototype.integration.client;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.service.invoker.HttpRequestValues;

import io.pipecrafts.prototype.integration.failure.DownstreamOperation;

/** Tags each request with {@code Interface#method}, a bounded and PII-free name for logs and metrics. */
final class OperationAttributeProcessor implements HttpRequestValues.Processor {

    static String operationOf(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }

    @Override
    public void process(Method method, MethodParameter[] parameters, @Nullable Object[] arguments,
            HttpRequestValues.Builder builder) {
        builder.addAttribute(DownstreamOperation.ATTRIBUTE, operationOf(method));
    }
}
