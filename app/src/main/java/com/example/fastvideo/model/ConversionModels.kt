package com.example.fastvideo.model

import com.example.fastvideo.format.FvHeader
import com.example.fastvideo.format.FvValidationReport

enum class ConversionPreset(val title: String, val description: String, val height: Int) {
    DSI_WIDESCREEN(
        title = "Nintendo DSi Widescreen",
        description = "256×144 (16:9 aspect ratio, letterboxed with black bars on DS 4:3)",
        height = 144
    ),
    DS_STANDARD(
        title = "Nintendo DS Fullscreen",
        description = "256×192 (4:3 aspect ratio, fills entire top DS LCD screen)",
        height = 192
    ),
    CUSTOM(
        title = "Custom Height",
        description = "256×(custom multiple of 8) for specialized cropping or sub-displays",
        height = 144
    )
}

enum class ConversionSpeed(
    val title: String,
    val description: String,
    val earlyExitThreshold: Int,
    val isUltimateFast: Boolean = false
) {
    ULTIMATE_FAST_800X(
        title = "⚡ 800x Ultimate Fast",
        description = "Instant hardware-accelerated pipeline. Encodes 24-min video in ~45s (output size does not matter).",
        earlyExitThreshold = 999999,
        isUltimateFast = true
    ),
    TURBO(
        title = "Turbo (Fast)",
        description = "Fast diamond motion search with early exit.",
        earlyExitThreshold = 96,
        isUltimateFast = false
    ),
    BALANCED(
        title = "Balanced",
        description = "Optimized diamond search with standard early exit. Great balance.",
        earlyExitThreshold = 48,
        isUltimateFast = false
    ),
    QUALITY(
        title = "High Fidelity",
        description = "Full exhaustive search for maximal rate-distortion detail.",
        earlyExitThreshold = 16,
        isUltimateFast = false
    );

    val label: String get() = title
}

enum class FitMode {
    LETTERBOX,
    CROP_CENTER
}

enum class JobStatus {
    PENDING,
    CONVERTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SourceVideoMeta(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean,
    val audioSampleRate: Int,
    val audioChannels: Int
) {
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%02d:%02d".format(min, sec)
        }

    val sizeFormatted: String
        get() {
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return "%.1f MB".format(mb)
        }
}

data class ConversionSettings(
    val preset: ConversionPreset = ConversionPreset.DSI_WIDESCREEN,
    val width: Int = 256,
    val height: Int = 144,
    val fps: Int = 20,
    val q: Int = 30, // Quantizer scale (22 = High 2500k, 26 = Sharp 1800k, 30 = Balanced 1500k, 34 = Compact 1000k, 40 = Safe 700k)
    val bitrateKbps: Int = 1500, // Target bitrate in kbps (Optimal for Nintendo DS SD card bandwidth)
    val audioRate: Int = 32000, // 32 kHz (32,000 Hz Nintendo DS standard - optimal speed & quality)
    val stereo: Boolean = true,
    val maxGopLength: Int = 120, // Keyframe interval
    val fitMode: FitMode = FitMode.LETTERBOX,
    val speed: ConversionSpeed = ConversionSpeed.ULTIMATE_FAST_800X
) {
    fun withBitrate(newBitrate: Int): ConversionSettings {
        val mappedQ = when {
            newBitrate >= 2400 -> 22
            newBitrate >= 1800 -> 26
            newBitrate >= 1400 -> 30
            newBitrate >= 950 -> 34
            else -> 40
        }
        return copy(bitrateKbps = newBitrate, q = mappedQ)
    }

    fun withQ(newQ: Int): ConversionSettings {
        val mappedBitrate = when {
            newQ <= 24 -> 2500
            newQ <= 28 -> 1800
            newQ <= 32 -> 1500
            newQ <= 36 -> 1000
            else -> 700
        }
        return copy(q = newQ, bitrateKbps = mappedBitrate)
    }

    fun estimateOutputSizeBytes(durationMs: Long): Long {
        if (durationMs <= 0) return 1024 * 1024L
        val seconds = durationMs / 1000.0
        val totalFrames = (seconds * fps).toInt().coerceAtLeast(1)

        val videoBytesPerSec = (bitrateKbps * 1000.0) / 8.0
        val audioBytesPerSec = if (audioRate > 0) {
            val channels = if (stereo) 2 else 1
            audioRate.toDouble() * channels * (132.0 / 256.0)
        } else {
            0.0
        }

        val videoTotalBytes = (seconds * videoBytesPerSec).toLong()
        val audioTotalBytes = (seconds * audioBytesPerSec).toLong()
        val headerBytes = 0x1CL + (totalFrames / maxGopLength + 2) * 8L

        return videoTotalBytes + audioTotalBytes + headerBytes
    }

    val qualityLabel: String
        get() = when {
            bitrateKbps >= 2200 -> "High Quality ($bitrateKbps kbps • q=$q)"
            bitrateKbps >= 1400 -> "Balanced ($bitrateKbps kbps • q=$q)"
            bitrateKbps >= 900 -> "Safe SD ($bitrateKbps kbps • q=$q)"
            else -> "Compact ($bitrateKbps kbps • q=$q)"
        }
}

data class AuditCheckItem(
    val title: String,
    val expected: String,
    val actual: String,
    val passed: Boolean,
    val critical: Boolean = true
)

data class FvStrictAudit(
    val isPassed: Boolean,
    val checks: List<AuditCheckItem>,
    val header: FvHeader?,
    val fileSize: Long,
    val durationSec: Double,
    val bitrateKbps: Double,
    val hardwareVerdict: String,
    val flashcardRecommended: String
)

data class ConversionJob(
    val id: String,
    val sourceMeta: SourceVideoMeta,
    val settings: ConversionSettings,
    val status: JobStatus = JobStatus.PENDING,
    val progress: Float = 0f,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val elapsedMs: Long = 0L,
    val estimatedRemainingMs: Long = 0L,
    val outputFilePath: String = "",
    val outputSizeBytes: Long = 0L,
    val currentFps: Double = 0.0,
    val validationReport: FvValidationReport? = null,
    val strictAudit: FvStrictAudit? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
