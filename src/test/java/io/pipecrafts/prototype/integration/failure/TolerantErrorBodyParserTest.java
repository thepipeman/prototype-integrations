package io.pipecrafts.prototype.integration.failure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

import tools.jackson.databind.json.JsonMapper;

class TolerantErrorBodyParserTest {

    private final TolerantErrorBodyParser parser = new TolerantErrorBodyParser(JsonMapper.builder().build());

    @Test
    void readsCodeAndMessage() {
        DownstreamError error = parser.parse("""
                {"code":"CODE_EXPIRED","message":"expired","extra":1}""".getBytes(UTF_8), MediaType.APPLICATION_JSON);

        assertThat(error).isEqualTo(new DownstreamError("CODE_EXPIRED", "expired"));
    }

    @Test
    void readsJsonRegardlessOfDeclaredContentType() {
        assertThat(parser.parse("{\"code\":\"X\"}".getBytes(UTF_8), MediaType.TEXT_PLAIN).code()).isEqualTo("X");
    }

    static Stream<Arguments> unusableBodies() {
        byte[] oversized = ("{\"code\":\"BIG\",\"message\":\"" + "x".repeat(TolerantErrorBodyParser.MAX_BODY_BYTES)
                + "\"}").getBytes(UTF_8);
        return Stream.of(
                Arguments.of("empty", new byte[0]),
                Arguments.of("html", "<html>Not Found</html>".getBytes(UTF_8)),
                Arguments.of("truncated json", "{\"code\":".getBytes(UTF_8)),
                Arguments.of("json array", "[{\"code\":\"X\"}]".getBytes(UTF_8)),
                Arguments.of("json string", "\"CODE\"".getBytes(UTF_8)),
                Arguments.of("json null", "null".getBytes(UTF_8)),
                Arguments.of("wrong field types", "{\"code\":42,\"message\":{\"a\":1}}".getBytes(UTF_8)),
                Arguments.of("blank code", "{\"code\":\"  \"}".getBytes(UTF_8)),
                Arguments.of("invalid utf-8", new byte[] {'{', '"', 'c', '"', ':', '"', (byte) 0xC3, (byte) 0x28, '"', '}'}),
                Arguments.of("deeply nested", "[".repeat(5_000).getBytes(UTF_8)),
                Arguments.of("oversized", oversized));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusableBodies")
    void neverThrowsAndYieldsEmptyError(String description, byte[] body) {
        assertThat(parser.parse(body, null)).isEqualTo(DownstreamError.empty());
    }
}
