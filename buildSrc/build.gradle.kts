plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Exposes org.gradle.api.* (DefaultTask, Property, TaskAction, ...) to buildSrc's own Kotlin
    // sources. Using this instead of the `kotlin-dsl` plugin avoids kotlin-dsl's bundled embedded
    // Kotlin version conflicting with the kotlin("jvm") 2.4.0 applied above.
    compileOnly(gradleApi())

    // Verified real: Maven Central, BSD-licensed, functionally stable — see
    // docs/ops/supply-chain-hardening.md §4 for the full adoption note, including the
    // "not under active development" caveat worth re-checking if this sits unused for years.
    implementation("com.github.javakeyring:java-keyring:1.0.4")

    testImplementation("io.kotest:kotest-runner-junit5:6.2.1")
    testImplementation("io.kotest:kotest-assertions-core:6.2.1")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
