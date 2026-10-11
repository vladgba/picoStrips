package x.vladgba.strips.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import x.vladgba.strips.engine.LumaFrame
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Minimal Camera2 wrapper: preview on a TextureView + a YUV stream whose Y plane is handed to
 * [analyzer] on a dedicated thread. Frames arriving while the analyzer is busy are dropped.
 */
class CameraController(
    ctx: Context,
    private val view: TextureView,
    private val analyzer: (LumaFrame) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = ctx.getSystemService(CameraManager::class.java)
    private var camThread: HandlerThread? = null
    private var camHandler: Handler? = null
    private var decodeThread: HandlerThread? = null
    private var decodeHandler: Handler? = null

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var request: CaptureRequest.Builder? = null

    private var size = Size(1280, 720)
    private var sensorOrientation = 90
    var hasTorch = false
        private set
    private var torchOn = false

    @Volatile private var running = false
    @Volatile var paused = false
    private val busy = AtomicBoolean(false)
    private var buffer = ByteArray(0)
    private var rotation = 0

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { if (running) open() }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = applyTransform()
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    fun start() {
        if (running) return
        running = true
        camThread = HandlerThread("camera").also { it.start(); camHandler = Handler(it.looper) }
        decodeThread = HandlerThread("decode").also { it.start(); decodeHandler = Handler(it.looper) }
        view.surfaceTextureListener = surfaceListener
        if (view.isAvailable) open()
    }

    fun stop() {
        if (!running) return
        running = false
        torchOn = false
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        previewSurface?.release()
        session = null; device = null; reader = null; previewSurface = null; request = null
        camThread?.quitSafely(); decodeThread?.quitSafely()
        camThread = null; decodeThread = null; camHandler = null; decodeHandler = null
    }

    fun toggleTorch(): Boolean {
        val req = request ?: return false
        val s = session ?: return false
        torchOn = !torchOn
        req.set(CaptureRequest.FLASH_MODE, if (torchOn) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
        runCatching { s.setRepeatingRequest(req.build(), null, camHandler) }
        return torchOn
    }

    @SuppressLint("MissingPermission")
    private fun open() {
        try {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull() ?: return onError("No camera")
            val ch = manager.getCameraCharacteristics(id)
            sensorOrientation = ch[CameraCharacteristics.SENSOR_ORIENTATION] ?: 90
            hasTorch = ch[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true
            val map = ch[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] ?: return onError("No camera")
            val yuv = map.getOutputSizes(ImageFormat.YUV_420_888).orEmpty().toSet()
            val tex = map.getOutputSizes(SurfaceTexture::class.java).orEmpty().toSet()
            size = chooseSize(yuv.intersect(tex).ifEmpty { yuv })
            val afModes = ch[CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES] ?: IntArray(0)
            val af = when {
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in afModes -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO in afModes -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                CameraMetadata.CONTROL_AF_MODE_AUTO in afModes -> CameraMetadata.CONTROL_AF_MODE_AUTO
                else -> CameraMetadata.CONTROL_AF_MODE_OFF
            }
            view.post { applyTransform() }

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    if (!running) { cam.close(); return }
                    device = cam
                    createSession(cam, af)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); if (device === cam) device = null }
                override fun onError(cam: CameraDevice, error: Int) {
                    cam.close(); if (device === cam) device = null
                    if (running) onError("Camera error $error")
                }
            }, camHandler)
        } catch (e: Exception) {
            onError(e.message ?: "Camera unavailable")
        }
    }

    @Suppress("DEPRECATION") // createCaptureSession(List) is fine down to API 24
    private fun createSession(cam: CameraDevice, afMode: Int) {
        val st = view.surfaceTexture ?: return
        st.setDefaultBufferSize(size.width, size.height)
        val preview = Surface(st).also { previewSurface = it }
        val ir = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).also { reader = it }
        ir.setOnImageAvailableListener({ r ->
            val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                if (paused || !busy.compareAndSet(false, true)) return@setOnImageAvailableListener
                val plane = img.planes[0]
                val w = img.width; val h = img.height
                val rowStride = plane.rowStride
                val src = plane.buffer
                if (buffer.size != w * h) buffer = ByteArray(w * h)
                if (rowStride == w) {
                    src.get(buffer, 0, w * h)
                } else {
                    for (y in 0 until h) {
                        src.position(y * rowStride)
                        src.get(buffer, y * w, w)
                    }
                }
                val frame = LumaFrame(buffer, w, h, rotation)
                val posted = decodeHandler?.post {
                    try { if (!paused) analyzer(frame) } finally { busy.set(false) }
                } ?: false
                if (!posted) busy.set(false)
            } catch (_: Exception) {
                busy.set(false)
            } finally {
                img.close()
            }
        }, camHandler)

        cam.createCaptureSession(listOf(preview, ir.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if (!running) { s.close(); return }
                session = s
                val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(preview)
                    addTarget(ir.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AF_MODE, afMode)
                }
                request = req
                runCatching { s.setRepeatingRequest(req.build(), null, camHandler) }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) { onError("Camera configuration failed") }
        }, camHandler)
    }

    /** Prefer ~720p: plenty for barcodes, cheap to decode. */
    private fun chooseSize(sizes: Set<Size>): Size {
        val usable = sizes.filter { it.width * it.height <= 1920 * 1080 && it.width >= 640 }.ifEmpty { sizes.toList() }
        return usable.minByOrNull { abs(it.width * it.height - 1280 * 720) + abs(it.width * 9 - it.height * 16) * 50 }
            ?: Size(640, 480)
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = view.display?.rotation ?: Surface.ROTATION_0

    fun applyTransform() {
        val vw = view.width.toFloat(); val vh = view.height.toFloat()
        if (vw == 0f || vh == 0f) return
        val rot = displayRotation()
        rotation = (sensorOrientation - rot * 90 + 360) % 360
        val m = Matrix()
        val cx = vw / 2; val cy = vh / 2
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            val viewRect = RectF(0f, 0f, vw, vh)
            val bufRect = RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
            bufRect.offset(cx - bufRect.centerX(), cy - bufRect.centerY())
            m.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL)
            val s = max(vh / size.height, vw / size.width)
            m.postScale(s, s, cx, cy)
            m.postRotate(90f * (rot - 2), cx, cy)
        } else {
            // The SurfaceTexture already rotates the image upright and stretches it to the view: undo the stretch (center-crop).
            val swapped = sensorOrientation % 180 != 0
            val cw = (if (swapped) size.height else size.width).toFloat()
            val ch = (if (swapped) size.width else size.height).toFloat()
            val ca = cw / ch; val va = vw / vh
            if (ca > va) m.setScale(ca / va, 1f, cx, cy) else m.setScale(1f, va / ca, cx, cy)
            if (rot == Surface.ROTATION_180) m.postRotate(180f, cx, cy)
        }
        view.setTransform(m)
    }
}
