package com.borinquenterrier.cef

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Kotlin utility to calculate the CRAP (Change Risk Anti-Patterns) index
 * and generate markdown reports (COVERAGE.md and CRAP.md) at the project root.
 *
 * Can be run directly as a Kotlin Main class.
 */
object CrapIndexReporter {

    private val isModuleDir = !File("composeApp").exists() && File("src/commonMain").exists()

    private val ROOT_DIR = when {
        isModuleDir -> File("..").absoluteFile
        else -> File("..").absoluteFile  // Go up one level from composeApp when running gradle task
    }

    private val SRC_DIR = run {
        val candidate = if (isModuleDir) {
            File("src/commonMain/kotlin/com/borinquenterrier/cef")
        } else {
            File("composeApp/src/commonMain/kotlin/com/borinquenterrier/cef")
        }
        // Fallback: if not found, try relative to current directory
        if (candidate.exists()) candidate else File("src/commonMain/kotlin/com/borinquenterrier/cef")
    }

    private val XML_REPORT = run {
        val candidates = listOf(
            File("composeApp/build/reports/kover/reportJvm.xml"),
            File("build/reports/kover/reportJvm.xml"),
            File("composeApp/build/reports/kover/report.xml"),
            File("build/reports/kover/report.xml")
        )
        candidates.firstOrNull { it.exists() }
            ?: File("composeApp/build/reports/kover/reportJvm.xml")
    }

    private val COVERAGE_MD = File(ROOT_DIR, "COVERAGE.md")
    private val CRAP_MD = File(ROOT_DIR, "CRAP.md")

    private val COMPLEXITY_KEYWORDS = listOf(
        Regex("\\bif\\b"),
        Regex("\\bwhen\\b"),
        Regex("\\bfor\\b"),
        Regex("\\bwhile\\b"),
        Regex("\\bcatch\\b"),
        Regex("\\b&&\\b"),
        Regex("\\b\\|\\|\\b"),
        Regex("\\?\\:"),
        Regex("\\?\\.let\\b"),
        Regex("\\?\\.also\\b"),
        Regex("\\?\\.run\\b"),
        Regex("\\.filter\\b"),
        Regex("\\.map\\b"),
        Regex("\\.forEach\\b"),
        Regex("\\.any\\b"),
        Regex("\\.all\\b")
    )

    data class MethodInfo(val name: String, val complexity: Int)

    data class FileMetrics(
        val filename: String,
        val complexity: Int,
        val lineCoverage: Double,
        val branchCoverage: Double,
        val instructionCoverage: Double,
        val linesCoveredDetail: String,
        val branchesCoveredDetail: String,
        val classes: List<String>,
        val methods: List<MethodInfo>
    ) {
        val crapIndex: Double
            get() = (complexity * complexity) * Math.pow(1 - lineCoverage, 3.0) + complexity

        val riskStatus: String
            get() = when {
                crapIndex > 30 -> "🔴 HIGH"
                crapIndex > 15 -> "🟡 MEDIUM"
                else -> "🟢 LOW"
            }

        val coverageStatus: String
            get() = when {
                lineCoverage >= 0.8 -> "🟢"
                lineCoverage >= 0.5 -> "🟡"
                else -> "🔴"
            }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Starting CRAP and Coverage Report Generation ===")

        if (!SRC_DIR.exists()) {
            println("Error: Source directory ${SRC_DIR.absolutePath} does not exist.")
            return
        }

        val coverageData = parseKoverXmlReport()
        val srcFiles = SRC_DIR.listFiles { file -> file.name.endsWith(".kt") } ?: emptyArray()
        val metricsList = mutableListOf<FileMetrics>()

        for (file in srcFiles) {
            val codeText = file.readText()
            val (methods, classComplexity) = calculateComplexity(codeText)
            val totalComplexity = methods.sumOf { it.complexity } + classComplexity
            val complexity = if (totalComplexity == 0) 1 else totalComplexity

            val cov = coverageData[file.name]
            val metrics = if (cov != null) {
                val lineTotal = cov.lineCovered + cov.lineMissed
                val branchTotal = cov.branchCovered + cov.branchMissed
                val instTotal = cov.instCovered + cov.instMissed

                FileMetrics(
                    filename = file.name,
                    complexity = complexity,
                    lineCoverage = if (lineTotal > 0) cov.lineCovered.toDouble() / lineTotal else 1.0,
                    branchCoverage = if (branchTotal > 0) cov.branchCovered.toDouble() / branchTotal else 1.0,
                    instructionCoverage = if (instTotal > 0) cov.instCovered.toDouble() / instTotal else 1.0,
                    linesCoveredDetail = "${cov.lineCovered}/$lineTotal",
                    branchesCoveredDetail = if (branchTotal > 0) "${cov.branchCovered}/$branchTotal" else "N/A",
                    classes = cov.classNames,
                    methods = methods
                )
            } else {
                FileMetrics(
                    filename = file.name,
                    complexity = complexity,
                    lineCoverage = 0.0,
                    branchCoverage = 0.0,
                    instructionCoverage = 0.0,
                    linesCoveredDetail = "0/0",
                    branchesCoveredDetail = "N/A",
                    classes = emptyList(),
                    methods = methods
                )
            }
            metricsList.add(metrics)
        }

        generateCoverageReport(metricsList)
        generateCrapReport(metricsList)

        println("=== Finished Report Generation ===")
    }

