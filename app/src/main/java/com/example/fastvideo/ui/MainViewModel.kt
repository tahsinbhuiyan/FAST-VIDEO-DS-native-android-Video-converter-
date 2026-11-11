package com.example.fastvideo.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastvideo.data.ConversionHistoryManager
import com.example.fastvideo.decoder.LocalVideoProcessor
import com.example.fastvideo.device.DeviceDiagnostics
import com.example.fastvideo.device.DeviceDiagnosticsReport
import com.example.fastvideo.format.FvFormat
import com.example.fastvideo.format.FvValidationReport
import com.example.fastvideo.model.AuditCheckItem
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.model.ConversionPreset
import com.example.fastvideo.model.ConversionSettings
import com.example.fastvideo.model.ConversionSpeed
import com.example.fastvideo.model.FitMode
import com.example.fastvideo.model.FvStrictAudit
import com.example.fastvideo.model.JobStatus
import com.example.fastvideo.model.SourceVideoMeta
import com.example.fastvideo.player.FvPlayerDecoder
import com.example.fastvideo.service.ConversionForegroundService
import com.example.fastvideo.service.ConversionManager
import com.example.fastvideo.util.ApkExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

enum class AppTab(val title: String) {
    CONVERTER("Converter"),
    HISTORY("History"),
    EMULATOR("DS Emulator"),
    VALIDATOR("Validator"),
    HELP("Guide")
}

