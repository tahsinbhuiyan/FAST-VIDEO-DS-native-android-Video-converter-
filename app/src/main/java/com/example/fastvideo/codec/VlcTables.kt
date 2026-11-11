package com.example.fastvideo.codec

import android.content.Context
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FastVideoDS Variable Length Coding Tables.
 * Precomputes BitLengthTable matching Gericom's Vlc.cs.
 */
object VlcTables {
    val tableA = ShortArray(4096)
    val tableB = ByteArray(256)
    val tableARefLinear = IntArray(4096)
    val bitLengthTable = IntArray(2 * 64 * 128)

    @Volatile
    var isInitialized = false
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val am = context.assets
            loadFromStreams(
                am.open("vlc_table_a.bin"),
                am.open("vlc_table_b.bin"),
                am.open("vlc_table_a_ref.bin")
            )
        } catch (t: Throwable) {
            val fileA = if (java.io.File("src/main/assets/vlc_table_a.bin").exists()) java.io.File("src/main/assets/vlc_table_a.bin") else java.io.File("app/src/main/assets/vlc_table_a.bin")
            val fileB = if (java.io.File("src/main/assets/vlc_table_b.bin").exists()) java.io.File("src/main/assets/vlc_table_b.bin") else java.io.File("app/src/main/assets/vlc_table_b.bin")
            val fileRef = if (java.io.File("src/main/assets/vlc_table_a_ref.bin").exists()) java.io.File("src/main/assets/vlc_table_a_ref.bin") else java.io.File("app/src/main/assets/vlc_table_a_ref.bin")
            if (fileA.exists() && fileB.exists() && fileRef.exists()) {
                loadFromStreams(fileA.inputStream(), fileB.inputStream(), fileRef.inputStream())
            } else {
                throw t
            }
        }
    }

    @Synchronized
    fun loadFromStreams(streamA: InputStream, streamB: InputStream, streamRef: InputStream) {
        if (isInitialized) return

        streamA.use { s ->
            val bytes = s.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 4096) {
                tableA[i] = buf.short
            }
        }

        streamB.use { s ->
            val bytes = s.readBytes()
            System.arraycopy(bytes, 0, tableB, 0, 256)
        }

        streamRef.use { s ->
            val bytes = s.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 4096) {
                tableARefLinear[i] = buf.int
            }
        }

        buildBitLengthTable()
        isInitialized = true
    }

    private fun buildBitLengthTable() {
        for (last in 0..1) {
            for (skip in 0 until 64) {
                for (value in -64 until 64) {
                    var valAbs = if (value < 0) -value else value
                    if (valAbs <= 31) {
                        val idx = tableARefLinear[valAbs * 64 * 2 + skip * 2 + last]
                        if (idx >= 0) {
                            bitLengthTable[last * 128 * 64 + skip * 128 + (value + 64)] =
                                tableA[idx].toInt() and 0xF
                            continue
                        }
                        val newskip = skip - (tableB[(valAbs or (last shl 6)) + 0x80].toInt() and 0xFF)
                        if (newskip >= 0) {
                            val idx2 = tableARefLinear[valAbs * 64 * 2 + newskip * 2 + last]
                            if (idx2 >= 0) {
                                bitLengthTable[last * 128 * 64 + skip * 128 + (value + 64)] =
                                    9 + (tableA[idx2].toInt() and 0xF)
                                continue
                            }
                        }
                    }

                    val newval = valAbs - (tableB[skip or (last shl 6)].toInt() and 0xFF)
                    if (newval in 0..31) {
                        val idx = tableARefLinear[newval * 64 * 2 + skip * 2 + last]
                        if (idx >= 0) {
                            bitLengthTable[last * 128 * 64 + skip * 128 + (value + 64)] =
                                8 + (tableA[idx].toInt() and 0xF)
                            continue
                        }
                    }

                    bitLengthTable[last * 128 * 64 + skip * 128 + (value + 64)] = 28
                }
            }
        }
    }
}
