package io.github.vladgba.picostrips.engine

import com.google.zxing.BarcodeFormat

/**
 * Canonical format names are stored in history as plain strings.
 * They follow ZXing's [BarcodeFormat] names where one exists (the generator uses ZXing);
 * formats ZXing can't draw (Micro QR, rMQR, DataBar Limited, DX film edge, …) use zxing-cpp's names.
 */
object Formats {
    class Info(val label: String, val short: String, val is2D: Boolean, val inputHint: String? = null)

    private val info = linkedMapOf(
        "QR_CODE" to Info("QR Code", "QR", true),
        "MICRO_QR_CODE" to Info("Micro QR", "µQR", true),
        "RMQR_CODE" to Info("rMQR", "rMQR", true),
        "AZTEC" to Info("Aztec", "Aztec", true),
        "AZTEC_RUNE" to Info("Aztec Rune", "Rune", true),
        "DATA_MATRIX" to Info("Data Matrix", "DM", true),
        "PDF_417" to Info("PDF417", "PDF417", true),
        "MICRO_PDF_417" to Info("MicroPDF417", "µPDF", true),
        "MAXICODE" to Info("MaxiCode", "Maxi", true),
        "CODE_128" to Info("Code 128", "C128", false, "ASCII text"),
        "CODE_39" to Info("Code 39", "C39", false, "A–Z, 0–9, space and - . \$ / + %"),
        "CODE_93" to Info("Code 93", "C93", false, "ASCII text"),
        "CODABAR" to Info("Codabar", "Codabar", false, "Digits and - \$ : / . + (optional A–D start/stop)"),
        "ITF" to Info("ITF", "ITF", false, "Even number of digits"),
        "EAN_13" to Info("EAN-13", "EAN-13", false, "12 or 13 digits"),
        "EAN_8" to Info("EAN-8", "EAN-8", false, "7 or 8 digits"),
        "UPC_A" to Info("UPC-A", "UPC-A", false, "11 or 12 digits"),
        "UPC_E" to Info("UPC-E", "UPC-E", false, "7 or 8 digits, starting with 0 or 1"),
        "UPC_EAN_EXTENSION" to Info("EAN add-on", "EAN+", false),
        "RSS_14" to Info("GS1 DataBar", "DataBar", false),
        "RSS_EXPANDED" to Info("GS1 DataBar Expanded", "DataBar Exp", false),
        "DATA_BAR_LTD" to Info("GS1 DataBar Limited", "DataBar Ltd", false),
        "DX_FILM_EDGE" to Info("DX Film Edge", "DX", false),
        "TELEPEN" to Info("Telepen", "Telepen", false),
        "PZN" to Info("PZN", "PZN", false),
        "CODE_32" to Info("Code 32", "C32", false),
    )

    /** Formats ZXing can draw, in the order shown in the generator. */
    val writable = listOf(
        "QR_CODE", "AZTEC", "DATA_MATRIX", "PDF_417",
        "CODE_128", "CODE_39", "CODE_93", "CODABAR", "ITF",
        "EAN_13", "EAN_8", "UPC_A", "UPC_E",
    )

    fun label(name: String) = info[name]?.label ?: pretty(name)
    fun short(name: String) = info[name]?.short ?: pretty(name)
    fun is2D(name: String) = info[name]?.is2D ?: false
    fun inputHint(name: String) = info[name]?.inputHint
    fun isWritable(name: String) = name in writable

    fun zxing(name: String): BarcodeFormat? = runCatching { BarcodeFormat.valueOf(name) }.getOrNull()

    private fun pretty(name: String) =
        name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
