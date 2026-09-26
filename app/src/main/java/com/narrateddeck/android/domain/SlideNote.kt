package com.narrateddeck.android.domain

/**
 * One slide from a PPTX with optional speaker notes and generated audio path.
 */
data class SlideNote(
    val index: Int,
    val titleHint: String,
    val notes: String,
    val audioFilePath: String? = null,
    val isGenerating: Boolean = false,
) {
    val displayTitle: String
        get() = titleHint.ifBlank { "Slide ${index + 1}" }

    val hasNotes: Boolean
        get() = notes.isNotBlank()
}