    private class XmlCoverage(
        val classNames: MutableList<String> = mutableListOf(),
        var instCovered: Int = 0,
        var instMissed: Int = 0,
        var lineCovered: Int = 0,
        var lineMissed: Int = 0,
        var branchCovered: Int = 0,
        var branchMissed: Int = 0
    )

    private fun parseKoverXmlReport(): Map<String, XmlCoverage> {
        if (!XML_REPORT.exists()) {
            println("Warning: XML report not found at ${XML_REPORT.absolutePath}. Defaulting to 0% coverage.")
            return emptyMap()
        }

        val result = mutableMapOf<String, XmlCoverage>()
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(XML_REPORT)
            doc.documentElement.normalize()

            val classNodes = doc.getElementsByTagName("class")
            for (i in 0 until classNodes.length) {
                val classElement = classNodes.item(i) as Element
                val sourceFile = classElement.getAttribute("sourcefilename") ?: continue
                val className = classElement.getAttribute("name")?.split("/")?.last() ?: "Unknown"

                val cov = result.getOrPut(sourceFile) { XmlCoverage() }
                cov.classNames.add(className)

                val counterNodes = classElement.getElementsByTagName("counter")
                for (j in 0 until counterNodes.length) {
                    val counter = counterNodes.item(j) as Element
                    val type = counter.getAttribute("type")
                    val missed = counter.getAttribute("missed").toIntOrNull() ?: 0
                    val covered = counter.getAttribute("covered").toIntOrNull() ?: 0

                    when (type) {
                        "INSTRUCTION" -> {
                            cov.instCovered += covered
                            cov.instMissed += missed
                        }

                        "LINE" -> {
                            cov.lineCovered += covered
                            cov.lineMissed += missed
                        }

                        "BRANCH" -> {
                            cov.branchCovered += covered
                            cov.branchMissed += missed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing XML report: ${e.message}")
        }
        return result
    }

    private fun calculateComplexity(codeText: String): Pair<List<MethodInfo>, Int> {
        val methods = mutableListOf<MethodInfo>()
        var classComplexity = 0

        val lines = codeText.split("\n")
        var currentMethodName: String? = null
        var currentMethodText = ""
        var braceCount = 0

        for (line in lines) {
            val stripped = line.trim()
            if (stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*")) {
                continue
            }

            if (line.contains("fun ") && currentMethodName == null) {
                val parts = stripped.split("fun ")
                val funcName = if (parts.size > 1) parts[1].split("(")[0].trim() else "unknown"
                currentMethodName = funcName
                currentMethodText = line
                braceCount = line.count { it == '{' } - line.count { it == '}' }
                if (line.contains("{") && braceCount == 0) {
                    methods.add(
                        MethodInfo(
                            currentMethodName,
                            countComplexityKeywords(currentMethodText) + 1
                        )
                    )
                    currentMethodName = null
                }
                continue
            }

            if (currentMethodName != null) {
                currentMethodText += "\n$line"
                braceCount += line.count { it == '{' } - line.count { it == '}' }
                if (braceCount <= 0) {
                    methods.add(
                        MethodInfo(
                            currentMethodName,
                            countComplexityKeywords(currentMethodText) + 1
                        )
                    )
                    currentMethodName = null
                }
            } else {
                classComplexity += countComplexityKeywords(line)
            }
        }
        return Pair(methods, classComplexity)
    }

    private fun countComplexityKeywords(text: String): Int {
        var count = 0
        for (regex in COMPLEXITY_KEYWORDS) {
            count += regex.findAll(text).count()
        }
        return count
    }

    private fun generateCoverageReport(metricsList: List<FileMetrics>) {
        val sorted = metricsList.sortedBy { it.lineCoverage }
        val totalLinesCovered = metricsList.sumOf {
            val cov = parseKoverXmlReport()[it.filename]
            cov?.lineCovered ?: 0
        }
        val totalLinesMissed = metricsList.sumOf {
            val cov = parseKoverXmlReport()[it.filename]
            cov?.lineMissed ?: 0
        }
        val totalLines = totalLinesCovered + totalLinesMissed
        val overallLineCoverage =
            if (totalLines > 0) (totalLinesCovered.toDouble() / totalLines) * 100.0 else 0.0

        val sb = StringBuilder()
        sb.append("# Code Coverage Report\n\n")
        sb.append("This report displays the **actual test coverage** for all classes in `composeApp/src/commonMain/kotlin`.\n")
        sb.append("Generated using the **JetBrains Kover** plugin after running JVM unit/integration tests.\n\n")

        sb.append("## Overall Metrics\n")
        sb.append(
            "- **Overall Line Coverage**: **${
                String.format(
                    "%.2f",
                    overallLineCoverage
                )
            }%** ($totalLinesCovered/$totalLines lines)\n"
        )
        sb.append("- **Total Source Files**: ${metricsList.size}\n\n")

        sb.append("## Coverage by File\n\n")
        sb.append("| Status | File | Line Coverage | Branch Coverage | Instruction Coverage | Classes |\n")
        sb.append("| :---: | :--- | :---: | :---: | :---: | :--- |\n")

        for (m in sorted) {
            val lineCovStr =
                "${String.format("%.1f", m.lineCoverage * 100)}% (${m.linesCoveredDetail})"
            val branchCovStr = if (m.branchesCoveredDetail != "N/A") "${
                String.format(
                    "%.1f",
                    m.branchCoverage * 100
                )
            }% (${m.branchesCoveredDetail})" else "N/A"
            val classesStr =
                if (m.classes.isNotEmpty()) m.classes.distinct().joinToString(", ") else "*None*"
            sb.append(
                "| ${m.coverageStatus} | ${m.filename} | $lineCovStr | $branchCovStr | ${
                    String.format(
                        "%.1f",
                        m.instructionCoverage * 100
                    )
                }% | $classesStr |\n"
            )
        }

        COVERAGE_MD.writeText(sb.toString())
        println("Generated COVERAGE.md at ${COVERAGE_MD.absolutePath}")
    }

