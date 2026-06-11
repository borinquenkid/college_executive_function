import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

tasks.register<JavaExec>("generateCrapReport") {
    group = "verification"
    description = "Generate CRAP and Coverage reports from the Kover XML report."
    mainClass.set("com.borinquenterrier.cef.CrapIndexReporter")
    
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
            implementation("com.russhwolf:multiplatform-settings:1.1.1")
            implementation(libs.ical4j)
            implementation(libs.google.api.services.calendar)
            implementation(libs.google.api.client)
            implementation(libs.google.oauth.client.jetty)
            implementation(libs.llamatik)
            implementation(libs.okio)
        }
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
                implementation(libs.mockk)
                implementation(libs.kotlinx.datetime)
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
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
