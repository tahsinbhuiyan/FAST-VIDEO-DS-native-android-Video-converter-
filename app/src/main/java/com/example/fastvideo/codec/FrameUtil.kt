package com.example.fastvideo.codec

import kotlin.math.abs

object FrameUtil {
    fun getTile8(data: ByteArray, stride: Int, srcX: Int, srcY: Int, result: ByteArray) {
        for (y in 0 until 8) {
            val srcOffset = (srcY + y) * stride + srcX
            val dstOffset = y * 8
            System.arraycopy(data, srcOffset, result, dstOffset, 8)
        }
    }

    fun setTile8(data: ByteArray, stride: Int, dstX: Int, dstY: Int, src: ByteArray) {
        for (y in 0 until 8) {
            val dstOffset = (dstY + y) * stride + dstX
            val srcOffset = y * 8
            System.arraycopy(src, srcOffset, data, dstOffset, 8)
        }
    }

    fun getTile2x2Step2(data: ByteArray, stride: Int, srcX: Int, srcY: Int, dst: ByteArray) {
        dst[0] = data[srcY * stride + srcX]
        dst[1] = data[srcY * stride + srcX + 2]
        dst[2] = data[(srcY + 2) * stride + srcX]
        dst[3] = data[(srcY + 2) * stride + srcX + 2]
    }

    fun setTile2x2Step2(data: ByteArray, stride: Int, dstX: Int, dstY: Int, src: ByteArray) {
        data[dstY * stride + dstX] = src[0]
        data[dstY * stride + dstX + 2] = src[1]
        data[(dstY + 2) * stride + dstX] = src[2]
        data[(dstY + 2) * stride + dstX + 2] = src[3]
    }

    fun getTileHalf8(data: ByteArray, width: Int, height: Int, srcX: Int, srcY: Int, result: ByteArray) {
        if (((srcX or srcY) and 1) == 0) {
            // Full integer pixel coordinates
            val startX = srcX shr 1
            val startY = srcY shr 1
            if (startX >= 0 && startX + 7 < width && startY >= 0 && startY + 7 < height) {
                for (y in 0 until 8) {
                    val srcOffset = (startY + y) * width + startX
                    System.arraycopy(data, srcOffset, result, y * 8, 8)
                }
            } else {
                for (y in 0 until 8) {
                    val y1 = (startY + y).coerceIn(0, height - 1)
                    val row = y1 * width
                    for (x in 0 until 8) {
                        val x1 = (startX + x).coerceIn(0, width - 1)
                        result[y * 8 + x] = data[row + x1]
                    }
                }
            }
        } else if ((srcY and 1) == 0) {
            // Half-pixel in X only
            val startX = srcX shr 1
            val startY = srcY shr 1
            for (y in 0 until 8) {
                val y1 = (startY + y).coerceIn(0, height - 1)
                val row = y1 * width
                for (x in 0 until 8) {
                    val x1 = (startX + x).coerceIn(0, width - 1)
                    val x2 = (startX + x + 1).coerceIn(0, width - 1)
                    val p1 = data[row + x1].toInt() and 0xFF
                    val p2 = data[row + x2].toInt() and 0xFF
                    val avg = (p1 + p2 + 1) shr 1
                    result[y * 8 + x] = avg.toByte()
                }
            }
        } else if ((srcX and 1) == 0) {
            // Half-pixel in Y only
            val startX = srcX shr 1
            val startY = srcY shr 1
            for (y in 0 until 8) {
                val y1 = (startY + y).coerceIn(0, height - 1)
                val y2 = (startY + y + 1).coerceIn(0, height - 1)
                val row1 = y1 * width
                val row2 = y2 * width
                for (x in 0 until 8) {
                    val x1 = (startX + x).coerceIn(0, width - 1)
                    val p1 = data[row1 + x1].toInt() and 0xFF
                    val p2 = data[row2 + x1].toInt() and 0xFF
                    val avg = (p1 + p2 + 1) shr 1
                    result[y * 8 + x] = avg.toByte()
                }
            }
        } else {
            // Half-pixel in both X and Y
            val startX = srcX shr 1
            val startY = srcY shr 1
            for (y in 0 until 8) {
                val y1 = (startY + y).coerceIn(0, height - 1)
                val y2 = (startY + y + 1).coerceIn(0, height - 1)
                val row1 = y1 * width
                val row2 = y2 * width
                for (x in 0 until 8) {
                    val x1 = (startX + x).coerceIn(0, width - 1)
                    val x2 = (startX + x + 1).coerceIn(0, width - 1)
                    val p1 = data[row1 + x1].toInt() and 0xFF
                    val p2 = data[row1 + x2].toInt() and 0xFF
                    val p3 = data[row2 + x1].toInt() and 0xFF
                    val p4 = data[row2 + x2].toInt() and 0xFF
                    val avg = (p1 + p2 + p3 + p4 + 2) shr 2
                    result[y * 8 + x] = avg.toByte()
                }
            }
        }
    }

    fun sad64(a: ByteArray, b: ByteArray): Int {
        var sum = 0
        for (i in 0 until 64) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            sum += if (d >= 0) d else -d
        }
        return sum
    }

    fun sad(a: ByteArray, b: ByteArray): Long {
        var sum = 0L
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            sum += if (d >= 0) d else -d
        }
        return sum
    }
}