    private fun generateCrapReport(metricsList: List<FileMetrics>) {
        val sorted = metricsList.sortedByDescending { it.crapIndex }
        val highRiskFiles = metricsList.filter { it.crapIndex > 30 }

        val sb = StringBuilder()
        sb.append("# Code Base CRAP Index Analysis\n\n")
        sb.append("This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.\n")
        sb.append("A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.\n\n")

        sb.append("## Heuristics Used\n")
        sb.append("- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.\n")
        sb.append("- **Coverage**: Calculated exactly from Kover's XML test coverage report.\n")
        sb.append("- **Formula**: $\\text{CRAP} = \\text{Complexity}^2 \\times (1 - \\text{Coverage})^3 + \\text{Complexity}$\n\n")

        sb.append("## Overall Summary\n")
        sb.append("- **Total Files Analyzed**: ${metricsList.size}\n")
        sb.append("- **High-Risk Files (CRAP > 30)**: ${highRiskFiles.size}\n\n")

        sb.append("### Top 15 High-Risk Files\n\n")
        sb.append("| File | Complexity | Real Coverage | CRAP Index | Risk Status |\n")
        sb.append("| :--- | :---: | :---: | :---: | :---: |\n")

        for (m in sorted.take(15)) {
            val covStr = "${String.format("%.1f", m.lineCoverage * 100)}%"
            sb.append(
                "| ${m.filename} | ${m.complexity} | $covStr | ${
                    String.format(
                        "%.2f",
                        m.crapIndex
                    )
                } | ${m.riskStatus} |\n"
            )
        }

        sb.append("\n---\n\n")
        sb.append("## Detailed File Breakdown\n\n")

        for (m in sorted) {
            sb.append(
                "### ${m.filename} (Score: ${
                    String.format(
                        "%.2f",
                        m.crapIndex
                    )
                } - ${m.riskStatus})\n"
            )
            sb.append("- **Total Complexity**: ${m.complexity}\n")
            sb.append("- **Real Coverage**: ${String.format("%.1f", m.lineCoverage * 100)}%\n\n")

            if (m.methods.isNotEmpty()) {
                sb.append("#### Methods list:\n")
                sb.append("| Method | Complexity |\n")
                sb.append("| :--- | :---: |\n")
                val sortedMethods = m.methods.sortedByDescending { it.complexity }
                for (func in sortedMethods.take(10)) {
                    sb.append("| `${func.name}` | ${func.complexity} |\n")
                }
                if (sortedMethods.size > 10) {
                    sb.append("| *... and ${sortedMethods.size - 10} more* | |\n")
                }
            }
            sb.append("\n")
        }

        CRAP_MD.writeText(sb.toString())
        println("Generated CRAP.md at ${CRAP_MD.absolutePath}")
    }
}
