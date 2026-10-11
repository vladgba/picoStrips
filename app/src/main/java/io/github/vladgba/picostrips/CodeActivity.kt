package io.github.vladgba.picostrips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import io.github.vladgba.picostrips.data.Entry
import io.github.vladgba.picostrips.data.History
import io.github.vladgba.picostrips.engine.CodeRenderer
import io.github.vladgba.picostrips.engine.Formats
import io.github.vladgba.picostrips.ui.Actions

/** Shows a saved code: drawn large (with max brightness, for showing to another scanner) plus its content. */
class CodeActivity : Activity() {
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code)
        Actions.applyInsets(this, findViewById(R.id.root))

        val entry = History.get(this).get(intent.getLongExtra(EXTRA_ID, -1)) ?: run { finish(); return }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.codeFormat).text = Formats.label(entry.format)
        val src = getString(if (entry.source == Entry.GENERATED) R.string.src_generated else R.string.src_scanned)
        val time = DateUtils.formatDateTime(this, entry.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
        findViewById<TextView>(R.id.codeMeta).text = listOfNotNull(src, entry.engine, time).joinToString(" · ")
        Actions.setLinkedText(findViewById(R.id.codeText), entry.text)

        val image = findViewById<ImageView>(R.id.codeImage)
        bitmap = if (Formats.isWritable(entry.format)) runCatching { CodeRenderer.render(entry.format, entry.text) }.getOrNull() else null
        if (bitmap != null) {
            image.setImageBitmap(bitmap)
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        } else {
            image.visibility = View.GONE
            findViewById<View>(R.id.codeNote).visibility = View.VISIBLE
        }

        val open = findViewById<View>(R.id.codeOpen)
        open.visibility = if (Actions.openUri(entry.text) != null) View.VISIBLE else View.GONE
        open.setOnClickListener { Actions.open(this, entry.text) }
        findViewById<View>(R.id.codeCopy).setOnClickListener { Actions.copy(this, entry.text) }
        findViewById<View>(R.id.codeShare).setOnClickListener { Actions.shareText(this, entry.text) }
        val shareImg = findViewById<View>(R.id.codeShareImg)
        shareImg.visibility = if (bitmap != null) View.VISIBLE else View.GONE
        shareImg.setOnClickListener { bitmap?.let { Actions.shareImage(this, it) } }
        findViewById<View>(R.id.codeDelete).setOnClickListener {
            History.get(this).delete(entry.id)
            finish()
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        fun open(ctx: Context, id: Long) {
            ctx.startActivity(Intent(ctx, CodeActivity::class.java).putExtra(EXTRA_ID, id))
        }
    }
}
