package com.example.fastvideo.service

import android.app.Application
import android.content.Context
import com.example.fastvideo.data.ConversionHistoryManager
import com.example.fastvideo.decoder.LocalVideoProcessor
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.model.FvStrictAudit
import com.example.fastvideo.model.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Singleton process-level conversion orchestrator.
 * Operates independently of Activity/ViewModel lifecycles so video encoding continues uninterrupted
 * when the app is minimized, locked, backgrounded, or quit by the user.
 */
object ConversionManager {
    // Process-level scope tied to the Application process, backed by SupervisorJob
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeJob = MutableStateFlow<ConversionJob?>(null)
    val activeJob: StateFlow<ConversionJob?> = _activeJob.asStateFlow()

    private val _queue = MutableStateFlow<List<ConversionJob>>(emptyList())
    val queue: StateFlow<List<ConversionJob>> = _queue.asStateFlow()

    private val _batchCompletedCount = MutableStateFlow(0)
    val batchCompletedCount: StateFlow<Int> = _batchCompletedCount.asStateFlow()

    private val _batchTotalCount = MutableStateFlow(0)
    val batchTotalCount: StateFlow<Int> = _batchTotalCount.asStateFlow()

    private val _lastCompletedAudit = MutableStateFlow<Pair<ConversionJob, FvStrictAudit>?>(null)
    val lastCompletedAudit: StateFlow<Pair<ConversionJob, FvStrictAudit>?> = _lastCompletedAudit.asStateFlow()

    private val _eventMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val eventMessages: SharedFlow<String> = _eventMessages.asSharedFlow()

    private var activeJobHandle: Job? = null
    @Volatile private var isCancelledFlag = false

    fun enqueueJobs(context: Context, jobs: List<ConversionJob>) {
        if (jobs.isEmpty()) return
        val app = context.applicationContext as Application
        _batchTotalCount.update { it + jobs.size }
        val currentQueue = _queue.value

        if (_activeJob.value == null) {
            val first = jobs.first()
            val remaining = currentQueue + jobs.drop(1)
            _queue.value = remaining
            runJob(app, first)
        } else {
            _queue.value = currentQueue + jobs
            _eventMessages.tryEmit("Added ${jobs.size} video(s) to background queue (#${_queue.value.size})")
        }
    }

    fun cancelActiveJob(context: Context) {
        isCancelledFlag = true
        activeJobHandle?.cancel()
        val app = context.applicationContext as Application
        val historyManager = ConversionHistoryManager(app)
        _activeJob.update { active ->
            active?.let {
                val c = it.copy(status = JobStatus.CANCELLED, errorMessage = "Cancelled by user")
                historyManager.saveJob(c)
                c
            }
        }
        _activeJob.value = null
        if (_queue.value.isEmpty()) {
            ConversionForegroundService.stop(context)
        } else {
            val next = _queue.value.first()
            _queue.update { it.drop(1) }
            runJob(app, next)
        }
        _eventMessages.tryEmit("Conversion cancelled.")
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _batchTotalCount.value = 0
        _batchCompletedCount.value = 0
        _eventMessages.tryEmit("Queue cleared.")
    }

    fun cancelQueuedJob(id: String) {
        _queue.update { it.filterNot { job -> job.id == id } }
        _eventMessages.tryEmit("Removed from queue.")
    }

    fun clearAuditModal() {
        _lastCompletedAudit.value = null
    }

