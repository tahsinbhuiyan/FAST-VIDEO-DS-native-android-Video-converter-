package com.example.fastvideo.bitstream

import java.io.ByteArrayOutputStream

/**
 * BitWriter matching the FastVideoDS bitstream specification.
 * Packs bits inside 16-bit little-endian words, reading bits MSB-first.
 */
class BitWriter {
    private var data = ByteArray(8192)
    private var size = 0
    private var buffer: Int = 0
    private var bitCount: Int = 0

    fun reset() {
        size = 0
        buffer = 0
        bitCount = 0
    }

    private fun ensureCapacity(extraBytes: Int) {
        if (size + extraBytes > data.size) {
            val newCap = maxOf(data.size * 2, size + extraBytes + 1024)
            val newArr = ByteArray(newCap)
            System.arraycopy(data, 0, newArr, 0, size)
            data = newArr
        }
    }

    fun writeBits(value: Long, nrBits: Int) {
        if (nrBits <= 0) return
        ensureCapacity((nrBits / 8) + 4)

        var rem = nrBits
        var v = value and if (nrBits >= 64) -1L else ((1L shl nrBits) - 1L)

        while (rem > 0) {
            val space = 16 - bitCount
            if (rem < space) {
                buffer = (buffer shl rem) or v.toInt()
                bitCount += rem
                break
            } else {
                val shift = rem - space
                val topBits = (v ushr shift).toInt()
                buffer = (buffer shl space) or topBits
                data[size++] = (buffer and 0xFF).toByte()
                data[size++] = ((buffer shr 8) and 0xFF).toByte()
                buffer = 0
                bitCount = 0
                rem -= space
                v = v and if (rem >= 64) -1L else ((1L shl rem) - 1L)
            }
        }
    }

    /**
     * Elias gamma coding for unsigned variable integers
     */
    fun writeUnsignedVarInt(value: Long) {
        val l = 63 - java.lang.Long.numberOfLeadingZeros(value + 1L)
        // Write L zeros
        for (i in 0 until l) {
            writeBits(0L, 1)
        }
        // Write stop bit
        writeBits(1L, 1)
        // Write remaining in L bits
        val rem = value - ((1L shl l) - 1L)
        writeBits(rem, l)
    }

    /**
     * Elias gamma coding for signed variable integers using zigzag mapping
     */
    fun writeSignedVarInt(value: Int) {
        val uval: Long = if (value <= 0) {
            1L - value.toLong() * 2L
        } else {
            value.toLong() * 2L
        }
        val l = 63 - java.lang.Long.numberOfLeadingZeros(uval)
        for (i in 0 until l) {
            writeBits(0L, 1)
        }
        writeBits(1L, 1)
        val rem = uval - (1L shl l)
        writeBits(rem, l)
    }

    fun flush() {
        if (bitCount > 0) {
            ensureCapacity(2)
            val padBits = 16 - bitCount
            buffer = buffer shl padBits
            data[size++] = (buffer and 0xFF).toByte()
            data[size++] = ((buffer shr 8) and 0xFF).toByte()
            buffer = 0
            bitCount = 0
        }
    }

    fun toByteArray(): ByteArray {
        flush()
        val result = ByteArray(size)
        System.arraycopy(data, 0, result, 0, size)
        return result
    }

    companion object {
        fun getSignedVarIntBitCount(value: Int): Int {
            val uval: Long = if (value <= 0) {
                1L - value.toLong() * 2L
            } else {
                value.toLong() * 2L
            }
            val l = 63 - java.lang.Long.numberOfLeadingZeros(uval)
            return l + 1 + l
        }

        fun getUnsignedVarIntBitCount(value: Long): Int {
            val l = 63 - java.lang.Long.numberOfLeadingZeros(value + 1L)
            return l + 1 + l
        }
    }
}
