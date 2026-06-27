import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.composeCompiler)
}

// ── KillEmulatorTask ──────────────────────────────────────────────────────────
// Kills every connected emulator and waits up to 15s for them to vanish from
// `adb devices`.  Safe to call when no emulator is running.

abstract class KillEmulatorTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val props = Properties()
        val file = localPropertiesFile.get().asFile
        if (file.exists()) file.inputStream().use { props.load(it) }
        val sdkDir = props.getProperty("sdk.dir") ?: return
        val adb = "$sdkDir/platform-tools/adb"

        val devicesOut = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(adb, "devices")
            standardOutput = devicesOut
            isIgnoreExitValue = true
            errorOutput = OutputStream.nullOutputStream()
        }

        val emulators = devicesOut.toString()
            .lines()
            .filter { it.startsWith("emulator-") && it.contains("\tdevice") }
            .map { it.trim().split("\\s+".toRegex()).first() }

        if (emulators.isEmpty()) {
            println("No running emulators to kill.")
            return
        }

        emulators.forEach { serial ->
            println("Killing $serial...")
            execOperations.exec {
                commandLine(adb, "-s", serial, "emu", "kill")
                isIgnoreExitValue = true
                standardOutput = OutputStream.nullOutputStream()
                errorOutput = OutputStream.nullOutputStream()
            }
        }

        // Wait up to 15 s for all emulators to disappear from `adb devices`
        for (i in 1..15) {
            Thread.sleep(1000)
            val check = ByteArrayOutputStream()
            execOperations.exec {
                commandLine(adb, "devices")
                standardOutput = check
                isIgnoreExitValue = true
                errorOutput = OutputStream.nullOutputStream()
            }
            val stillRunning = check.toString().lines()
                .any { it.startsWith("emulator-") && it.contains("\tdevice") }
            if (!stillRunning) {
                println("All emulators stopped.")
                return
            }
        }
        println("Warning: some emulators may still be shutting down — proceeding anyway.")
    }
}

// ── SetupAndroid17Task ────────────────────────────────────────────────────────
// Downloads Android 17 (API 37) SDK components and creates the AVD if missing.

abstract class SetupAndroid17Task @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val props = Properties()
        val file = localPropertiesFile.get().asFile
        if (file.exists()) file.inputStream().use { props.load(it) }
        val sdkDir = props.getProperty("sdk.dir") ?: error("sdk.dir not found in local.properties")
        val avdName = props.getProperty("avd.name") ?: "Medium_Phone_API_37.1"

        val sdkManager = listOf(
            "$sdkDir/cmdline-tools/latest/bin/sdkmanager",
            "$sdkDir/cmdline-tools/bin/sdkmanager",
            "$sdkDir/tools/bin/sdkmanager"
        ).firstOrNull { File(it).exists() } ?: error("sdkmanager not found in $sdkDir")

        val avdManager = listOf(
            "$sdkDir/cmdline-tools/latest/bin/avdmanager",
            "$sdkDir/cmdline-tools/bin/avdmanager",
            "$sdkDir/tools/bin/avdmanager"
        ).firstOrNull { File(it).exists() } ?: error("avdmanager not found in $sdkDir")

        val arch = if (System.getProperty("os.arch") == "aarch64") "arm64-v8a" else "x86_64"
        val systemImage = "system-images;android-37;google_apis;$arch"

        println("Checking Android 17 (API 37) components...")

        val platformDir = File("$sdkDir/platforms/android-37")
        if (!platformDir.exists()) {
            println("Installing platforms;android-37 and $systemImage...")
            execOperations.exec { commandLine("bash", "-c", "yes | $sdkManager --licenses") }
            execOperations.exec { commandLine(sdkManager, "--install", "platforms;android-37", systemImage) }
        }

        val avdListOut = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(avdManager, "list", "avd")
            standardOutput = avdListOut
        }
        if (!avdListOut.toString().contains(avdName)) {
            println("Creating AVD: $avdName...")
            execOperations.exec {
                commandLine("bash", "-c", "echo no | $avdManager create avd -n $avdName -k '$systemImage' --force")
            }
        } else {
            println("AVD $avdName already exists.")
        }
    }
}

