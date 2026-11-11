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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
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
  fun `test elias gamma varints`() {
    val writer = BitWriter()
    val unsignedValues = longArrayOf(0L, 1L, 5L, 42L, 100L, 255L)
    for (v in unsignedValues) {
      writer.writeUnsignedVarInt(v)
    }
    val signedValues = intArrayOf(0, -1, 1, -5, 5, -50, 50)
    for (v in signedValues) {
      writer.writeSignedVarInt(v)
    }
    writer.flush()
    val bytes = writer.toByteArray()

    val reader = BitReader(bytes)
    for (v in unsignedValues) {
      assertEquals(v, reader.readUnsignedVarInt())
    }
    for (v in signedValues) {
      assertEquals(v, reader.readSignedVarInt())
    }
  }

  @Test
  fun `test adpcm audio codec roundtrip`() {
    // Generate a simple 440Hz sine wave PCM chunk (256 samples)
    val samples = ShortArray(256)
    for (i in samples.indices) {
      samples[i] = (Math.sin(2.0 * Math.PI * 440.0 * i / 47605.0) * 16000.0).toInt().toShort()
    }

    val (encoded, _) = Adpcm.encode(samples)
    assertEquals(132, encoded.size) // 4 bytes header + 128 bytes nibbles

    // Decode back
    val decoded = Adpcm.decode(encoded)
    assertEquals(256, decoded.size)

    // Check correlation between original and decoded signal
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
  fun `test fastvideo header serialization and parsing`() {
    val header = FvHeader(
      signature = FvHeader.FV_SIGNATURE,
      width = 256,
      height = 144,
      fpsNum = 24,
      fpsDen = 1,
      audioRate = 47605,
      audioChannels = 2,
      nrFrames = 100,
      nrKeyFrames = 2
    )

    val keyframes = listOf(
      FvKeyframe(frame = 0, offset = 0L),
      FvKeyframe(frame = 50, offset = 12000L)
    )

    val out = ByteArrayOutputStream()
    FvFormat.writeHeader(out, header, keyframes)
    val bytes = out.toByteArray()

    val (parsedHeader, parsedKf) = FvFormat.parseHeader(ByteArrayInputStream(bytes))
    assertEquals(FvHeader.FV_SIGNATURE, parsedHeader.signature)
    assertEquals(256, parsedHeader.width)
    assertEquals(144, parsedHeader.height)
    assertEquals(24, parsedHeader.fpsNum)
    assertEquals(47605, parsedHeader.audioRate)
    assertEquals(2, parsedHeader.audioChannels)
    assertEquals(100, parsedHeader.nrFrames)
    assertEquals(2, parsedKf.size)
    assertEquals(0, parsedKf[0].frame)
    assertEquals(50, parsedKf[1].frame)
  }

  @Test
  fun `test fastvideo container file validation`() {
    val tempFile = File.createTempFile("fv_test", ".fv")
    try {
      val header = FvHeader(
        signature = FvHeader.FV_SIGNATURE,
        width = 256,
        height = 144,
        fpsNum = 24,
        fpsDen = 1,
        audioRate = 47605,
        audioChannels = 1,
        nrFrames = 1,
        nrKeyFrames = 1
      )

      val expectedHeaderLen = FvHeader.HEADER_SIZE + 8
      FileOutputStream(tempFile).use { out ->
        val keyframes = listOf(FvKeyframe(frame = 0, offset = expectedHeaderLen.toLong()))
        FvFormat.writeHeader(out, header, keyframes)

        val videoPadded = 16
        val audioFrames = 1
        val sizeField = (audioFrames shl 17) or (videoPadded and 0x1FFFF)
        val b = ByteArray(4)
        b[0] = (sizeField and 0xFF).toByte()
        b[1] = ((sizeField shr 8) and 0xFF).toByte()
        b[2] = ((sizeField shr 16) and 0xFF).toByte()
        b[3] = ((sizeField shr 24) and 0xFF).toByte()
        out.write(b)

        out.write(ByteArray(132))
        out.write(ByteArray(16))
      }

      val result = FvFormat.validateFile(tempFile)
      assertTrue("Container validation should pass: ${result.errors}", result.isValid)
      assertEquals(256, result.header?.width)
      assertEquals(144, result.header?.height)
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `test 10-point strict after-convert audit`() {
    val tempFile = File.createTempFile("strict_audit_test", ".fv")
    try {
      val header = FvHeader(
        signature = FvHeader.FV_SIGNATURE,
        width = 256,
        height = 144,
        fpsNum = 20,
        fpsDen = 1,
        audioRate = 47605,
        audioChannels = 2,
        nrFrames = 20,
        nrKeyFrames = 1
      )

      val expectedHeaderLen = FvHeader.HEADER_SIZE + 8
      FileOutputStream(tempFile).use { out ->
        val keyframes = listOf(FvKeyframe(frame = 0, offset = expectedHeaderLen.toLong()))
        FvFormat.writeHeader(out, header, keyframes)

        repeat(20) {
          val videoPadded = 32
          val audioFrames = 2
          val sizeField = (audioFrames shl 17) or (videoPadded and 0x1FFFF)
          val b = ByteArray(4)
          b[0] = (sizeField and 0xFF).toByte()
          b[1] = ((sizeField shr 8) and 0xFF).toByte()
          b[2] = ((sizeField shr 16) and 0xFF).toByte()
          b[3] = ((sizeField shr 24) and 0xFF).toByte()
          out.write(b)

          out.write(ByteArray(132 * 2))
          out.write(ByteArray(32))
        }
      }

      val settings = com.example.fastvideo.model.ConversionSettings(
        width = 256,
        height = 144,
        fps = 20,
        audioRate = 47605,
        stereo = true,
        maxGopLength = 120
      )

      val audit = FvFormat.runStrictPostConvertAudit(
        file = tempFile,
        expectedSettings = settings,
        sourceDurationMs = 1000L,
        expectedFrames = 20
      )

      assertEquals(10, audit.checks.size)
      assertTrue("10-point audit must pass on compliant bitstream", audit.isPassed)
      assertTrue("Container signature check must pass", audit.checks[0].passed)
      assertTrue("Dimensions check must pass", audit.checks[1].passed)
      assertTrue("Framerate check must pass", audit.checks[2].passed)
      assertTrue("Hardware audio sample rate check must pass", audit.checks[3].passed)
      assertTrue("Stereo channel check must pass", audit.checks[4].passed)
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `test fast motion estimation early exit for rapid mobile conversion`() {
    val srcFrame = com.example.fastvideo.codec.Rgb555Frame(256, 144)
    val targetR = ByteArray(64) { 0 }
    val targetG = ByteArray(64) { 0 }
    val targetB = ByteArray(64) { 0 }
    val center = com.example.fastvideo.codec.MotionVector(20, 20)
    val cheap = com.example.fastvideo.codec.MotionVector(20, 20)

    val (mvTurbo, score) = com.example.fastvideo.codec.MotionEstimation.findMotionVector(
      targetR = targetR,
      targetG = targetG,
      targetB = targetB,
      src = srcFrame,
      center = center,
      cheap = cheap,
      earlyExitThreshold = 96,
      maxDiamondSteps = 4
    )

    assertEquals(20, mvTurbo.x)
    assertEquals(20, mvTurbo.y)
    assertTrue("Score should be non-negative", score >= 0)
  }

  @Test
  fun `test 800x ultimate fast encoder bypasses motion estimation`() {
    val encoder = com.example.fastvideo.codec.FastVideoDSEncoder(
      width = 256,
      height = 144,
      q = 30,
      isUltimateFast = true
    )

    val frame = com.example.fastvideo.codec.Rgb555Frame(256, 144)
    // Send 5 frames
    for (i in 0 until 5) {
      encoder.sendFrame(frame)
    }
    encoder.flush()

    var count = 0
    while (true) {
      val enc = encoder.receiveFrame() ?: break
      assertEquals(com.example.fastvideo.codec.FastVideoDSEncoder.FvFrameType.IFrame, enc.type)
      assertTrue("Encoded frame data should not be empty", enc.data.isNotEmpty())
      count++
    }
    assertEquals(5, count)
  }
}
