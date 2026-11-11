package com.example.fastvideo.codec

data class MotionVector(
    var x: Int = 0,
    var y: Int = 0
) {
    fun copy(): MotionVector = MotionVector(x, y)
}
