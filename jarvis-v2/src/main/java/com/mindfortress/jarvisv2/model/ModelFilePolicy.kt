package com.mindfortress.jarvisv2.model

object ModelFilePolicy {
    const val MINIMUM_MODEL_BYTES: Long = 100L * 1024L * 1024L

    fun isSupportedFileName(name: String?): Boolean =
        name?.trim()?.endsWith(".litertlm", ignoreCase = true) == true

    fun isPlausibleModelFile(name: String?, sizeBytes: Long): Boolean =
        isSupportedFileName(name) && sizeBytes >= MINIMUM_MODEL_BYTES
}
