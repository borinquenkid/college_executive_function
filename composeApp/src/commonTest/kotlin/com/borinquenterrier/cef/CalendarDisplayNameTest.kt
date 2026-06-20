package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CalendarDisplayNameTest : FunSpec({

    test("default calendar id resolves to the default label") {
        CalendarDisplayName.resolve("default", "anything") shouldBe "CEF Academic (Default)"
    }

    test("non-default id returns the provided calendar name") {
        CalendarDisplayName.resolve("abc123", "My Study Calendar") shouldBe "My Study Calendar"
    }

    test("empty string id is not treated as default") {
        CalendarDisplayName.resolve("", "My Calendar") shouldBe "My Calendar"
    }

    test("default label contains 'Default'") {
        CalendarDisplayName.resolve("default", "") shouldContain "Default"
    }
})
