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
                val GOOGLE_CLIENT_ID: String? = if (_cid.isEmpty()) null else _cid.map { (it xor K).toChar() }.joinToString("")
                val GOOGLE_CLIENT_SECRET: String? = if (_cs.isEmpty()) null else _cs.map { (it xor K).toChar() }.joinToString("")
                val WEB3FORMS_ACCESS_KEY: String = _w3f.map { (it xor K).toChar() }.joinToString("")
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

tasks.register<JavaExec>("generateCrapReport") {
    group = "verification"
    description = "Generate CRAP and Coverage reports from the Kover XML report."
    mainClass.set("com.borinquenterrier.cef.CrapIndexReporter")
    outputs.upToDateWhen { false }
    dependsOn("koverXmlReportJvm")

    val jvmTarget = kotlin.targets.getByName("jvm")
    val jvmTestCompilation = jvmTarget.compilations.getByName("test")
    classpath = files(
        jvmTestCompilation.output.classesDirs,
        jvmTestCompilation.compileDependencyFiles,
        jvmTestCompilation.runtimeDependencyFiles
    )
}

// Single entry-point: runs jvmTest → koverXmlReportJvm → generateCrapReport in order.
tasks.register("refreshCrap") {
    group = "verification"
    description = "Run tests, refresh Kover XML coverage, and regenerate CRAP/COVERAGE reports."
    dependsOn("generateCrapReport")
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
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.mockk)
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
            }

            macOS {
                bundleID = "com.borinquenterrier.college_executive_function"
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
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
