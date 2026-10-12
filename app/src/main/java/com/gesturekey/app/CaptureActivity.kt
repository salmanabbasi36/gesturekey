package com.gesturekey.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Opened by the widget. Front camera, live gesture recognition on-device, and once a gesture
 * is held steadily: a short visible countdown (with Cancel), then the target app opens.
 * Frames are never written anywhere.
 */
class CaptureActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var hint: TextView
    private lateinit var seeing: TextView
    private lateinit var holdBar: ProgressBar
    private lateinit var bigEmoji: TextView
    private lateinit var sendingText: TextView
    private lateinit var cancelButton: Button

    private lateinit var settings: AppSettings
    private lateinit var stabilizer: GestureStabilizer

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var recognizer: GestureRecognizer? = null // analysis thread only

    @Volatile private var locked = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSend: Runnable? = null

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "GestureKey needs the camera to see your gesture", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings = SettingsStore(this).load()
        stabilizer = GestureStabilizer(settings.enabledLabels)
        setContentView(buildLayout())

        if (settings.enabledLabels.isEmpty()) {
            hint.text = "No gestures are switched on. Open the GestureKey app to set them up."
        }
        analysisExecutor.execute { loadRecognizer() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        analysisExecutor.execute {
            recognizer?.close()
            recognizer = null
        }
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun loadRecognizer() {
        try {
            recognizer = GestureRecognizer.createFromOptions(
                this,
                GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(1)
                    .build(),
            )
        } catch (e: Exception) {
            runOnUiThread { hint.text = "Couldn't load the gesture model: ${e.message}" }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {
                hint.text = "Couldn't open the front camera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Analysis thread. */
    private fun analyze(proxy: ImageProxy) {
        try {
            val model = recognizer ?: return
            if (locked) return

            val frame = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            val upright = if (rotation == 0) frame else {
                Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            }
            val result = model.recognizeForVideo(BitmapImageBuilder(upright).build(), SystemClock.uptimeMillis())
            if (upright !== frame) upright.recycle()
            frame.recycle()

            val top = result.gestures().firstOrNull()?.firstOrNull()
            val label = top?.categoryName() ?: Gesture.NONE
            val score = top?.score() ?: 0f
            val fired = stabilizer.offer(label, score)
            val candidate = stabilizer.candidate
            val progress = stabilizer.progress
            if (fired != null) locked = true

            runOnUiThread {
                showSeeing(candidate, label, progress)
                if (fired != null) onGesture(fired)
            }
        } catch (e: Exception) {
            // A bad frame is not worth crashing for; the next one will do.
        } finally {
            proxy.close()
        }
    }

    private fun showSeeing(candidate: String?, rawLabel: String, progress: Float) {
        if (pendingSend != null) return
        val gesture = candidate?.let { Gesture.fromLabel(it) }
        seeing.text = when {
            gesture != null -> "${gesture.emoji}  hold it…"
            rawLabel != Gesture.NONE && Gesture.fromLabel(rawLabel) != null -> "${Gesture.fromLabel(rawLabel)!!.emoji} is switched off"
            else -> "Show a gesture to the camera"
        }
        holdBar.progress = (progress * 100).toInt()
    }

    private fun onGesture(label: String) {
        val setting = settings.forLabel(label) ?: return unlock()
        val target = settings.targetFor(setting)
        bigEmoji.text = setting.gesture.emoji
        bigEmoji.visibility = View.VISIBLE
        sendingText.text = "${setting.message}\n→ ${describe(target)}"
        sendingText.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
        seeing.text = "Opening ${target.app.label}…"
        holdBar.progress = 100

        val send = Runnable {
            pendingSend = null
            val note = MessageLauncher.launch(this, target, setting.message)
            if (note != null) Toast.makeText(this, note, Toast.LENGTH_LONG).show()
            finish()
        }
        pendingSend = send
        mainHandler.postDelayed(send, SEND_DELAY_MS)
    }

    private fun cancel() {
        pendingSend?.let { mainHandler.removeCallbacks(it) }
        pendingSend = null
        bigEmoji.visibility = View.GONE
        sendingText.visibility = View.GONE
        cancelButton.visibility = View.GONE
        unlock()
    }

    private fun unlock() {
        analysisExecutor.execute {
            stabilizer.reset()
            locked = false
        }
    }

    private fun describe(target: Target): String {
        val phone = target.phone.trim()
        return if (target.app.usesPhone && phone.isNotEmpty()) "${target.app.label} · $phone" else target.app.label
    }

    private fun buildLayout(): View {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        val panel = { GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.argb(170, 0, 0, 0)) } }

        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }

        hint = TextView(this).apply {
            text = settings.gestures.filter { it.enabled && it.message.isNotBlank() }
                .joinToString("   ") { "${it.gesture.emoji} ${it.message.take(14)}" }
            setTextColor(Color.WHITE)
            textSize = 14f
            background = panel()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        bigEmoji = TextView(this).apply {
            textSize = 120f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        sendingText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = panel()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            visibility = View.GONE
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(bigEmoji)
            addView(sendingText)
        }

        seeing = TextView(this).apply {
            text = "Starting camera…"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        holdBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        cancelButton = Button(this).apply {
            text = "Cancel"
            visibility = View.GONE
            setOnClickListener { cancel() }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel()
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(seeing)
            addView(holdBar)
            addView(cancelButton)
        }

        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(previewView, FrameLayout.LayoutParams(-1, -1))
            addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(12), dp(48), dp(12), 0) })
            addView(center, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dp(24), 0, dp(24), 0) })
            addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(40)) })
        }
    }

    private companion object {
        const val MODEL_ASSET = "gesture_recognizer.task"
        const val SEND_DELAY_MS = 1_200L
    }
}
