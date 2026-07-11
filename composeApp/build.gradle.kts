import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.prefs.Preferences as JPrefs

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kover)
    alias(libs.plugins.sonarqube)
}

fun loadEnvKey(key: String): String? {
    System.getenv(key)?.let { return it }
    val envFile = rootProject.file(".env").takeIf { it.exists() } ?: return null
    return envFile.readLines()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
}

val generateBuildSecrets = tasks.register("generateBuildSecrets") {
    val localPropertiesFile = project.rootProject.file("local.properties")
    val envFile = project.rootProject.file(".env")
    
    // Use a lazy provider so Gradle only validates files that actually exist —
    // both are gitignored secret sources and absent on CI runners.
    inputs.files(project.provider {
        listOf(localPropertiesFile, envFile).filter { it.exists() }
    }).withPropertyName("secretSourceFiles").optional()
    
    val outputDir = layout.buildDirectory.dir("generated/cef/commonMain/kotlin")
    outputs.dir(outputDir)

    inputs.property("GOOGLE_CLIENT_ID", System.getenv("GOOGLE_CLIENT_ID") ?: "")
    inputs.property("GOOGLE_CLIENT_SECRET", System.getenv("GOOGLE_CLIENT_SECRET") ?: "")
    inputs.property("WEB3FORMS_ACCESS_KEY", System.getenv("WEB3FORMS_ACCESS_KEY") ?: "")
    inputs.property("CEF_OTLP_ENDPOINT", System.getenv("CEF_OTLP_ENDPOINT") ?: "")
    inputs.property("CEF_OTLP_USER", System.getenv("CEF_OTLP_USER") ?: "")
    inputs.property("CEF_OTLP_PASSWORD", System.getenv("CEF_OTLP_PASSWORD") ?: "")
    
    doLast {
        // Read local.properties
        val localProps = Properties()
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProps.load(it) }
        }
        
        // Read .env file in root
        val envProps = Properties()
        if (envFile.exists()) {
            envFile.inputStream().use { envProps.load(it) }
        }
        
        val clientId = System.getenv("GOOGLE_CLIENT_ID")
            ?: localProps.getProperty("GOOGLE_CLIENT_ID")
            ?: envProps.getProperty("GOOGLE_CLIENT_ID")
            ?: ""
            
        val clientSecret = System.getenv("GOOGLE_CLIENT_SECRET")
            ?: localProps.getProperty("GOOGLE_CLIENT_SECRET")
            ?: envProps.getProperty("GOOGLE_CLIENT_SECRET")
            ?: ""

        if (System.getenv("GITHUB_ACTIONS") == "true") {
            if (clientId.isBlank() || clientSecret.isBlank()) {
                error("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables must be set on CI. Please configure them in GitHub Repository Secrets.")
            }
        }


        val web3FormsAccessKey = System.getenv("WEB3FORMS_ACCESS_KEY")
            ?: localProps.getProperty("WEB3FORMS_ACCESS_KEY")
            ?: envProps.getProperty("WEB3FORMS_ACCESS_KEY")
            ?: "cef-academic-anonymous-bugs-key-placeholder"

        fun resolve(key: String) = System.getenv(key)
            ?: localProps.getProperty(key)
            ?: envProps.getProperty(key)
            ?: ""

        val otlpEndpoint = resolve("CEF_OTLP_ENDPOINT")
        val otlpUser     = resolve("CEF_OTLP_USER")
        val otlpPassword = resolve("CEF_OTLP_PASSWORD")

        // XOR obfuscation: secrets are stored as IntArrays in the bytecode, never as string literals.
        // The key is an Int constant — strings/javap will not reveal the credential value.
        val obfKey = 0x4A3F
        fun obfuscate(s: String): String {
            val parts = s.map { "0x${(it.code xor obfKey).toString(16).uppercase()}" }
            return "intArrayOf(${parts.joinToString(", ")})"
        }

        val secretsFile = outputDir.get().file("com/borinquenterrier/cef/BuildSecrets.kt").asFile
        secretsFile.parentFile.mkdirs()

        secretsFile.writeText("""
            package com.borinquenterrier.cef

            object BuildSecrets {
                private const val K = $obfKey
                private val _cid = ${if (clientId.isBlank()) "intArrayOf()" else obfuscate(clientId)}
                private val _cs = ${if (clientSecret.isBlank()) "intArrayOf()" else obfuscate(clientSecret)}
                private val _w3f = ${obfuscate(web3FormsAccessKey)}
                private val _oep = ${if (otlpEndpoint.isBlank()) "intArrayOf()" else obfuscate(otlpEndpoint)}
                private val _ou  = ${if (otlpUser.isBlank()) "intArrayOf()" else obfuscate(otlpUser)}
                private val _opw = ${if (otlpPassword.isBlank()) "intArrayOf()" else obfuscate(otlpPassword)}
                val GOOGLE_CLIENT_ID: String? = if (_cid.isEmpty()) null else _cid.map { (it xor K).toChar() }.joinToString("")
                val GOOGLE_CLIENT_SECRET: String? = if (_cs.isEmpty()) null else _cs.map { (it xor K).toChar() }.joinToString("")
                val WEB3FORMS_ACCESS_KEY: String = _w3f.map { (it xor K).toChar() }.joinToString("")
                val OTLP_ENDPOINT: String? = if (_oep.isEmpty()) null else _oep.map { (it xor K).toChar() }.joinToString("")
                val OTLP_USER: String?     = if (_ou.isEmpty())  null else _ou.map  { (it xor K).toChar() }.joinToString("")
                val OTLP_PASSWORD: String? = if (_opw.isEmpty()) null else _opw.map { (it xor K).toChar() }.joinToString("")
            }
        """.trimIndent() + "\n")
    }
}

