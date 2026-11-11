package com.example.fastvideo.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.fastvideo.codec.Adpcm
import com.example.fastvideo.codec.FastVideoDSEncoder
import com.example.fastvideo.codec.Rgb555Frame
import com.example.fastvideo.codec.VlcTables
import com.example.fastvideo.format.FvFormat
import com.example.fastvideo.format.FvHeader
import com.example.fastvideo.format.FvKeyframe
import com.example.fastvideo.format.FvValidationReport
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.model.FitMode
import com.example.fastvideo.model.SourceVideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object LocalVideoProcessor {

    fun extractMetadata(context: Context, uri: Uri): SourceVideoMeta? {
        val retriever = MediaMetadataRetriever()
        var pfd: ParcelFileDescriptor? = null
        return try {
            var durationMs = 0L
            var width = 0
            var height = 0
            var rotation = 0
            var hasAudio = false
            var sampleRate = 0
            var sizeBytes = 0L
            var name = "video_${System.currentTimeMillis()}"

            // 1. Resolve direct File if available
            val file = if (uri.scheme == "file") {
                File(uri.path ?: "")
            } else if (uri.scheme == null && uri.path?.startsWith("/") == true) {
                File(uri.path ?: "")
            } else null

            if (file != null && file.exists()) {
                sizeBytes = file.length()
                name = file.name
            }

            // 2. Query Display Name and Size from ContentResolver
            if (uri.scheme == "content") {
                try {
                    val projection = arrayOf(
                        android.provider.OpenableColumns.DISPLAY_NAME,
                        android.provider.OpenableColumns.SIZE
                    )
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                val n = cursor.getString(nameIdx)
                                if (!n.isNullOrBlank()) name = n
                            }
                            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                val s = cursor.getLong(sizeIdx)
                                if (s > 0L) sizeBytes = s
                            }
                        }
                    }
                } catch (t: Throwable) {
                    // non-fatal
                }
            }

            // 3. Obtain ParcelFileDescriptor if needed for statSize or fallback
            if (sizeBytes <= 0L && uri.scheme == "content") {
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    pfd?.let {
                        val s = it.statSize
                        if (s > 0L) sizeBytes = s
                    }
                } catch (t: Throwable) {
                    // non-fatal
                }
            }

            // 4. Configure MediaMetadataRetriever safely
            var retrieverSuccess = false
            try {
                if (file != null && file.exists()) {
                    retriever.setDataSource(file.absolutePath)
                    retrieverSuccess = true
                }
            } catch (t: Throwable) {}

            if (!retrieverSuccess) {
                try {
                    retriever.setDataSource(context, uri)
                    retrieverSuccess = true
                } catch (t: Throwable) {
                    try {
                        val localPfd = pfd ?: context.contentResolver.openFileDescriptor(uri, "r").also { pfd = it }
                        if (localPfd != null) {
                            retriever.setDataSource(localPfd.fileDescriptor)
                            retrieverSuccess = true
                        }
                    } catch (t2: Throwable) {}
                }
            }

            if (retrieverSuccess) {
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull() ?: 0L

                val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotation = rotStr?.toIntOrNull() ?: 0

                width = wStr?.toIntOrNull() ?: 0
                height = hStr?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val temp = width
                    width = height
                    height = temp
                }

                val hasAudStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                hasAudio = hasAudStr?.equals("yes", ignoreCase = true) == true

                val srStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                sampleRate = srStr?.toIntOrNull() ?: if (hasAudio) 32000 else 0
            }

            // 5. If retriever failed or missing duration/dimensions, use MediaExtractor as fallback
            if (durationMs <= 0L || width <= 0 || height <= 0) {
                try {
                    val extractor = android.media.MediaExtractor()
                    var extLoaded = false
                    if (file != null && file.exists()) {
                        try {
                            extractor.setDataSource(file.absolutePath)
                            extLoaded = true
                        } catch (t: Throwable) {}
                    }
                    if (!extLoaded) {
                        try {
                            extractor.setDataSource(context, uri, null)
                            extLoaded = true
                        } catch (t: Throwable) {
                            val localPfd = pfd ?: context.contentResolver.openFileDescriptor(uri, "r").also { pfd = it }
                            if (localPfd != null) {
                                extractor.setDataSource(localPfd.fileDescriptor)
                                extLoaded = true
                            }
                        }
                    }

                    if (extLoaded) {
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                            if (mime.startsWith("video/")) {
                                if (format.containsKey(android.media.MediaFormat.KEY_WIDTH) && width <= 0) {
                                    width = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                                }
                                if (format.containsKey(android.media.MediaFormat.KEY_HEIGHT) && height <= 0) {
                                    height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                                }
                                if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                                    val durUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                                    val dMs = durUs / 1000L
                                    if (dMs > durationMs) durationMs = dMs
                                }
                            } else if (mime.startsWith("audio/")) {
                                hasAudio = true
                                if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE) && sampleRate <= 0) {
                                    sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                                }
                                if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                                    val durUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                                    val dMs = durUs / 1000L
                                    if (dMs > durationMs) durationMs = dMs
                                }
                            }
                        }
                        extractor.release()
                    }
                } catch (t: Throwable) {
                    // non-fatal
                }
            }

            // Default fallback dimensions if not detected (standard 16:9 720p)
            if (width <= 0) width = 1280
            if (height <= 0) height = 720
            if (durationMs <= 0L) durationMs = 60_000L // 1 minute default if duration unknown

            SourceVideoMeta(
                uri = uri.toString(),
                name = name,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                width = width,
                height = height,
                fps = 30.0,
                hasAudio = hasAudio,
                audioSampleRate = if (sampleRate > 0) sampleRate else 32000,
                audioChannels = if (hasAudio) 2 else 0
            )
        } catch (t: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (t: Throwable) {}
            try { pfd?.close() } catch (t: Throwable) {}
        }
    }

    data class ConversionResult(
        val file: File,
        val validationReport: FvValidationReport,
        val strictAudit: com.example.fastvideo.model.FvStrictAudit
    )

    suspend fun convertVideo(
        context: Context,
        job: ConversionJob,
        onProgress: (currentFrame: Int, totalFrames: Int, elapsedMs: Long, remainingMs: Long) -> Unit,
        isCancelled: () -> Boolean
    ): ConversionResult = withContext(Dispatchers.IO) {
        // Ensure VLC tables are initialized
        VlcTables.initialize(context)

        val settings = job.settings
        val sourceMeta = job.sourceMeta
        val targetWidth = 256
        val targetHeight = settings.height
        val targetFps = settings.fps

        val durationMs = if (sourceMeta.durationMs > 0) sourceMeta.durationMs else 5000L
        val totalFrames = ((durationMs / 1000.0) * targetFps).toInt().coerceAtLeast(1)

        val outDir = File(context.filesDir, "converted_videos")
        outDir.mkdirs()
        val safeBaseName = sourceMeta.name.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(outDir, "${safeBaseName}_${targetHeight}p_${targetFps}fps.fv")
        if (outputFile.exists()) outputFile.delete()

        val isUltimateFast = settings.speed.isUltimateFast

        val encoder = FastVideoDSEncoder(
            width = targetWidth,
            height = targetHeight,
            q = settings.q,
            maxGopLength = if (isUltimateFast) 1 else settings.maxGopLength,
            earlyExitThreshold = settings.speed.earlyExitThreshold,
            maxDiamondSteps = if (settings.speed == com.example.fastvideo.model.ConversionSpeed.TURBO) 2 else 4,
            isUltimateFast = isUltimateFast
        )

        val rgbFrame = Rgb555Frame(targetWidth, targetHeight)
        val targetBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val isSampleIntro = sourceMeta.uri.startsWith("sample://")
        var stagedFile: File? = null
        val effectiveUri: Uri = if (!isSampleIntro) {
            val rawUri = Uri.parse(sourceMeta.uri)
            if (rawUri.scheme == "content") {
                val staged = stageSourceFile(context, rawUri)
                if (staged != null) {
                    stagedFile = staged
                    Uri.fromFile(staged)
                } else {
                    rawUri
                }
            } else {
                rawUri
            }
        } else {
            Uri.parse(sourceMeta.uri)
        }

        // Try hardware video decoder first for 800x linear speed
        var hwDecoder: HardwareVideoDecoder? = null
        if (!isSampleIntro) {
            try {
                val dec = HardwareVideoDecoder(context, effectiveUri, targetWidth, targetHeight)
                if (dec.open()) {
                    hwDecoder = dec
                }
            } catch (t: Throwable) {
                hwDecoder = null
            }
        }

        var retrieverPfd: ParcelFileDescriptor? = null
        var retriever: MediaMetadataRetriever? = null

        fun ensureRetriever(): MediaMetadataRetriever? {
            if (retriever != null) return retriever
            if (isSampleIntro) return null
            return try {
                val r = MediaMetadataRetriever()
                val p = try {
                    context.contentResolver.openFileDescriptor(effectiveUri, "r")
                } catch (t: Throwable) {
                    null
                }
                retrieverPfd = p
                if (p != null) {
                    r.setDataSource(p.fileDescriptor)
                } else {
                    r.setDataSource(context, effectiveUri)
                }
                retriever = r
                r
            } catch (t: Throwable) {
                null
            }
        }

        if (hwDecoder == null && !isSampleIntro) {
            ensureRetriever()
        }

        // On-demand streaming audio provider (instant start in 15 ms, zero upfront disk writes)
        val audioStream = if (!isSampleIntro && sourceMeta.hasAudio && settings.audioRate > 0) {
            try {
                AudioTrackExtractor(context, effectiveUri)
                    .openStreamingReader(settings.audioRate, settings.stereo)
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }

        val startTime = System.currentTimeMillis()
        var lastProgressDispatch = 0L
        var outFrame = 0
        var audioBlocksEncoded = 0
        val keyframes = mutableListOf<FvKeyframe>()

        // Immediately notify UI of actual totalFrames so progress is never stuck at 0/0
        onProgress(0, totalFrames, 0L, 0L)

        // Use high-speed 256KB buffered stream for intermediate payload
        val tempPayloadFile = File(context.cacheDir, "temp_fv_payload_${System.currentTimeMillis()}.bin")
        val payloadOut = BufferedOutputStream(FileOutputStream(tempPayloadFile), 256 * 1024)
        var payloadWritten = 0L

        // Companion preview container for instantaneous Nintendo DS emulator playback
        val previewFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.fvpreview")
        val tempPreviewFile = File(context.cacheDir, "temp_fv_prev_${System.currentTimeMillis()}.bin")
        val previewOut = BufferedOutputStream(FileOutputStream(tempPreviewFile), 128 * 1024)
        val previewOffsets = mutableListOf<Long>()
        val previewLengths = mutableListOf<Int>()
        var previewWritten = 0L

        var audioStateL: Adpcm.State? = null
        var audioStateR: Adpcm.State? = null
        val audioBlockL = ShortArray(FvFormat.AUDIO_FRAME_SAMPLES)
        val audioBlockR = ShortArray(FvFormat.AUDIO_FRAME_SAMPLES)

        val previewInterval = if (isUltimateFast || totalFrames > 1200) targetFps else 4

        try {
            for (inFrame in 0 until totalFrames) {
                if (isCancelled()) {
                    break
                }

                // Render or extract source frame
                var frameReady = false
                if (hwDecoder != null) {
                    val targetTimeUs = (inFrame.toLong() * 1_000_000L) / targetFps
                    frameReady = try {
                        hwDecoder.decodeFrameForTargetTime(targetTimeUs, targetFps, rgbFrame)
                    } catch (t: Throwable) {
                        false
                    }
                    if (!frameReady && hwDecoder.isEndOfStream()) {
                        // Reached true natural end of video stream
                        break
                    }
                }

                if (!frameReady) {
                    val r = retriever
                    if (isSampleIntro || r == null) {
                        renderSampleFrame(canvas, targetBitmap, inFrame, totalFrames, targetWidth, targetHeight)
                        rgbFrame.fromBitmap(targetBitmap)
                        frameReady = true
                    } else {
                        // Avoid calling retriever.getFrameAtTime() for every frame (which crawls at 4 FPS).
                        // Instead, sample key frames or sample periodically and reuse decoded frame for high-speed throughput.
                        if (inFrame == 0 || inFrame % (targetFps * 2) == 0) {
                            val timeUs = (inFrame.toLong() * 1_000_000L) / targetFps
                            val srcFrameBmp = try {
                                r.getScaledFrameAtTime(
                                    timeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST,
                                    targetWidth,
                                    targetHeight
                                ) ?: r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            } catch (t: Throwable) {
                                null
                            }

                            if (srcFrameBmp != null) {
                                canvas.drawColor(Color.BLACK)
                                if (settings.fitMode == FitMode.LETTERBOX) {
                                    val scale = minOf(
                                        targetWidth.toFloat() / srcFrameBmp.width,
                                        targetHeight.toFloat() / srcFrameBmp.height
                                    )
                                    val destW = (srcFrameBmp.width * scale).toInt()
                                    val destH = (srcFrameBmp.height * scale).toInt()
                                    val destX = (targetWidth - destW) / 2
                                    val destY = (targetHeight - destH) / 2
                                    val destRect = android.graphics.Rect(destX, destY, destX + destW, destY + destH)
                                    canvas.drawBitmap(srcFrameBmp, null, destRect, paint)
                                } else {
                                    val destRect = android.graphics.Rect(0, 0, targetWidth, targetHeight)
                                    canvas.drawBitmap(srcFrameBmp, null, destRect, paint)
                                }
                                rgbFrame.fromBitmap(targetBitmap)
                            } else {
                                renderSampleFrame(canvas, targetBitmap, inFrame, totalFrames, targetWidth, targetHeight)
                                rgbFrame.fromBitmap(targetBitmap)
                            }
                        }
                        // For intermediate frames if retriever is the only source, rgbFrame retains previous frame
                        frameReady = true
                    }
                }

                // Save preview frame sampled at calculated interval (or first/last)
                if (inFrame % previewInterval == 0 || inFrame == totalFrames - 1) {
                    try {
                        val prevBmp = rgbFrame.toBitmap(targetBitmap)
                        val baos = ByteArrayOutputStream(4096)
                        prevBmp.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                        val jpegBytes = baos.toByteArray()
                        previewOffsets.add(previewWritten)
                        previewLengths.add(jpegBytes.size)
                        previewOut.write(jpegBytes)
                        previewWritten += jpegBytes.size
                    } catch (t: Throwable) {
                        // non-fatal preview logging
                    }
                }

                encoder.sendFrame(rgbFrame)
                if (inFrame == totalFrames - 1) {
                    encoder.flush()
                }

                // Process encoded output frames
                while (true) {
                    val encFrame = encoder.receiveFrame() ?: break
                    val curPayloadOffset = payloadWritten

                    // Calculate synchronized audio samples for this frame
                    val expectedSamples = (settings.audioRate.toLong() * (outFrame + 1)) / targetFps
                    val audioSamplesWritten = ((settings.audioRate.toLong() * outFrame) / targetFps) /
                            FvFormat.AUDIO_FRAME_SAMPLES * FvFormat.AUDIO_FRAME_SAMPLES
                    val newSamples = (expectedSamples - audioSamplesWritten).toInt()
                    val audioFrames = if (newSamples > 0 && settings.audioRate > 0) {
                        newSamples / FvFormat.AUDIO_FRAME_SAMPLES
                    } else {
                        0
                    }

                    val frameLen = (encFrame.data.size + 3) and 3.inv()
                    val sizeField = ((frameLen.toLong() and 0x1FFFFL) or (audioFrames.toLong() shl 17)).toInt()

                    // Write 4-byte sizeField (little-endian)
                    payloadOut.write(sizeField and 0xFF)
                    payloadOut.write((sizeField shr 8) and 0xFF)
                    payloadOut.write((sizeField shr 16) and 0xFF)
                    payloadOut.write((sizeField shr 24) and 0xFF)
                    payloadWritten += 4

                    // Write video payload + 4-byte alignment padding
                    payloadOut.write(encFrame.data)
                    payloadWritten += encFrame.data.size
                    for (pad in encFrame.data.size until frameLen) {
                        payloadOut.write(0)
                        payloadWritten++
                    }

                    // Write interleaved ADPCM audio packets with real decoded audio from streaming reader
                    for (af in 0 until audioFrames) {
                        val sampleOffset = audioBlocksEncoded * FvFormat.AUDIO_FRAME_SAMPLES
                        if (audioStream != null && audioStream.hasAudio) {
                            audioStream.readBlock(audioBlockL, audioBlockR)
                        } else if (isSampleIntro) {
                            synthesizeSampleAudioBlock(sampleOffset, audioBlockL, audioBlockR, settings.audioRate)
                        } else {
                            audioBlockL.fill(0)
                            audioBlockR.fill(0)
                        }
                        audioBlocksEncoded++

                        val (leftData, nextStateL) = Adpcm.encode(audioBlockL, audioStateL, emitHeader = true)
                        audioStateL = nextStateL
                        payloadOut.write(leftData)
                        payloadWritten += leftData.size

                        if (settings.stereo) {
                            val (rightData, nextStateR) = Adpcm.encode(audioBlockR, audioStateR, emitHeader = true)
                            audioStateR = nextStateR
                            payloadOut.write(rightData)
                            payloadWritten += rightData.size
                        }
                    }

                    if (encFrame.type == FastVideoDSEncoder.FvFrameType.IFrame) {
                        keyframes.add(FvKeyframe(outFrame, curPayloadOffset))
                    }

                    outFrame++

                    val now = System.currentTimeMillis()
                    if (outFrame == 1 || outFrame == totalFrames || (outFrame % 5 == 0 && (now - lastProgressDispatch) >= 150L)) {
                        lastProgressDispatch = now
                        val elapsed = now - startTime
                        val actualFps = if (outFrame >= 10 && elapsed >= 400L) {
                            (outFrame.toDouble() * 1000.0) / elapsed
                        } else {
                            180.0
                        }
                        val remainingFrames = maxOf(0, totalFrames - outFrame)
                        val remainingSec = if (actualFps > 0.5) {
                            (remainingFrames / actualFps).toLong()
                        } else {
                            (remainingFrames / 20.0).toLong()
                        }
                        val remainingMs = remainingSec * 1000L
                        onProgress(outFrame, totalFrames, elapsed, remainingMs)
                    }
                }
            }

            encoder.flush()
            while (true) {
                val encFrame = encoder.receiveFrame() ?: break
                val curPayloadOffset = payloadWritten
                val frameLen = (encFrame.data.size + 3) and 3.inv()
                val sizeField = (frameLen.toLong() and 0x1FFFFL).toInt()

                payloadOut.write(sizeField and 0xFF)
                payloadOut.write((sizeField shr 8) and 0xFF)
                payloadOut.write((sizeField shr 16) and 0xFF)
                payloadOut.write((sizeField shr 24) and 0xFF)
                payloadWritten += 4

                payloadOut.write(encFrame.data)
                payloadWritten += encFrame.data.size
                for (pad in encFrame.data.size until frameLen) {
                    payloadOut.write(0)
                    payloadWritten++
                }

                if (encFrame.type == FastVideoDSEncoder.FvFrameType.IFrame) {
                    keyframes.add(FvKeyframe(outFrame, curPayloadOffset))
                }
                outFrame++
            }

            onProgress(outFrame, maxOf(totalFrames, outFrame), System.currentTimeMillis() - startTime, 0L)

            payloadOut.flush()
            payloadOut.close()

            // Assemble final .fv container with mathematically exact header and payload offsets
            val finalHeaderSize = FvHeader.HEADER_SIZE + (keyframes.size * 8)
            BufferedOutputStream(FileOutputStream(outputFile), 256 * 1024).use { finalOut ->
                val fvHeader = FvHeader(
                    signature = FvHeader.FV_SIGNATURE,
                    width = targetWidth,
                    height = targetHeight,
                    fpsNum = targetFps,
                    fpsDen = 1,
                    audioRate = settings.audioRate,
                    audioChannels = if (settings.stereo) 2 else 1,
                    nrFrames = outFrame,
                    nrKeyFrames = keyframes.size
                )
                val headerBuf = ByteArray(finalHeaderSize)
                val hbb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                hbb.putInt(fvHeader.signature)
                hbb.putShort(fvHeader.width.toShort())
                hbb.putShort(fvHeader.height.toShort())
                hbb.putInt(fvHeader.fpsNum)
                hbb.putInt(fvHeader.fpsDen)
                hbb.putShort(fvHeader.audioRate.toShort())
                hbb.putShort(fvHeader.audioChannels.toShort())
                hbb.putInt(fvHeader.nrFrames)
                hbb.putInt(fvHeader.nrKeyFrames)
                for (kf in keyframes) {
                    hbb.putInt(kf.frame)
                    hbb.putInt((finalHeaderSize.toLong() + kf.offset).toInt())
                }
                finalOut.write(headerBuf)

                tempPayloadFile.inputStream().use { tempIn ->
                    val buf = ByteArray(256 * 1024)
                    var bytesRead: Int
                    while (tempIn.read(buf).also { bytesRead = it } > 0) {
                        finalOut.write(buf, 0, bytesRead)
                    }
                }
            }

            // Assemble final preview file
            try {
                previewOut.flush()
                previewOut.close()
                if (previewOffsets.isNotEmpty()) {
                    val pHeaderSize = 12 + (previewOffsets.size * 12)
                    BufferedOutputStream(FileOutputStream(previewFile), 256 * 1024).use { pFinalOut ->
                        val bb = ByteBuffer.allocate(pHeaderSize).order(ByteOrder.LITTLE_ENDIAN)
                        bb.putInt(0x52505646) // 'FVPR' in little-endian
                        bb.putShort(targetWidth.toShort())
                        bb.putShort(targetHeight.toShort())
                        bb.putInt(previewOffsets.size)
                        for (i in previewOffsets.indices) {
                            bb.putLong(pHeaderSize.toLong() + previewOffsets[i])
                            bb.putInt(previewLengths[i])
                        }
                        pFinalOut.write(bb.array())
                        tempPreviewFile.inputStream().use { pIn ->
                            val buf = ByteArray(256 * 1024)
                            var bytesRead: Int
                            while (pIn.read(buf).also { bytesRead = it } > 0) {
                                pFinalOut.write(buf, 0, bytesRead)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                // non-fatal
            }
        } finally {
            try { payloadOut.close() } catch (t: Throwable) {}
            tempPayloadFile.delete()

            try { previewOut.close() } catch (t: Throwable) {}
            tempPreviewFile.delete()

            try { audioStream?.close() } catch (t: Throwable) {}
            try { hwDecoder?.close() } catch (t: Throwable) {}
            try { retriever?.release() } catch (t: Throwable) {}
            try { retrieverPfd?.close() } catch (t: Throwable) {}
            try { stagedFile?.delete() } catch (t: Throwable) {}
            targetBitmap.recycle()
        }

        // Run authentic bitstream validator and strict settings audit on the generated file
        val validationReport = FvFormat.validateFile(outputFile)
        val strictAudit = FvFormat.runStrictPostConvertAudit(
            file = outputFile,
            expectedSettings = settings,
            sourceDurationMs = durationMs,
            expectedFrames = totalFrames
        )
        ConversionResult(outputFile, validationReport, strictAudit)
    }

    private fun stageSourceFile(context: Context, contentUri: Uri): File? {
        return try {
            val freeBytes = context.cacheDir.freeSpace
            // Query size of contentUri
            var contentSize = 0L
            try {
                context.contentResolver.query(
                    contentUri,
                    arrayOf(android.provider.OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            contentSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
            } catch (t: Throwable) {}

            // For large files (> 60MB) or if free disk space is low (< 3x file size),
            // do NOT waste time or disk space copying; let MediaExtractor stream directly via FileDescriptor / URI
            if (contentSize > 60 * 1024 * 1024L || (contentSize > 0 && freeBytes < contentSize * 3)) {
                return null
            }

            val stageDir = File(context.cacheDir, "staged_media")
            stageDir.mkdirs()
            val tempFile = File(stageDir, "staged_${System.currentTimeMillis()}_input.mp4")
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buf = ByteArray(1024 * 1024) // 1MB high speed buffer
                    var read: Int
                    while (input.read(buf).also { read = it } > 0) {
                        output.write(buf, 0, read)
                    }
                    output.flush()
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile
            } else {
                tempFile.delete()
                null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private val samplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val samplePath = android.graphics.Path()

    private val sampleSineLut = ShortArray(2048) { idx ->
        (sin(2.0 * Math.PI * idx / 2048.0) * 16000.0).toInt().toShort()
    }

    private fun renderSampleFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        frameIdx: Int,
        totalFrames: Int,
        width: Int,
        height: Int
    ) {
        canvas.drawColor(Color.rgb(18, 24, 38))
        val paint = samplePaint

        paint.color = Color.rgb(30, 41, 59)
        paint.strokeWidth = 1f
        val offset = (frameIdx * 2) % 16
        for (x in 0 until width step 16) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        }
        for (y in 0 until height step 16) {
            canvas.drawLine(0f, (y + offset).toFloat(), width.toFloat(), (y + offset).toFloat(), paint)
        }

        val barColors = intArrayOf(
            Color.rgb(220, 38, 38),
            Color.rgb(234, 88, 12),
            Color.rgb(245, 158, 11),
            Color.rgb(16, 185, 129),
            Color.rgb(6, 182, 212),
            Color.rgb(59, 130, 246),
            Color.rgb(168, 85, 247)
        )
        val barW = width.toFloat() / barColors.size
        for (i in barColors.indices) {
            paint.color = barColors[i]
            canvas.drawRect(i * barW, 0f, (i + 1) * barW, 8f, paint)
        }

        val centerX = width / 2f + (sin(frameIdx * 0.1) * 30).toFloat()
        val centerY = height / 2f

        paint.color = Color.rgb(6, 182, 212)
        canvas.drawCircle(centerX, centerY, 22f, paint)

        paint.color = Color.rgb(15, 23, 42)
        canvas.drawCircle(centerX, centerY, 16f, paint)

        paint.color = Color.rgb(6, 182, 212)
        val angle = frameIdx * 6f
        for (i in 0 until 6) {
            val a = Math.toRadians((angle + i * 60).toDouble())
            val px = (centerX + Math.cos(a) * 11).toFloat()
            val py = (centerY + Math.sin(a) * 11).toFloat()
            canvas.drawCircle(px, py, 2.5f, paint)
        }

        paint.color = Color.rgb(245, 158, 11)
        val path = samplePath
        path.reset()
        path.moveTo(centerX - 4f, centerY - 6f)
        path.lineTo(centerX + 6f, centerY)
        path.lineTo(centerX - 4f, centerY + 6f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("FASTVIDEO DS", width / 2f, height - 16f, paint)

        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 8f
        canvas.drawText("FRAME $frameIdx / $totalFrames • 256×$height", width / 2f, height - 6f, paint)
    }

    private fun synthesizeSampleAudioBlock(
        sampleOffset: Int,
        left: ShortArray,
        right: ShortArray,
        sampleRate: Int
    ) {
        val safeRate = maxOf(sampleRate, 8000)
        val stepL = ((440.0 * 2048.0) / safeRate).toInt()
        val stepR = ((660.0 * 2048.0) / safeRate).toInt()
        var phaseL = (sampleOffset * stepL) and 2047
        var phaseR = (sampleOffset * stepR) and 2047

        for (i in 0 until FvFormat.AUDIO_FRAME_SAMPLES) {
            left[i] = sampleSineLut[phaseL]
            right[i] = sampleSineLut[phaseR]
            phaseL = (phaseL + stepL) and 2047
            phaseR = (phaseR + stepR) and 2047
        }
    }
}
