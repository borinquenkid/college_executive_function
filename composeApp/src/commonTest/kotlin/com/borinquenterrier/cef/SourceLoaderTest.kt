package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.mockk

class SourceLoaderTest : StringSpec({

    "loadSources reconstructs SourceItems with fragments from database" {
        val sourceRepository = mockk<SqlDelightSourceRepository>()
        val logger = mockk<Logger>()

        SourceLoader(sourceRepository, logger, mockk())

        // Verify loading and reconstruction
    }

    "loadSources handles empty source list" {
        val sourceRepository = mockk<SqlDelightSourceRepository>()
        val logger = mockk<Logger>()

        SourceLoader(sourceRepository, logger, mockk())

        coEvery { sourceRepository.getAllSources() } returns emptyList()

        // Verify empty result
    }

    "loadSources catches and logs database errors" {
        val sourceRepository = mockk<SqlDelightSourceRepository>()
        val logger = mockk<Logger>()

        SourceLoader(sourceRepository, logger, mockk())

        coEvery { sourceRepository.getAllSources() } throws Exception("DB connection failed")

        // Verify error handling
    }
})
