package com.borinquenterrier.cef.buildsrc

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** Exact repo directory name, matching `scripts/migrate-secrets-to-keychain.sh`'s `SERVICE` and
 *  `scripts/load-secrets-from-keychain.sh`'s naming convention — entries created by either path
 *  read/write the same Keychain items. Must NOT be derived from `rootProject.name`
 *  ("CollegeExecutiveFunction"), which is a different string. */
const val CEF_KEYCHAIN_SERVICE = "college_executive_function"

/** Same six secrets `scripts/load-secrets-from-keychain.sh` already manages — kept in one place
 *  so composeApp's and server's `devSecrets` tasks can't drift apart. */
val CEF_DEV_SECRET_KEYS = listOf(
    "GOOGLE_CLIENT_SECRET",
    "CEF_GEMINI_API_KEY",
    "CEF_OTLP_PASSWORD",
    "CEF_TEST_USER_API_KEY",
    "OOC_TOKEN",
    "SONAR_TOKEN",
)

/**
 * Reads/writes one named secret in an OS-native keychain (macOS Keychain, Windows Credential
 * Manager, Linux GNOME/KDE keyrings via java-keyring). Abstracted so [DevSecretsResolver] can be
 * unit tested without a real OS keystore.
 */
interface SecretStore {
    fun read(service: String, account: String): String?
    fun write(service: String, account: String, value: String)
}

/** Wraps java-keyring. Uses the same service/account naming as `scripts/migrate-secrets-to-keychain.sh`
 *  (service = repo directory name, account = env var name), so entries are interchangeable with
 *  the existing shell-script-managed Keychain items.
 *
 *  macOS-specific gotcha, not used there — see [SecurityCliSecretStore]: java-keyring's macOS
 *  backend reads via Security.framework under the Gradle daemon JVM's own process identity, which
 *  differs from whatever process originally wrote the entry. macOS then shows a blocking
 *  SecurityAgent GUI prompt per item on first read — fine interactively, but there's no way to
 *  grant it non-interactively, and it hangs forever in a headless/CI context. */
class JavaKeyringSecretStore : SecretStore {
    override fun read(service: String, account: String): String? =
        Keyring.create().use { keyring ->
            try {
                keyring.getPassword(service, account)
            } catch (_: PasswordAccessException) {
                null
            }
        }

    override fun write(service: String, account: String, value: String) {
        Keyring.create().use { keyring -> keyring.setPassword(service, account, value) }
    }
}

/** macOS-only: shells out to `/usr/bin/security`, exactly matching
 *  `scripts/load-secrets-from-keychain.sh` / `scripts/migrate-secrets-to-keychain.sh`. Entries are
 *  already trusted for reads by that same CLI identity (it's what created them), so — unlike
 *  [JavaKeyringSecretStore] — this never triggers a macOS Keychain authorization prompt. See
 *  `docs/ops/keychain-secrets-migration.md` for the honest scope of what this trust boundary does
 *  and doesn't protect against. */
class SecurityCliSecretStore : SecretStore {
    override fun read(service: String, account: String): String? {
        val result = runSecurity("find-generic-password", "-a", account, "-s", service, "-w")
        return result?.takeIf { it.isNotBlank() }
    }

    override fun write(service: String, account: String, value: String) {
        runSecurity("add-generic-password", "-a", account, "-s", service, "-w", value, "-U")
    }

    private fun runSecurity(vararg args: String): String? {
        val process = ProcessBuilder(listOf("/usr/bin/security") + args)
            .redirectErrorStream(false)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        return if (exitCode == 0) output else null
    }
}

/** Picks the right [SecretStore] for the current OS — `security` CLI on macOS (avoids the GUI
 *  prompt described on [JavaKeyringSecretStore]), java-keyring everywhere else. */
fun defaultSecretStoreForOs(osName: String = System.getProperty("os.name")): SecretStore =
    if (osName.contains("mac", ignoreCase = true)) SecurityCliSecretStore() else JavaKeyringSecretStore()

/** Prompts the developer for a missing secret's value. Returns null when no interactive console
 *  is available (e.g. an IDE-launched Gradle run) rather than hanging or silently continuing. */
interface SecretPrompter {
    fun promptFor(key: String): String?
}

class ConsoleSecretPrompter : SecretPrompter {
    override fun promptFor(key: String): String? {
        val console = System.console() ?: return null
        console.printf("Enter value for %s (will be saved to Keychain): ", key)
        val chars = console.readPassword()
        return chars?.concatToString()?.takeIf { it.isNotBlank() }
    }
}

/** Thrown when one or more secrets are missing and no interactive console is available to prompt
 *  for them. Carries every missing key at once (not just the first) so a dev fixes the whole list
 *  in one pass rather than discovering them one Gradle invocation at a time. */
class MissingSecretsException(val missingKeys: List<String>, serviceName: String) : RuntimeException(
    "Missing dev secret(s) in Keychain (service \"$serviceName\"): ${missingKeys.joinToString(", ")}. " +
        "Run `./gradlew devSecrets` from a real terminal (not an IDE run button) to be prompted " +
        "for each one — IDE-launched Gradle runs have no console to prompt through. " +
        "Alternative: `scripts/migrate-secrets-to-keychain.sh` after populating .env locally."
)

/**
 * Resolves each of [keys] from [store] under [serviceName], prompting via [prompter] and
 * persisting back to [store] for any that are missing. If any key is still missing after
 * prompting (no console available), throws [MissingSecretsException] listing all of them —
 * fails fast with one actionable message rather than partially injecting secrets.
 */
class DevSecretsResolver(
    private val store: SecretStore,
    private val prompter: SecretPrompter,
    private val serviceName: String,
) {
    fun resolve(keys: List<String>): Map<String, String> {
        val resolved = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()

        for (key in keys) {
            val existing = store.read(serviceName, key)?.takeIf { it.isNotBlank() }
            if (existing != null) {
                resolved[key] = existing
                continue
            }

            val prompted = prompter.promptFor(key)
            if (prompted == null) {
                missing += key
            } else {
                store.write(serviceName, key, prompted)
                resolved[key] = prompted
            }
        }

        if (missing.isNotEmpty()) {
            throw MissingSecretsException(missing, serviceName)
        }

        return resolved
    }
}

/**
 * Gradle task wrapper around [DevSecretsResolver]. Register once per module that needs secrets
 * injected into a `run`/`bootRun`-style task, then have that task read [resolvedSecrets] in a
 * `doFirst` block and call `environment(...)` — see composeApp's and server's `build.gradle.kts`.
 *
 * Never up-to-date: Keychain contents can change between builds independent of any Gradle input,
 * so this always re-resolves rather than caching a stale result.
 */
abstract class DevSecretsTask : DefaultTask() {

    @get:Input
    abstract val serviceName: Property<String>

    @get:Input
    abstract val secretKeys: ListProperty<String>

    @get:Internal
    abstract val resolvedSecrets: MapProperty<String, String>

    init {
        group = "credentials"
        description = "Resolves dev secrets from the OS keychain, prompting for any missing ones " +
            "when run from a terminal. Depended on by :run tasks that need them injected."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun resolve() {
        val resolver = DevSecretsResolver(defaultSecretStoreForOs(), ConsoleSecretPrompter(), serviceName.get())
        val resolved = resolver.resolve(secretKeys.get())
        resolvedSecrets.set(resolved)
        logger.lifecycle("devSecrets: resolved {} secret(s) for service \"{}\".", resolved.size, serviceName.get())
    }
}
