package com.example.fastvideo.codec

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * FastVideoDS 4-bit IMA-ADPCM audio encoder and decoder.
 * Exact port of Gericom's FastVideoDSEncoder/Adpcm.cs.
 *
 * Each audio block encodes 256 samples (128 bytes nibbles + 4 bytes header [lastSample, lastIdx] = 132 bytes).
 */
object Adpcm {
    private val indexTable = intArrayOf(
        -3, -3, -2, -1, 2, 4, 6, 8,
        -3, -3, -2, -1, 2, 4, 6, 8
    )

    val stepTable = shortArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14,
        16, 17, 19, 21, 23, 25, 28,
        31, 34, 37, 41, 45, 50, 55,
        60, 66, 73, 80, 88, 97, 107,
        118, 130, 143, 157, 173, 190, 209,
        230, 253, 279, 307, 337, 371, 408,
        449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552,
        1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026,
        4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630,
        9493, 10442, 11487, 12635, 13899, 15289, 16818,
        18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    data class State(val lastSample: Short, val lastIdx: Int)

    private fun getBestTableIndex(diff: Int): Int {
        var lowestDiff = Int.MAX_VALUE
        var lowestIdx = -1
        val absDiff = abs(diff)
        for (i in stepTable.indices) {
            val d = abs(absDiff - stepTable[i].toInt())
            if (d < lowestDiff) {
                lowestDiff = d
                lowestIdx = i
            }
        }
        return lowestIdx
    }

    fun encode(samples: ShortArray, state: State? = null, emitHeader: Boolean = true): Pair<ByteArray, State> {
        require((samples.size and 7) == 0) { "Samples must be multiple of 8" }

        val result = ByteArray(4 + samples.size / 2)
        val buf = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        var idx: Int
        var last: Short

        if (state == null) {
            require(emitHeader) { "emitHeader must be true when state is null" }
            val firstDiff = if (samples.size > 1) samples[1] - samples[0] else 0
            idx = (getBestTableIndex(firstDiff) + 3).coerceAtMost(88)
            val initial = samples[0] - (stepTable[idx] / 8)
            last = initial.coerceIn(-0x7FFF, 0x7FFF).toShort()
        } else {
            idx = state.lastIdx
            last = state.lastSample
        }

        var offset = 0
        if (emitHeader) {
            buf.putShort(0, last)
            buf.putShort(2, idx.toShort())
            offset += 4
        }

        for (i in samples.indices step 8) {
            var nibbles = 0L
            for (j in 0 until 8) {
                val step = stepTable[idx].toInt()
                val targetSample = samples[i + j].toInt()
                var best = -1
                var bestScore = Int.MAX_VALUE

                for (k in 0 until 16) {
                    val diff2 = (step * ((k and 7) * 2 + 1)) shr 3
                    val sign = if (((k shr 3) and 1) == 1) -1 else 1
                    var sample3 = (last.toInt() + diff2 * sign).coerceIn(-0x7FFF, 0x7FFF)
                    var score = abs(sample3 - targetSample)

                    if (i + j + 1 < samples.size) {
                        val nextIdx = (idx + indexTable[k]).coerceIn(0, 88)
                        val step2 = stepTable[nextIdx].toInt()
                        var bestScore2 = Int.MAX_VALUE
                        val nextTarget = samples[i + j + 1].toInt()

                        for (l in 0 until 16) {
                            val diff3 = (step2 * ((l and 7) * 2 + 1)) shr 3
                            val sign2 = if (((l shr 3) and 1) == 1) -1 else 1
                            val sample4 = (sample3 + diff3 * sign2).coerceIn(-0x7FFF, 0x7FFF)
                            val score2 = abs(sample4 - nextTarget)
                            if (score2 < bestScore2) {
                                bestScore2 = score2
                            }
                        }
                        score += bestScore2
                    }

                    if (score < bestScore) {
                        bestScore = score
                        best = k
                    }
                }

                nibbles = nibbles or ((best.toLong() and 0xF) shl (j * 4))
                val diff = (step * ((best and 7) * 2 + 1)) shr 3
                val sign = if (((best shr 3) and 1) == 1) -1 else 1
                val sample2 = (last.toInt() + diff * sign).coerceIn(-0x7FFF, 0x7FFF)
                last = sample2.toShort()
                idx = (idx + indexTable[best]).coerceIn(0, 88)
            }

            buf.putInt(offset, nibbles.toInt())
            offset += 4
        }

        return Pair(result, State(last, idx))
    }

    fun decode(data: ByteArray): ShortArray {
        require((data.size and 3) == 0) { "Samples must be multiple of 4 bytes" }
        val outSamples = ShortArray((data.size - 4) * 2)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        var last = buf.getShort(0)
        var idx = buf.getShort(2).toInt() and 0xFFFF
        var offset = 4

        var outIdx = 0
        while (outIdx < outSamples.size) {
            var nibbles = buf.getInt(offset)
            offset += 4
            for (j in 0 until 8) {
                val nibble = nibbles and 0xF
                nibbles = nibbles ushr 4

                val step = stepTable[idx].toInt()
                val diff = (step * ((nibble and 7) * 2 + 1)) shr 3
                val sign = if (((nibble shr 3) and 1) == 1) -1 else 1
                val sample = (last.toInt() + diff * sign).coerceIn(-0x7FFF, 0x7FFF)

                outSamples[outIdx++] = sample.toShort()
                last = sample.toShort()
                idx = (idx + indexTable[nibble]).coerceIn(0, 88)
            }
        }
        return outSamples
    }
}