// ── PrepareEmulatorTask ───────────────────────────────────────────────────────
// Ensures an emulator is running and fully booted before the install step.
//
// Behaviour:
//  - If a device is already connected AND responds to adb within 5s  → wake the
//    screen and return immediately (no restart).
//  - If a device is connected but unresponsive (hanging)              → kill it
//    first, then start a fresh one.
//  - If no device is connected                                        → start a
//    fresh emulator.
//
// AVD selection: uses avd.name from local.properties when that AVD actually
// exists; otherwise falls back to the first AVD returned by `emulator -list-avds`
// so a stale name in local.properties never blocks the build.

abstract class PrepareEmulatorTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val props = Properties()
        val file = localPropertiesFile.get().asFile
        if (file.exists()) file.inputStream().use { props.load(it) }

        val sdkDir = props.getProperty("sdk.dir") ?: error("sdk.dir not in local.properties")
        val adb = "$sdkDir/platform-tools/adb"
        val emulatorBin = "$sdkDir/emulator/emulator"

        // Resolve AVD: prefer configured name if it actually exists, else first available
        val configuredAvd = props.getProperty("avd.name") ?: ""
        val availableAvds = listAvds(emulatorBin)
        val avdName = when {
            configuredAvd.isNotBlank() && availableAvds.contains(configuredAvd) -> configuredAvd
            availableAvds.isNotEmpty() -> availableAvds.first().also {
                println("Configured AVD '$configuredAvd' not found — using '$it'.")
            }
            else -> error("No AVDs found. Run setupAndroid17 first or create one in Android Studio.")
        }

        val isConnected = isAdbDeviceConnected(adb)
        if (isConnected) {
            if (isEmulatorResponsive(adb)) {
                println("Emulator already running and responsive — waking screen.")
                wakeScreen(adb)
                return
            }
            println("Emulator connected but unresponsive — killing before restart.")
            killAllEmulators(adb)
        }

        println("Starting emulator: $avdName")
        ProcessBuilder(emulatorBin, "-avd", avdName, "-no-snapshot-load", "-no-boot-anim")
            .inheritIO()
            .start()

        println("Waiting for emulator to boot (up to 5 min)...")
        var booted = false
        for (attempt in 1..60) {
            Thread.sleep(5000)

            val bootOut = ByteArrayOutputStream()
            val adbOk = try {
                execOperations.exec {
                    commandLine(adb, "shell", "getprop", "sys.boot_completed")
                    isIgnoreExitValue = true
                    standardOutput = bootOut
                    errorOutput = OutputStream.nullOutputStream()
                }.exitValue == 0
            } catch (e: Exception) { false }

            if (adbOk && bootOut.toString().trim() == "1") {
                // Verify the package manager is up before declaring ready
                val pmOut = ByteArrayOutputStream()
                execOperations.exec {
                    commandLine(adb, "shell", "pm", "list", "packages", "android")
                    isIgnoreExitValue = true
                    standardOutput = pmOut
                    errorOutput = OutputStream.nullOutputStream()
                }
                if (pmOut.toString().contains("package:android")) {
                    println("Emulator ready (attempt $attempt).")
                    booted = true
                    break
                }
            }
            println("Still booting... ($attempt/60)")
        }

        if (!booted) error("Emulator failed to boot within 5 minutes.")
        wakeScreen(adb)
    }

    /** Lists available AVDs by shelling out to the emulator binary. */
    private fun listAvds(emulatorBin: String): List<String> = try {
        ProcessBuilder(emulatorBin, "-list-avds")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readLines()
            .filter { it.isNotBlank() }
    } catch (e: Exception) { emptyList() }

    /** True if `adb get-state` returns exit 0 (at least one device in "device" state). */
    private fun isAdbDeviceConnected(adb: String): Boolean = try {
        execOperations.exec {
            commandLine(adb, "get-state")
            isIgnoreExitValue = true
            standardOutput = OutputStream.nullOutputStream()
            errorOutput = OutputStream.nullOutputStream()
        }.exitValue == 0
    } catch (e: Exception) { false }

    /**
     * True if the device responds to `adb shell getprop sys.boot_completed` within 5 s
     * and reports that boot is complete.  Uses ProcessBuilder (not execOperations) so we
     * can apply a timeout without blocking indefinitely on a hung device.
     */
    private fun isEmulatorResponsive(adb: String): Boolean = try {
        val proc = ProcessBuilder(adb, "shell", "getprop", "sys.boot_completed")
            .redirectErrorStream(true)
            .start()
        val done = proc.waitFor(5, TimeUnit.SECONDS)
        if (!done) { proc.destroyForcibly(); false }
        else proc.inputStream.bufferedReader().readText().trim() == "1"
    } catch (e: Exception) { false }

    /** Sends `emu kill` to every connected emulator and waits briefly for them to stop. */
    private fun killAllEmulators(adb: String) {
        val out = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(adb, "devices")
            standardOutput = out
            isIgnoreExitValue = true
            errorOutput = OutputStream.nullOutputStream()
        }
        out.toString().lines()
            .filter { it.startsWith("emulator-") && it.contains("\tdevice") }
            .map { it.trim().split("\\s+".toRegex()).first() }
            .forEach { serial ->
                execOperations.exec {
                    commandLine(adb, "-s", serial, "emu", "kill")
                    isIgnoreExitValue = true
                    standardOutput = OutputStream.nullOutputStream()
                    errorOutput = OutputStream.nullOutputStream()
                }
            }
        Thread.sleep(3000)
    }

    /** Wakes the emulator screen so the launched app is immediately visible. */
    private fun wakeScreen(adb: String) {
        listOf("KEYCODE_WAKEUP", "KEYCODE_MENU").forEach { key ->
            execOperations.exec {
                commandLine(adb, "shell", "input", "keyevent", key)
                isIgnoreExitValue = true
                standardOutput = OutputStream.nullOutputStream()
                errorOutput = OutputStream.nullOutputStream()
            }
        }
    }
}

