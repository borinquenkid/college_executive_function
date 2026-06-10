plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.borinquenterrier.cef.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":composeApp"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.multiplatform.settings)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("generateTypescript") {
    group = "codegen"
    description = "Generates web/src/cef-types.ts from Kotlin @Serializable classes"
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.borinquenterrier.cef.TypeScriptGeneratorKt")
    args(rootProject.projectDir.resolve("web/src").absolutePath)
}

