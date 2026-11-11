package com.example.fastvideo.codec

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * Represents a frame with 5-bit color channels (stored as 8-bit shifted by 3),
 * with Bayer 4x4 matrix ordered dithering matching FastVideoDS Rgb555Frame.cs.
 */
class Rgb555Frame(val width: Int, val height: Int) {
    val r: ByteArray = ByteArray(width * height)
    val g: ByteArray = ByteArray(width * height)
    val b: ByteArray = ByteArray(width * height)

    fun copy(): Rgb555Frame {
        val f = Rgb555Frame(width, height)
        System.arraycopy(r, 0, f.r, 0, r.size)
        System.arraycopy(g, 0, f.g, 0, g.size)
        System.arraycopy(b, 0, f.b, 0, b.size)
        return f
    }

    fun toBitmap(reusableBitmap: Bitmap? = null): Bitmap {
        val bmp = if (reusableBitmap != null && reusableBitmap.width == width && reusableBitmap.height == height) {
            reusableBitmap
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val pixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val cr = r[i].toInt() and 0xFF
            val cg = g[i].toInt() and 0xFF
            val cb = b[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private val scratchPixels = IntArray(width * height)

    fun fromBitmap(bitmap: Bitmap) {
        val scaled = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        scaled.getPixels(scratchPixels, 0, width, 0, 0, width, height)

        val lut = ditherLut
        val mat = ditherMatrixFlat

        for (y in 0 until height) {
            val row = y * width
            val matY = (y and 3) shl 2
            for (x in 0 until width) {
                val pixel = scratchPixels[row + x]
                val cr = (pixel shr 16) and 0xFF
                val cg = (pixel shr 8) and 0xFF
                val cb = pixel and 0xFF

                val step = mat[matY or (x and 3)]
                r[row + x] = lut[(cr shl 4) or step]
                g[row + x] = lut[(cg shl 4) or step]
                b[row + x] = lut[(cb shl 4) or step]
            }
        }
    }

    companion object {
        val ditherMatrixFlat = intArrayOf(
            0, 12, 3, 15,
            8, 4, 11, 7,
            2, 14, 1, 13,
            10, 6, 9, 5
        )

        private val toLinear8 = DoubleArray(256) { i -> (i / 255.0).pow(2.2) }
        private val toLinear5 = DoubleArray(32) { i -> (i / 31.0).pow(2.2) }

        val ditherLut: ByteArray = ByteArray(256 * 16).apply {
            for (color in 0..255) {
                for (step in 0..15) {
                    val noise = step / 16.0
                    val c0 = color * 31 / 255
                    val c1 = (c0 + 1).coerceIn(0, 31)
                    val discr = toLinear5[c0] * (1.0 - noise) + toLinear5[c1] * noise
                    val pr = if (discr < toLinear8[color]) c1 else c0
                    this[(color shl 4) or step] = (pr shl 3).toByte()
                }
            }
        }
    }
}

class RefFrame(val frame: Rgb555Frame) {
    var refCount = 1

    fun ref(): RefFrame {
        refCount++
        return this
    }

    fun unref() {
        refCount--
    }
}
