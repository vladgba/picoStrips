package io.github.vladgba.picostrips.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import io.github.vladgba.picostrips.CodeActivity
import io.github.vladgba.picostrips.R
import io.github.vladgba.picostrips.data.Entry
import io.github.vladgba.picostrips.data.History
import io.github.vladgba.picostrips.engine.Formats
import io.github.vladgba.picostrips.engine.Images
import io.github.vladgba.picostrips.engine.ScanResult
import io.github.vladgba.picostrips.engine.ZXingCppEngine
import kotlin.concurrent.thread

class ScanPage(private val activity: Activity, val root: View) {
    private val viewfinder: ViewfinderView = root.findViewById(R.id.viewfinder)
    private val torchBtn: ImageButton = root.findViewById(R.id.btnTorch)
    private val permissionPanel: View = root.findViewById(R.id.permissionPanel)
    private val permissionText: TextView = root.findViewById(R.id.permissionText)
    private val grantBtn: Button = root.findViewById(R.id.btnGrant)
    private val card: View = root.findViewById(R.id.resultCard)
    private val cardFormat: TextView = root.findViewById(R.id.resultFormat)
    private val cardText: TextView = root.findViewById(R.id.resultText)
    private val cardOpen: Button = root.findViewById(R.id.btnOpen)
    private val cardCount: TextView = root.findViewById(R.id.resultCount)
    private val hint: View = root.findViewById(R.id.scanHint)

    private val engine = ZXingCppEngine()
    private var lastId = -1L
    private var lastText = ""
    private var visible = false
    private var deniedOnce = false
    private var shownKey: String? = null
    private var sessionCount = 0

    /** Short fade-in so a replaced popup visibly changes (own clock, see Tween). */
    private val cardFade = Tween(card, FADE_MS) { card.alpha = 0.25f + 0.75f * it }

    /** Code key -> last time the decoder saw it. Touched only on the decode thread. */
    private val recent = HashMap<String, Long>()

    private val camera = CameraController(activity, root.findViewById(R.id.preview), ::onFrame) { msg ->
        activity.runOnUiThread { showPermissionPanel(msg, null) }
    }

    init {
        torchBtn.setOnClickListener { torchBtn.alpha = if (camera.toggleTorch()) 1f else 0.7f }
        torchBtn.alpha = 0.7f
        root.findViewById<View>(R.id.btnImage).setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            activity.startActivityForResult(i, REQ_IMAGE)
        }
        card.setOnClickListener { if (lastId > 0) CodeActivity.open(activity, lastId) }
        root.findViewById<View>(R.id.btnCopy).setOnClickListener { Actions.copy(activity, lastText) }
        root.findViewById<View>(R.id.btnShare).setOnClickListener { Actions.shareText(activity, lastText) }
        cardOpen.setOnClickListener { Actions.open(activity, lastText) }
        root.findViewById<View>(R.id.btnClose).setOnClickListener { dismissCard() }
    }

    fun show() {
        visible = true
        if (hasPermission()) {
            permissionPanel.visibility = View.GONE
            camera.paused = false
            camera.start()
        } else if (!deniedOnce) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        } else {
            showPermissionPanel(null, activity.getString(R.string.grant_camera))
        }
    }

    fun hide() {
        visible = false
        camera.stop()
        torchBtn.alpha = 0.7f
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) { deniedOnce = false; if (visible) show() }
        else {
            deniedOnce = true
            val canAsk = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            showPermissionPanel(null, activity.getString(if (canAsk) R.string.grant_camera else R.string.open_settings))
        }
    }

    fun onImagePicked(uri: Uri) {
        thread(name = "image-decode") {
            // Own reader instance: the live one belongs to the camera decode thread.
            val result = runCatching { Images.load(activity, uri)?.let { ZXingCppEngine().decodeBitmap(it) } }.getOrNull()
            activity.runOnUiThread {
                if (result == null) Actions.toast(activity, R.string.nothing_found) else deliver(result, force = true)
            }
        }
    }

    private fun hasPermission() = activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionPanel(message: String?, button: String?) {
        permissionPanel.visibility = View.VISIBLE
        permissionText.text = message ?: activity.getString(R.string.camera_needed)
        grantBtn.visibility = if (button != null) View.VISIBLE else View.GONE
        grantBtn.text = button
        grantBtn.setOnClickListener {
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || !deniedOnce) {
                activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            } else {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                deniedOnce = false
            }
        }
    }

    /**
     * Continuous scanning: the camera never pauses. A code only pops up when it is new, i.e. it
     * hasn't been seen for [GONE_MS] — so a code held in view (or two codes in view at once)
     * doesn't keep re-triggering, but moving to another code replaces the popup immediately.
     */
    private fun onFrame(frame: io.github.vladgba.picostrips.engine.LumaFrame) {
        val result = try { engine.decode(frame) } catch (_: Throwable) { null } ?: return
        if (result.text.isEmpty()) return
        val key = key(result)
        val now = SystemClock.uptimeMillis()
        val last = recent.put(key, now)
        if (recent.size > 64) recent.entries.removeAll { now - it.value > GONE_MS }
        if (last != null && now - last < GONE_MS) return
        activity.runOnUiThread { deliver(result) }
    }

    private fun key(r: ScanResult) = r.format + '\u0000' + r.text

    private fun deliver(r: ScanResult, force: Boolean = false) {
        val key = key(r)
        if (!force && key == shownKey && card.visibility == View.VISIBLE) return
        shownKey = key
        sessionCount++
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewfinder.hit(r.corners, r.imageWidth, r.imageHeight)
        val id = History.get(activity).add(r.format, r.text, Entry.SCANNED, null)
        present { bind(r, id) }
    }

    private fun bind(r: ScanResult, id: Long) {
        lastId = id
        lastText = r.text
        cardFormat.text = Formats.label(r.format)
        cardCount.text = if (sessionCount > 1) activity.getString(R.string.scan_count, sessionCount) else ""
        Actions.setLinkedText(cardText, r.text)
        cardOpen.visibility = if (Actions.openUri(r.text) != null) View.VISIBLE else View.GONE
    }

    private fun present(bind: () -> Unit) {
        bind()
        hint.visibility = View.GONE
        card.visibility = View.VISIBLE
        cardFade.start()
    }

    private fun dismissCard() {
        shownKey = null
        cardFade.cancel()
        card.visibility = View.GONE
        hint.visibility = View.VISIBLE
    }

    companion object {
        const val REQ_CAMERA = 1
        const val REQ_IMAGE = 2
        private const val GONE_MS = 2500L
        private const val FADE_MS = 150L
    }
}
