package com.example.carlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

class DashboardActivity : Activity(), LocationListener {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var tripKm = 0.0
    private var speedKmh = 0
    private val prefs by lazy { getSharedPreferences("reference_dashboard", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/dashboard.html")
        tripKm = prefs.getFloat("trip", 0f).toDouble()
        handler.postDelayed({ updateUi() }, 1500)
    }

    inner class Bridge {
        @JavascriptInterface fun action(name: String) {
            runOnUiThread {
                when (name) {
                    "carplay" -> openConfigured("carplay", "CarPlay / Zlink")
                    "radio" -> openConfigured("radio", "Radyo")
                    "apps" -> showApps()
                    "navigation" -> openConfigured("navigation", "Navigasyon")
                    "prev" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    "play" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    "next" -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    "saveParking" -> {
                        lastLocation?.let { prefs.edit().putString("parking", "${it.latitude},${it.longitude}").apply() }
                        Toast.makeText(this@DashboardActivity, if (lastLocation != null) "Park konumu kaydedildi" else "GPS konumu bekleniyor", Toast.LENGTH_SHORT).show()
                    }
                    "openParking" -> {
                        val coord = prefs.getString("parking", null)
                        if (coord != null) try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:$coord?q=$coord"))) } catch (_: Exception) {}
                    }
                    "settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
                    "update" -> Toast.makeText(this@DashboardActivity, "Güncelleme sistemi henüz bu arayüze bağlanmadı", Toast.LENGTH_LONG).show()
                    "history" -> Toast.makeText(this@DashboardActivity, "Sürüş geçmişi henüz bağlanmadı", Toast.LENGTH_LONG).show()
                    "speedLimit" -> Toast.makeText(this@DashboardActivity, "Hız uyarısı ayarı hazırlanıyor", Toast.LENGTH_LONG).show()
                    "volumeNormal" -> setVolume(0.5f)
                    "volumeSport" -> setVolume(0.8f)
                    "volumeQuiet" -> setVolume(0.15f)
                }
            }
        }
    }

    private fun setVolume(fraction: Float) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * fraction).toInt(), 0)
    }

    private fun mediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = android.os.SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun openConfigured(key: String, label: String) {
        val pkg = prefs.getString(key, null)
        if (pkg != null && packageManager.getLaunchIntentForPackage(pkg) != null) {
            startActivity(packageManager.getLaunchIntentForPackage(pkg))
            return
        }
        chooseApp(label) { selected ->
            prefs.edit().putString(key, selected).apply()
            packageManager.getLaunchIntentForPackage(selected)?.let { startActivity(it) }
        }
    }

    private fun showApps() = chooseApp("Uygulamalar") { pkg ->
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    private fun chooseApp(title: String, chosen: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.ROOT) }
        val names = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle(title).setItems(names) { _, which ->
            chosen(apps[which].activityInfo.packageName)
        }.setNegativeButton("İptal", null).show()
    }

    private fun js(script: String) { if (::web.isInitialized) web.evaluateJavascript(script, null) }
    private fun updateUi() {
        js("window.setTelemetry(${speedKmh},'${String.format(Locale.US, "%.1f", tripKm)}')")
        try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(this, MediaListener::class.java)
            val controller = manager.getActiveSessions(component).firstOrNull()
            val metadata = controller?.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            js("window.setMedia(${JSONObject.quote(title)},${JSONObject.quote(artist)})")
        } catch (_: Exception) {}
        handler.postDelayed({ updateUi() }, 3000)
    }

    override fun onResume() {
        super.onResume()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 3f, this) } catch (_: Exception) {}
        } else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
    }
    override fun onLocationChanged(location: Location) {
        speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt().coerceAtLeast(0) else 0
        lastLocation?.let { previous ->
            val meters = previous.distanceTo(location)
            if (meters in 1f..200f && location.accuracy < 50f) tripKm += meters / 1000.0
        }
        lastLocation = location
    }
    override fun onPause() {
        super.onPause()
        locationManager?.removeUpdates(this)
        prefs.edit().putFloat("trip", tripKm.toFloat()).apply()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { js("show('home')") }
}
