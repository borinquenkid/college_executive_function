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
    
    inputs.file(localPropertiesFile).optional()
    inputs.file(envFile).optional()
    
    val outputDir = layout.buildDirectory.dir("generated/cef/commonMain/kotlin")
    outputs.dir(outputDir)
    
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

        val web3FormsAccessKey = System.getenv("WEB3FORMS_ACCESS_KEY")
            ?: localProps.getProperty("WEB3FORMS_ACCESS_KEY")
            ?: envProps.getProperty("WEB3FORMS_ACCESS_KEY")
            ?: "cef-academic-anonymous-bugs-key-placeholder"
            
        val secretsFile = outputDir.get().file("com/borinquenterrier/cef/BuildSecrets.kt").asFile
        secretsFile.parentFile.mkdirs()
        
        secretsFile.writeText("""
            package com.borinquenterrier.cef
            
            object BuildSecrets {
                val GOOGLE_CLIENT_ID: String? = ${if (clientId.isBlank()) "null" else "\"$clientId\""}
                val GOOGLE_CLIENT_SECRET: String? = ${if (clientSecret.isBlank()) "null" else "\"$clientSecret\""}
                val WEB3FORMS_ACCESS_KEY: String = "$web3FormsAccessKey"
            }
        """.trimIndent() + "\n")
    }
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
            packageName = "com.borinquenterrier.college_executive_function"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "8g"
}
