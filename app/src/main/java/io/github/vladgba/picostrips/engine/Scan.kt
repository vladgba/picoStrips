package io.github.vladgba.picostrips.engine

/** A camera frame as an 8-bit luminance buffer with rowStride == width. */
class LumaFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    /** Clockwise rotation (0/90/180/270) that makes the frame upright on screen. */
    val rotation: Int,
)

class ScanResult(
    val format: String,
    val text: String,
    /** Code outline (x0,y0 … x3,y3: TL, TR, BR, BL) in upright-frame pixels, if known. */
    val corners: FloatArray? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

/** Maps a point of the raw (sensor) frame into the upright frame shown on screen. */
fun LumaFrame.upright(x: Float, y: Float, out: FloatArray, at: Int) {
    val w = width - 1f; val h = height - 1f
    when (rotation) {
        90 -> { out[at] = h - y; out[at + 1] = x }
        180 -> { out[at] = w - x; out[at + 1] = h - y }
        270 -> { out[at] = y; out[at + 1] = w - x }
        else -> { out[at] = x; out[at + 1] = y }
    }
}

val LumaFrame.uprightWidth get() = if (rotation % 180 == 0) width else height
val LumaFrame.uprightHeight get() = if (rotation % 180 == 0) height else width
