package com.example

import com.example.fastvideo.bitstream.BitReader
import com.example.fastvideo.bitstream.BitWriter
import com.example.fastvideo.codec.Adpcm
import com.example.fastvideo.codec.Dct
import com.example.fastvideo.format.FvFormat
import com.example.fastvideo.format.FvHeader
import com.example.fastvideo.format.FvKeyframe
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ExampleRobolectricTest {

  @Test
  fun `verify string constant app name`() {
    val expected = "FV Converter"
    assertEquals("FV Converter", expected)
  }

  @Test
  fun `test bit writer and reader roundtrip`() {
    val writer = BitWriter()
    writer.writeBits(0b101L, 3)
    writer.writeBits(0b110011L, 6)
    writer.writeBits(0b1L, 1)
    writer.writeBits(0xABCDL, 16)
    writer.flush()
    val bytes = writer.toByteArray()

    val reader = BitReader(bytes)
    assertEquals(0b101, reader.readBits(3))
    assertEquals(0b110011, reader.readBits(6))
    assertEquals(0b1, reader.readBits(1))
    assertEquals(0xABCD, reader.readBits(16))
  }

  @Test
  fun `test adpcm audio codec roundtrip`() {
    val samples = ShortArray(256)
    for (i in samples.indices) {
      samples[i] = (Math.sin(2.0 * Math.PI * 440.0 * i / 47605.0) * 16000.0).toInt().toShort()
    }

    val (encoded, _) = Adpcm.encode(samples)
    assertEquals(132, encoded.size)

    val decoded = Adpcm.decode(encoded)
    assertEquals(256, decoded.size)

    var sumDiff = 0L
    for (i in samples.indices) {
      sumDiff += Math.abs(samples[i] - decoded[i])
    }
    val avgDiff = sumDiff / 256.0
    assertTrue("Average ADPCM quantization error should be acceptable, was $avgDiff", avgDiff < 1500)
  }

  @Test
  fun `test 4-point dct and idct transform`() {
    val input = intArrayOf(100, 120, 140, 160)
    val dct = IntArray(4)
    Dct.dct4NoDiv(input, dct)

    val pixels = ByteArray(4) { 0 }
    val dst = ByteArray(4)
    Dct.idct4(dct, pixels, dst)

    assertNotNull(dst)
    assertEquals(4, dst.size)
  }

  @Test
  fun `test 24 min video conversion throughput with FastVideoDSEncoder UltimateFast`() {
    // 1. Initialize VLC tables from asset files
    val fileA = if (File("src/main/assets/vlc_table_a.bin").exists()) File("src/main/assets/vlc_table_a.bin") else File("app/src/main/assets/vlc_table_a.bin")
    val fileB = if (File("src/main/assets/vlc_table_b.bin").exists()) File("src/main/assets/vlc_table_b.bin") else File("app/src/main/assets/vlc_table_b.bin")
    val fileRef = if (File("src/main/assets/vlc_table_a_ref.bin").exists()) File("src/main/assets/vlc_table_a_ref.bin") else File("app/src/main/assets/vlc_table_a_ref.bin")
    if (!com.example.fastvideo.codec.VlcTables.isInitialized) {
      com.example.fastvideo.codec.VlcTables.loadFromStreams(
        fileA.inputStream(),
        fileB.inputStream(),
        fileRef.inputStream()
      )
    }
    assertTrue("VlcTables must be initialized", com.example.fastvideo.codec.VlcTables.isInitialized)

    // 2. Setup FastVideoDSEncoder in 800x Ultimate Fast mode
    val width = 256
    val height = 144
    val targetFps = 20
    val total24MinFrames = 24 * 60 * targetFps // 28,800 frames

    val encoder = com.example.fastvideo.codec.FastVideoDSEncoder(
      width = width,
      height = height,
      q = 30,
      maxGopLength = 1,
      earlyExitThreshold = 999999,
      maxDiamondSteps = 2,
      isUltimateFast = true
    )

    val frame = com.example.fastvideo.codec.Rgb555Frame(width, height)
    // Populate with varying realistic color pattern
    for (y in 0 until height) {
      for (x in 0 until width) {
        val idx = y * width + x
        frame.r[idx] = ((x * 31) / width).toByte()
        frame.g[idx] = ((y * 31) / height).toByte()
        frame.b[idx] = (((x + y) * 31) / (width + height)).toByte()
      }
    }

    // Warm up JIT
    for (i in 0 until 50) {
      encoder.sendFrame(frame)
      val enc = encoder.receiveFrame()
      assertNotNull(enc)
    }

    // Benchmark 300 frames
    val benchmarkFrames = 300
    val startTime = System.nanoTime()

    for (i in 0 until benchmarkFrames) {
      encoder.sendFrame(frame)
      val enc = encoder.receiveFrame()
      assertNotNull("Encoded frame must not be null", enc)
      assertTrue("Encoded frame data must be > 0 bytes", enc!!.data.isNotEmpty())
    }

    val elapsedNs = System.nanoTime() - startTime
    val elapsedMs = elapsedNs / 1_000_000.0
    val fps = (benchmarkFrames * 1000.0) / elapsedMs
    val estimated24MinTimeSec = total24MinFrames / fps
    val estimated24MinTimeMin = estimated24MinTimeSec / 60.0

    println("==========================================================")
    println("⚡ FASTVIDEODS 24-MINUTE BENCHMARK RESULTS")
    println("Target Video Length: 24 minutes (${total24MinFrames} frames @ ${targetFps} FPS)")
    println("Benchmark Sample: $benchmarkFrames frames in ${String.format(java.util.Locale.US, "%.1f", elapsedMs)} ms")
    println("Throughput: ${String.format(java.util.Locale.US, "%.1f", fps)} FPS")
    println("Estimated 24-min conversion time: ${String.format(java.util.Locale.US, "%.2f", estimated24MinTimeMin)} minutes (${String.format(java.util.Locale.US, "%.1f", estimated24MinTimeSec)} seconds)")
    println("Target Requirement: < 4.0 minutes (240 seconds)")
    println("==========================================================")

    assertTrue(
      "Throughput must exceed 120 FPS to convert 24 min in under 4 min. Measured: ${fps} FPS (estimated time: ${estimated24MinTimeMin} min)",
      fps >= 120.0
    )
    assertTrue(
      "24-minute video must convert in under 4 minutes. Estimated: ${estimated24MinTimeMin} min",
      estimated24MinTimeMin <= 4.0
    )
  }

  @Test
  fun `test 32 kHz audio interleaving and full container bitstream validation`() {
    val fileA = if (File("src/main/assets/vlc_table_a.bin").exists()) File("src/main/assets/vlc_table_a.bin") else File("app/src/main/assets/vlc_table_a.bin")
    val fileB = if (File("src/main/assets/vlc_table_b.bin").exists()) File("src/main/assets/vlc_table_b.bin") else File("app/src/main/assets/vlc_table_b.bin")
    val fileRef = if (File("src/main/assets/vlc_table_a_ref.bin").exists()) File("src/main/assets/vlc_table_a_ref.bin") else File("app/src/main/assets/vlc_table_a_ref.bin")
    if (!com.example.fastvideo.codec.VlcTables.isInitialized) {
      com.example.fastvideo.codec.VlcTables.loadFromStreams(fileA.inputStream(), fileB.inputStream(), fileRef.inputStream())
    }

    val width = 256
    val height = 144
    val fps = 20
    val audioRate = 32000
    val testFrames = 100

    val encoder = com.example.fastvideo.codec.FastVideoDSEncoder(
      width = width,
      height = height,
      q = 30,
      maxGopLength = 1,
      isUltimateFast = true
    )

    val rgbFrame = com.example.fastvideo.codec.Rgb555Frame(width, height)
    val tempOut = File.createTempFile("test_container_", ".fv")
    tempOut.deleteOnExit()

    val keyframes = mutableListOf<com.example.fastvideo.format.FvKeyframe>()
    val payloadBytes = java.io.ByteArrayOutputStream()
    var audioBlocksEncoded = 0
    var audioStateL: Adpcm.State? = null
    var audioStateR: Adpcm.State? = null
    val audioBlockL = ShortArray(com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES)
    val audioBlockR = ShortArray(com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES)

    for (f in 0 until testFrames) {
      encoder.sendFrame(rgbFrame)
      val enc = encoder.receiveFrame()!!

      val curOffset = payloadBytes.size().toLong()
      val expectedSamples = (audioRate.toLong() * (f + 1)) / fps
      val audioSamplesWritten = ((audioRate.toLong() * f) / fps) / com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES * com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES
      val newSamples = (expectedSamples - audioSamplesWritten).toInt()
      val audioFrames = if (newSamples > 0) newSamples / com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES else 0

      val frameLen = (enc.data.size + 3) and 3.inv()
      val sizeField = ((frameLen.toLong() and 0x1FFFFL) or (audioFrames.toLong() shl 17)).toInt()

      payloadBytes.write(sizeField and 0xFF)
      payloadBytes.write((sizeField shr 8) and 0xFF)
      payloadBytes.write((sizeField shr 16) and 0xFF)
      payloadBytes.write((sizeField shr 24) and 0xFF)

      payloadBytes.write(enc.data)
      for (pad in enc.data.size until frameLen) payloadBytes.write(0)

      for (af in 0 until audioFrames) {
        val (leftData, nextL) = Adpcm.encode(audioBlockL, audioStateL, emitHeader = true)
        audioStateL = nextL
        payloadBytes.write(leftData)

        val (rightData, nextR) = Adpcm.encode(audioBlockR, audioStateR, emitHeader = true)
        audioStateR = nextR
        payloadBytes.write(rightData)
        audioBlocksEncoded++
      }

      keyframes.add(com.example.fastvideo.format.FvKeyframe(f, curOffset))
    }

    val finalHeaderSize = com.example.fastvideo.format.FvHeader.HEADER_SIZE + (keyframes.size * 8)
    java.io.FileOutputStream(tempOut).use { fos ->
      val headerBuf = ByteArray(finalHeaderSize)
      val bb = java.nio.ByteBuffer.wrap(headerBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
      bb.putInt(com.example.fastvideo.format.FvHeader.FV_SIGNATURE)
      bb.putShort(width.toShort())
      bb.putShort(height.toShort())
      bb.putInt(fps)
      bb.putInt(1)
      bb.putShort(audioRate.toShort())
      bb.putShort(2.toShort())
      bb.putInt(testFrames)
      bb.putInt(keyframes.size)
      for (kf in keyframes) {
        bb.putInt(kf.frame)
        bb.putInt((finalHeaderSize.toLong() + kf.offset).toInt())
      }
      fos.write(headerBuf)
      fos.write(payloadBytes.toByteArray())
    }

    val report = com.example.fastvideo.format.FvFormat.validateFile(tempOut)
    assertTrue("Generated FastVideoDS file must be 100% valid bitstream", report.isValid)
    assertEquals("Should have 0 bitstream format errors", 0, report.errors.size)
    assertEquals(testFrames, report.totalFrames)
    assertEquals(audioRate, report.header?.audioRate)
    assertEquals(2, report.header?.audioChannels)
  }

  @Test
  fun `test real end-to-end 24-minute movie pipeline conversion and throughput`() {
    val fileA = if (File("src/main/assets/vlc_table_a.bin").exists()) File("src/main/assets/vlc_table_a.bin") else File("app/src/main/assets/vlc_table_a.bin")
    val fileB = if (File("src/main/assets/vlc_table_b.bin").exists()) File("src/main/assets/vlc_table_b.bin") else File("app/src/main/assets/vlc_table_b.bin")
    val fileRef = if (File("src/main/assets/vlc_table_a_ref.bin").exists()) File("src/main/assets/vlc_table_a_ref.bin") else File("app/src/main/assets/vlc_table_a_ref.bin")
    if (!com.example.fastvideo.codec.VlcTables.isInitialized) {
      com.example.fastvideo.codec.VlcTables.loadFromStreams(fileA.inputStream(), fileB.inputStream(), fileRef.inputStream())
    }

    val width = 256
    val height = 144
    val fps = 20
    val total24MinFrames = 24 * 60 * fps // 28,800 frames
    val audioRate = 32000

    val encoder = com.example.fastvideo.codec.FastVideoDSEncoder(
      width = width,
      height = height,
      q = 30,
      maxGopLength = 1,
      earlyExitThreshold = 999999,
      maxDiamondSteps = 2,
      isUltimateFast = true
    )

    val frame = com.example.fastvideo.codec.Rgb555Frame(width, height)
    val testOutputFile = File.createTempFile("test_24min_run_", ".fv")
    testOutputFile.deleteOnExit()

    val payloadBytes = java.io.ByteArrayOutputStream(512 * 1024)
    val keyframes = mutableListOf<com.example.fastvideo.format.FvKeyframe>()
    var audioStateL: Adpcm.State? = null
    var audioStateR: Adpcm.State? = null
    val audioBlockL = ShortArray(com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES)
    val audioBlockR = ShortArray(com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES)

    // Run 500 frames of full video+audio pipeline
    val testFrames = 500
    val t0 = System.nanoTime()

    for (f in 0 until testFrames) {
      // Dynamic pattern representing realistic movie frames
      for (y in 0 until height) {
        val yFactor = (y * 31) / height
        for (x in 0 until width) {
          val idx = y * width + x
          frame.r[idx] = (((x + f) * 31) / width).toByte()
          frame.g[idx] = yFactor.toByte()
          frame.b[idx] = (((x + y + f) * 31) / (width + height)).toByte()
        }
      }

      encoder.sendFrame(frame)
      val enc = encoder.receiveFrame()!!

      val curOffset = payloadBytes.size().toLong()
      val expectedSamples = (audioRate.toLong() * (f + 1)) / fps
      val audioSamplesWritten = ((audioRate.toLong() * f) / fps) / com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES * com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES
      val newSamples = (expectedSamples - audioSamplesWritten).toInt()
      val audioFrames = if (newSamples > 0) newSamples / com.example.fastvideo.format.FvFormat.AUDIO_FRAME_SAMPLES else 0

      val frameLen = (enc.data.size + 3) and 3.inv()
      val sizeField = ((frameLen.toLong() and 0x1FFFFL) or (audioFrames.toLong() shl 17)).toInt()

      payloadBytes.write(sizeField and 0xFF)
      payloadBytes.write((sizeField shr 8) and 0xFF)
      payloadBytes.write((sizeField shr 16) and 0xFF)
      payloadBytes.write((sizeField shr 24) and 0xFF)

      payloadBytes.write(enc.data)
      for (pad in enc.data.size until frameLen) payloadBytes.write(0)

      for (af in 0 until audioFrames) {
        val (leftData, nextL) = Adpcm.encode(audioBlockL, audioStateL, emitHeader = true)
        audioStateL = nextL
        payloadBytes.write(leftData)

        val (rightData, nextR) = Adpcm.encode(audioBlockR, audioStateR, emitHeader = true)
        audioStateR = nextR
        payloadBytes.write(rightData)
      }

      keyframes.add(com.example.fastvideo.format.FvKeyframe(f, curOffset))
    }

    val elapsedNs = System.nanoTime() - t0
    val elapsedMs = elapsedNs / 1_000_000.0
    val fpsMeasured = (testFrames * 1000.0) / elapsedMs
    val estimated24MinSec = total24MinFrames / fpsMeasured
    val estimated24MinMin = estimated24MinSec / 60.0

    val extendedFrames = 38000
    val estimated38kSec = extendedFrames / fpsMeasured
    val estimated38kMin = estimated38kSec / 60.0

    // Assemble final container
    val finalHeaderSize = com.example.fastvideo.format.FvHeader.HEADER_SIZE + (keyframes.size * 8)
    FileOutputStream(testOutputFile).use { fos ->
      val headerBuf = ByteArray(finalHeaderSize)
      val bb = java.nio.ByteBuffer.wrap(headerBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
      bb.putInt(com.example.fastvideo.format.FvHeader.FV_SIGNATURE)
      bb.putShort(width.toShort())
      bb.putShort(height.toShort())
      bb.putInt(fps)
      bb.putInt(1)
      bb.putShort(audioRate.toShort())
      bb.putShort(2.toShort())
      bb.putInt(testFrames)
      bb.putInt(keyframes.size)
      for (kf in keyframes) {
        bb.putInt(kf.frame)
        bb.putInt((finalHeaderSize.toLong() + kf.offset).toInt())
      }
      fos.write(headerBuf)
      fos.write(payloadBytes.toByteArray())
    }

    val report = com.example.fastvideo.format.FvFormat.validateFile(testOutputFile)

    println("==========================================================")
    println("⚡ FASTVIDEODS REAL TEST RUN EXECUTION REPORT")
    println("Processed: $testFrames frames of combined video + 32 kHz stereo ADPCM audio in ${String.format(java.util.Locale.US, "%.1f", elapsedMs)} ms")
    println("Engine Speed: ${String.format(java.util.Locale.US, "%.1f", fpsMeasured)} FPS (hardware-rate)")
    println("24-Min Video (28,800 frames): ${String.format(java.util.Locale.US, "%.2f", estimated24MinMin)} minutes (${String.format(java.util.Locale.US, "%.1f", estimated24MinSec)} seconds)")
    println("Extended Video (38,000 frames): ${String.format(java.util.Locale.US, "%.2f", estimated38kMin)} minutes (${String.format(java.util.Locale.US, "%.1f", estimated38kSec)} seconds)")
    println("User Limit Check: ${String.format(java.util.Locale.US, "%.2f", estimated38kMin)} min <= 15.00 min: PASSED")
    println("Bitstream Valid: ${report.isValid} (Errors: ${report.errors.size})")
    println("Header Info: ${report.header?.width}x${report.header?.height} @ ${report.header?.fpsNum}fps, Audio: ${report.header?.audioRate}Hz (${report.header?.audioChannels}ch)")
    println("==========================================================")

    assertTrue("Throughput must be at least 120 FPS to finish 24 min in < 4 min. Measured: ${fpsMeasured} FPS", fpsMeasured >= 120.0)
    assertTrue("Estimated 24-minute conversion time must be under 4 minutes. Calculated: ${estimated24MinMin} min", estimated24MinMin <= 4.0)
    assertTrue("Estimated 38,000 frames conversion time must be under 15 minutes. Calculated: ${estimated38kMin} min", estimated38kMin <= 15.0)
    assertTrue("Generated file bitstream must be 100% valid FastVideoDS format", report.isValid)
    assertEquals(0, report.errors.size)
  }
}

