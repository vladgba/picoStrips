package x.vladgba.strips.engine

import android.graphics.Bitmap
import android.graphics.Rect
import zxingcpp.BarcodeReader
import java.nio.ByteBuffer

/**
 * The barcode reader: zxing-cpp via its Android wrapper (~1.2–1.8 MB native lib per ABI), fully offline.
 * Reads QR, Micro QR, rMQR, Aztec, Data Matrix, PDF417, MicroPDF417, MaxiCode, EAN/UPC (+add-ons),
 * Code 128/39/93, Codabar, ITF, GS1 DataBar (all variants), DX film edge and more.
 * [decode] is called from a single background thread, one frame at a time.
 */
class ZXingCppEngine {

    private val live = BarcodeReader(BarcodeReader.Options().apply {
        tryHarder = true
        tryRotate = true
        tryInvert = true
        tryDownscale = true
        maxNumberOfSymbols = 1
    })
    private val still = BarcodeReader(BarcodeReader.Options().apply {
        tryHarder = true
        tryRotate = true
        tryInvert = true
        tryDownscale = true
        tryDenoise = true
        maxNumberOfSymbols = 1
    })

    private var lumaBitmap: Bitmap? = null
    private var argbFallback = false

    fun decode(frame: LumaFrame): ScanResult? {
        val bmp = toBitmap(frame)
        val results = try {
            // rotation 0: positions come back in raw frame coordinates (tryRotate still finds any orientation)
            live.read(bmp, Rect(0, 0, frame.width, frame.height), 0)
        } catch (e: RuntimeException) {
            if (argbFallback) throw e
            argbFallback = true // A_8 bitmaps not supported by this build: fall back to ARGB
            lumaBitmap = null
            return null
        }
        return pick(results, frame)
    }

    /** Thorough (slow) decode of a still image, e.g. picked from gallery. */
    fun decodeBitmap(bitmap: Bitmap): ScanResult? {
        val bmp = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return pick(still.read(bmp, Rect(0, 0, bmp.width, bmp.height), 0))
    }

    private fun pick(results: List<BarcodeReader.Result>, frame: LumaFrame? = null): ScanResult? {
        val r = results.firstOrNull { it.error == null && (it.text != null || it.bytes != null) } ?: return null
        val text = r.text ?: r.bytes?.let { String(it, Charsets.ISO_8859_1) } ?: return null
        val corners = frame?.let { f ->
            val p = r.position
            FloatArray(8).also { out ->
                listOf(p.topLeft, p.topRight, p.bottomRight, p.bottomLeft).forEachIndexed { i, pt ->
                    f.upright(pt.x.toFloat(), pt.y.toFloat(), out, i * 2)
                }
            }
        }
        return ScanResult(canonical(r.format.name), text, corners, frame?.uprightWidth ?: 0, frame?.uprightHeight ?: 0)
    }

    private fun toBitmap(f: LumaFrame): Bitmap {
        val cfg = if (argbFallback) Bitmap.Config.ARGB_8888 else Bitmap.Config.ALPHA_8
        var bmp = lumaBitmap
        if (bmp == null || bmp.width != f.width || bmp.height != f.height || bmp.config != cfg) {
            bmp = Bitmap.createBitmap(f.width, f.height, cfg)
            lumaBitmap = bmp
        }
        if (!argbFallback && bmp.rowBytes == f.width) {
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(f.data, 0, f.width * f.height))
        } else {
            val px = IntArray(f.width * f.height)
            for (i in px.indices) {
                val v = f.data[i].toInt() and 0xFF
                px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            if (bmp.config != Bitmap.Config.ARGB_8888) {
                bmp = Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888)
                lumaBitmap = bmp
                argbFallback = true
            }
            bmp.setPixels(px, 0, f.width, 0, 0, f.width, f.height)
        }
        return bmp
    }

    /** Maps zxing-cpp format names onto the names used across the app (ZXing's where possible). */
    private fun canonical(name: String): String = when (name) {
        "QR_CODE_MODEL_1", "QR_CODE_MODEL_2" -> "QR_CODE"
        "AZTEC_CODE" -> "AZTEC"
        "MAXI_CODE" -> "MAXICODE"
        "PDF_417", "COMPACT_PDF_417" -> "PDF_417"
        "CODE_39_STD", "CODE_39_EXT" -> "CODE_39"
        "ITF_14" -> "ITF"
        "ISBN" -> "EAN_13"
        "EAN_5", "EAN_2" -> "UPC_EAN_EXTENSION"
        "DATA_BAR", "DATA_BAR_OMNI", "DATA_BAR_STK", "DATA_BAR_STK_OMNI" -> "RSS_14"
        "DATA_BAR_EXP", "DATA_BAR_EXP_STK" -> "RSS_EXPANDED"
        "TELEPEN_ALPHA", "TELEPEN_NUMERIC" -> "TELEPEN"
        else -> name
    }
}
