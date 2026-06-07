package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec

class CrapIndexReportTest : FunSpec({
    test("Generate coverage and CRAP reports") {
        CrapIndexReporter.main(emptyArray())
    }
})