tasks.register<JavaExec>("generateTest") {
    group = "verification"
    description = "Automatically generate Kotest unit tests using local LLM via Ollama."
    mainClass.set("com.borinquenterrier.cef.LocalTestGenerator")
    
    // Wire classpath from jvmTest source set
    val jvmTarget = kotlin.targets.getByName("jvm")
    val jvmTestCompilation = jvmTarget.compilations.getByName("test")
    classpath = files(
        jvmTestCompilation.output.classesDirs,
        jvmTestCompilation.compileDependencyFiles,
        jvmTestCompilation.runtimeDependencyFiles
    )
    
    // Enable interactive console input
    standardInput = System.`in`
}

// Never cache koverXmlReportJvm: new tests may exist even when source classes are unchanged.
tasks.matching { it.name == "koverXmlReportJvm" }.configureEach {
    outputs.upToDateWhen { false }
}

// HARD-1 (ROADMAP.md Phase 10): a packaged release build with blank OTLP secrets ships with
// tracing silently downgraded to NoopTracer — until now the only trace was a println swallowed
// by Gradle's output, so a release could ship blind with nobody noticing. Fail loud instead,
// for any packageRelease{Dmg,Msi,Deb} invocation. This is a standalone task (not folded into
// generateBuildSecrets's doLast) and deliberately never cacheable, so it re-checks the actual
// secrets on every release-packaging run rather than silently reusing a stale UP-TO-DATE result
// from an earlier build that had different (or missing) secrets.
val verifyReleaseTelemetrySecrets = tasks.register("verifyReleaseTelemetrySecrets") {
    group = "verification"
    description = "Fails the build if a packageRelease* task is running without OTLP telemetry secrets configured."
    outputs.upToDateWhen { false }
    val localPropertiesFile = project.rootProject.file("local.properties")
    val envFile = project.rootProject.file(".env")
    doLast {
        val localProps = Properties()
        if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { localProps.load(it) }
        val envProps = Properties()
        if (envFile.exists()) envFile.inputStream().use { envProps.load(it) }
        fun resolve(key: String) = System.getenv(key) ?: localProps.getProperty(key) ?: envProps.getProperty(key) ?: ""

        val missing = listOf("CEF_OTLP_ENDPOINT", "CEF_OTLP_USER", "CEF_OTLP_PASSWORD").filter { resolve(it).isBlank() }
        if (missing.isNotEmpty()) {
            error(
                "Release packaging build is about to ship with tracing DISABLED (NoopTracer) " +
                    "— missing OTLP secret(s): ${missing.joinToString(", ")}. Configure them as " +
                    "repository secrets before cutting a release (see ROADMAP.md HARD-1), or this " +
                    "release ships blind with no way to tell."
            )
        }
    }
}

tasks.matching { it.name.contains("packageRelease", ignoreCase = true) }.configureEach {
    dependsOn(verifyReleaseTelemetrySecrets)
}