// ── RunAppTask ────────────────────────────────────────────────────────────────

abstract class RunAppTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @TaskAction
    fun run() {
        val props = Properties()
        val file = localPropertiesFile.get().asFile
        if (file.exists()) file.inputStream().use { props.load(it) }
        val sdkDir = props.getProperty("sdk.dir")
        val adb = "$sdkDir/platform-tools/adb"
        val namespace = "com.borinquenterrier.cef"

        println("Launching MainActivity...")
        execOperations.exec {
            commandLine(adb, "shell", "am", "start", "-n", "$namespace/$namespace.MainActivity")
        }
    }
}

// ── Kotlin / Android config ───────────────────────────────────────────────────

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

    buildFeatures { compose = true }

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

// ── Task registrations ────────────────────────────────────────────────────────

val localProps = project.rootProject.layout.projectDirectory.file("local.properties")

val setupAndroid17 = tasks.register<SetupAndroid17Task>("setupAndroid17") {
    group = "application"
    description = "Downloads Android 17 SDK components and creates the AVD if missing."
    localPropertiesFile.set(localProps)
}

val prepareEmulator = tasks.register<PrepareEmulatorTask>("prepareEmulator") {
    group = "application"
    description = "Ensures an Android emulator is running and fully booted."
    dependsOn(setupAndroid17)
    localPropertiesFile.set(localProps)
}

val killEmulator = tasks.register<KillEmulatorTask>("killEmulator") {
    group = "application"
    description = "Kill all running emulators and wait for them to stop."
    localPropertiesFile.set(localProps)
}

// installDebug must wait for the emulator to be ready
tasks.whenTaskAdded {
    if (name == "installDebug") {
        dependsOn(prepareEmulator)
    }
}

// prepareEmulator must run after killEmulator when both are in the graph
// (this applies when restartAndRun pulls both in)
tasks.named("prepareEmulator") {
    mustRunAfter(killEmulator)
}

tasks.register<RunAppTask>("runApp") {
    group = "application"
    description = "Build, install, and launch the app (starts emulator if needed)."
    dependsOn("installDebug")
    localPropertiesFile.set(localProps)
}

tasks.register("restartAndRun") {
    group = "application"
    description = "Kill any running emulator, restart fresh, build, install, and launch the app."
    dependsOn(killEmulator, "runApp")
}

// ── Dependencies ──────────────────────────────────────────────────────────────

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
}
