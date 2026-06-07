package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import okio.Path.Companion.toPath

class ModelManagerTest : FunSpec({
    test("getModelFile returns correct path") {
        val manager = ModelManager(mockk(), "/tmp/models")
        manager.getModelFile() shouldBe "/tmp/models/Qwen3.5-9B-Q4_K_M.gguf".toPath()
    }

    test("isModelDownloaded returns false when file does not exist") {
        val manager = ModelManager(mockk(), "/tmp/non_existent_dir_random_12345")
        manager.isModelDownloaded() shouldBe false
    }
})
