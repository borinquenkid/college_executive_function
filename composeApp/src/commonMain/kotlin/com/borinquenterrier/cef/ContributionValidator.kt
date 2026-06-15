package com.borinquenterrier.cef

object ContributionValidator {

    // Mirrors the POISON_PATTERNS in scripts/validate_contributions.py.
    // Applied to extracted text *before* sending to AI to prevent prompt injection.
    private val POISON_PATTERNS: List<Pair<String, Regex>> = listOf(
        // Command/Shell Injection
        "command substitution \$(…)"       to Regex("""\$\(.*\)"""),
        "backtick execution"               to Regex("""`[^`]+`"""),
        "rm -rf"                           to Regex("""\brm\s+-rf\b""", RegexOption.IGNORE_CASE),
        "sudo"                             to Regex("""\bsudo\s+""", RegexOption.IGNORE_CASE),
        "chmod/chown"                      to Regex("""\b(chmod|chown)\s+""", RegexOption.IGNORE_CASE),
        "curl/wget"                        to Regex("""\b(curl|wget)\b""", RegexOption.IGNORE_CASE),
        "shell command with flags"         to Regex("""\b(sh|bash|python|perl|eval|exec)\b\s+-[a-zA-Z0-9]"""),
        "pipe into shell"                  to Regex("""\|\s*(sh|bash|python|perl|eval|exec)\b""", RegexOption.IGNORE_CASE),
        // Script & HTML/JS Injection
        "<script> tag"                     to Regex("""<script\b[^>]*>""", RegexOption.IGNORE_CASE),
        "javascript: URL"                  to Regex("""\bjavascript:""", RegexOption.IGNORE_CASE),
        "onload handler"                   to Regex("""\bonload\s*=""", RegexOption.IGNORE_CASE),
        "onerror handler"                  to Regex("""\bonerror\s*=""", RegexOption.IGNORE_CASE),
        "<iframe>"                         to Regex("""<\s*iframe\b""", RegexOption.IGNORE_CASE),
        "<object/embed/applet>"            to Regex("""<\s*(object|embed|applet)\b""", RegexOption.IGNORE_CASE),
        // Path Traversal
        "Unix path traversal"              to Regex("""\.\./"""),
        "Windows path traversal"           to Regex("""\.\.\\"""),
        // Dangerous SQL
        "DROP TABLE/DATABASE/VIEW"         to Regex("""\bDROP\s+(TABLE|DATABASE|VIEW)\b""", RegexOption.IGNORE_CASE),
        "DELETE FROM"                      to Regex("""\bDELETE\s+FROM\b""", RegexOption.IGNORE_CASE),
        "UNION SELECT"                     to Regex("""\bUNION\s+(ALL\s+)?SELECT\b""", RegexOption.IGNORE_CASE),
        "INSERT INTO"                      to Regex("""\bINSERT\s+INTO\b""", RegexOption.IGNORE_CASE),
        "UPDATE … SET"                     to Regex("""\bUPDATE\s+\w+\s+SET\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * Scans [fragments] for dangerous content that could exploit the AI or the pipeline.
     * Throws [ContributionPoisonException] on the first match found.
     */
    fun validate(fragments: List<SourceFragment>) {
        val combined = fragments.joinToString("\n") { it.text }
        for ((label, pattern) in POISON_PATTERNS) {
            val match = pattern.find(combined) ?: continue
            val snippet = combined.substring(
                maxOf(0, match.range.first - 20),
                minOf(combined.length, match.range.last + 40)
            ).replace('\n', ' ')
            throw ContributionPoisonException(label, snippet)
        }
    }
}

class ContributionPoisonException(val label: String, val snippet: String) : Exception(
    "Source contains forbidden pattern '$label' near: \"$snippet\""
)
