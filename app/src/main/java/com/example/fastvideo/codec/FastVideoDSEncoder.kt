package com.example.fastvideo.codec

import com.example.fastvideo.bitstream.BitWriter
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.min

/**
 * Genuine ARM-compatible FastVideoDS encoder ported from Gericom's FastVideoDSEncoder.cs.
 * Replaces x86 AVX2 intrinsics with portable scalar operations that execute identically
 * on ARM phones (such as the MediaTek Helio G35 in the OPPO A16 and Qualcomm Snapdragon 675 in the Samsung Galaxy A70).
 */
class FastVideoDSEncoder(
    val width: Int = 256,
    val height: Int = 144,
    var q: Int = 30,
    val maxGopLength: Int = 250,
    val earlyExitThreshold: Int = 48,
    val maxDiamondSteps: Int = 4,
    val isUltimateFast: Boolean = false
) {
    init {
        require(width > 0 && (width and 7) == 0) { "Width must be > 0 and multiple of 8" }
        require(height > 0 && (height and 7) == 0) { "Height must be > 0 and multiple of 8" }
    }

    enum class FvFrameType {
        IFrame,
        PFrame
    }

    data class EncFrame(
        val data: ByteArray,
        val type: FvFrameType,
        val frameNumber: Int
    )

    private var backRefFrame: Rgb555Frame? = null
    private val frameQueue = ArrayDeque<Pair<Rgb555Frame, Boolean>>()
    private var frameNumber = 0
    private var gopLength = 0
    private var flushing = false

    val isFrameQueueEmpty: Boolean
        get() = frameQueue.isEmpty()

    private val scratchDecFrame = Rgb555Frame(width, height)
    private val scratchBitWriter = BitWriter()
    private val scratchQuantsR = Array(16) { IntArray(4) }
    private val scratchQuantsG = Array(16) { IntArray(4) }
    private val scratchQuantsB = Array(16) { IntArray(4) }
    private val scratchDeQuants = Array(16) { IntArray(4) }
    private val scratchCombDct2 = IntArray(64)
    private val scratchData = ByteArray(4)
    private val scratchDataG = ByteArray(4)
    private val scratchIntData = IntArray(4)
    private val scratchDct = IntArray(4)
    private val scratchUndct = ByteArray(4)

    fun sendFrame(frame: Rgb555Frame, forceIFrame: Boolean = false) {
        if (isUltimateFast) {
            frameQueue.add(Pair(frame, forceIFrame))
        } else {
            frameQueue.add(Pair(frame.copy(), forceIFrame))
        }
    }

    fun flush() {
        flushing = true
    }

    fun receiveFrame(): EncFrame? {
        if (frameQueue.isEmpty()) return null

        if (isUltimateFast) {
            val (frame, _) = frameQueue.poll()!!
            val (data, decFrame) = encodeIFrame(frame)
            backRefFrame = decFrame
            gopLength = 0
            return EncFrame(data, FvFrameType.IFrame, frameNumber++)
        }

        val iFrameThreshold = (width / 8) * (height / 8) * 0.6f

        if (backRefFrame == null || gopLength >= maxGopLength || frameQueue.peek()!!.second) {
            val (frame, _) = frameQueue.poll()!!
            val (data, decFrame) = encodeIFrame(frame)
            backRefFrame = decFrame
            gopLength = 0
            return EncFrame(data, FvFrameType.IFrame, frameNumber++)
        }

        val (nextFrame, _) = frameQueue.peek()!!
        val (pData, pDecFrame, iBlockCount) = encodePFrame(nextFrame, backRefFrame!!)

        if (iBlockCount > iFrameThreshold) {
            // Force I-frame if too many intra macroblocks
            val (frame, _) = frameQueue.poll()!!
            val (data, decFrame) = encodeIFrame(frame)
            backRefFrame = decFrame
            gopLength = 0
            return EncFrame(data, FvFrameType.IFrame, frameNumber++)
        }

        frameQueue.poll()
        backRefFrame = pDecFrame
        gopLength++
        return EncFrame(pData, FvFrameType.PFrame, frameNumber++)
    }

    fun encodeIFrame(frame: Rgb555Frame): Pair<ByteArray, Rgb555Frame> {
        val decFrame = scratchDecFrame
        scratchBitWriter.reset()
        val bw = scratchBitWriter
        bw.writeBits(0L, 1) // 0 = I frame
        bw.writeBits(q.toLong(), 6)

        val quantsR = scratchQuantsR
        val quantsG = scratchQuantsG
        val quantsB = scratchQuantsB
        val deQuants = scratchDeQuants
        val combDct2 = scratchCombDct2

        val data = scratchData
        val dataG = scratchDataG
        val intData = scratchIntData
        val dct = scratchDct
        val undct = scratchUndct
        var lastGDC = 0

        for (y in 0 until height step 8) {
            for (x in 0 until width step 8) {
                var i = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(frame.g, frame.width, x + x2 + x3, y + y2 + y3, data)
                                if ((x3 + x2) == 0 && (y3 + y2) == 0) {
                                    dataG.fill(0)
                                } else if (x2 == 0 && y2 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.g, decFrame.width, x + x3, y + y2 + y3 - 1, dataG)
                                } else if (x2 == 0 && x3 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.g, decFrame.width, x + x2 + x3 - 4, y + y2 + y3, dataG)
                                } else if (x2 == 0 && y2 == 0 && y3 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.g, decFrame.width, x + x2 + x3, y + y2 + y3 - 4, dataG)
                                } else {
                                    FrameUtil.getTile2x2Step2(decFrame.g, decFrame.width, x + x2 + x3 - 1, y + y2 + y3, dataG)
                                }

                                for (j in 0 until 4) {
                                    intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                }
                                Dct.dct4NoDiv(intData, dct)
                                quantize4(dct, quantsG[i], deQuants[i])
                                Dct.idct4(deQuants[i], dataG, undct)
                                FrameUtil.setTile2x2Step2(decFrame.g, decFrame.width, x + x2 + x3, y + y2 + y3, undct)
                                i++
                            }
                        }
                    }
                }

                for (j in 0 until 4) {
                    val j16 = j * 16
                    for (k in 0 until 16) {
                        combDct2[j16 + k] = quantsG[k][j]
                    }
                }
                combDct2[0] -= lastGDC
                lastGDC = quantsG[0][0]
                Vlc.encodeDct(combDct2, bw)

                // R Channel
                i = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(frame.r, frame.width, x + x2 + x3, y + y2 + y3, data)
                                if ((x3 + x2) == 0 && (y3 + y2) == 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.g, decFrame.width, x + x2 + x3, y + y2 + y3, dataG)
                                } else if (x2 == 0 && y2 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.r, decFrame.width, x + x3, y + y2 + y3 - 1, dataG)
                                } else if (x2 == 0 && x3 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.r, decFrame.width, x + x2 + x3 - 4, y + y2 + y3, dataG)
                                } else if (x2 == 0 && y2 == 0 && y3 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.r, decFrame.width, x + x2 + x3, y + y2 + y3 - 4, dataG)
                                } else {
                                    FrameUtil.getTile2x2Step2(decFrame.r, decFrame.width, x + x2 + x3 - 1, y + y2 + y3, dataG)
                                }

                                for (j in 0 until 4) {
                                    intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                }
                                Dct.dct4NoDiv(intData, dct)
                                quantize4(dct, quantsR[i], deQuants[i])
                                Dct.idct4(deQuants[i], dataG, undct)
                                FrameUtil.setTile2x2Step2(decFrame.r, decFrame.width, x + x2 + x3, y + y2 + y3, undct)
                                i++
                            }
                        }
                    }
                }

                for (j in 0 until 4) {
                    val j16 = j * 16
                    for (k in 0 until 16) {
                        combDct2[j16 + k] = quantsR[k][j]
                    }
                }
                Vlc.encodeDct(combDct2, bw)

                // B Channel
                i = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(frame.b, frame.width, x + x2 + x3, y + y2 + y3, data)
                                if ((x3 + x2) == 0 && (y3 + y2) == 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.g, decFrame.width, x + x2 + x3, y + y2 + y3, dataG)
                                } else if (x2 == 0 && y2 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.b, decFrame.width, x + x3, y + y2 + y3 - 1, dataG)
                                } else if (x2 == 0 && x3 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.b, decFrame.width, x + x2 + x3 - 4, y + y2 + y3, dataG)
                                } else if (x2 == 0 && y2 == 0 && y3 != 0) {
                                    FrameUtil.getTile2x2Step2(decFrame.b, decFrame.width, x + x2 + x3, y + y2 + y3 - 4, dataG)
                                } else {
                                    FrameUtil.getTile2x2Step2(decFrame.b, decFrame.width, x + x2 + x3 - 1, y + y2 + y3, dataG)
                                }

                                for (j in 0 until 4) {
                                    intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                }
                                Dct.dct4NoDiv(intData, dct)
                                quantize4(dct, quantsB[i], deQuants[i])
                                Dct.idct4(deQuants[i], dataG, undct)
                                FrameUtil.setTile2x2Step2(decFrame.b, decFrame.width, x + x2 + x3, y + y2 + y3, undct)
                                i++
                            }
                        }
                    }
                }

                for (j in 0 until 4) {
                    val j16 = j * 16
                    for (k in 0 until 16) {
                        combDct2[j16 + k] = quantsB[k][j]
                    }
                }
                Vlc.encodeDct(combDct2, bw)
            }
        }

        return Pair(bw.toByteArray(), decFrame)
    }

    private class BlockConfig {
        val dctsR = Array(16) { IntArray(4) }
        val quantsR = Array(16) { IntArray(4) }
        val deQuantsR = Array(16) { IntArray(4) }
        val combDctR = IntArray(64)

        val dctsG = Array(16) { IntArray(4) }
        val quantsG = Array(16) { IntArray(4) }
        val deQuantsG = Array(16) { IntArray(4) }
        val combDctG = IntArray(64)

        val dctsB = Array(16) { IntArray(4) }
        val quantsB = Array(16) { IntArray(4) }
        val deQuantsB = Array(16) { IntArray(4) }
        val combDctB = IntArray(64)
    }

    fun encodePFrame(frame: Rgb555Frame, refFrame: Rgb555Frame): Triple<ByteArray, Rgb555Frame, Int> {
        val decFrame = Rgb555Frame(width, height)
        val vectorBw = BitWriter()
        val dctBw = BitWriter()
        vectorBw.writeBits(1L, 1) // 1 = P frame

        var iBlockCount = 0
        val iBlockConfig = BlockConfig()
        val pBlockConfig = BlockConfig()

        val vecBuf = Array(2) { Array(width / 8) { MotionVector(0, 0) } }
        var curVecBuf = 0
        val lastVec = MotionVector(0, 0)

        val predR = ByteArray(64)
        val predG = ByteArray(64)
        val predB = ByteArray(64)
        val fullDataR = ByteArray(64)
        val fullDataG = ByteArray(64)
        val fullDataB = ByteArray(64)

        val data = ByteArray(4)
        val dataG = ByteArray(4)
        val intData = IntArray(4)
        val dct = IntArray(4)
        val undct = ByteArray(4)

        for (y in 0 until height step 8) {
            for (x in 0 until width step 8) {
                var predX = 0
                var predY = 0
                val mvecX = mutableListOf<Int>()
                val mvecY = mutableListOf<Int>()

                if (x != 0) {
                    mvecX.add(vecBuf[curVecBuf][(x shr 3) - 1].x)
                    mvecY.add(vecBuf[curVecBuf][(x shr 3) - 1].y)
                }
                if (y != 0) {
                    mvecX.add(vecBuf[1 - curVecBuf][x shr 3].x)
                    mvecY.add(vecBuf[1 - curVecBuf][x shr 3].y)
                    if (x != width - 8) {
                        mvecX.add(vecBuf[1 - curVecBuf][(x shr 3) + 1].x)
                        mvecY.add(vecBuf[1 - curVecBuf][(x shr 3) + 1].y)
                    }
                }
                if (mvecX.isNotEmpty()) {
                    mvecX.sort()
                    mvecY.sort()
                    predX = mvecX[mvecX.size / 2]
                    predY = mvecY[mvecY.size / 2]
                }
                lastVec.x = predX
                lastVec.y = predY

                FrameUtil.getTile8(frame.r, frame.width, x, y, fullDataR)
                FrameUtil.getTile8(frame.g, frame.width, x, y, fullDataG)
                FrameUtil.getTile8(frame.b, frame.width, x, y, fullDataB)

                val (bestVec, _) = MotionEstimation.findMotionVector(
                    fullDataR, fullDataG, fullDataB, refFrame,
                    MotionVector(x shl 1, y shl 1),
                    MotionVector(lastVec.x + (x shl 1), lastVec.y + (y shl 1)),
                    earlyExitThreshold = earlyExitThreshold,
                    maxDiamondSteps = maxDiamondSteps
                )

                FrameUtil.getTileHalf8(refFrame.r, refFrame.width, refFrame.height, bestVec.x, bestVec.y, predR)
                FrameUtil.getTileHalf8(refFrame.g, refFrame.width, refFrame.height, bestVec.x, bestVec.y, predG)
                FrameUtil.getTileHalf8(refFrame.b, refFrame.width, refFrame.height, bestVec.x, bestVec.y, predB)

                bestVec.x -= (x shl 1)
                bestVec.y -= (y shl 1)

                var iBitCount = 1
                val iTmpG = ByteArray(64)
                val iTmpR = ByteArray(64)
                val iTmpB = ByteArray(64)

                // Evaluate Intra block option
                run {
                    var idx = 0
                    for (y3 in 0 until 8 step 4) {
                        for (x3 in 0 until 8 step 4) {
                            for (y2 in 0 until 2) {
                                for (x2 in 0 until 2) {
                                    FrameUtil.getTile2x2Step2(frame.g, frame.width, x + x2 + x3, y + y2 + y3, data)
                                    if ((x3 + x2) == 0 && (y3 + y2) == 0) {
                                        dataG.fill(0)
                                    } else if (x2 == 0 && y2 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpG, 8, x3, y2 + y3 - 1, dataG)
                                    } else if (x2 == 0 && x3 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpG, 8, x2 + x3 - 4, y2 + y3, dataG)
                                    } else if (x2 == 0 && y2 == 0 && y3 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpG, 8, x2 + x3, y2 + y3 - 4, dataG)
                                    } else {
                                        FrameUtil.getTile2x2Step2(iTmpG, 8, x2 + x3 - 1, y2 + y3, dataG)
                                    }

                                    for (j in 0 until 4) {
                                        intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                    }
                                    Dct.dct4NoDiv(intData, dct)
                                    quantize4(dct, iBlockConfig.quantsG[idx], iBlockConfig.deQuantsG[idx])
                                    Dct.idct4(iBlockConfig.deQuantsG[idx], dataG, undct)
                                    FrameUtil.setTile2x2Step2(iTmpG, 8, x2 + x3, y2 + y3, undct)
                                    idx++
                                }
                            }
                        }
                    }

                    for (j in 0 until 4) {
                        for (k in 0 until 16) {
                            iBlockConfig.combDctG[j * 16 + k] = iBlockConfig.quantsG[k][j]
                        }
                    }
                    iBitCount += Vlc.calcDctBitCount(iBlockConfig.combDctG)

                    // R Intra
                    idx = 0
                    for (y3 in 0 until 8 step 4) {
                        for (x3 in 0 until 8 step 4) {
                            for (y2 in 0 until 2) {
                                for (x2 in 0 until 2) {
                                    FrameUtil.getTile2x2Step2(frame.r, frame.width, x + x2 + x3, y + y2 + y3, data)
                                    if ((x3 + x2) == 0 && (y3 + y2) == 0) {
                                        FrameUtil.getTile2x2Step2(iTmpG, 8, x2 + x3, y2 + y3, dataG)
                                    } else if (x2 == 0 && y2 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpR, 8, x3, y2 + y3 - 1, dataG)
                                    } else if (x2 == 0 && x3 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpR, 8, x2 + x3 - 4, y2 + y3, dataG)
                                    } else if (x2 == 0 && y2 == 0 && y3 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpR, 8, x2 + x3, y2 + y3 - 4, dataG)
                                    } else {
                                        FrameUtil.getTile2x2Step2(iTmpR, 8, x2 + x3 - 1, y2 + y3, dataG)
                                    }

                                    for (j in 0 until 4) {
                                        intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                    }
                                    Dct.dct4NoDiv(intData, dct)
                                    quantize4(dct, iBlockConfig.quantsR[idx], iBlockConfig.deQuantsR[idx])
                                    Dct.idct4(iBlockConfig.deQuantsR[idx], dataG, undct)
                                    FrameUtil.setTile2x2Step2(iTmpR, 8, x2 + x3, y2 + y3, undct)
                                    idx++
                                }
                            }
                        }
                    }

                    for (j in 0 until 4) {
                        for (k in 0 until 16) {
                            iBlockConfig.combDctR[j * 16 + k] = iBlockConfig.quantsR[k][j]
                        }
                    }
                    iBitCount += Vlc.calcDctBitCount(iBlockConfig.combDctR)

                    // B Intra
                    idx = 0
                    for (y3 in 0 until 8 step 4) {
                        for (x3 in 0 until 8 step 4) {
                            for (y2 in 0 until 2) {
                                for (x2 in 0 until 2) {
                                    FrameUtil.getTile2x2Step2(frame.b, frame.width, x + x2 + x3, y + y2 + y3, data)
                                    if ((x3 + x2) == 0 && (y3 + y2) == 0) {
                                        FrameUtil.getTile2x2Step2(iTmpG, 8, x2 + x3, y2 + y3, dataG)
                                    } else if (x2 == 0 && y2 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpB, 8, x3, y2 + y3 - 1, dataG)
                                    } else if (x2 == 0 && x3 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpB, 8, x2 + x3 - 4, y2 + y3, dataG)
                                    } else if (x2 == 0 && y2 == 0 && y3 != 0) {
                                        FrameUtil.getTile2x2Step2(iTmpB, 8, x2 + x3, y2 + y3 - 4, dataG)
                                    } else {
                                        FrameUtil.getTile2x2Step2(iTmpB, 8, x2 + x3 - 1, y2 + y3, dataG)
                                    }

                                    for (j in 0 until 4) {
                                        intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                    }
                                    Dct.dct4NoDiv(intData, dct)
                                    quantize4(dct, iBlockConfig.quantsB[idx], iBlockConfig.deQuantsB[idx])
                                    Dct.idct4(iBlockConfig.deQuantsB[idx], dataG, undct)
                                    FrameUtil.setTile2x2Step2(iTmpB, 8, x2 + x3, y2 + y3, undct)
                                    idx++
                                }
                            }
                        }
                    }

                    for (j in 0 until 4) {
                        for (k in 0 until 16) {
                            iBlockConfig.combDctB[j * 16 + k] = iBlockConfig.quantsB[k][j]
                        }
                    }
                    iBitCount += Vlc.calcDctBitCount(iBlockConfig.combDctB)
                }

                // Evaluate Inter (P) block option
                val pTmpG = ByteArray(64)
                val pTmpR = ByteArray(64)
                val pTmpB = ByteArray(64)

                var idx = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(frame.g, frame.width, x + x2 + x3, y + y2 + y3, data)
                                FrameUtil.getTile2x2Step2(predG, 8, x2 + x3, y2 + y3, dataG)
                                for (j in 0 until 4) {
                                    intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                }
                                Dct.dct4NoDiv(intData, pBlockConfig.dctsG[idx])
                                idx++
                            }
                        }
                    }
                }
                var pBitCount = quantizeComb4PRD(pBlockConfig.dctsG, pBlockConfig.combDctG, pBlockConfig.deQuantsG)

                idx = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(predG, 8, x2 + x3, y2 + y3, dataG)
                                Dct.idct4(pBlockConfig.deQuantsG[idx], dataG, undct)
                                FrameUtil.setTile2x2Step2(pTmpG, 8, x2 + x3, y2 + y3, undct)
                                idx++
                            }
                        }
                    }
                }

                idx = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(frame.r, frame.width, x + x2 + x3, y + y2 + y3, data)
                                FrameUtil.getTile2x2Step2(predR, 8, x2 + x3, y2 + y3, dataG)
                                for (j in 0 until 4) {
                                    intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                }
                                Dct.dct4NoDiv(intData, pBlockConfig.dctsR[idx])
                                idx++
                            }
                        }
                    }
                }
                pBitCount += quantizeComb4PRD(pBlockConfig.dctsR, pBlockConfig.combDctR, pBlockConfig.deQuantsR)

                idx = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(predR, 8, x2 + x3, y2 + y3, dataG)
                                Dct.idct4(pBlockConfig.deQuantsR[idx], dataG, undct)
                                FrameUtil.setTile2x2Step2(pTmpR, 8, x2 + x3, y2 + y3, undct)
                                idx++
                            }
                        }
                    }
                }

                idx = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(frame.b, frame.width, x + x2 + x3, y + y2 + y3, data)
                                FrameUtil.getTile2x2Step2(predB, 8, x2 + x3, y2 + y3, dataG)
                                for (j in 0 until 4) {
                                    intData[j] = (data[j].toInt() and 0xFF) - (dataG[j].toInt() and 0xFF)
                                }
                                Dct.dct4NoDiv(intData, pBlockConfig.dctsB[idx])
                                idx++
                            }
                        }
                    }
                }
                pBitCount += quantizeComb4PRD(pBlockConfig.dctsB, pBlockConfig.combDctB, pBlockConfig.deQuantsB)

                idx = 0
                for (y3 in 0 until 8 step 4) {
                    for (x3 in 0 until 8 step 4) {
                        for (y2 in 0 until 2) {
                            for (x2 in 0 until 2) {
                                FrameUtil.getTile2x2Step2(predB, 8, x2 + x3, y2 + y3, dataG)
                                Dct.idct4(pBlockConfig.deQuantsB[idx], dataG, undct)
                                FrameUtil.setTile2x2Step2(pTmpB, 8, x2 + x3, y2 + y3, undct)
                                idx++
                            }
                        }
                    }
                }

                val (sadIR, sadPR) = sadIP(fullDataR, iTmpR, pTmpR)
                val (sadIG, sadPG) = sadIP(fullDataG, iTmpG, pTmpG)
                val (sadIB, sadPB) = sadIP(fullDataB, iTmpB, pTmpB)

                val sadI = sadIR + sadIG + sadIB
                val sadP = sadPR + sadPG + sadPB

                val vecBits = if (bestVec.x == lastVec.x && bestVec.y == lastVec.y) {
                    1
                } else {
                    1 + BitWriter.getSignedVarIntBitCount(bestVec.x - lastVec.x) +
                            BitWriter.getSignedVarIntBitCount(bestVec.y - lastVec.y)
                }

                val lambda = 0.35f
                val rdI = iBitCount * lambda + sadI
                val rdP = (pBitCount + vecBits) * lambda + sadP

                if (rdI < rdP && (rdI - rdP) < -0.001f) {
                    // Choose Intra block
                    vectorBw.writeBits(1L, 1) // 1 = intra block in P-frame
                    Vlc.encodeDct(iBlockConfig.combDctG, dctBw)
                    Vlc.encodeDct(iBlockConfig.combDctR, dctBw)
                    Vlc.encodeDct(iBlockConfig.combDctB, dctBw)

                    FrameUtil.setTile8(decFrame.r, decFrame.width, x, y, iTmpR)
                    FrameUtil.setTile8(decFrame.g, decFrame.width, x, y, iTmpG)
                    FrameUtil.setTile8(decFrame.b, decFrame.width, x, y, iTmpB)
                    vecBuf[curVecBuf][x shr 3] = MotionVector(0, 0)
                    iBlockCount++
                } else {
                    // Choose Inter (P) block
                    vectorBw.writeBits(0L, 1) // 0 = inter block
                    if (bestVec.x == lastVec.x && bestVec.y == lastVec.y) {
                        vectorBw.writeBits(1L, 1)
                    } else {
                        vectorBw.writeBits(0L, 1)
                        vectorBw.writeSignedVarInt(bestVec.x - lastVec.x)
                        vectorBw.writeSignedVarInt(bestVec.y - lastVec.y)
                    }

                    if (allZero(pBlockConfig.combDctG)) {
                        dctBw.writeBits(0L, 1)
                    } else {
                        dctBw.writeBits(1L, 1)
                        Vlc.encodeDct(pBlockConfig.combDctG, dctBw)
                    }

                    if (allZero(pBlockConfig.combDctR)) {
                        dctBw.writeBits(0L, 1)
                    } else {
                        dctBw.writeBits(1L, 1)
                        Vlc.encodeDct(pBlockConfig.combDctR, dctBw)
                    }

                    if (allZero(pBlockConfig.combDctB)) {
                        dctBw.writeBits(0L, 1)
                    } else {
                        dctBw.writeBits(1L, 1)
                        Vlc.encodeDct(pBlockConfig.combDctB, dctBw)
                    }

                    FrameUtil.setTile8(decFrame.r, decFrame.width, x, y, pTmpR)
                    FrameUtil.setTile8(decFrame.g, decFrame.width, x, y, pTmpG)
                    FrameUtil.setTile8(decFrame.b, decFrame.width, x, y, pTmpB)
                    vecBuf[curVecBuf][x shr 3] = bestVec
                }
            }
            curVecBuf = 1 - curVecBuf
        }

        val vecData = vectorBw.toByteArray()
        val dctData = dctBw.toByteArray()
        val finalData = ByteArray(vecData.size + dctData.size)
        System.arraycopy(vecData, 0, finalData, 0, vecData.size)
        System.arraycopy(dctData, 0, finalData, vecData.size, dctData.size)

        return Triple(finalData, decFrame, iBlockCount)
    }

    private fun quantize4(dct: IntArray, dst: IntArray, dstReconstructed: IntArray): Boolean {
        for (i in 0 until 4) {
            val qVal = qTab4[i]
            val f = fTab4[i]
            dst[i] = if (dct[i] < 0) {
                -(((f - dct[i]) * qVal) shr 18)
            } else {
                ((f + dct[i]) * qVal) shr 18
            }
        }
        var nrZeros = 0
        for (i in 0 until 4) {
            if (dst[i] == 0) nrZeros++ else nrZeros = 0
        }
        for (i in 0 until 4) {
            dstReconstructed[i] = dst[i] * deQTab4[i]
        }
        return nrZeros == 4
    }

    private fun quantizeCombTrellis(dcts: Array<IntArray>, dst: IntArray): Int {
        for (j in 0 until 4) {
            for (i in 0 until 16) {
                dst[j * 16 + i] = dcts[i][j]
            }
        }

        val bias = 0L
        val runTab = IntArray(65)
        val levelTab = IntArray(65)
        val scoreTab = IntArray(65)
        val survivor = IntArray(65)
        var lastRun = 0
        var lastLevel = 0
        var lastScore = 0
        val coeff = IntArray(2 * 64)
        val coeffCount = IntArray(64)
        val escLength = 28
        val lambda = 200
        val startI = 0
        var lastNonZero = -1
        var lastI = startI

        val threshold1 = ((1L shl 18) - bias - 1L) and 0xFFFFFFFFL
        val threshold2 = (threshold1 shl 1) and 0xFFFFFFFFL

        for (i in 63 downTo startI) {
            val level = dst[i] * qTab4P[i shr 4]
            if (((level.toLong() + threshold1) and 0xFFFFFFFFL) > threshold2) {
                lastNonZero = i
                break
            }
        }

        for (i in startI..lastNonZero) {
            val level = dst[i] * qTab4P[i shr 4]
            if (((level.toLong() + threshold1) and 0xFFFFFFFFL) > threshold2) {
                if (level > 0) {
                    val l = ((bias + level) shr 18).toInt()
                    coeff[i] = l
                    coeff[64 + i] = l - 1
                } else {
                    val l = ((bias - level) shr 18).toInt()
                    coeff[i] = -l
                    coeff[64 + i] = -l + 1
                }
                coeffCount[i] = min(abs(coeff[i]), 2)
            } else {
                coeff[i] = (level shr 31) or 1
                coeffCount[i] = 1
            }
        }

        if (lastNonZero < startI) {
            for (idx in startI until 64) dst[idx] = 0
            return lastNonZero
        }

        scoreTab[startI] = 0
        survivor[0] = startI
        var survivorCount = 1

        val bitLengthTable = VlcTables.bitLengthTable

        for (i in startI..lastNonZero) {
            val dctCoeff = abs(dst[i])
            var bestScore = 256 * 256 * 256 * 120
            val zeroDistortion = dctCoeff * dctCoeff

            for (levelIndex in 0 until coeffCount[i]) {
                var level = coeff[levelIndex * 64 + i]
                val alevel = abs(level)
                val unquantCoeff = alevel * deQTab4P[i shr 4]
                var distortion = (unquantCoeff - dctCoeff) * (unquantCoeff - dctCoeff) - zeroDistortion
                level += 64

                if ((level and 127.inv()) == 0) {
                    for (j in survivorCount - 1 downTo 0) {
                        val run = i - survivor[j]
                        val score = distortion + bitLengthTable[run * 128 + level] * lambda + scoreTab[i - run]
                        if (score < bestScore) {
                            bestScore = score
                            runTab[i + 1] = run
                            levelTab[i + 1] = level - 64
                        }
                    }
                    for (j in survivorCount - 1 downTo 0) {
                        val run = i - survivor[j]
                        val score = distortion + bitLengthTable[64 * 128 + run * 128 + level] * lambda + scoreTab[i - run]
                        if (score < lastScore) {
                            lastScore = score
                            lastRun = run
                            lastLevel = level - 64
                            lastI = i + 1
                        }
                    }
                } else {
                    distortion += escLength * lambda
                    for (j in survivorCount - 1 downTo 0) {
                        val run = i - survivor[j]
                        val score = distortion + scoreTab[i - run]
                        if (score < bestScore) {
                            bestScore = score
                            runTab[i + 1] = run
                            levelTab[i + 1] = level - 64
                        }
                    }
                    for (j in survivorCount - 1 downTo 0) {
                        val run = i - survivor[j]
                        val score = distortion + scoreTab[i - run]
                        if (score < lastScore) {
                            lastScore = score
                            lastRun = run
                            lastLevel = level - 64
                            lastI = i + 1
                        }
                    }
                }
            }

            scoreTab[i + 1] = bestScore
            val limit = if (lastNonZero <= 27) bestScore else bestScore + lambda
            while (survivorCount > 0 && scoreTab[survivor[survivorCount - 1]] > limit) {
                survivorCount--
            }
            survivor[survivorCount++] = i + 1
        }

        val dc = abs(dst[0])
        lastNonZero = lastI - 1
        for (idx in startI until 64) dst[idx] = 0

        if (lastNonZero < startI) return lastNonZero

        if (lastNonZero == 0 && startI == 0) {
            var bestLevel = 0
            var bestScore = dc * dc
            for (i in 0 until coeffCount[0]) {
                val level = coeff[i * 64]
                val alevel = abs(level)
                var unquantCoeff = (alevel * deQTab4P[i shr 4]) shr 3
                unquantCoeff = (unquantCoeff + 4) shr 3
                unquantCoeff = unquantCoeff shl 6
                val distortion = (unquantCoeff - dc) * (unquantCoeff - dc)
                val shiftedLevel = level + 64
                val score = when {
                    shiftedLevel == 64 -> distortion
                    (shiftedLevel and 127.inv()) == 0 -> distortion + bitLengthTable[64 * 128 + shiftedLevel] * lambda
                    else -> distortion + escLength * lambda
                }
                if (score < bestScore) {
                    bestScore = score
                    bestLevel = level
                }
            }
            dst[0] = bestLevel
            return if (bestLevel == 0) -1 else lastNonZero
        }

        var i2 = lastI
        dst[lastNonZero] = lastLevel
        i2 -= (lastRun + 1)
        while (i2 > startI) {
            dst[i2 - 1] = levelTab[i2]
            i2 -= (runTab[i2] + 1)
        }
        return lastNonZero
    }

    private fun quantizeComb4PRD(
        dcts: Array<IntArray>,
        dst: IntArray,
        reconstructed: Array<IntArray>
    ): Int {
        val lastZero = quantizeCombTrellis(dcts, dst)
        for (j in 0 until 4) {
            for (i in 0 until 16) {
                reconstructed[i][j] = dst[j * 16 + i] * deQTab4P[j]
            }
        }
        return if (lastZero < 0) 1 else 1 + Vlc.calcDctBitCount(dst, lastZero)
    }

    private fun sadIP(original: ByteArray, iFrame: ByteArray, pFrame: ByteArray): Pair<Long, Long> {
        var sadI = 0L
        var sadP = 0L
        for (i in 0 until 64) {
            val orig = original[i].toInt() and 0xFF
            val pi = iFrame[i].toInt() and 0xFF
            val pp = pFrame[i].toInt() and 0xFF
            sadI += abs(orig - pi)
            sadP += abs(orig - pp)
        }
        return Pair(sadI, sadP)
    }

    private fun allZero(src: IntArray): Boolean {
        for (v in src) {
            if (v != 0) return false
        }
        return true
    }

    companion object {
        private val qTab4 = intArrayOf(
            262144 / 32, 262144 / 23,
            262144 / 23, 262144 / 64
        )

        private val deQTab4 = intArrayOf(
            32, 23,
            23, 64
        )

        private val fTab4 = IntArray(4) { i ->
            val qVal = qTab4[i]
            min(
                ((1 shl 17) + (qVal shr 1)) / qVal,
                (((32 - 11) shl 12) + (qVal shr 1)) / qVal
            )
        }

        private val qTab4P = intArrayOf(
            262144 / 32, 262144 / 23,
            262144 / 23, 262144 / 64
        )

        private val deQTab4P = intArrayOf(
            32, 23,
            23, 64
        )
    }
}
