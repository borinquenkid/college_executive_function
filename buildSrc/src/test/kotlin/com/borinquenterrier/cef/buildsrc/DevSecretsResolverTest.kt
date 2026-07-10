package com.borinquenterrier.cef.buildsrc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private class FakeSecretStore(initial: Map<String, String> = emptyMap()) : SecretStore {
    val values = initial.toMutableMap()
    val writes = mutableListOf<Pair<String, String>>()

    override fun read(service: String, account: String): String? = values[account]

    override fun write(service: String, account: String, value: String) {
        values[account] = value
        writes += account to value
    }
}

private class FakePrompter(private val answers: Map<String, String?>) : SecretPrompter {
    val prompted = mutableListOf<String>()

    override fun promptFor(key: String): String? {
        prompted += key
        return answers[key]
    }
}

class DevSecretsResolverTest : FunSpec({

    test("returns every key already present without prompting") {
        val store = FakeSecretStore(mapOf("A" to "a-value", "B" to "b-value"))
        val prompter = FakePrompter(emptyMap())
        val resolver = DevSecretsResolver(store, prompter, "test-service")

        val result = resolver.resolve(listOf("A", "B"))

        result shouldContainExactly mapOf("A" to "a-value", "B" to "b-value")
        prompter.prompted shouldBe emptyList()
    }

    test("prompts for a missing key and persists the answer back to the store") {
        val store = FakeSecretStore(mapOf("A" to "a-value"))
        val prompter = FakePrompter(mapOf("B" to "prompted-b"))
        val resolver = DevSecretsResolver(store, prompter, "test-service")

        val result = resolver.resolve(listOf("A", "B"))

        result shouldContainExactly mapOf("A" to "a-value", "B" to "prompted-b")
        store.writes shouldBe listOf("B" to "prompted-b")
    }

    test("throws MissingSecretsException listing every unresolved key when no console is available") {
        val store = FakeSecretStore(mapOf("A" to "a-value"))
        val prompter = FakePrompter(mapOf("B" to null, "C" to null))
        val resolver = DevSecretsResolver(store, prompter, "test-service")

        val ex = shouldThrow<MissingSecretsException> { resolver.resolve(listOf("A", "B", "C")) }

        ex.missingKeys shouldBe listOf("B", "C")
        store.writes shouldBe emptyList()
    }

    test("treats a blank stored value the same as missing") {
        val store = FakeSecretStore(mapOf("A" to ""))
        val prompter = FakePrompter(mapOf("A" to "real-value"))
        val resolver = DevSecretsResolver(store, prompter, "test-service")

        val result = resolver.resolve(listOf("A"))

        result shouldContainExactly mapOf("A" to "real-value")
    }
})

class DefaultSecretStoreForOsTest : FunSpec({

    test("picks the security-CLI store on macOS to avoid the Keychain GUI prompt") {
        defaultSecretStoreForOs("Mac OS X").shouldBeInstanceOf<SecurityCliSecretStore>()
    }

    test("picks java-keyring on non-macOS platforms") {
        defaultSecretStoreForOs("Windows 11").shouldBeInstanceOf<JavaKeyringSecretStore>()
        defaultSecretStoreForOs("Linux").shouldBeInstanceOf<JavaKeyringSecretStore>()
    }
})
