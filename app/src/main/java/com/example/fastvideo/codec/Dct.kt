package com.example.fastvideo.codec

/**
 * FastVideoDS 4-point Discrete Cosine Transform.
 * Operates on 4 pixels per sub-block.
 */
object Dct {
    fun dct4NoDiv(pixels: IntArray, dst: IntArray) {
        val t0 = pixels[0] + pixels[1]
        val t1 = pixels[0] - pixels[1]
        val t2 = pixels[2] + pixels[3]
        val t3 = pixels[2] - pixels[3]
        dst[0] = t0 + t2
        dst[1] = t0 - t2
        dst[2] = t1 + t3
        dst[3] = t1 - t3
    }

    fun idct4(dct: IntArray, pixels: ByteArray, dst: ByteArray) {
        val r0 = dct[0] + 16
        val t0 = r0 + dct[1]
        val t2 = r0 - dct[1]
        val t1 = dct[2] + dct[3]
        val t3 = dct[2] - dct[3]

        val p0 = (pixels[0].toInt() and 0xFF) ushr 3
        val p1 = (pixels[1].toInt() and 0xFF) ushr 3
        val p2 = (pixels[2].toInt() and 0xFF) ushr 3
        val p3 = (pixels[3].toInt() and 0xFF) ushr 3

        val d0 = (p0 + ((t0 + t1) shr 5)).coerceIn(0, 31) shl 3
        val d1 = (p1 + ((t0 - t1) shr 5)).coerceIn(0, 31) shl 3
        val d2 = (p2 + ((t2 + t3) shr 5)).coerceIn(0, 31) shl 3
        val d3 = (p3 + ((t2 - t3) shr 5)).coerceIn(0, 31) shl 3

        dst[0] = d0.toByte()
        dst[1] = d1.toByte()
        dst[2] = d2.toByte()
        dst[3] = d3.toByte()
    }
}
