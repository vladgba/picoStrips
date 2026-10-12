package x.vladgba.strips.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.honeywell.aidc.AidcManager
import com.honeywell.aidc.BarcodeFailureEvent
import com.honeywell.aidc.BarcodeReadEvent
import com.honeywell.aidc.BarcodeReader
import x.vladgba.strips.engine.ScanResult

/**
 * Built-in scan engine of Honeywell mobile computers (CT40/CT60/EDA5x…), via Honeywell's DataCollection.jar.
 * On any other device [open] does nothing, so the class is safe to use unconditionally.
 *
 * Call [open] in onCreate, [resume]/[pause] with the activity and [close] in onDestroy. The reader is
 * claimed only while resumed, so other apps (and the keyboard wedge) get the scanner back when we leave.
 * [onScan] is always invoked on the main thread.
 */
class HoneywellScanner(
    private val context: Context,
    private val onScan: (ScanResult) -> Unit,
) : BarcodeReader.BarcodeListener {

    private val main = Handler(Looper.getMainLooper())
    private var manager: AidcManager? = null
    private var reader: BarcodeReader? = null
    private var resumed = false
    private var closed = false

    fun open() {
        if (!isAvailable(context)) return
        try {
            AidcManager.create(context.applicationContext) { m ->
                if (closed) { m.close(); return@create }
                manager = m
                try {
                    reader = m.createBarcodeReader().apply {
                        configure()
                        addBarcodeListener(this@HoneywellScanner)
                    }
                    if (resumed) claim()
                } catch (e: Exception) {
                    Log.w(TAG, "createBarcodeReader failed", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AidcManager.create failed", e)
        }
    }

    fun resume() {
        resumed = true
        claim()
    }

    fun pause() {
        resumed = false
        try { reader?.release() } catch (e: Exception) { Log.w(TAG, "release failed", e) }
    }

    fun close() {
        closed = true
        reader?.let {
            try { it.removeBarcodeListener(this); it.close() } catch (e: Exception) { Log.w(TAG, "close failed", e) }
        }
        reader = null
        try { manager?.close() } catch (_: Exception) {}
        manager = null
    }

    private fun claim() {
        try { reader?.claim() } catch (e: Exception) { Log.w(TAG, "claim failed", e) }
    }

    override fun onBarcodeEvent(event: BarcodeReadEvent) {
        val text = fixEncoding(event.barcodeData ?: return)
        if (text.isEmpty()) return
        val result = ScanResult(format(event.codeId, event.aimId), text)
        main.post { if (!closed) onScan(result) }
    }

    override fun onFailureEvent(event: BarcodeFailureEvent) = Unit

    private fun BarcodeReader.configure() {
        set(BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE, BarcodeReader.TRIGGER_CONTROL_MODE_AUTO_CONTROL)
        // We show our own popup with an Open button; don't let the service jump to the browser.
        set(BarcodeReader.PROPERTY_DATA_PROCESSOR_LAUNCH_BROWSER, false)
        set(BarcodeReader.PROPERTY_DATA_PROCESSOR_LAUNCH_EZ_CONFIG, false)
        // Everything picoStrips knows about; other symbologies keep the device defaults.
        for (p in ENABLED) set(p, true)
    }

    private fun BarcodeReader.set(name: String, value: Any) {
        try {
            when (value) {
                is Boolean -> setProperty(name, value)
                is String -> setProperty(name, value)
                is Int -> setProperty(name, value)
            }
        } catch (e: Exception) {
            Log.w(TAG, "setProperty $name unsupported", e)
        }
    }

    companion object {
        private const val TAG = "HoneywellScanner"

        /** Package of Honeywell's data collection service, the one AidcManager binds to. */
        private const val DCS_PACKAGE = "com.intermec.datacollectionservice"

        fun isAvailable(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(DCS_PACKAGE, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        private val ENABLED = listOf(
            BarcodeReader.PROPERTY_QR_CODE_ENABLED,
            BarcodeReader.PROPERTY_DATAMATRIX_ENABLED,
            BarcodeReader.PROPERTY_AZTEC_ENABLED,
            BarcodeReader.PROPERTY_PDF_417_ENABLED,
            BarcodeReader.PROPERTY_MICRO_PDF_417_ENABLED,
            BarcodeReader.PROPERTY_MAXICODE_ENABLED,
            BarcodeReader.PROPERTY_CODE_128_ENABLED,
            BarcodeReader.PROPERTY_GS1_128_ENABLED,
            BarcodeReader.PROPERTY_CODE_39_ENABLED,
            BarcodeReader.PROPERTY_CODE_93_ENABLED,
            BarcodeReader.PROPERTY_CODABAR_ENABLED,
            BarcodeReader.PROPERTY_INTERLEAVED_25_ENABLED,
            BarcodeReader.PROPERTY_EAN_13_ENABLED,
            BarcodeReader.PROPERTY_EAN_8_ENABLED,
            BarcodeReader.PROPERTY_UPC_A_ENABLE,
            BarcodeReader.PROPERTY_UPC_E_ENABLED,
            BarcodeReader.PROPERTY_RSS_ENABLED,
            BarcodeReader.PROPERTY_RSS_LIMITED_ENABLED,
            BarcodeReader.PROPERTY_RSS_EXPANDED_ENABLED,
            BarcodeReader.PROPERTY_TELEPEN_ENABLED,
        )

        /** Honeywell code ID -> picoStrips format name (see Formats). */
        private val CODE_IDS = mapOf(
            "s" to "QR_CODE", "w" to "DATA_MATRIX", "z" to "AZTEC", "r" to "PDF_417", "R" to "MICRO_PDF_417",
            "x" to "MAXICODE", "j" to "CODE_128", "I" to "CODE_128", "b" to "CODE_39", "i" to "CODE_93",
            "a" to "CODABAR", "e" to "ITF", "d" to "EAN_13", "D" to "EAN_8", "c" to "UPC_A", "E" to "UPC_E",
            "y" to "RSS_14", "{" to "DATA_BAR_LTD", "}" to "RSS_EXPANDED", "t" to "TELEPEN",
            "h" to "CODE_11", "m" to "MATRIX_2_OF_5", "f" to "STANDARD_2_OF_5", "g" to "MSI",
        )

        /** AIM symbology identifier (the letter after "]") -> format, used when the code ID is unknown. */
        private val AIM_IDS = mapOf(
            'Q' to "QR_CODE", 'd' to "DATA_MATRIX", 'z' to "AZTEC", 'L' to "PDF_417", 'U' to "MAXICODE",
            'C' to "CODE_128", 'A' to "CODE_39", 'G' to "CODE_93", 'F' to "CODABAR", 'I' to "ITF",
            'E' to "EAN_13", 'e' to "RSS_14", 'B' to "TELEPEN", 'H' to "CODE_11", 'M' to "MSI",
        )

        internal fun format(codeId: String?, aimId: String?): String {
            codeId?.let { CODE_IDS[it] }?.let { return it }
            val aim = aimId?.removePrefix("]")?.firstOrNull()
            return aim?.let { AIM_IDS[it] } ?: "UNKNOWN"
        }

        /**
         * The scanner delivers UTF-8 bytes decoded as ISO-8859-1. Re-decode them as UTF-8, but only if every
         * char fits in one byte (otherwise the data is already proper Unicode) and the bytes are valid UTF-8.
         */
        internal fun fixEncoding(raw: String): String {
            if (raw.any { it.code > 0xFF }) return raw
            val bytes = raw.toByteArray(Charsets.ISO_8859_1)
            return try {
                Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                raw
            }
        }
    }
}