// HARD-6 (ROADMAP.md Phase 10): desktop's isDebug defaulted to true unless a `debug` system
// property or DEBUG env var was explicitly set to "false" — nothing in this file or
// release-desktop.yml ever set that, so a packaged .dmg/.msi/.deb shipped debug-on (unbounded
// debug_logs.txt growth included) by default. Rather than add another flag nobody remembers to
// set, bake whether this invocation was requested to package a release straight from the
// requested task names into a compiled constant, so a packageRelease* build can never silently
// ship debug-on the way an unset env var let it before. Computed as a local val (not a top-level
// script var) and read only inside this task's own doLast — capturing the outer script object in
// a task action breaks the configuration cache.
val generateJvmBuildFlags = tasks.register("generateJvmBuildFlags") {
    group = "build"
    description = "Bakes whether this invocation is packaging a desktop release into a compiled constant."
    val isPackagingDesktopRelease = gradle.startParameter.taskNames.any {
        it.contains("packageRelease", ignoreCase = true)
    }
    inputs.property("isPackagingDesktopRelease", isPackagingDesktopRelease)
    val jvmBuildFlagsDir = layout.buildDirectory.dir("generated/cef/jvmMain/kotlin")
    outputs.dir(jvmBuildFlagsDir)
    outputs.upToDateWhen { false }
    doLast {
        val file = jvmBuildFlagsDir.get().file("com/borinquenterrier/cef/JvmBuildFlags.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.borinquenterrier.cef

            internal const val IS_PACKAGED_DESKTOP_RELEASE: Boolean = $isPackagingDesktopRelease
            """.trimIndent() + "\n"
        )
    }
}

// Exclude pure Compose UI files (annotated @file:UiOnly) from coverage gate and reports.
// These files contain only rendering code and cannot be meaningfully unit-tested without
// a full Compose test harness. Domain logic is already extracted into separate classes.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("com.borinquenterrier.cef.UiOnly")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

// Retired the hand-rolled CrapIndexReporter (regex/brace-counting complexity proxy) in favor
// of SonarQube's real AST-based analysis — see docs/ops/sonarqube-local.md. Config ported
// from the sibling oficio project's :server module, adapted to this module's KMP source
// layout (commonMain + jvmMain feed the analyzer; jvmTest is the only test source Kover
// instruments today).
sonar {
    properties {
        property("sonar.projectKey", "cef-composeApp")
        property("sonar.projectName", "College Executive Function (composeApp)")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.sources", "src/commonMain/kotlin,src/jvmMain/kotlin")
        property("sonar.tests", "src/jvmTest/kotlin")
        // Absolute path — Sonar's JaCoCo-XML import silently shows 0% coverage on a
        // bad relative path rather than failing the scan.
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/kover/reportJvm.xml").get().asFile.absolutePath,
        )
        loadEnvKey("SONAR_TOKEN")?.let { property("sonar.token", it) }
    }
}

// A stale/missing report would otherwise silently read as 0% coverage instead of failing.
tasks.named("sonar") {
    dependsOn("koverXmlReportJvm")
}

// Replaces the old generateCrapReport/refreshCrap entry point. Chains
// jvmTest → koverXmlReportJvm → sonar → the Quality Gate check in one command.
// Requires `docker compose up -d sonarqube` running locally (see docs/ops/sonarqube-local.md).
// ADR 0004 / ROADMAP Phase 13 EB-3. Run after `jvmTest -PrunAITests=true` so evals/current_*.json
// is fresh. Prints (and, in CI, appends to $GITHUB_STEP_SUMMARY) a delta table against the
// checked-in evals/baseline_*.json files. Visibility only — never fails the build.
tasks.register<JavaExec>("evalBaselineDelta") {
    group = "verification"
    description = "Diffs evals/current_*.json against evals/baseline_*.json and prints a delta summary."
    mainClass.set("com.borinquenterrier.cef.EvalBaselineComparator")

    val jvmTarget = kotlin.targets.getByName("jvm")
    val jvmTestCompilation = jvmTarget.compilations.getByName("test")
    classpath = files(
        jvmTestCompilation.output.classesDirs,
        jvmTestCompilation.compileDependencyFiles,
        jvmTestCompilation.runtimeDependencyFiles
    )
}

tasks.register<JavaExec>("checkQualityGate") {
    group = "verification"
    description = "Polls the last Sonar analysis and hard-fails if the Quality Gate is not OK."
    mainClass.set("com.borinquenterrier.cef.SonarQualityGateChecker")
    dependsOn("sonar")
    systemProperty("sonar.host.url", "http://localhost:9000")
    loadEnvKey("SONAR_TOKEN")?.let { systemProperty("sonar.token", it) }

    val jvmTarget = kotlin.targets.getByName("jvm")
    val jvmTestCompilation = jvmTarget.compilations.getByName("test")
    classpath = files(
        jvmTestCompilation.output.classesDirs,
        jvmTestCompilation.compileDependencyFiles,
        jvmTestCompilation.runtimeDependencyFiles
    )
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildSecrets)
        }
        jvmMain {
            kotlin.srcDir(generateJvmBuildFlags)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.pdfbox.android)
            implementation(libs.play.services.auth)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.ical4j)
            implementation(libs.google.api.services.calendar)
            implementation(libs.google.api.client)
            implementation(libs.google.oauth.client.jetty)
            implementation(libs.okio)        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.framework.engine)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.datetime)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(kotlin("test-junit5"))
                implementation(libs.mockk)
                implementation(libs.kotlinx.datetime)
                implementation(libs.dotenv.kotlin)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.mockk)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.espresso.core)
                implementation(libs.androidx.espresso.intents)
                implementation(libs.play.services.auth)
                implementation(libs.multiplatform.settings.test)
            }
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.java)
            implementation(libs.google.http.client.gson)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.pdfbox)
            implementation(libs.opentelemetry.sdk)
            implementation(libs.opentelemetry.exporter.otlp)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.borinquenterrier.cef.db")
        }
    }
}

android {
    namespace = "com.borinquenterrier.college_executive_function"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.borinquenterrier.cef.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Display/bundle name shown to users (Finder, Start Menu, Dock, .app/.exe/.deb names).
            // The technical identifier stays as com.borinquenterrier.college_executive_function
            // (set explicitly via macOS.bundleID below) so existing installs/Launch Services
            // associations aren't treated as a different app after this rename.
            packageName = "CEF"
            packageVersion = (findProperty("cef.versionName") as String?) ?: "1.0.0"

            // Calling modules(...) replaces the plugin's default minimal set
            // (java.base, java.desktop, java.logging, jdk.crypto.ec) entirely, so we must
            // list everything the app needs: those defaults, plus what `suggestRuntimeModules`
            // (jdeps static analysis) detects, plus java.net.http — which jdeps misses because
            // ktor-client-java resolves its engine via ServiceLoader at runtime, causing jlink to
            // strip it and crash with NoClassDefFoundError: java/net/http/HttpClient$Version.
            modules(
                "java.base", "java.desktop", "java.logging", "jdk.crypto.ec",
                "java.compiler", "java.instrument", "java.management", "java.naming",
                "java.prefs", "java.security.jgss", "java.sql", "jdk.httpserver", "jdk.unsupported",
                "java.net.http"
            )

            windows {
                menuGroup = "College Executive Function"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "AA9FA31A-BB3B-4443-B61C-721556B04FEA"
                iconFile.set(project.file("packaging/cef-icon.ico"))
            }

            macOS {
                bundleID = "com.borinquenterrier.college_executive_function"
                iconFile.set(project.file("packaging/cef-icon.icns"))
            }

            linux {
                iconFile.set(project.file("packaging/cef-icon.png"))
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Resolves local dev secrets (Google OAuth, Gemini, OTLP, etc.) from the OS keychain and injects
// them into `run`'s child JVM process — see docs/ops/supply-chain-hardening.md §4 and ROADMAP.md
// Phase 11 Task 8. Prompting only works from a real terminal (`./gradlew run`); IDE-launched runs
// fail fast with a message telling you to run `./gradlew devSecrets` from a terminal first.
val devSecrets = tasks.register<com.borinquenterrier.cef.buildsrc.DevSecretsTask>("devSecrets") {
    serviceName.set(com.borinquenterrier.cef.buildsrc.CEF_KEYCHAIN_SERVICE)
    secretKeys.set(com.borinquenterrier.cef.buildsrc.CEF_DEV_SECRET_KEYS)
}

// Compose Desktop's plugin registers "run" lazily (later than this script body runs), so
// tasks.named("run") can't find it yet here — tasks.matching(...) stays live against tasks
// registered afterward and picks it up once it exists.
tasks.matching { it.name == "run" }.configureEach {
    dependsOn(devSecrets)
    doFirst {
        val exec = this as JavaExec
        devSecrets.get().resolvedSecrets.get().forEach { (key, value) -> exec.environment(key, value) }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "8g"
    outputs.upToDateWhen { false }
    // AI integration tests are excluded by default. Pass -PrunAITests=true (CI or manual) to include them.
    if (!project.hasProperty("runAITests")) {
        filter {
            excludeTestsMatching("*IntegrationTest*")
            excludeTestsMatching("*ContributorPdf*")
        }
    }
    // Pass -PcontributionFilter=<ContributionIndex name> to run a single PDF entry.
    // e.g. ./gradlew :composeApp:jvmTest -PcontributionFilter=STLCC_ENG101_WEEKLY
    project.findProperty("contributionFilter")?.let {
        systemProperty("contributionFilter", it)
    }
    // Pass -PrecordEvalBaseline=true (alongside -PrunAITests=true) to promote the 3 eval-shaped
    // classes' freshly-computed metrics into the checked-in evals/baseline_*.json files (ADR 0004
    // / ROADMAP Phase 13 EB-1/EB-2). Human-run-and-review only — never set by the nightly workflow.
    project.findProperty("recordEvalBaseline")?.let {
        systemProperty("recordEvalBaseline", it)
    }
}

/**
 * One-time helper: reads GOOGLE_ACCESS_TOKEN and GOOGLE_REFRESH_TOKEN from the app's
 * Java Preferences node (written there after a successful sign-in) and patches .env so
 * the values are available to integration tests locally and can be copied to GitHub Secrets.
 *
 * Usage:
 *   1. Run the app and sign in with Google (2FA handled interactively in the browser).
 *   2. ./gradlew :composeApp:exportTokens
 */
tasks.register("exportTokens") {
    group = "credentials"
    description = "Copies Google tokens from app Preferences into .env for test use."
    doLast {
        val prefs: JPrefs = JPrefs.userRoot().node("com/borinquenterrier/cef")
        val accessToken: String  = prefs.get("GOOGLE_ACCESS_TOKEN",  "")
        val refreshToken: String = prefs.get("GOOGLE_REFRESH_TOKEN", "")

        if (accessToken.isEmpty())  error("GOOGLE_ACCESS_TOKEN not found in Preferences — sign in via the app first.")
        if (refreshToken.isEmpty()) error("GOOGLE_REFRESH_TOKEN not found in Preferences — sign in via the app first.")

        val envFile = rootProject.file(".env")
        val lines: MutableList<String> = if (envFile.exists()) envFile.readLines().toMutableList() else mutableListOf()

        fun upsert(key: String, value: String) {
            val idx = lines.indexOfFirst { it.startsWith("$key=") }
            if (idx >= 0) lines[idx] = "$key=$value" else lines += "$key=$value"
        }

        upsert("GOOGLE_ACCESS_TOKEN",  accessToken)
        upsert("GOOGLE_REFRESH_TOKEN", refreshToken)
        envFile.writeText(lines.joinToString("\n") + "\n")

        println("✓ .env updated.")
        println("  GOOGLE_ACCESS_TOKEN  = ${accessToken.substring(0, minOf(12, accessToken.length))}…")
        println("  GOOGLE_REFRESH_TOKEN = ${refreshToken.substring(0, minOf(12, refreshToken.length))}…")
        println()
        println("Next: update the same values in GitHub → Settings → Secrets → Actions.")
    }
}

/**
 * Custom task to query logs/traces directly from OpenObserve Cloud using credentials in .env
 * by running the production CLI query utility.
 *
 * Usage:
 *   ./gradlew :composeApp:queryOpenObserve -Pquery="SELECT * FROM 'cef-ios' LIMIT 5"
 *   ./gradlew :composeApp:queryOpenObserve -Pquery="SELECT * FROM 'cef-ios' WHERE status_code = 2" -Pdays=30
 */
tasks.register<JavaExec>("queryOpenObserve") {
    group = "telemetry"
    description = "Queries OpenObserve Cloud using the production CLI utility."

    val jvm = kotlin.targets.getByName("jvm")
    val main = jvm.compilations.getByName("main")
    classpath = (main.runtimeDependencyFiles ?: files()) + main.output.allOutputs
    mainClass.set("com.borinquenterrier.cef.OpenObserveQueryCliKt")

    // Pass properties as command-line arguments
    val queryStr = project.findProperty("query") as? String ?: ""
    val days = project.findProperty("days") as? String ?: "7"
    val type = project.findProperty("type") as? String ?: "logs"
    args(queryStr, days, type)
}

