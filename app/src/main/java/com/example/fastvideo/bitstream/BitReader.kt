package com.example.fastvideo.bitstream

/**
 * BitReader matching the FastVideoDS bitstream specification.
 * Decodes 16-bit little-endian words, reading bits MSB-first.
 */
class BitReader(private val data: ByteArray, private var offset: Int = 0) {
    private var buffer: Int = 0
    private var bitCount: Int = 0

    fun readBits(nrBits: Int): Long {
        var result = 0L
        for (i in 0 until nrBits) {
            if (bitCount == 0) {
                if (offset + 1 < data.size) {
                    val b0 = data[offset++].toInt() and 0xFF
                    val b1 = data[offset++].toInt() and 0xFF
                    buffer = (b1 shl 8) or b0
                    bitCount = 16
                } else if (offset < data.size) {
                    buffer = data[offset++].toInt() and 0xFF
                    bitCount = 8
                } else {
                    buffer = 0
                    bitCount = 16
                }
            }
            bitCount--
            val bit = (buffer ushr bitCount) and 1
            result = (result shl 1) or bit.toLong()
        }
        return result
    }

    /**
     * Reads an Elias gamma encoded unsigned variable integer
     */
    fun readUnsignedVarInt(): Long {
        var l = 0
        while (readBits(1) == 0L) {
            l++
            if (l > 60) break // Guard against runaway bitstream
        }
        val rem = if (l > 0) readBits(l) else 0L
        return rem + ((1L shl l) - 1L)
    }

    /**
     * Reads an Elias gamma encoded signed variable integer
     */
    fun readSignedVarInt(): Int {
        var l = 0
        while (readBits(1) == 0L) {
            l++
            if (l > 60) break
        }
        val rem = if (l > 0) readBits(l) else 0L
        val uval = rem + (1L shl l)
        val finalVal = if ((uval and 1L) != 0L) {
            -(uval shr 1)
        } else {
            uval shr 1
        }
        return finalVal.toInt()
    }
}
