package x.vladgba.strips.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import x.vladgba.strips.R
import x.vladgba.strips.ShareProvider
import java.io.File

/** Copy / share / open helpers. Everything is handed to other apps via intents; we never touch the network. */
object Actions {
    /** Any RFC 3986 scheme followed by a single token: covers http, mailto, FIDO:/, otpauth://, bitcoin:, ... */
    private val uriRegex = Regex("^[A-Za-z][A-Za-z0-9+.-]*:\\S+$")

    /** Structured payloads that look like URIs but no app opens via ACTION_VIEW. */
    private val notUris = setOf("wifi", "mecard", "matmsg", "bizcard", "begin")

    /** Links inside longer text: `scheme:/...`, well-known opaque schemes, and bare `www.` hosts. */
    private val linkRegex = Regex(
        "(?i)(?<![A-Za-z0-9+.-])(?:[a-z][a-z0-9+.-]*:/\\S+|(?:mailto|tel|sms|smsto|geo|market|magnet|bitcoin):\\S+|www\\.\\S+)"
    )
    private const val TRAILING = ".,;:!?'\")]}>"

    fun openUri(text: String): Uri? {
        val t = text.trim()
        if (t.startsWith("www.", ignoreCase = true) && t.none { it.isWhitespace() }) return Uri.parse("https://$t")
        if (!uriRegex.matches(t)) return null
        if (t.substringBefore(':').lowercase() in notUris) return null
        return Uri.parse(t)
    }

    /** Shows [text] in [view] with every link in it tappable; taps elsewhere fall through to the view's parent / selection. */
    @SuppressLint("ClickableViewAccessibility")
    fun setLinkedText(view: TextView, text: String) {
        val whole = openUri(text)
        val s = SpannableString(text)
        if (whole != null) {
            s.setSpan(link(view.context, text.trim()), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            for (m in linkRegex.findAll(text)) {
                val url = m.value.trimEnd { it in TRAILING }
                if (openUri(url) == null) continue
                s.setSpan(link(view.context, url), m.range.first, m.range.first + url.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        view.text = s
        view.setOnTouchListener { v, e ->
            val tv = v as TextView
            val layout = tv.layout ?: return@setOnTouchListener false
            val x = e.x.toInt() - tv.totalPaddingLeft + tv.scrollX
            val y = e.y.toInt() - tv.totalPaddingTop + tv.scrollY
            val line = layout.getLineForVertical(y)
            if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return@setOnTouchListener false
            val off = layout.getOffsetForHorizontal(line, x.toFloat())
            val span = (tv.text as? Spanned)?.getSpans(off, off, ClickableSpan::class.java)?.firstOrNull()
                ?: return@setOnTouchListener false
            if (e.action == MotionEvent.ACTION_UP) span.onClick(tv)
            true
        }
    }

    private fun link(ctx: Context, url: String) = object : ClickableSpan() {
        override fun onClick(widget: View) = open(ctx, url)
    }

    fun copy(ctx: Context, text: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", text))
        if (Build.VERSION.SDK_INT < 33) toast(ctx, R.string.copied)
    }

    fun shareText(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, null))
    }

    fun open(ctx: Context, text: String) {
        val uri = openUri(text) ?: return
        // Intent filters match schemes case-sensitively: try as scanned (e.g. "FIDO:/"), then lowercased.
        for (u in listOf(uri, uri.normalizeScheme()).distinct()) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        toast(ctx, R.string.no_app)
    }

    fun shareImage(ctx: Context, bitmap: Bitmap) {
        val dir = File(ctx.cacheDir, ShareProvider.DIR).apply { mkdirs() }
        val f = File(dir, "code.png")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = ShareProvider.uriFor(ctx, f.name)
        val i = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri(null, uri)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun toast(ctx: Context, res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()

    /** Edge-to-edge (forced on Android 15+): pad the root by the system bars and the keyboard. */
    @Suppress("DEPRECATION")
    fun applyInsets(activity: Activity, root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        if (Build.VERSION.SDK_INT >= 30) activity.window.setDecorFitsSystemWindows(false)
        else root.systemUiVisibility = root.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        root.requestApplyInsets()
    }
}
