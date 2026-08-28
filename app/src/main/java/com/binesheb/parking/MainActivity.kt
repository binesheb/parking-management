package com.binesheb.parking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView; private lateinit var status: TextView; private lateinit var result: TextView; private lateinit var capacityView: TextView; private lateinit var store: EventStore
    private val executor = Executors.newSingleThreadExecutor(); private val busy = AtomicBoolean(false); private val sync by lazy { SyncClient(this) }; private val handler = Handler(Looper.getMainLooper())
    private var lastPlate = ""; private var stableCount = 0; private var lastEventAt = 0L; private var role = DeviceRole.ENTRY_GATE
    private val retry = object : Runnable { override fun run() { sync.retryPending(); handler.postDelayed(this, 30_000) } }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() else status.text = "Camera permission is required" }
    override fun onCreate(state: Bundle?) { super.onCreate(state); role = DeviceRole.fromCode(getPreferences(MODE_PRIVATE).getString("role", DeviceRole.ENTRY_GATE.code)!!); store = EventStore(this); buildUi(); UpdateManager.check(this, BuildConfig.VERSION_CODE) { outcome -> runOnUiThread { if (outcome == "update_ready") status.text = "Verified update ready" } }; handler.post(retry); if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA) }
    private fun capacity(): Int = getSharedPreferences("parking", MODE_PRIVATE).getInt("capacity", 0)
    private fun parkedCount(): Int = store.currentParkingCount()
    private fun buildUi() { val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 10, 16, 10) }; val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; val title = TextView(this).apply { text = "PARKING • ${role.label}"; textSize = 20f; setPadding(4, 4, 12, 4) }; val roleButton = Button(this).apply { text = "Role"; setOnClickListener { chooseRole() } }; val capacityButton = Button(this).apply { text = "Parking"; setOnClickListener { configureCapacity() } }; val settingsButton = Button(this).apply { text = "Server"; setOnClickListener { configureServer() } }; header.addView(title, LinearLayout.LayoutParams(0, 60, 1f)); header.addView(roleButton); header.addView(capacityButton); header.addView(settingsButton); preview = PreviewView(this); result = TextView(this).apply { text = "Waiting for vehicle…"; textSize = 30f; setPadding(8, 12, 8, 12) }; capacityView = TextView(this).apply { textSize = 18f; setPadding(8, 4, 8, 8) }; status = TextView(this).apply { textSize = 15f }; root.addView(header); root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(result); root.addView(capacityView); root.addView(status); setContentView(root); refreshCapacity() }
    private fun configureCapacity() { val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(if (capacity() == 0) "" else capacity().toString()); hint = "Number of parking spaces" }; AlertDialog.Builder(this).setTitle("Parking capacity").setMessage("Set the total number of parking spaces. Use 0 for unlimited/not configured.").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> val value = input.text.toString().toIntOrNull()?.coerceIn(0, 100000) ?: 0; getSharedPreferences("parking", MODE_PRIVATE).edit().putInt("capacity", value).apply(); refreshCapacity() }.show() }
    private fun refreshCapacity() { val cap = capacity(); val occupied = parkedCount(); capacityView.text = if (cap > 0) "Parking: $occupied / $cap occupied  •  ${maxOf(cap - occupied, 0)} FREE" else "Parking capacity: not configured" }
    private fun chooseRole() { val labels = DeviceRole.entries.map { it.label }.toTypedArray(); AlertDialog.Builder(this).setTitle("Camera role").setItems(labels) { _, which -> role = DeviceRole.entries[which]; getPreferences(MODE_PRIVATE).edit().putString("role", role.code).apply(); recreate() }.show() }
    private fun configureServer() { val current = getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", "") ?: ""; val input = EditText(this).apply { setText(current); hint = "http://192.168.1.10:8080" }; AlertDialog.Builder(this).setTitle("Central server URL").setMessage("Leave blank for local-only operation.").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> getSharedPreferences("settings", MODE_PRIVATE).edit().putString("endpoint", input.text.toString().trim()).apply(); status.text = "Server setting saved"; sync.retryPending() }.show() }
    private fun startCamera() { val future = ProcessCameraProvider.getInstance(this); future.addListener({ val provider = future.get(); val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }; val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); analysis.setAnalyzer(executor) { proxy -> val media = proxy.image; if (media == null || !busy.compareAndSet(false, true)) { proxy.close(); return@setAnalyzer }; recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { PlateParser.find(it.text)?.let(::onCandidate) }.addOnCompleteListener { proxy.close(); busy.set(false) } }; provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysis); status.post { status.text = "Camera active • ${role.label} • auto recognition enabled" } }, ContextCompat.getMainExecutor(this)) }
    private fun onCandidate(plate: String) { if (plate == lastPlate) stableCount++ else { lastPlate = plate; stableCount = 1 }; if (stableCount < 3 || System.currentTimeMillis() - lastEventAt < 4000) return; lastEventAt = System.currentTimeMillis(); stableCount = 0; if (role == DeviceRole.PARKING_ENTRY && capacity() > 0 && parkedCount() >= capacity()) { result.post { result.text = "$plate  •  PARKING FULL" }; status.post { status.text = "Vehicle not recorded: parking is full" }; return }; val event = store.insert(ParkingEvent(plate = plate, eventType = role.eventType, timestamp = System.currentTimeMillis(), deviceRole = role.code)); result.post { result.text = "${event.plate}  •  ${event.eventType}" }; refreshCapacity(); status.post { status.text = "Recorded locally • ${store.count()} events • sync queue active" }; sync.enqueue(event) }
    override fun onDestroy() { handler.removeCallbacks(retry); executor.shutdown(); super.onDestroy() }
}
