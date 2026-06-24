import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
        val avdName = props.getProperty("avd.name") ?: "Medium_Phone_API_37.1"
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
            var attempts = 0
            while (!booted && attempts < 60) {
                attempts++
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
            if (booted) {
                println("Emulator ready.")
            } else {
                error("Emulator failed to boot in time.")
            }
        } else {
            println("Device/Emulator already connected.")
        }
    }
}

// Task to download and setup Android 17 components
abstract class SetupAndroid17Task @Inject constructor(
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
        val sdkDir = props.getProperty("sdk.dir") ?: error("sdk.dir not found in local.properties")
        val avdName = props.getProperty("avd.name") ?: "Medium_Phone_API_37.1"
        
        val sdkManager = listOf(
            "${sdkDir}/cmdline-tools/latest/bin/sdkmanager",
            "${sdkDir}/cmdline-tools/bin/sdkmanager",
            "${sdkDir}/tools/bin/sdkmanager"
        ).firstOrNull { File(it).exists() } ?: error("sdkmanager not found in $sdkDir")

        val avdManager = listOf(
            "${sdkDir}/cmdline-tools/latest/bin/avdmanager",
            "${sdkDir}/cmdline-tools/bin/avdmanager",
            "${sdkDir}/tools/bin/avdmanager"
        ).firstOrNull { File(it).exists() } ?: error("avdmanager not found in $sdkDir")

        val arch = if (System.getProperty("os.arch") == "aarch64") "arm64-v8a" else "x86_64"
        val systemImage = "system-images;android-37;google_apis;$arch"

        println("Checking Android 17 (API 37) components...")
        
        // Fast check for platform
        val platformDir = File("$sdkDir/platforms/android-37")
        if (!platformDir.exists()) {
            println("Installing platforms;android-37 and $systemImage...")
            execOperations.exec {
                commandLine("bash", "-c", "yes | $sdkManager --licenses")
            }
            execOperations.exec {
                commandLine(sdkManager, "--install", "platforms;android-37", systemImage)
            }
        }

        // Check if AVD exists
        val avdListOutput = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(avdManager, "list", "avd")
            standardOutput = avdListOutput
        }
        
        if (!avdListOutput.toString().contains(avdName)) {
            println("Creating AVD: $avdName...")
            execOperations.exec {
                commandLine("bash", "-c", "echo no | $avdManager create avd -n $avdName -k '$systemImage' --force")
            }
        } else {
            println("AVD $avdName already exists.")
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

val setupAndroid17 = tasks.register<SetupAndroid17Task>("setupAndroid17") {
    group = "application"
    description = "Downloads Android 17 SDK components and creates the AVD if missing."
    localPropertiesFile.set(project.rootProject.layout.projectDirectory.file("local.properties"))
}

val prepareEmulator = tasks.register<PrepareEmulatorTask>("prepareEmulator") {
    group = "application"
    description = "Ensures an Android device or emulator is running."
    dependsOn(setupAndroid17)
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
