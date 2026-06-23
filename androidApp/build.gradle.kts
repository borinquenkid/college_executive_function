import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.composeCompiler)
}

// Task to ensure a device/emulator is ready
abstract class PrepareEmulatorTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val props = Properties()
        val file = localPropertiesFile.get().asFile
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        
        val sdkDir = props.getProperty("sdk.dir")
        val avdName = props.getProperty("avd.name") ?: "Medium_Phone_API_36.1"
        val adb = "${sdkDir}/platform-tools/adb"
        val emulator = "${sdkDir}/emulator/emulator"
        
        val isDeviceConnected = try {
            execOperations.exec {
                commandLine(adb, "get-state")
                isIgnoreExitValue = true
                standardOutput = OutputStream.nullOutputStream()
                errorOutput = OutputStream.nullOutputStream()
            }.exitValue == 0
        } catch (e: Exception) {
            false
        }

        if (!isDeviceConnected) {
            println("Starting emulator: $avdName...")
            ProcessBuilder(emulator, "-avd", avdName, "-no-snapshot-load").start()
            
            println("Waiting for device to be online...")
            var booted = false
            while (!booted) {
                val output = ByteArrayOutputStream()
                val result = try {
                    execOperations.exec {
                        commandLine(adb, "shell", "getprop", "sys.boot_completed")
                        isIgnoreExitValue = true
                        standardOutput = output
                        errorOutput = OutputStream.nullOutputStream()
                    }.exitValue == 0
                } catch (e: Exception) {
                    false
                }
                
                val isBootCompleted = output.toString().trim() == "1"
                
                if (result && isBootCompleted) {
                    // One more check to ensure it's really responsive and package service is up
                    val ready = try {
                        val checkOutput = ByteArrayOutputStream()
                        execOperations.exec {
                            commandLine(adb, "shell", "service", "check", "package")
                            isIgnoreExitValue = true
                            standardOutput = checkOutput
                            errorOutput = OutputStream.nullOutputStream()
                        }
                        checkOutput.toString().contains("Service package: found")
                    } catch (e: Exception) { false }
                    
                    if (ready) {
                        // The service check might pass while pm is still warming up
                        val pmReady = try {
                            val pmOutput = ByteArrayOutputStream()
                            execOperations.exec {
                                commandLine(adb, "shell", "pm", "list", "packages", "android")
                                isIgnoreExitValue = true
                                standardOutput = pmOutput
                                errorOutput = OutputStream.nullOutputStream()
                            }
                            pmOutput.toString().contains("package:android")
                        } catch (e: Exception) { false }
                        
                        if (pmReady) {
                            println("Package manager is ready. Stabilizing...")
                            Thread.sleep(20000) // 20s for safe measure
                            booted = true
                        }
                    }
                }
                
                if (!booted) {
                    println("Device still booting (sys.boot_completed=${output.toString().trim()})...")
                    Thread.sleep(5000)
                }
            }
            println("Emulator ready.")
        } else {
            println("Device/Emulator already connected.")
        }
    }
}

abstract class RunAppTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val props = Properties()
        val file = localPropertiesFile.get().asFile
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        val sdkDir = props.getProperty("sdk.dir")
        val adb = "${sdkDir}/platform-tools/adb"
        val namespace = "com.borinquenterrier.cef"
        
        println("Launching MainActivity...")
        execOperations.exec {
            commandLine(adb, "shell", "am", "start", "-n", "$namespace/$namespace.MainActivity")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.borinquenterrier.cef"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.borinquenterrier.cef"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = (findProperty("cef.versionName") as String?) ?: "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

val prepareEmulator = tasks.register<PrepareEmulatorTask>("prepareEmulator") {
    group = "application"
    description = "Ensures an Android device or emulator is running."
    localPropertiesFile.set(project.rootProject.layout.projectDirectory.file("local.properties"))
}

// Ensure the installer WAITS for the emulator to be fully ready
tasks.whenTaskAdded {
    if (name == "installDebug") {
        dependsOn(prepareEmulator)
    }
}

tasks.register<RunAppTask>("runApp") {
    group = "application"
    description = "Starts emulator, installs, and launches the app."
    dependsOn("installDebug")
    localPropertiesFile.set(project.rootProject.layout.projectDirectory.file("local.properties"))
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
}
