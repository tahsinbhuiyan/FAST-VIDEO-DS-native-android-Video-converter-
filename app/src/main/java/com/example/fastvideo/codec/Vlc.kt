package com.example.fastvideo.codec

import com.example.fastvideo.bitstream.BitWriter

object Vlc {
    fun calcDctBitCount(dct: IntArray): Int {
        var lastNonZero = 0
        for (i in dct.indices.reversed()) {
            if (dct[i] != 0) {
                lastNonZero = i
                break
            }
        }
        return calcDctBitCount(dct, lastNonZero)
    }

    fun calcDctBitCount(dct: IntArray, lastNonZero: Int): Int {
        val bitLengthTable = VlcTables.bitLengthTable
        var bitCount = 0
        var skip = 0
        for (i in 0 until lastNonZero) {
            if (dct[i] == 0) {
                skip++
                continue
            }
            val v = dct[i]
            if (v + 64 in 0 until 128) {
                bitCount += bitLengthTable[(skip * 128) + (v + 64)]
            } else {
                bitCount += 28
            }
            skip = 0
        }

        if (lastNonZero in dct.indices) {
            val v = dct[lastNonZero]
            if (v + 64 in 0 until 128) {
                bitCount += bitLengthTable[128 * 64 + (skip * 128) + (v + 64)]
            } else {
                bitCount += 28
            }
        }
        return bitCount
    }

    fun encodeDct(dct: IntArray, writer: BitWriter) {
        val tabA = VlcTables.tableA
        val tabB = VlcTables.tableB
        val tabRef = VlcTables.tableARefLinear

        var lastNonZero = 0
        for (i in 63 downTo 0) {
            if (dct[i] != 0) {
                lastNonZero = i
                break
            }
        }

        var skip = 0
        for (i in dct.indices) {
            if (dct[i] == 0 && lastNonZero != 0) {
                if (i == lastNonZero) {
                    writer.writeBits(0L, 2)
                    break
                }
                skip++
                continue
            }

            var valAbs = dct[i]
            if (valAbs < 0) valAbs = -valAbs
            val isLast = if (i == lastNonZero) 1 else 0

            var handled = false
            if (valAbs <= 31) {
                val idx = tabRef[valAbs * 64 * 2 + skip * 2 + isLast]
                if (idx >= 0) {
                    val nrbits = tabA[idx].toInt() and 0xF
                    var tidx = idx.toLong()
                    if (nrbits < 12) {
                        tidx = tidx ushr (12 - nrbits)
                    } else if (nrbits > 12) {
                        tidx = tidx shl (nrbits - 12)
                    }
                    if (dct[i] < 0) tidx = tidx or 1L
                    writer.writeBits(tidx, nrbits)
                    skip = 0
                    handled = true
                } else {
                    val newskip = skip - (tabB[(valAbs or (isLast shl 6)) + 0x80].toInt() and 0xFF)
                    if (newskip >= 0) {
                        val idx2 = tabRef[valAbs * 64 * 2 + newskip * 2 + isLast]
                        if (idx2 >= 0) {
                            writer.writeBits(3L, 7)
                            writer.writeBits(1L, 1)
                            writer.writeBits(0L, 1)
                            val nrbits = tabA[idx2].toInt() and 0xF
                            var tidx = idx2.toLong()
                            if (nrbits < 12) {
                                tidx = tidx ushr (12 - nrbits)
                            } else if (nrbits > 12) {
                                tidx = tidx shl (nrbits - 12)
                            }
                            if (dct[i] < 0) tidx = tidx or 1L
                            writer.writeBits(tidx, nrbits)
                            skip = 0
                            handled = true
                        }
                    }
                }
            }

            if (!handled) {
                val newval = valAbs - (tabB[skip or (isLast shl 6)].toInt() and 0xFF)
                if (newval in 0..31) {
                    val idx = tabRef[newval * 64 * 2 + skip * 2 + isLast]
                    if (idx >= 0) {
                        writer.writeBits(3L, 7)
                        writer.writeBits(0L, 1)
                        val nrbits = tabA[idx].toInt() and 0xF
                        var tidx = idx.toLong()
                        if (nrbits < 12) {
                            tidx = tidx ushr (12 - nrbits)
                        } else if (nrbits > 12) {
                            tidx = tidx shl (nrbits - 12)
                        }
                        if (dct[i] < 0) tidx = tidx or 1L
                        writer.writeBits(tidx, nrbits)
                        skip = 0
                        handled = true
                    }
                }
            }

            if (!handled) {
                // Escape code
                writer.writeBits(3L, 7)
                writer.writeBits(1L, 1)
                writer.writeBits(1L, 1)
                if (i == lastNonZero) {
                    writer.writeBits(1L, 1)
                } else {
                    writer.writeBits(0L, 1)
                }
                writer.writeBits(skip.toLong(), 6)
                skip = 0
                val rawVal = (dct[i].toLong()) and 0xFFFL
                writer.writeBits(rawVal, 12)
            }

            if (i == lastNonZero) {
                break
            }
        }
    }
}
