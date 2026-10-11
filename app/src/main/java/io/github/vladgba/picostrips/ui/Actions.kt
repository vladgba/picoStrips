package io.github.vladgba.picostrips.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import io.github.vladgba.picostrips.R
import io.github.vladgba.picostrips.ShareProvider
import java.io.File

/** Copy / share / open helpers. Everything is handed to other apps via intents; we never touch the network. */
object Actions {
    private val schemes = listOf("http://", "https://", "mailto:", "tel:", "sms:", "smsto:", "geo:", "market://")

    fun openUri(text: String): Uri? {
        val t = text.trim()
        if (t.contains('\n')) return null
        val lower = t.lowercase()
        return if (schemes.any { lower.startsWith(it) }) Uri.parse(t) else null
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
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast(ctx, R.string.no_app)
        }
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
