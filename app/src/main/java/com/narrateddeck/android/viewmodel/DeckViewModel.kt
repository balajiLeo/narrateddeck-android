package com.narrateddeck.android.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narrateddeck.android.domain.AudioExporter
import com.narrateddeck.android.domain.PptxParseException
import com.narrateddeck.android.domain.PptxParser
import com.narrateddeck.android.domain.SlideNote
import com.narrateddeck.android.domain.TtsNarrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DeckUiState(
    val slides: List<SlideNote> = emptyList(),
    val sourceName: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val exportZipPath: String? = null,
    val isExporting: Boolean = false,
)

/**
 * Coordinates import → review/edit → TTS → export for the MVP flow.
 */
class DeckViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state.asStateFlow()

    private val narrator = TtsNarrator(application)
    private val audioDir: File =
        File(application.cacheDir, "narrated_audio").also { it.mkdirs() }
    private val exportDir: File =
        File(application.cacheDir, "exports").also { it.mkdirs() }

    fun importPptx(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _state.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = "Parsing PPTX…")
            }
            try {
                val slides = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        PptxParser.parse(stream)
                    } ?: throw PptxParseException("Could not open selected file")
                }
                // Clear prior audio cache for a fresh import
                audioDir.listFiles()?.forEach { it.delete() }
                _state.update {
                    DeckUiState(
                        slides = slides,
                        sourceName = displayName ?: "presentation.pptx",
                        statusMessage = "Loaded ${slides.size} slide(s)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to parse PPTX",
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun updateNotes(index: Int, notes: String) {
        _state.update { current ->
            current.copy(
                slides = current.slides.map { slide ->
                    if (slide.index == index) {
                        slide.copy(notes = notes, audioFilePath = null)
                    } else {
                        slide
                    }
                },
                statusMessage = null,
            )
        }
    }

    fun previewSpeak(index: Int) {
        val slide = _state.value.slides.find { it.index == index } ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null, statusMessage = "Speaking slide ${index + 1}…") }
                if (slide.audioFilePath != null && File(slide.audioFilePath).exists()) {
                    narrator.playFile(slide.audioFilePath)
                } else {
                    narrator.speak(slide.notes)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = e.message ?: "TTS preview failed", statusMessage = null)
                }
            }
        }
    }

    fun stopPreview() {
        narrator.stopSpeaking()
        _state.update { it.copy(statusMessage = null) }
    }

    fun regenerateAudio(index: Int) {
        val slide = _state.value.slides.find { it.index == index } ?: return
        if (slide.notes.isBlank()) {
            _state.update { it.copy(errorMessage = "Slide ${index + 1} has no notes to narrate") }
            return
        }
        viewModelScope.launch {
            setGenerating(index, true)
            try {
                val fileName = "slide_%03d.wav".format(index + 1)
                val path = narrator.synthesizeToFile(slide.notes, audioDir, fileName)
                _state.update { current ->
                    current.copy(
                        slides = current.slides.map {
                            if (it.index == index) {
                                it.copy(audioFilePath = path, isGenerating = false)
                            } else {
                                it
                            }
                        },
                        statusMessage = "Generated audio for slide ${index + 1}",
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                setGenerating(index, false)
                _state.update {
                    it.copy(errorMessage = e.message ?: "Audio generation failed")
                }
            }
        }
    }

    fun regenerateAllMissing() {
        val targets = _state.value.slides.filter {
            it.hasNotes && (it.audioFilePath == null || !File(it.audioFilePath).exists())
        }
        targets.forEach { regenerateAudio(it.index) }
    }

    fun exportZip() {
        viewModelScope.launch {
            _state.update {
                it.copy(isExporting = true, errorMessage = null, statusMessage = "Building export zip…")
            }
            try {
                // Ensure audio exists for slides with notes
                val slides = _state.value.slides
                for (slide in slides) {
                    if (slide.hasNotes &&
                        (slide.audioFilePath == null || !File(slide.audioFilePath!!).exists())
                    ) {
                        val fileName = "slide_%03d.wav".format(slide.index + 1)
                        val path = narrator.synthesizeToFile(slide.notes, audioDir, fileName)
                        _state.update { current ->
                            current.copy(
                                slides = current.slides.map {
                                    if (it.index == slide.index) it.copy(audioFilePath = path) else it
                                }
                            )
                        }
                    }
                }
                val result = withContext(Dispatchers.IO) {
                    AudioExporter.export(_state.value.slides, exportDir)
                }
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportZipPath = result.zipFile.absolutePath,
                        statusMessage = "Exported ${result.audioCount} audio file(s) + scripts.txt",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.message ?: "Export failed",
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun clearStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    private fun setGenerating(index: Int, generating: Boolean) {
        _state.update { current ->
            current.copy(
                slides = current.slides.map {
                    if (it.index == index) it.copy(isGenerating = generating) else it
                }
            )
        }
    }

    override fun onCleared() {
        narrator.shutdown()
        super.onCleared()
    }
}
