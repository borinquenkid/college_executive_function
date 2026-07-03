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
    implementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.dotenv.kotlin)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests hitting the real Gemini API are excluded by default (real quota cost, need a
    // configured key). Pass -PrunAITests=true to include them, matching the same flag
    // composeApp uses for its own AI integration tests. Note: server's existing
    // *IntegrationTest classes (e.g. WebIngestionIntegrationTest) are fully mocked and
    // always run — only the *LiveIntegrationTest suffix opts into this exclusion.
    if (!project.hasProperty("runAITests")) {
        filter {
            excludeTestsMatching("*LiveIntegrationTest*")
        }
    }
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

tasks.register<JavaExec>("vacuumBackup") {
    group = "maintenance"
    description = "VACUUM INTO snapshot backup of every tenant SQLite database. " +
        "Reads CEF_TENANT_BASE_DIR / CEF_BACKUP_DIR, or pass them as -Ptenant= -Pbackup=. See DEPLOYMENT.md."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.borinquenterrier.cef.BackupCliKt")
    project.findProperty("tenant")?.let { tenantDir -> project.findProperty("backup")?.let { backupDir ->
        args(tenantDir, backupDir)
    } }
}