    private fun runJob(app: Application, job: ConversionJob) {
        isCancelledFlag = false
        val durationMs = if (job.sourceMeta.durationMs > 0) job.sourceMeta.durationMs else 5000L
        val expectedTotalFrames = ((durationMs / 1000.0) * job.settings.fps).toInt().coerceAtLeast(1)
        val startingJob = job.copy(
            status = JobStatus.CONVERTING,
            progress = 0f,
            currentFrame = 0,
            totalFrames = maxOf(job.totalFrames, expectedTotalFrames, 1)
        )
        _activeJob.value = startingJob

        val batchCurrent = _batchCompletedCount.value + 1
        val batchTotal = maxOf(_batchTotalCount.value, _queue.value.size + 1)
        val notifTitle = if (batchTotal > 1) {
            "Converting [$batchCurrent/$batchTotal]: ${job.sourceMeta.name}"
        } else {
            "Converting ${job.sourceMeta.name} to .fv..."
        }

        ConversionForegroundService.start(
            app,
            notifTitle,
            0,
            100
        )

        activeJobHandle = scope.launch {
            val historyManager = ConversionHistoryManager(app)
            var lastNotifPercent = -1
            var lastNotifTime = 0L

            try {
                val result = LocalVideoProcessor.convertVideo(
                    context = app,
                    job = startingJob,
                    onProgress = { current, total, elapsed, remaining ->
                        val safeTotal = maxOf(total, startingJob.totalFrames, 1)
                        val prog = (current.toFloat() / safeTotal).coerceIn(0f, 1f)
                        val fps = if (elapsed >= 400L && current >= 5) {
                            (current.toDouble() * 1000.0) / elapsed
                        } else {
                            0.0
                        }
                        _activeJob.update { active ->
                            active?.copy(
                                progress = prog,
                                currentFrame = current,
                                totalFrames = safeTotal,
                                elapsedMs = elapsed,
                                estimatedRemainingMs = remaining,
                                currentFps = fps
                            )
                        }

                        val percent = (prog * 100).toInt()
                        val now = System.currentTimeMillis()
                        if (percent != lastNotifPercent || (now - lastNotifTime) >= 1000L) {
                            lastNotifPercent = percent
                            lastNotifTime = now
                            val remSec = remaining / 1000
                            val etaStr = if (remSec >= 60) "${remSec / 60}m ${remSec % 60}s" else "${remSec}s"
                            val fpsStr = if (fps > 0.0) "⚡ ${String.format(java.util.Locale.US, "%.1f", fps)} FPS • " else ""
                            ConversionForegroundService.start(
                                app,
                                "${fpsStr}Frame $current / $total ($percent%) • ETA: $etaStr",
                                percent,
                                100
                            )
                        }
                    },
                    isCancelled = { isCancelledFlag }
                )

                if (isCancelledFlag) {
                    val cancelledJob = startingJob.copy(
                        status = JobStatus.CANCELLED,
                        errorMessage = "Cancelled by user"
                    )
                    historyManager.saveJob(cancelledJob)
                    finishJob(app, cancelledJob)
                } else {
                    val completedJob = startingJob.copy(
                        status = JobStatus.COMPLETED,
                        progress = 1f,
                        outputFilePath = result.file.absolutePath,
                        outputSizeBytes = result.file.length(),
                        validationReport = result.validationReport,
                        strictAudit = result.strictAudit
                    )
                    historyManager.saveJob(completedJob)
                    _batchCompletedCount.update { it + 1 }
                    _lastCompletedAudit.value = Pair(completedJob, result.strictAudit)
                    _eventMessages.tryEmit("Conversion Complete! Strict settings audit passed.")
                    finishJob(app, completedJob)
                }
            } catch (t: Throwable) {
                val failedJob = startingJob.copy(
                    status = JobStatus.FAILED,
                    errorMessage = t.message ?: "Conversion failed"
                )
                historyManager.saveJob(failedJob)
                _eventMessages.tryEmit("Conversion failed: ${t.message ?: "Error"}")
                finishJob(app, failedJob)
            }
        }
    }

    private fun finishJob(app: Application, finishedJob: ConversionJob) {
        val nextJob = _queue.value.firstOrNull()
        if (nextJob == null) {
            _activeJob.value = null
            ConversionForegroundService.showCompletedNotification(app, finishedJob)
        } else {
            _queue.update { it.drop(1) }
            runJob(app, nextJob)
        }
    }
}
