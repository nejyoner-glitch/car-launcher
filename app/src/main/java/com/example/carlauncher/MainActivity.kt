package com.example.carlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private data class AppItem(val label: String, val pkg: String?, val icon: Drawable?)

    private companion object {
        const val SLOT_COUNT = 6
        const val REQ_LOCATION = 100
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private lateinit var speedText: TextView
    private lateinit var gpsStatus: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var art: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout

    private var locationManager: LocationManager? = null
    private var lastFixTime = 0L

    // ---------------------------------------------------------------- yaşam döngüsü

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("slots", MODE_PRIVATE)

        speedText = findViewById(R.id.speed)
        gpsStatus = findViewById(R.id.gpsStatus)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        art = findViewById(R.id.art)
        btnPlay = findViewById(R.id.btnPlay)
        row1 = findViewById(R.id.row1)
        row2 = findViewById(R.id.row2)

        findViewById<View>(R.id.btnPrev).setOnClickListener { mediaPrev() }
        findViewById<View>(R.id.btnNext).setOnClickListener { mediaNext() }
        btnPlay.setOnClickListener { mediaPlayPause() }
        findViewById<View>(R.id.btnAllApps).setOnClickListener { showAllApps() }
        findViewById<View>(R.id.mediaCard).setOnClickListener {
            if (!hasNotificationAccess()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        buildShortcuts()
        requestLocationIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        startLocation()
        handler.post(mediaTick)
    }

    override fun onPause() {
        super.onPause()
        stopLocation()
        handler.removeCallbacks(mediaTick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Launcher'dan geri tuşuyla çıkılmasın.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    // ---------------------------------------------------------------- kısayollar

    private fun buildShortcuts() {
        row1.removeAllViews()
        row2.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (slot in 0 until SLOT_COUNT) {
            val parent = if (slot < SLOT_COUNT / 2) row1 else row2
            val view = inflater.inflate(R.layout.item_app, parent, false)
            val icon = view.findViewById<ImageView>(R.id.icon)
            val label = view.findViewById<TextView>(R.id.label)

            val pkg = prefs.getString("slot$slot", null)
            val item = pkg?.let { loadApp(it) }
            if (item != null) {
                icon.setImageDrawable(item.icon)
                label.text = item.label
            } else {
                icon.setImageResource(R.drawable.ic_add)
                label.text = getString(R.string.add_shortcut)
            }

            view.setOnClickListener {
                val p = prefs.getString("slot$slot", null)
                if (p != null && launchPackage(p)) return@setOnClickListener
                pickForSlot(slot)
            }
            view.setOnLongClickListener {
                pickForSlot(slot)
                true
            }
            parent.addView(view)
        }
    }

    private fun pickForSlot(slot: Int) {
        val items = mutableListOf(AppItem(getString(R.string.remove_shortcut), null, null))
        items += queryApps()
        showAppDialog(getString(R.string.pick_app), items) { chosen ->
            if (chosen.pkg == null) prefs.edit().remove("slot$slot").apply()
            else prefs.edit().putString("slot$slot", chosen.pkg).apply()
            buildShortcuts()
        }
    }

    private fun showAllApps() {
        showAppDialog(getString(R.string.all_apps), queryApps()) { chosen ->
            chosen.pkg?.let { launchPackage(it) }
        }
    }

    private fun showAppDialog(titleText: String, items: List<AppItem>, onPick: (AppItem) -> Unit) {
        val adapter = object : ArrayAdapter<AppItem>(this, 0, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_picker, parent, false)
                val item = getItem(position)!!
                val icon = v.findViewById<ImageView>(R.id.icon)
                if (item.icon != null) {
                    icon.setImageDrawable(item.icon)
                    icon.visibility = View.VISIBLE
                } else {
                    icon.visibility = View.INVISIBLE
                }
                v.findViewById<TextView>(R.id.label).text = item.label
                return v
            }
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(titleText)
            .setAdapter(adapter) { _, which -> onPick(items[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun queryApps(): List<AppItem> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun loadApp(pkg: String): AppItem? = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        AppItem(packageManager.getApplicationLabel(info).toString(), pkg, packageManager.getApplicationIcon(info))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun launchPackage(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- medya

    private val mediaTick = object : Runnable {
        override fun run() {
            refreshMedia()
            handler.postDelayed(this, 1000)
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun activeController(): MediaController? {
        if (!hasNotificationAccess()) return null
        return try {
            val sm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = sm.getActiveSessions(ComponentName(this, MediaListener::class.java))
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }

    private fun refreshMedia() {
        val controller = activeController()
        if (controller == null) {
            title.text = if (hasNotificationAccess()) getString(R.string.no_media) else getString(R.string.media_access)
            artist.text = ""
            art.setImageResource(R.drawable.ic_music)
            btnPlay.setImageResource(R.drawable.ic_play)
            return
        }
        val meta = controller.metadata
        title.text = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(R.string.no_media)
        artist.text = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val bmp: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (bmp != null) {
            art.setPadding(0, 0, 0, 0)
            art.setImageBitmap(bmp)
        } else {
            art.setPadding(24, 24, 24, 24)
            art.setImageResource(R.drawable.ic_music)
        }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun mediaPlayPause() {
        val c = activeController()
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        } else {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        handler.postDelayed({ refreshMedia() }, 300)
    }

    private fun mediaNext() {
        activeController()?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        handler.postDelayed({ refreshMedia() }, 400)
    }

    private fun mediaPrev() {
        activeController()?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        handler.postDelayed({ refreshMedia() }, 400)
    }

    private fun sendMediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------------------------------------------------------------- hız (GPS)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFixTime = System.currentTimeMillis()
            val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            speedText.text = (if (kmh < 2f) 0 else kmh.roundToInt()).toString()
            gpsStatus.text = "GPS"
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            gpsStatus.text = getString(R.string.gps_waiting)
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && !hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) startLocation()
    }

    private fun startLocation() {
        if (!hasLocationPermission()) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
            }
        } catch (e: SecurityException) {
            // izin yok, sessizce geç
        }
    }

    private fun stopLocation() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: SecurityException) {
        }
    }
}
