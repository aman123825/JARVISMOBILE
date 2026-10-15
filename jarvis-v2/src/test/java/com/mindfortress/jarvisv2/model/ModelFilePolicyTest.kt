package com.mindfortress.jarvisv2.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFilePolicyTest {
    @Test
    fun acceptsExportedLiteRtLmFile() {
        assertTrue(ModelFilePolicy.isSupportedFileName("gemma-4-E2B-it.litertlm"))
        assertTrue(ModelFilePolicy.isSupportedFileName("JARVIS.LITERTLM"))
    }

    @Test
    fun rejectsTrainingCheckpointsAndAdapters() {
        assertFalse(ModelFilePolicy.isSupportedFileName("adapter_model.safetensors"))
        assertFalse(ModelFilePolicy.isSupportedFileName("checkpoint-3950.zip"))
        assertFalse(ModelFilePolicy.isSupportedFileName("model.gguf"))
        assertFalse(ModelFilePolicy.isSupportedFileName(null))
    }

    @Test
    fun requiresPlausibleFileSize() {
        assertTrue(
            ModelFilePolicy.isPlausibleModelFile(
                "model.litertlm",
                ModelFilePolicy.MINIMUM_MODEL_BYTES
            )
        )
        assertFalse(
            ModelFilePolicy.isPlausibleModelFile(
                "model.litertlm",
                ModelFilePolicy.MINIMUM_MODEL_BYTES - 1
            )
        )
        assertFalse(
            ModelFilePolicy.isPlausibleModelFile(
                "adapter_model.safetensors",
                900_000_000L
            )
        )
    }
}
