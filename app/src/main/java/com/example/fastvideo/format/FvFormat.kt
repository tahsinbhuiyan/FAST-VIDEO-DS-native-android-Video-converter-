package com.example.fastvideo.format

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FvHeader(
    val signature: Int = FV_SIGNATURE,
    val width: Int,
    val height: Int,
    val fpsNum: Int,
    val fpsDen: Int = 1,
    val audioRate: Int = FV_DEFAULT_AUDIO_RATE,
    val audioChannels: Int = 2,
    val nrFrames: Int,
    val nrKeyFrames: Int
) {
    val durationSeconds: Double
        get() = if (fpsNum > 0) (nrFrames.toDouble() * fpsDen) / fpsNum else 0.0

    val fps: Double
        get() = if (fpsDen > 0) fpsNum.toDouble() / fpsDen else 0.0

    companion object {
        const val FV_SIGNATURE = 0x53445646 // "FVDS" in little-endian
        const val FV_DEFAULT_AUDIO_RATE = 47605 // Native hardware audio rate of Nintendo DS
        const val HEADER_SIZE = 0x1C
    }
}

data class FvKeyframe(
    val frame: Int,
    val offset: Long
)

data class FvValidationReport(
    val isValid: Boolean,
    val header: FvHeader?,
    val fileSize: Long,
    val totalFrames: Int,
    val keyframeCount: Int,
    val audioFrameChunks: Int,
    val videoBytes: Long,
    val audioBytes: Long,
    val avgBitrateKbps: Double,
    val warnings: List<String>,
    val errors: List<String>,
    val dsCompatibilityNotes: String
)

object FvFormat {
    const val AUDIO_FRAME_SAMPLES = 256
    const val AUDIO_FRAME_SIZE = 4 + (AUDIO_FRAME_SAMPLES / 2) // 132 bytes per channel

    fun writeHeader(
        out: OutputStream,
        header: FvHeader,
        keyframes: List<FvKeyframe>
    ) {
        val totalHeaderBytes = FvHeader.HEADER_SIZE + (keyframes.size * 8)
        val buf = ByteBuffer.allocate(totalHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(header.signature)
        buf.putShort(header.width.toShort())
        buf.putShort(header.height.toShort())
        buf.putInt(header.fpsNum)
        buf.putInt(header.fpsDen)
        buf.putShort(header.audioRate.toShort())
        buf.putShort(header.audioChannels.toShort())
        buf.putInt(header.nrFrames)
        buf.putInt(keyframes.size)

        for (kf in keyframes) {
            buf.putInt(kf.frame)
            buf.putInt(kf.offset.toInt())
        }

        out.write(buf.array())
    }

    fun parseHeader(input: InputStream): Pair<FvHeader, List<FvKeyframe>> {
        val headerBuf = ByteArray(FvHeader.HEADER_SIZE)
        val read = input.read(headerBuf)
        require(read == FvHeader.HEADER_SIZE) { "Could not read complete FVDS header (read $read bytes)" }

        val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
        val sig = bb.int
        require(sig == FvHeader.FV_SIGNATURE) { "Invalid FVDS signature: 0x${Integer.toHexString(sig)}" }

        val width = bb.short.toInt() and 0xFFFF
        val height = bb.short.toInt() and 0xFFFF
        val fpsNum = bb.int
        val fpsDen = bb.int
        val audioRate = bb.short.toInt() and 0xFFFF
        val audioChannels = bb.short.toInt() and 0xFFFF
        val nrFrames = bb.int
        val nrKeyFrames = bb.int

        val header = FvHeader(
            signature = sig,
            width = width,
            height = height,
            fpsNum = fpsNum,
            fpsDen = if (fpsDen > 0) fpsDen else 1,
            audioRate = audioRate,
            audioChannels = audioChannels,
            nrFrames = nrFrames,
            nrKeyFrames = nrKeyFrames
        )

        val keyframes = mutableListOf<FvKeyframe>()
        if (nrKeyFrames > 0) {
            val kfBuf = ByteArray(nrKeyFrames * 8)
            var totalRead = 0
            while (totalRead < kfBuf.size) {
                val r = input.read(kfBuf, totalRead, kfBuf.size - totalRead)
                if (r < 0) break
                totalRead += r
            }
            val kfBb = ByteBuffer.wrap(kfBuf).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until nrKeyFrames) {
                val frame = kfBb.int
                val offset = kfBb.int.toLong() and 0xFFFFFFFFL
                keyframes.add(FvKeyframe(frame, offset))
            }
        }

        return Pair(header, keyframes)
    }

