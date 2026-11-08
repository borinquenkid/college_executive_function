package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GenAiSpanAttributesTest : FunSpec({

    class RecordingScope : SpanScope {
        val attrs = linkedMapOf<String, Any>()
        override fun setAttribute(key: String, value: String) { attrs[key] = value }
        override fun setAttribute(key: String, value: Long) { attrs[key] = value }
        override fun recordException(t: Throwable) = Unit
        override fun addEvent(name: String, attributes: Map<String, String>) = Unit
    }

    test("records operation, provider, model and token usage from a Gemini response body") {
        val scope = RecordingScope()
        val body = """{"candidates":[],"usageMetadata":{"promptTokenCount":1200,"candidatesTokenCount":345,"totalTokenCount":1545}}"""

        GenAiSpanAttributes.apply(scope, "gemini-3.8-flash", body)

        scope.attrs shouldBe mapOf(
            "gen_ai.operation.name" to "generate_content",
            "gen_ai.provider.name" to "gcp.gemini",
            "gen_ai.request.model" to "gemini-3.8-flash",
            "gen_ai.usage.input_tokens" to 1200L,
            "gen_ai.usage.output_tokens" to 345L
        )
    }

    test("omits token attributes when usageMetadata is absent") {
        val scope = RecordingScope()

        GenAiSpanAttributes.apply(scope, "gemini-2.5-flash", """{"candidates":[]}""")

        scope.attrs.keys shouldBe setOf("gen_ai.operation.name", "gen_ai.provider.name", "gen_ai.request.model")
    }

    test("does not throw and omits tokens when the body is not JSON (e.g. an error page)") {
        val scope = RecordingScope()

        GenAiSpanAttributes.apply(scope, "gemini-2.5-flash", "<html>502 Bad Gateway</html>")

        scope.attrs.keys shouldBe setOf("gen_ai.operation.name", "gen_ai.provider.name", "gen_ai.request.model")
    }

    test("records only the token counts that are present") {
        val scope = RecordingScope()

        GenAiSpanAttributes.apply(scope, "m", """{"usageMetadata":{"promptTokenCount":7}}""")

        scope.attrs["gen_ai.usage.input_tokens"] shouldBe 7L
        scope.attrs.containsKey("gen_ai.usage.output_tokens") shouldBe false
    }
})
