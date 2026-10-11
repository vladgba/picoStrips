package x.vladgba.strips.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets
import java.util.EnumMap

/** Draws codes with ZXing, pixel-exact (integer module size, no smoothing). */
object CodeRenderer {
    class Error(message: String) : Exception(message)

    fun matrix(format: String, text: String, ecLevel: String? = null): BitMatrix {
        val bf = Formats.zxing(format)?.takeIf { Formats.isWritable(format) }
            ?: throw Error("${Formats.label(format)} can't be generated")
        if (text.isEmpty()) throw Error("Enter some content")
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        if (!StandardCharsets.ISO_8859_1.newEncoder().canEncode(text)) {
            if (!Formats.is2D(format)) throw Error("${Formats.label(format)} supports Latin characters only")
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        }
        // 2D: we add our own quiet zone. 1D: MARGIN is the total for both sides; 22 gives the 11-module
        // quiet zone EAN-13 needs (ZXing defaults to 5/side, too thin: e.g. its own reader rejects UPC-E).
        hints[EncodeHintType.MARGIN] = if (Formats.is2D(format)) 0 else 22
        if (bf == BarcodeFormat.QR_CODE) {
            hints[EncodeHintType.ERROR_CORRECTION] = runCatching { ErrorCorrectionLevel.valueOf(ecLevel ?: "M") }
                .getOrDefault(ErrorCorrectionLevel.M)
        }
        return try {
            MultiFormatWriter().encode(text, bf, 0, 0, hints)
        } catch (e: Exception) {
            throw Error(friendly(format, e))
        }
    }

    /**
     * @param target approximate width of the result in pixels
     */
    fun render(format: String, text: String, ecLevel: String? = null, target: Int = 960): Bitmap =
        toBitmap(matrix(format, text, ecLevel), Formats.is2D(format), target)

    fun toBitmap(m: BitMatrix, is2D: Boolean, target: Int): Bitmap {
        val quiet = if (is2D) 4 else 0 // 1D writers already add side margins
        val cols = m.width + quiet * 2
        val module = maxOf(1, target / cols)
        val w = cols * module
        val h = if (is2D) (m.height + quiet * 2) * module else maxOf(module * 40, w * 2 / 5)
        val px = IntArray(w * h) { Color.WHITE }
        if (is2D) {
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (!m[x, y]) continue
                val ox = (x + quiet) * module; val oy = (y + quiet) * module
                for (dy in 0 until module) {
                    val row = (oy + dy) * w + ox
                    px.fill(Color.BLACK, row, row + module)
                }
            }
        } else {
            val line = IntArray(w) { Color.WHITE }
            for (x in 0 until m.width) if (m[x, 0]) line.fill(Color.BLACK, x * module, (x + 1) * module)
            val margin = h / 12
            for (y in margin until h - margin) System.arraycopy(line, 0, px, y * w, w)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun friendly(format: String, e: Exception): String {
        val hint = Formats.inputHint(format)
        val msg = e.message?.takeIf { it.isNotBlank() && it.length < 120 }
        return when {
            hint != null && msg != null -> "$msg — ${Formats.label(format)}: $hint"
            hint != null -> "${Formats.label(format)} needs: $hint"
            msg != null -> msg
            else -> "Content doesn't fit ${Formats.label(format)}"
        }
    }
}
