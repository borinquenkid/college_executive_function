package com.borinquenterrier.cef

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.*

/**
 * Local Test Generator Utility.
 *
 * Automates the following workflow:
 * 1. Navigates code directories under commonMain.
 * 2. Iterates through all Kotlin source files.
 * 3. Finds the corresponding test file name `*Test.kt` in commonTest.
 * 4. If the test file does not exist, creates an empty/templated test file.
 * 5. Reads the source file and enumerates all public methods.
 * 6. Reads the test file and enumerates all existing test cases.
 * 7. Discovers all missing test cases.
 * 8. Queries the local Ollama LLM to generate tests for the missing methods and writes them to the test file.
 */
object LocalTestGenerator {

    private val BASE_DIR: File = run {
        val currentDir = File(".").absoluteFile
        if (File(currentDir, "composeApp").exists()) {
            File(currentDir, "composeApp")
        } else {
            currentDir
        }
    }

    private val SRC_DIR = File(BASE_DIR, "src/commonMain/kotlin/com/borinquenterrier/cef")
    private val TEST_DIR = File(BASE_DIR, "src/commonTest/kotlin/com/borinquenterrier/cef")
    
    private const val OLLAMA_URL = "http://localhost:11434/api/generate"
    private const val DEFAULT_MODEL = "llama3.2:3b" // Lightweight for 16GB RAM constraints