data class MainUiState(
    val currentTab: AppTab = AppTab.CONVERTER,
    val diagnostics: DeviceDiagnosticsReport? = null,
    val selectedSource: SourceVideoMeta? = null,
    val selectedBatchSources: List<SourceVideoMeta> = emptyList(),
    val settings: ConversionSettings = ConversionSettings(),
    val activeJob: ConversionJob? = null,
    val queue: List<ConversionJob> = emptyList(),
    val history: List<ConversionJob> = emptyList(),
    val showSetupDialog: Boolean = false,
    val userMessage: String? = null,
    val apkExportResult: ApkExporter.ExportResult? = null,

    // Batch progress tracking
    val batchTotalCount: Int = 0,
    val batchCompletedCount: Int = 0,

    // Strict after-convert inspection audit
    val lastCompletedAudit: FvStrictAudit? = null,
    val lastCompletedJob: ConversionJob? = null,
    val showStrictAuditDialog: Boolean = false,

    // DS Emulator state
    val emulatorJob: ConversionJob? = null,
    val emulatorFrame: FvPlayerDecoder.DecodedFrameResult? = null,
    val isEmulatorPlaying: Boolean = false,
    val emulatorFps: Double = 20.0,
    val emulatorAudioLevelL: Float = 0.05f,
    val emulatorAudioLevelR: Float = 0.05f,

    // Standalone validator inspection state
    val inspectionReport: FvValidationReport? = null
) {
    val toastMessage: String? get() = userMessage
    val selectedVideo: SourceVideoMeta? get() = selectedSource
    val showSetupSheet: Boolean get() = showSetupDialog
    val validationResult: FvValidationReport? get() = inspectionReport
    val deviceSpecs: DeviceDiagnosticsReport? get() = diagnostics
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val historyManager = ConversionHistoryManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            settings = historyManager.getSavedSettings(),
            history = historyManager.getHistory()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var playerDecoder: FvPlayerDecoder? = null
    private var emulatorPlaybackJob: Job? = null

    init {
        refreshDiagnostics()

        viewModelScope.launch {
            ConversionManager.activeJob.collect { active ->
                _uiState.update { it.copy(activeJob = active) }
            }
        }
        viewModelScope.launch {
            ConversionManager.queue.collect { q ->
                _uiState.update { it.copy(queue = q) }
            }
        }
        viewModelScope.launch {
            ConversionManager.batchTotalCount.collect { total ->
                _uiState.update { it.copy(batchTotalCount = total) }
            }
        }
        viewModelScope.launch {
            ConversionManager.batchCompletedCount.collect { comp ->
                _uiState.update { it.copy(batchCompletedCount = comp) }
            }
        }
        viewModelScope.launch {
            ConversionManager.lastCompletedAudit.collect { pair ->
                if (pair != null) {
                    val (job, audit) = pair
                    _uiState.update {
                        it.copy(
                            lastCompletedJob = job,
                            lastCompletedAudit = audit,
                            showStrictAuditDialog = true,
                            history = historyManager.getHistory()
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            ConversionManager.eventMessages.collect { msg ->
                showMessage(msg)
            }
        }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val diag = DeviceDiagnostics.collect(getApplication())
            _uiState.update { it.copy(diagnostics = diag) }
        }
    }

    fun selectSourceVideo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val meta = LocalVideoProcessor.extractMetadata(getApplication(), uri)
            if (meta != null) {
                _uiState.update {
                    it.copy(
                        selectedSource = meta,
                        selectedBatchSources = emptyList(),
                        showSetupDialog = true
                    )
                }
            } else {
                showMessage("Could not read video metadata. Ensure file is not DRM-protected.")
            }
        }
    }

    fun selectMultipleSourceVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<SourceVideoMeta>()
            for (uri in uris) {
                val meta = LocalVideoProcessor.extractMetadata(getApplication(), uri)
                if (meta != null) {
                    list.add(meta)
                }
            }
            if (list.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        selectedBatchSources = list,
                        selectedSource = list.first(),
                        showSetupDialog = true
                    )
                }
                showMessage("Selected ${list.size} videos for batch conversion.")
            } else {
                showMessage("Could not read metadata for selected videos.")
            }
        }
    }

    fun startRetroIntroSampleConversion() {
        val sampleMeta = SourceVideoMeta(
            uri = "sample://retro_ds_intro",
            name = "Nintendo_DS_Retro_Intro.mp4",
            sizeBytes = 2 * 1024 * 1024L,
            durationMs = 5000L,
            width = 256,
            height = 144,
            fps = 20.0,
            hasAudio = true,
            audioSampleRate = 47605,
            audioChannels = 2
        )
        _uiState.update {
            it.copy(
                selectedSource = sampleMeta,
                selectedBatchSources = emptyList(),
                showSetupDialog = true
            )
        }
    }

    fun start24MinMovieTestRun() {
        val movieMeta = SourceVideoMeta(
            uri = "sample://24min_movie_test",
            name = "24_Min_Full_Movie_Benchmark.mp4",
            sizeBytes = 450 * 1024 * 1024L,
            durationMs = 24 * 60 * 1000L, // 24:00 (1,440,000 ms = 28,800 frames)
            width = 256,
            height = 144,
            fps = 20.0,
            hasAudio = true,
            audioSampleRate = 32000,
            audioChannels = 2
        )
        _uiState.update {
            it.copy(
                selectedSource = movieMeta,
                selectedBatchSources = emptyList(),
                showSetupDialog = true
            )
        }
    }

    fun updateSettings(newSettings: ConversionSettings) {
        historyManager.saveSettings(newSettings)
        _uiState.update { it.copy(settings = newSettings) }
    }

    fun dismissSetupDialog() {
        _uiState.update {
            it.copy(
                showSetupDialog = false,
                selectedBatchSources = emptyList()
            )
        }
    }

    fun startConversionFromDialog() {
        val sources = if (_uiState.value.selectedBatchSources.isNotEmpty()) {
            _uiState.value.selectedBatchSources
        } else if (_uiState.value.selectedSource != null) {
            listOf(_uiState.value.selectedSource!!)
        } else {
            return
        }

        val currentSettings = _uiState.value.settings
        _uiState.update {
            it.copy(
                showSetupDialog = false,
                selectedBatchSources = emptyList()
            )
        }

        val newJobs = sources.map { src ->
            val durationMs = if (src.durationMs > 0) src.durationMs else 5000L
            val frames = ((durationMs / 1000.0) * currentSettings.fps).toInt().coerceAtLeast(1)
            ConversionJob(
                id = UUID.randomUUID().toString(),
                sourceMeta = src,
                settings = currentSettings,
                status = JobStatus.PENDING,
                totalFrames = frames
            )
        }

        ConversionManager.enqueueJobs(getApplication(), newJobs)
    }

    fun cancelActiveConversion() {
        ConversionManager.cancelActiveJob(getApplication())
    }

    fun clearQueue() {
        ConversionManager.clearQueue()
    }

    fun cancelQueuedJob(id: String) {
        ConversionManager.cancelQueuedJob(id)
    }

    fun dismissAuditDialog() {
        ConversionManager.clearAuditModal()
        _uiState.update { it.copy(showStrictAuditDialog = false) }
    }

    fun openAuditForJob(job: ConversionJob) {
        val file = File(job.outputFilePath)
        if (file.exists()) {
            val audit = job.strictAudit ?: FvFormat.runStrictPostConvertAudit(
                file = file,
                expectedSettings = job.settings,
                sourceDurationMs = job.sourceMeta.durationMs,
                expectedFrames = job.totalFrames
            )
            _uiState.update {
                it.copy(
                    lastCompletedJob = job,
                    lastCompletedAudit = audit,
                    showStrictAuditDialog = true
                )
            }
        } else {
            showMessage("File does not exist.")
        }
    }

    fun deleteJob(jobId: String) {
        historyManager.deleteJob(jobId)
        _uiState.update { it.copy(history = historyManager.getHistory()) }
        showMessage("Item removed.")
    }

    fun clearAllHistory() {
        historyManager.clearHistory()
        _uiState.update { it.copy(history = emptyList()) }
        showMessage("History cleared.")
    }

    /**
     * One-Tap APK Download / Export directly to user's device Downloads/Files.
     */
    fun exportApkOneTap() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApkExporter.exportApkToDownloads(getApplication())
            _uiState.update { it.copy(apkExportResult = result) }
            showMessage(result.message)
        }
    }

    fun shareApk() {
        ApkExporter.shareApk(getApplication())
    }

    fun saveFvToDownloads(job: ConversionJob) {
        viewModelScope.launch(Dispatchers.IO) {
            val srcFile = File(job.outputFilePath)
            if (!srcFile.exists()) {
                showMessage("Source .fv file not found on device.")
                return@launch
            }

            try {
                val fileName = srcFile.name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = getApplication<Application>().contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(srcFile).use { it.copyTo(out) }
                        }
                        showMessage("Saved $fileName to Downloads folder!")
                    }
                } else {
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val target = File(downloads, fileName)
                    FileInputStream(srcFile).use { inStream ->
                        FileOutputStream(target).use { inStream.copyTo(it) }
                    }
                    showMessage("Saved $fileName to ${target.absolutePath}!")
                }
            } catch (e: Exception) {
                showMessage("Failed to save to Downloads: ${e.message}")
            }
        }
    }

    fun shareFvFile(job: ConversionJob) {
        val srcFile = File(job.outputFilePath)
        if (!srcFile.exists()) {
            showMessage("File does not exist.")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                srcFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, srcFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share .fv Video for Nintendo DS")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            showMessage("Could not share file: ${e.message}")
        }
    }

    // DS Emulator Controls with real-time AudioTrack and buttery smooth video pacing
    fun openInDsEmulator(job: ConversionJob) {
        val file = File(job.outputFilePath)
        if (!file.exists()) {
            showMessage(".fv file does not exist.")
            return
        }

        playerDecoder?.close()
        val decoder = FvPlayerDecoder(file)
        if (decoder.open()) {
            playerDecoder = decoder
            val firstFrame = decoder.decodeNextFrame()
            _uiState.update {
                it.copy(
                    currentTab = AppTab.EMULATOR,
                    emulatorJob = job,
                    emulatorFrame = firstFrame,
                    emulatorFps = decoder.fps,
                    isEmulatorPlaying = false,
                    emulatorAudioLevelL = firstFrame?.audioLevelL ?: 0.05f,
                    emulatorAudioLevelR = firstFrame?.audioLevelR ?: 0.05f
                )
            }
        } else {
            showMessage("Could not open .fv video in DS Emulator.")
        }
    }

    fun toggleEmulatorPlay() {
        val isPlaying = _uiState.value.isEmulatorPlaying
        if (isPlaying) {
            pauseEmulator()
        } else {
            playEmulator()
        }
    }

    private fun playEmulator() {
        val decoder = playerDecoder ?: return
        _uiState.update { it.copy(isEmulatorPlaying = true) }
        decoder.playAudio()
        emulatorPlaybackJob?.cancel()
        emulatorPlaybackJob = viewModelScope.launch(Dispatchers.IO) {
            val frameDelay = (1000.0 / decoder.fps).toLong().coerceIn(16L, 100L)
            var nextFrameTime = System.currentTimeMillis()
            while (isActive && _uiState.value.isEmulatorPlaying) {
                val frame = decoder.decodeNextFrame()
                if (frame != null) {
                    _uiState.update {
                        it.copy(
                            emulatorFrame = frame,
                            emulatorAudioLevelL = frame.audioLevelL,
                            emulatorAudioLevelR = frame.audioLevelR
                        )
                    }
                } else {
                    // Loop back to start
                    decoder.seekToKeyframe(0)
                    nextFrameTime = System.currentTimeMillis()
                }

                nextFrameTime += frameDelay
                val sleepTime = nextFrameTime - System.currentTimeMillis()
                if (sleepTime > 0) {
                    delay(sleepTime)
                } else {
                    nextFrameTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun pauseEmulator() {
        emulatorPlaybackJob?.cancel()
        playerDecoder?.pauseAudio()
        _uiState.update { it.copy(isEmulatorPlaying = false) }
    }

    fun stepEmulatorFrame() {
        pauseEmulator()
        viewModelScope.launch(Dispatchers.IO) {
            val frame = playerDecoder?.decodeNextFrame()
            if (frame != null) {
                _uiState.update {
                    it.copy(
                        emulatorFrame = frame,
                        emulatorAudioLevelL = frame.audioLevelL,
                        emulatorAudioLevelR = frame.audioLevelR
                    )
                }
            }
        }
    }

    fun seekEmulator(targetFrame: Int) {
        pauseEmulator()
        viewModelScope.launch(Dispatchers.IO) {
            playerDecoder?.seekToKeyframe(targetFrame)
            val frame = playerDecoder?.decodeNextFrame()
            if (frame != null) {
                _uiState.update {
                    it.copy(
                        emulatorFrame = frame,
                        emulatorAudioLevelL = frame.audioLevelL,
                        emulatorAudioLevelR = frame.audioLevelR
                    )
                }
            }
        }
    }

    fun inspectValidationFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = FvFormat.validateFile(file)
            _uiState.update {
                it.copy(
                    currentTab = AppTab.VALIDATOR,
                    inspectionReport = report
                )
            }
        }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(userMessage = msg) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun showToast(msg: String) = showMessage(msg)
    fun dismissToast() = clearUserMessage()

    fun selectFileForPlayer(filePath: String) {
        val file = File(filePath)
        val job = ConversionJob(
            id = UUID.randomUUID().toString(),
            sourceMeta = SourceVideoMeta(
                uri = filePath,
                name = file.name,
                sizeBytes = file.length(),
                durationMs = 0L,
                width = 256,
                height = 144,
                fps = 20.0,
                hasAudio = true,
                audioSampleRate = 47605,
                audioChannels = 2
            ),
            settings = ConversionSettings(),
            status = JobStatus.COMPLETED,
            outputFilePath = filePath,
            outputSizeBytes = file.length()
        )
        openInDsEmulator(job)
    }

    fun validateBitstream(filePath: String) {
        inspectValidationFile(File(filePath))
    }

    fun exportApk() = exportApkOneTap()

    fun generateDemoSample() = startRetroIntroSampleConversion()

    fun shareFile(filePath: String) {
        val file = File(filePath)
        val job = ConversionJob(
            id = UUID.randomUUID().toString(),
            sourceMeta = SourceVideoMeta(
                uri = filePath,
                name = file.name,
                sizeBytes = file.length(),
                durationMs = 0L,
                width = 256,
                height = 144,
                fps = 20.0,
                hasAudio = true,
                audioSampleRate = 47605,
                audioChannels = 2
            ),
            settings = ConversionSettings(),
            status = JobStatus.COMPLETED,
            outputFilePath = filePath,
            outputSizeBytes = file.length()
        )
        shareFvFile(job)
    }

    fun exportConvertedFvToDownloads(job: ConversionJob) = saveFvToDownloads(job)

    fun deleteConvertedItem(job: ConversionJob) = deleteJob(job.id)

    fun clearHistory() = clearAllHistory()

    fun closeSetupSheet() = dismissSetupDialog()

    fun startConversion(settings: ConversionSettings) {
        updateSettings(settings)
        startConversionFromDialog()
    }

    override fun onCleared() {
        playerDecoder?.close()
        emulatorPlaybackJob?.cancel()
        super.onCleared()
    }
}