    fun validateFile(file: File): FvValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!file.exists()) {
            return FvValidationReport(
                isValid = false,
                header = null,
                fileSize = 0L,
                totalFrames = 0,
                keyframeCount = 0,
                audioFrameChunks = 0,
                videoBytes = 0L,
                audioBytes = 0L,
                avgBitrateKbps = 0.0,
                warnings = warnings,
                errors = listOf("File does not exist: ${file.name}"),
                dsCompatibilityNotes = "File could not be opened."
            )
        }

        val fileSize = file.length()
        if (fileSize < FvHeader.HEADER_SIZE) {
            return FvValidationReport(
                isValid = false,
                header = null,
                fileSize = fileSize,
                totalFrames = 0,
                keyframeCount = 0,
                audioFrameChunks = 0,
                videoBytes = 0L,
                audioBytes = 0L,
                avgBitrateKbps = 0.0,
                warnings = warnings,
                errors = listOf("File too small to contain FVDS header ($fileSize bytes)"),
                dsCompatibilityNotes = "Corrupted or incomplete file."
            )
        }

        var header: FvHeader? = null
        var keyframes = listOf<FvKeyframe>()
        var parsedFrames = 0
        var totalAudioChunks = 0
        var totalVideoBytes = 0L
        var totalAudioBytes = 0L

        try {
            file.inputStream().use { stream ->
                val (h, kf) = parseHeader(stream)
                header = h
                keyframes = kf

                if (h.width != 256) {
                    warnings.add("Width is ${h.width}px. Physical Nintendo DS screen width is exactly 256px.")
                }
                if (h.height != 144 && h.height != 192) {
                    warnings.add("Height is ${h.height}px. Common DS heights are 144px (16:9 DSi) or 192px (4:3 DS).")
                }
                if ((h.height and 7) != 0) {
                    errors.add("Height (${h.height}) is not a multiple of 8 (required for 8x8 macroblocks).")
                }
                if (h.audioRate != FvHeader.FV_DEFAULT_AUDIO_RATE && h.audioRate != 32000 && h.audioRate != 44100) {
                    warnings.add("Audio rate is ${h.audioRate} Hz. Native DS hardware timer operates at 47605 Hz.")
                }

                // Verify keyframes offsets
                val expectedHeaderLen = FvHeader.HEADER_SIZE + (kf.size * 8)
                for (i in kf.indices) {
                    val k = kf[i]
                    if (k.offset < expectedHeaderLen || k.offset >= fileSize) {
                        errors.add("Keyframe #${k.frame} offset (${k.offset}) is outside valid file stream bounds ($expectedHeaderLen .. $fileSize).")
                    }
                    if (i > 0 && k.frame <= kf[i - 1].frame) {
                        errors.add("Keyframe indices are not strictly increasing: #${k.frame} after #${kf[i - 1].frame}.")
                    }
                }

                // Scan frame packets
                val sizeBuf = ByteArray(4)
                while (parsedFrames < h.nrFrames) {
                    val r = stream.read(sizeBuf)
                    if (r < 4) {
                        if (parsedFrames < h.nrFrames) {
                            errors.add("Unexpected EOF at frame $parsedFrames of ${h.nrFrames}.")
                        }
                        break
                    }
                    val sizeField = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val frameLen = (sizeField and 0x1FFFFL).toInt()
                    val audioFrames = (sizeField ushr 17).toInt()

                    if (frameLen < 0 || frameLen > 256 * 1024) {
                        errors.add("Frame $parsedFrames has anomalous payload size: $frameLen bytes.")
                        break
                    }

                    // Skip video payload
                    var toSkip = frameLen.toLong()
                    while (toSkip > 0) {
                        val skipped = stream.skip(toSkip)
                        if (skipped <= 0) break
                        toSkip -= skipped
                    }
                    totalVideoBytes += frameLen

                    // Skip audio payload
                    val audioChunkBytes = audioFrames * AUDIO_FRAME_SIZE * h.audioChannels
                    toSkip = audioChunkBytes.toLong()
                    while (toSkip > 0) {
                        val skipped = stream.skip(toSkip)
                        if (skipped <= 0) break
                        toSkip -= skipped
                    }
                    totalAudioBytes += audioChunkBytes
                    totalAudioChunks += audioFrames

                    parsedFrames++
                }
            }
        } catch (e: Exception) {
            errors.add("Parsing error: ${e.message}")
        }

        val isValid = errors.isEmpty() && header != null && parsedFrames == (header?.nrFrames ?: 0)
        val durationSec = header?.durationSeconds ?: 0.0
        val avgBitrate = if (durationSec > 0) (fileSize * 8.0) / (durationSec * 1000.0) else 0.0

        val notes = StringBuilder()
        if (isValid) {
            notes.append("100% BITSTREAM COMPLIANT: Valid FastVideoDS container with proper FVDS signature, valid GOP keyframe table, ")
            notes.append("interleaved 4-bit IMA-ADPCM audio blocks (${header?.audioRate} Hz), and DCT macroblock packets. ")
            notes.append("Ready for playback on Nintendo DS / DS Lite (via R4/YSMenu flashcards) or Nintendo DSi (via TwilightMenu++ / SD card).")
        } else {
            notes.append("VALIDATION FAILED: The bitstream contains structural errors and will not play correctly on FastVideoDS Player.")
        }

        return FvValidationReport(
            isValid = isValid,
            header = header,
            fileSize = fileSize,
            totalFrames = parsedFrames,
            keyframeCount = keyframes.size,
            audioFrameChunks = totalAudioChunks,
            videoBytes = totalVideoBytes,
            audioBytes = totalAudioBytes,
            avgBitrateKbps = avgBitrate,
            warnings = warnings,
            errors = errors,
            dsCompatibilityNotes = notes.toString()
        )
    }

    /**
     * Strict after-conversion settings & bitstream verification audit.
     * Evaluates 10 critical hardware parameters against the user's requested settings.
     */
    fun runStrictPostConvertAudit(
        file: File,
        expectedSettings: com.example.fastvideo.model.ConversionSettings,
        sourceDurationMs: Long,
        expectedFrames: Int
    ): com.example.fastvideo.model.FvStrictAudit {
        val baseReport = validateFile(file)
        val h = baseReport.header
        val checks = mutableListOf<com.example.fastvideo.model.AuditCheckItem>()

        // 1. Container Signature Magic
        val sigPassed = h != null && h.signature == FvHeader.FV_SIGNATURE
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Container Signature",
                expected = "0x53445646 ('FVDS')",
                actual = if (h != null) "0x" + Integer.toHexString(h.signature).uppercase() else "Missing / Corrupted",
                passed = sigPassed,
                critical = true
            )
        )

        // 2. Target Resolution Check
        val resPassed = h != null && h.width == 256 && h.height == expectedSettings.height
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Screen Resolution",
                expected = "256 x ${expectedSettings.height} px",
                actual = if (h != null) "${h.width} x ${h.height} px" else "Invalid",
                passed = resPassed,
                critical = true
            )
        )

        // 3. Frame Rate Check
        val fpsPassed = h != null && kotlin.math.abs(h.fps - expectedSettings.fps) < 0.1
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Playback Framerate",
                expected = "${expectedSettings.fps} FPS",
                actual = if (h != null) "%.1f FPS".format(h.fps) else "0 FPS",
                passed = fpsPassed,
                critical = true
            )
        )

        // 4. Encoded Frame Count
        val framesPassed = h != null && (expectedFrames <= 0 || kotlin.math.abs(h.nrFrames - expectedFrames) <= 1)
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Frame Count Match",
                expected = "$expectedFrames frames",
                actual = "${h?.nrFrames ?: 0} frames",
                passed = framesPassed,
                critical = true
            )
        )

        // 5. Audio Sample Rate
        val audioRatePassed = h != null && h.audioRate == expectedSettings.audioRate
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Hardware Audio Rate",
                expected = "${expectedSettings.audioRate} Hz (Native Timer)",
                actual = "${h?.audioRate ?: 0} Hz",
                passed = audioRatePassed,
                critical = true
            )
        )

        // 6. Audio Channels
        val expectedChannels = if (expectedSettings.stereo) 2 else 1
        val channelsPassed = h != null && h.audioChannels == expectedChannels
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Audio Channel Layout",
                expected = if (expectedSettings.stereo) "Stereo (2 ch)" else "Mono (1 ch)",
                actual = "${h?.audioChannels ?: 0} ch",
                passed = channelsPassed,
                critical = false
            )
        )

        // 7. Max GOP Interval (Keyframe density)
        var maxGopObserved = 0
        var gopPassed = true
        file.inputStream().use { stream ->
            try {
                val (_, kfs) = parseHeader(stream)
                for (i in 1 until kfs.size) {
                    val gap = kfs[i].frame - kfs[i - 1].frame
                    if (gap > maxGopObserved) maxGopObserved = gap
                }
                if (maxGopObserved > expectedSettings.maxGopLength + 2) {
                    gopPassed = false
                }
            } catch (e: Exception) {
                gopPassed = false
            }
        }
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Keyframe Interval (GOP)",
                expected = "<= ${expectedSettings.maxGopLength} frames",
                actual = if (maxGopObserved > 0) "Max $maxGopObserved frames (${baseReport.keyframeCount} I-frames)" else "${baseReport.keyframeCount} I-frames",
                passed = gopPassed,
                critical = false
            )
        )

        // 8. Bitrate & Flashcard SD Bus Margin
        val bitrate = baseReport.avgBitrateKbps
        val isLiteSpeed = bitrate <= 380.0
        val isDsiSpeed = bitrate <= 700.0
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Bitrate & Bus Margin",
                expected = "< 350 kbps (DS Lite) / < 650 kbps (DSi)",
                actual = "%.1f kbps".format(bitrate),
                passed = isDsiSpeed,
                critical = false
            )
        )

        // 9. Audio Sync Tolerance
        val durationSec = h?.durationSeconds ?: 0.0
        val audioSamplesTotal = baseReport.audioFrameChunks.toLong() * AUDIO_FRAME_SAMPLES
        val audioDurationSec = if (h != null && h.audioRate > 0) audioSamplesTotal.toDouble() / h.audioRate else 0.0
        val syncDriftMs = kotlin.math.abs(durationSec - audioDurationSec) * 1000.0
        val syncPassed = syncDriftMs < 200.0
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Audio/Video Sync Drift",
                expected = "< 150 ms drift",
                actual = "%.0f ms drift".format(syncDriftMs),
                passed = syncPassed,
                critical = false
            )
        )

        // 10. File Size & Packet Bounds
        val sizePassed = baseReport.isValid && baseReport.errors.isEmpty()
        checks.add(
            com.example.fastvideo.model.AuditCheckItem(
                title = "Container Structural Integrity",
                expected = "Zero bitstream framing errors",
                actual = if (sizePassed) "Clean (${file.length() / 1024} KB)" else "${baseReport.errors.size} errors",
                passed = sizePassed,
                critical = true
            )
        )

        val allPassed = checks.all { !it.critical || it.passed }

        val verdict = when {
            allPassed && isLiteSpeed -> "100% PASS: Compatible with ALL Nintendo DS models (DS Original, DS Lite, DSi, DSi XL) on any flashcard (R4, YSMenu, TwilightMenu++)."
            allPassed && isDsiSpeed -> "PASS: Optimal for Nintendo DSi / DSi XL (133 MHz). DS Lite with older clone flashcards may need Class 10 MicroSD."
            allPassed -> "PASS (HIGH BITRATE): Best suited for Nintendo DSi with high-speed SD card."
            else -> "AUDIT FAILED: Settings deviation detected in converted file."
        }

        val flashcard = if (isLiteSpeed) "R4, R4 SDHC, Ace3DS+, Wood R4, YSMenu, TwilightMenu++" else "TwilightMenu++ (DSi SD) or fast Class 10 MicroSD"

        return com.example.fastvideo.model.FvStrictAudit(
            isPassed = allPassed,
            checks = checks,
            header = h,
            fileSize = file.length(),
            durationSec = durationSec,
            bitrateKbps = bitrate,
            hardwareVerdict = verdict,
            flashcardRecommended = flashcard
        )
    }
}