    @JvmStatic
    fun main(args: Array<String>) {
        val modelName = System.getenv("OLLAMA_MODEL") ?: DEFAULT_MODEL
        println("Using Ollama Model: $modelName")

        if (!SRC_DIR.exists()) {
            println("Error: Source directory ${SRC_DIR.absolutePath} does not exist.")
            return
        }

        // 1) It should know which code directories to navigate
        val codeDirs = getCodeDirectories(SRC_DIR)
        println("Discovered ${codeDirs.size} code directories to navigate:")
        codeDirs.forEach { dir ->
            println(" - ${dir.relativeTo(BASE_DIR).path}")
        }

        // Collect all source files across directories
        val allSrcFiles = codeDirs.flatMap { dir ->
            dir.listFiles { file -> file.isFile && file.name.endsWith(".kt") }?.toList() ?: emptyList()
        }
        println("Found ${allSrcFiles.size} Kotlin source files across all directories.")

        val selection: String
        val targetFileArg: String?

        if (args.isNotEmpty()) {
            selection = args[0]
            targetFileArg = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else null
            println("Arguments provided - Selection: $selection, Target: $targetFileArg")
        } else {
            println("\nChoose mode:")
            println("1. Scan & report status of all classes (dry run)")
            println("2. Generate tests for a specific file")
            println("3. Run full auto-generation for all files with missing tests")
            print("Selection (1/2/3): ")
            selection = System.`in`.bufferedReader().readLine()?.trim() ?: "1"
            targetFileArg = null
        }

        when (selection) {
            "1" -> dryRun(codeDirs)
            "2" -> {
                val filename = targetFileArg ?: run {
                    print("Enter the Kotlin source filename or path (e.g. NormalizationService.kt): ")
                    System.`in`.bufferedReader().readLine()?.trim() ?: ""
                }
                val file = allSrcFiles.find { 
                    it.name.equals(filename, ignoreCase = true) || 
                    it.relativeTo(SRC_DIR).path.equals(filename, ignoreCase = true)
                }
                if (file != null) {
                    processFile(file, modelName)
                } else {
                    println("Error: File '$filename' not found.")
                }
            }
            "3" -> {
                println("Starting full auto-generation...")
                // 2) Iterate through the code directories
                for (dir in codeDirs) {
                    println("\nProcessing directory: ${dir.relativeTo(BASE_DIR).path}")
                    val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".kt") } ?: emptyArray()
                    for (file in files) {
                        processFile(file, modelName)
                    }
                }
            }
            else -> println("Invalid selection: $selection")
        }
    }

    /**
     * Recursively walks code directories under baseDir to find any folder containing .kt files.
     */
    private fun getCodeDirectories(baseDir: File): List<File> {
        val directories = mutableListOf<File>()
        fun traverse(dir: File) {
            if (!dir.exists() || !dir.isDirectory) return
            val files = dir.listFiles() ?: return
            var hasKotlinFile = false
            for (f in files) {
                if (f.isDirectory) {
                    traverse(f)
                } else if (f.isFile && f.name.endsWith(".kt")) {
                    hasKotlinFile = true
                }
            }
            if (hasKotlinFile) {
                directories.add(dir)
            }
        }
        traverse(baseDir)
        return directories.sortedBy { it.absolutePath }
    }

    private fun dryRun(codeDirs: List<File>) {
        println("\n--- Codebase Test Coverage Status ---")
        println(String.format("%-45s | %-12s | %-15s | %-15s", "File", "Test Exists", "Public Methods", "Missing Tests"))
        println("-".repeat(95))

        for (dir in codeDirs) {
            val relativeDir = dir.relativeTo(SRC_DIR).path
            val testTargetDir = if (relativeDir.isEmpty()) TEST_DIR else File(TEST_DIR, relativeDir)
            val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".kt") } ?: emptyArray()

            for (file in files) {
                val baseName = file.name.substringBeforeLast(".")
                val testFile = File(testTargetDir, "${baseName}Test.kt")
                val testExists = if (testFile.exists()) "Yes 🟢" else "No 🔴"
                
                val codeText = file.readText()
                val publicMethods = extractPublicMethods(codeText)
                
                val missingCount = if (testFile.exists()) {
                    val testText = testFile.readText()
                    val existingTests = extractExistingTests(testText)
                    val missing = findMissingMethods(publicMethods, existingTests)
                    missing.size.toString()
                } else {
                    publicMethods.size.toString()
                }

                val displayName = if (relativeDir.isEmpty()) file.name else "$relativeDir/${file.name}"
                println(String.format("%-45s | %-12s | %-15s | %-15s", displayName, testExists, publicMethods.size.toString(), missingCount))
            }
        }
    }

    private fun processFile(sourceFile: File, modelName: String) {
        val baseName = sourceFile.name.substringBeforeLast(".")
        val relativeDir = sourceFile.parentFile.relativeTo(SRC_DIR).path
        val testTargetDir = if (relativeDir.isEmpty()) TEST_DIR else File(TEST_DIR, relativeDir)
        
        // 3) Find a file and then attempt to find the corresponding test file name *Test.kt
        val testFile = File(testTargetDir, "${baseName}Test.kt")
        
        println("\nProcessing source file: ${sourceFile.relativeTo(SRC_DIR).path}")
        val codeText = sourceFile.readText()
        val publicMethods = extractPublicMethods(codeText)
        
        if (publicMethods.isEmpty()) {
            println("  -> No public methods found to test. Skipping.")
            return
        }

        // 4) If it does not exist create an empty file
        if (!testFile.exists()) {
            println("  -> Test file does not exist. Creating empty file: ${testFile.relativeTo(BASE_DIR).path}")
            testFile.parentFile.mkdirs()
            testFile.createNewFile()
        }

        // 5) Read the file
        val testText = testFile.readText()

        // 6) Enumerate the tests
        val existingTests = extractExistingTests(testText)

        // 7) Discover all the missing tests
        val missingMethods = findMissingMethods(publicMethods, existingTests)

        if (missingMethods.isEmpty()) {
            println("  -> All public methods are already covered by tests in ${testFile.name}. Skipping.")
            return
        }

        println("  -> Discovered ${missingMethods.size} missing test(s) for methods: ${missingMethods.joinToString(", ")}")
        println("  -> Querying local LLM to generate tests...")

        val prompt = buildPromptForMissingTests(codeText, missingMethods, baseName)
        val response = callOllama(prompt, modelName)

        if (response == null) {
            println("  -> Error: Failed to get response from local Ollama service.")
            return
        }

        // Parse imports from response
        val llmImports = mutableListOf<String>()
        val cleanResponseLines = mutableListOf<String>()
        val lines = response.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("// IMPORT:") || trimmed.startsWith("//IMPORT:")) {
                val imp = trimmed.substringAfter("IMPORT:").trim()
                if (imp.isNotEmpty()) {
                    llmImports.add(imp)
                }
            } else if (trimmed.startsWith("import ")) {
                llmImports.add(trimmed)
            } else {
                cleanResponseLines.add(line)
            }
        }
        val cleanResponse = cleanResponseLines.joinToString("\n")

        val kotlinCode = extractKotlinCode(cleanResponse)
        if (kotlinCode.isBlank()) {
            println("  -> Error: Could not extract Kotlin test blocks from LLM response.")
            return
        }

        println("  -> Writing/appending new tests in the Test file: ${testFile.name}...")
        // 8) Write the missingtests in the Test file.
        writeTestsToFile(testFile, sourceFile, baseName, kotlinCode, llmImports)
        println("  -> Successfully updated ${testFile.name} with new test cases!")
    }

    private fun extractPackageName(sourceText: String): String {
        val packageLine = sourceText.split("\n").find { it.trim().startsWith("package ") }
        return packageLine?.trim()?.substringAfter("package ")?.substringBefore(";")?.trim() ?: "com.borinquenterrier.cef"
    }

    private fun extractPublicMethods(codeText: String): List<String> {
        val methods = mutableListOf<String>()
        val lines = codeText.split("\n")
        for (line in lines) {
            val stripped = line.trim()
            if (stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*") || stripped.startsWith("import ") || stripped.startsWith("package ")) {
                continue
            }
            if (stripped.contains("fun ") && !stripped.contains("private ") && !stripped.contains("internal ") && !stripped.contains("protected ")) {
                val funIdx = stripped.indexOf("fun ")
                if (funIdx != -1) {
                    val afterFun = stripped.substring(funIdx + 4).trim()
                    var nameAndParams = afterFun
                    if (nameAndParams.startsWith("<")) {
                        var depth = 0
                        var closeIndex = -1
                        for (i in nameAndParams.indices) {
                            if (nameAndParams[i] == '<') depth++
                            else if (nameAndParams[i] == '>') {
                                depth--
                                if (depth == 0) {
                                    closeIndex = i
                                    break
                                }
                            }
                        }
                        if (closeIndex != -1) {
                            nameAndParams = nameAndParams.substring(closeIndex + 1).trim()
                        }
                    }
                    val methodName = nameAndParams.substringBefore("(").trim()
                    if (methodName.isNotEmpty() && methodName.all { it.isLetterOrDigit() || it == '_' }) {
                        methods.add(methodName)
                    }
                }
            }
        }
        return methods.distinct()
    }

    private fun extractExistingTests(testCodeText: String): List<String> {
        val tests = mutableListOf<String>()
        val regex = Regex("""test\s*\(\s*"([^"]+)"\s*\)""")
        regex.findAll(testCodeText).forEach { matchResult ->
            tests.add(matchResult.groupValues[1].trim())
        }
        return tests.distinct()
    }

    private fun findMissingMethods(methods: List<String>, existingTests: List<String>): List<String> {
        val missing = mutableListOf<String>()
        for (method in methods) {
            val normalizedMethod = method.lowercase()
            val hasTest = existingTests.any { test ->
                val normalizedTest = test.lowercase().replace(" ", "").replace("_", "")
                normalizedTest.contains(normalizedMethod)
            }
            if (!hasTest) {
                missing.add(method)
            }
        }
        return missing
    }

    private fun buildPromptForMissingTests(classCode: String, missingMethods: List<String>, className: String): String {
        return """
            You are a senior Kotlin developer. Your task is to write Kotest unit tests for the missing methods of the class/file `$className`.
            
            Use the Kotest framework (FunSpec style) and MockK for mocking dependencies.
            
            Here is the source code of the file:
            ```kotlin
            $classCode
            ```
            
            The following methods/functions are currently untested. Please write unit test blocks specifically for them:
            ${missingMethods.joinToString("\n") { "- $it" }}
            
            Rules:
            1. Use the FunSpec test format: `test("description") { ... }`.
            2. For assertions, use Kotest matchers with your actual test variables (e.g. `result shouldBe 42` or `result.shouldNotBeNull()`, or `shouldThrowAny { ... }`). Do NOT use literal placeholders like "actual" or "expected" unless you have declared them as variables in your test code. Do NOT use JUnit/Mockito assertions like `assertNotNull`, `assertEquals`, `assertSame`, `expectThrow`, etc.
            3. Mock any dependencies using MockK (e.g. `val dependency = mockk<Dependency>()`, `coEvery { dependency.call() } returns ...`). Make sure to use lowercase `mockk` rather than `Mockk`.
            4. If a function is a package-level/top-level function (outside any class), call it directly as a regular function (e.g. `createDatabase(mockFactory)` rather than `DriverFactory.createDatabase(mockFactory)`).
            5. Provide ONLY the test cases themselves. Do NOT wrap them in a class or FunSpec header, and do NOT include package declaration.
            6. If you need specific imports for external packages (e.g., SQLDelight's `app.cash.sqldelight.db.SqlDriver` or `com.borinquenterrier.cef.db.AppDatabase`), write them in standard Kotlin import format (e.g. `import app.cash.sqldelight.db.SqlDriver`) or inside a comment like `// IMPORT: app.cash.sqldelight.db.SqlDriver` at the top of your response.
            
            Example of expected output format:
            ```kotlin
            import app.cash.sqldelight.db.SqlDriver

            test("should save event successfully") {
                val db = mockk<AppDatabase>()
                every { db.save() } returns true
                val result = db.save()
                result shouldBe true
            }

            test("should delete event successfully") {
                val db = mockk<AppDatabase>()
                every { db.delete() } returns Unit
                db.delete()
                // test finished successfully
            }
            ```
            
            Do not include any introductory or concluding text, only the raw Kotlin test cases and needed imports.
        """.trimIndent()
    }

    private fun callOllama(prompt: String, model: String): String? {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val jsonRequest = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("stream", false)
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(OLLAMA_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .timeout(Duration.ofMinutes(5))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val jsonResponse = Json.parseToJsonElement(response.body()).jsonObject
                jsonResponse["response"]?.jsonPrimitive?.content
            } else {
                println("Ollama returned HTTP Status Code: ${response.statusCode()}")
                null
            }
        } catch (e: Exception) {
            println("Connection to Ollama failed: ${e.message}")
            null
        }
    }

    private fun extractKotlinCode(response: String): String {
        val startBlock = "```kotlin"
        val endBlock = "```"
        
        if (response.contains(startBlock)) {
            return response.substringAfter(startBlock).substringBefore(endBlock).trim()
        }
        if (response.contains("```")) {
            return response.substringAfter("```").substringBefore("```").trim()
        }
        return response.trim()
    }

    private fun writeTestsToFile(testFile: File, sourceFile: File, baseName: String, newTestsCode: String, llmImports: List<String>) {
        val originalText = testFile.readText().trim()
        val packageName = extractPackageName(sourceFile.readText())

        // Strip the FunSpec class header if LLM accidentally wrapped it
        var cleanTestsCode = newTestsCode.trim()
        if (cleanTestsCode.contains("class ") && cleanTestsCode.contains("FunSpec")) {
            val funSpecIndex = cleanTestsCode.indexOf("FunSpec")
            val firstBrace = cleanTestsCode.indexOf('{', funSpecIndex)
            val lastBrace = cleanTestsCode.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleanTestsCode = cleanTestsCode.substring(firstBrace + 1, lastBrace).trim()
            }
        }

        // Clean up common LLM typos/hallucinations in test code
        cleanTestsCode = cleanTestsCode
            .replace("Mockk<", "mockk<")
            .replace("Mockk(", "mockk(")
            .replace("${baseName}.createDatabase", "createDatabase")
            .replace("DriverFactory.createDatabase", "createDatabase")

        // Collect all imports from the existing file if it's not empty
        val existingImports = mutableSetOf<String>()
        if (originalText.isNotEmpty() && originalText != "// placeholder") {
            val lines = originalText.split("\n")
            for (line in lines) {
                if (line.trim().startsWith("import ")) {
                    existingImports.add(line.trim())
                }
            }
        }

        // Add standard imports and any imports proposed by the LLM
        val standardImports = listOf(
            "import io.kotest.core.spec.style.FunSpec",
            "import io.kotest.matchers.shouldBe",
            "import io.kotest.matchers.nulls.shouldNotBeNull",
            "import io.kotest.assertions.throwables.shouldThrowAny",
            "import io.mockk.*"
        )
        existingImports.addAll(standardImports)
        for (imp in llmImports) {
            var trimmed = imp.trim()
            if (trimmed.contains("javaparser") || trimmed.contains("expectFailure") || trimmed.contains("expectThrow") || trimmed.contains("assertSame") || trimmed.contains("assertNotNull") || trimmed.contains("TemporaryFolder") || trimmed.contains("junit") || trimmed.contains("com.mockk")) {
                continue
            }
            if (trimmed.startsWith("import kotest.")) {
                trimmed = "import io.kotest." + trimmed.substring(14)
            }
            if (trimmed.startsWith("import ")) {
                existingImports.add(trimmed)
            } else if (trimmed.isNotEmpty()) {
                val prefix = if (trimmed.startsWith("kotest.")) "io.kotest." + trimmed.substring(7) else trimmed
                existingImports.add("import $prefix")
            }
        }

        val sortedImports = existingImports.sorted().joinToString("\n")

        if (originalText.isEmpty() || originalText == "// placeholder" || !originalText.contains("class ")) {
            val template = """
                package $packageName

                $sortedImports

                class ${baseName}Test : FunSpec({
                ${cleanTestsCode.prependIndent("    ")}
                })
            """.trimIndent().trim()
            testFile.writeText(template + "\n")
        } else {
            val lines = originalText.split("\n")
            val packageIndex = lines.indexOfFirst { it.trim().startsWith("package ") }
            val classIndex = lines.indexOfFirst { it.contains("class ") }
            
            val restOfClass = if (classIndex != -1) {
                lines.subList(classIndex, lines.size).joinToString("\n")
            } else {
                originalText
            }

            val lastBraceIndex = restOfClass.lastIndexOf('}')
            val updatedRestOfClass = if (lastBraceIndex != -1) {
                val before = restOfClass.substring(0, lastBraceIndex)
                val after = restOfClass.substring(lastBraceIndex)
                buildString {
                    append(before)
                    append("\n")
                    append(cleanTestsCode.prependIndent("    "))
                    append("\n")
                    append(after)
                }
            } else {
                restOfClass + "\n\n" + cleanTestsCode
            }

            val packageLine = if (packageIndex != -1) lines[packageIndex] else "package $packageName"

            val updatedFileText = """
                $packageLine

                $sortedImports

                $updatedRestOfClass
            """.trimIndent().trim()
            testFile.writeText(updatedFileText + "\n")
        }
    }
}
