plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

dependencies {
    implementation(project(":shared"))
}
