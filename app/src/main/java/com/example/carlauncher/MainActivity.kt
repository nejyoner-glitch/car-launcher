package com.example.carlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.ToneGenerator
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val launcherUpdater by lazy { LauncherUpdater(this) }


    private data class AppItem(val label: String, val pkg: String?, val icon: Drawable?)

    private companion object {
        const val PER_PAGE = 6
        const val PAGES = 2
        const val REQ_LOCATION = 100
        var autoStarted = false

        val ACCENTS = intArrayOf(
            0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(),
            0xFFF06292.toInt(), 0xFFBA68C8.toInt()
        )
        val ACCENT_NAMES = arrayOf("Mavi", "Yeşil", "Turuncu", "Pembe", "Mor")
        val BGS = intArrayOf(
            0xFF0C1A2E.toInt(), 0xFF000000.toInt(), 0xFF14181F.toInt(),
            0xFF0F2A1D.toInt(), 0xFF2A1226.toInt()
        )
        val BG_NAMES = arrayOf("Lacivert", "Siyah", "Grafit", "Koyu yeşil", "Mürdüm")
        val LIMITS = intArrayOf(0, 50, 80, 90, 110, 120, 140)
        val DIRECTIONS = arrayOf("K", "KD", "D", "GD", "G", "GB", "B", "KB")
        const val RED = 0xFFEF5350.toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private lateinit var root: View
    private lateinit var clock: TextClock
    private lateinit var dateText: TextView
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var speedText: TextView
    private lateinit var headingText: TextView
    private lateinit var tripText: TextView
    private lateinit var gpsStatus: TextView
    private lateinit var weatherText: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var art: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var progress: ProgressBar
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout
    private lateinit var dots: TextView
    private var page = 0

    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("tr", "TR"))

    private var locationManager: LocationManager? = null
    private var lastLoc: Location? = null
    private var tripKm = 0f
    private var maxKmh = 0
    private var overLimit = false
    private var tone: ToneGenerator? = null

    private var weatherFetching = false
    private var lastWeatherFetch = 0L
    private var lastMediaRefresh = 0L
    private var lastMediaKey = ""
    private var lastMediaArt: Bitmap? = null
    private var lastAutoCarplayAttempt = 0L

    // ---------------------------------------------------------------- yaşam döngüsü

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("slots", MODE_PRIVATE)

        root = findViewById(R.id.root)
        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.date)
        speedGauge = findViewById(R.id.speedGauge)
        speedText = findViewById(R.id.speed)
        headingText = findViewById(R.id.heading)
        tripText = findViewById(R.id.trip)
        gpsStatus = findViewById(R.id.gpsStatus)
        weatherText = findViewById(R.id.weather)
        weatherText.text = prefs.getString("weather_cached", "Hava durumu bekleniyor")
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        art = findViewById(R.id.art)
        btnPlay = findViewById(R.id.btnPlay)
        progress = findViewById(R.id.progress)
        row1 = findViewById(R.id.row1)
        row2 = findViewById(R.id.row2)
        dots = findViewById(R.id.dots)

        val speedBox = findViewById<View>(R.id.speedBox)
        speedGauge.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            speedGauge.post { speedBox.translationY = speedGauge.numberCenterY() - speedBox.height / 2f }
        }

        tripKm = prefs.getFloat("trip", 0f)
        maxKmh = prefs.getInt("max", 0)

        findViewById<View>(R.id.btnPrev).setOnClickListener { mediaPrev() }
        findViewById<View>(R.id.btnNext).setOnClickListener { mediaNext() }
        btnPlay.setOnClickListener { mediaPlayPause() }
        findViewById<View>(R.id.btnAllApps).setOnClickListener { showAllApps() }
        findViewById<View>(R.id.btnCarPlay).setOnClickListener { openPhoneProjection() }
        findViewById<View>(R.id.btnVolDown).setOnClickListener { changeVolume(AudioManager.ADJUST_LOWER) }
        findViewById<View>(R.id.btnVolUp).setOnClickListener { changeVolume(AudioManager.ADJUST_RAISE) }
        findViewById<View>(R.id.btnMute).setOnClickListener {
            changeVolume(if (Build.VERSION.SDK_INT >= 23) AudioManager.ADJUST_TOGGLE_MUTE else AudioManager.ADJUST_MUTE)
        }
        findViewById<View>(R.id.btnVoice).setOnClickListener { startVoice() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.mediaCard).setOnClickListener {
            val c = activeController()
            if (c != null) {
                launchPackage(c.packageName)
            } else if (!hasNotificationAccess()) {
                openSettingsScreen(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        }
        findViewById<SwipeLayout>(R.id.mediaCard).onSwipe = { dir ->
            if (dir > 0) mediaNext() else mediaPrev()
        }
        findViewById<SwipeLayout>(R.id.shortcutArea).onSwipe = { dir ->
            val np = (page + dir).coerceIn(0, PAGES - 1)
            if (np != page) {
                page = np
                buildShortcuts()
            }
        }
        findViewById<View>(R.id.speedCard).setOnLongClickListener {
            resetTrip()
            Toast.makeText(this, "Gezi sayacı sıfırlandı", Toast.LENGTH_SHORT).show()
            true
        }

        buildShortcuts()
        applyTheme()
        updateTripText()
        requestLocationIfNeeded()
        if (savedInstanceState == null) showRenaultSplash()

        // CarPlay launch is scheduled from onResume after preferences and UI are ready.
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applyTheme()
        startLocation()
        handler.post(tick)
        launcherUpdater.onResume()
        scheduleCarPlayStart()
    }

    override fun onPause() {
        super.onPause()
        stopLocation()
        launcherUpdater.onPause()
        handler.removeCallbacks(tick)
        prefs.edit().putFloat("trip", tripKm).putInt("max", maxKmh).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        tone?.release()
        tone = null
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

    private fun showRenaultSplash() {
        // Only on a fresh Activity launch, not on resume from CarPlay or other apps.
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 11, 20))
            isClickable = true
            isFocusable = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val density = resources.displayMetrics.density
        val emblem = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 228, 238)
                style = Paint.Style.STROKE
                strokeWidth = 9f
                strokeJoin = Paint.Join.ROUND
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val sx = width / 200f
                val sy = height / 220f
                canvas.save()
                canvas.scale(sx, sy)
                val diamond = Path().apply {
                    moveTo(100f, 9f)
                    lineTo(176f, 108f)
                    lineTo(100f, 211f)
                    lineTo(24f, 108f)
                    close()
                }
                canvas.drawPath(diamond, paint)
                val inner = Path().apply {
                    moveTo(100f, 46f)
                    lineTo(145f, 108f)
                    lineTo(100f, 173f)
                    lineTo(55f, 108f)
                    close()
                }
                paint.strokeWidth = 7f
                canvas.drawPath(inner, paint)
                canvas.restore()
            }
        }
        content.addView(emblem, LinearLayout.LayoutParams((130 * density).toInt(), (142 * density).toInt()))
        content.addView(TextView(this).apply {
            text = "RENAULT"
            textSize = 27f
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (12 * density).toInt() })
        overlay.addView(content, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val decor = window.decorView as ViewGroup
        decor.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(220).start()
        handler.postDelayed({
            if (!isFinishing && overlay.parent != null) {
                overlay.animate().alpha(0f).setDuration(350).withEndAction {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                }.start()
            }
        }, 1650)
    }

    private fun scheduleCarPlayStart() {
        if (!prefs.getBoolean("carplayAuto", true)) return
        val pkg = prefs.getString("autostart", null) ?: return
        val now = SystemClock.elapsedRealtime()
        if (autoStarted || now - lastAutoCarplayAttempt < 20_000L) return
        autoStarted = true
        lastAutoCarplayAttempt = now
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && hasWindowFocus() && !launchPackage(pkg)) {
                Toast.makeText(this, "CarPlay uygulaması açılamadı. Ayarlar'dan tekrar seç.", Toast.LENGTH_LONG).show()
            }
        }, 2200)
    }

    private fun accentColor(): Int = ACCENTS[prefs.getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)]

    private fun applyTheme() {
        root.setBackgroundColor(BGS[prefs.getInt("bg", 0).coerceIn(0, BGS.size - 1)])
        speedText.setTextColor(if (overLimit) RED else Color.WHITE)
        speedGauge.gaugeColor = if (overLimit) RED else accentColor()
        progress.progressTintList = ColorStateList.valueOf(accentColor())
        progress.progressBackgroundTintList = ColorStateList.valueOf(0xFF2A3A52.toInt())

        val fmt = if (prefs.getBoolean("h24", true)) "HH:mm" else "h:mm a"
        clock.setFormat12Hour(fmt)
        clock.setFormat24Hour(fmt)

        if (prefs.getBoolean("keepOn", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        weatherText.text = if (prefs.getBoolean("weather", true)) prefs.getString("weather_cached", "Hava durumu bekleniyor") else ""
    }

    // Saniyede bir: tarih (Türkçe) ve medya bilgisi.
    private val tick = object : Runnable {
        override fun run() {
            dateText.text = dateFormat.format(Date())
            if (SystemClock.elapsedRealtime() - lastMediaRefresh >= 1500L) {
                refreshMedia()
                lastMediaRefresh = SystemClock.elapsedRealtime()
            }
            handler.postDelayed(this, 1000)
        }
    }

    // ---------------------------------------------------------------- kısayollar

    private fun buildShortcuts() {
        row1.removeAllViews()
        row2.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (i in 0 until PER_PAGE) {
            val slot = page * PER_PAGE + i
            val parent = if (i < PER_PAGE / 2) row1 else row2
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
        dots.text = (0 until PAGES).joinToString("   ") { if (it == page) "●" else "○" }
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

    private fun openPhoneProjection() {
        val preferred = prefs.getString("carplay_package", null)
            ?: prefs.getString("autostart", null)
        val candidates = listOfNotNull(preferred) + listOf(
            "com.zjinnova.zlink", "com.zjinnova.zlink5", "com.zjinnova.zlink6",
            "com.carlinkit.autokit"
        )
        if (candidates.distinct().any { launchPackage(it) }) return
        showAppDialog("Android Auto / CarPlay uygulamasını seç", queryApps()) { selected ->
            selected.pkg?.let {
                prefs.edit().putString("carplay_package", it).apply()
                launchPackage(it)
            }
        }
    }

    private fun showAllApps() {
        startActivity(Intent(this, AllAppsActivity::class.java))
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

    private fun openSettingsScreen(action: String) {
        try {
            val i = Intent(action)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Bu ekran bu ünitede açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- ayarlar

    private fun showSettings() {
        val limit = LIMITS[prefs.getInt("limit", 0).coerceIn(0, LIMITS.size - 1)]
        val autoPkg = prefs.getString("autostart", null)
        val autoLabel = autoPkg?.let { loadApp(it)?.label } ?: "Yok"
        val entries = arrayOf(
            "Hız uyarı limiti: " + (if (limit == 0) "Kapalı" else "$limit km/h"),
            "Vurgu rengi: " + ACCENT_NAMES[prefs.getInt("accent", 0).coerceIn(0, ACCENT_NAMES.size - 1)],
            "Arka plan: " + BG_NAMES[prefs.getInt("bg", 0).coerceIn(0, BG_NAMES.size - 1)],
            "Saat biçimi: " + (if (prefs.getBoolean("h24", true)) "24 saat" else "12 saat"),
            "Ekranı açık tut: " + (if (prefs.getBoolean("keepOn", true)) "Açık" else "Kapalı"),
            "Hava durumu: " + (if (prefs.getBoolean("weather", true)) "Açık" else "Kapalı"),
            "Açılışta otomatik açılacak uygulama: $autoLabel",
            "Gezi sayacını sıfırla",
            "Medya bilgisi izni (bildirim erişimi)",
            "Wi-Fi ayarları",
            "Bluetooth ayarları",
            "Ekran ve parlaklık ayarları",
            "Varsayılan ana ekran uygulaması",
            "Sistem ayarları",
            "Güncellemeleri kontrol et",
            "İndirilen güncellemeyi yükle",
            "CarPlay otomatik açılış: " + (if (prefs.getBoolean("carplayAuto", true)) "Açık" else "Kapalı")
        )
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.settings_title)
            .setItems(entries) { _, which -> handleSetting(which) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun handleSetting(which: Int) {
        when (which) {
            0 -> {
                val next = (prefs.getInt("limit", 0) + 1) % LIMITS.size
                prefs.edit().putInt("limit", next).apply()
                showSettings()
            }
            1 -> {
                val next = (prefs.getInt("accent", 0) + 1) % ACCENTS.size
                prefs.edit().putInt("accent", next).apply()
                applyTheme()
                showSettings()
            }
            2 -> {
                val next = (prefs.getInt("bg", 0) + 1) % BGS.size
                prefs.edit().putInt("bg", next).apply()
                applyTheme()
                showSettings()
            }
            3 -> {
                prefs.edit().putBoolean("h24", !prefs.getBoolean("h24", true)).apply()
                applyTheme()
                showSettings()
            }
            4 -> {
                prefs.edit().putBoolean("keepOn", !prefs.getBoolean("keepOn", true)).apply()
                applyTheme()
                showSettings()
            }
            5 -> {
                prefs.edit().putBoolean("weather", !prefs.getBoolean("weather", true)).apply()
                lastWeatherFetch = 0L
                applyTheme()
                showSettings()
            }
            6 -> {
                val items = mutableListOf(AppItem(getString(R.string.remove_shortcut), null, null))
                items += queryApps()
                showAppDialog("Açılışta açılacak uygulama", items) { chosen ->
                    if (chosen.pkg == null) prefs.edit().remove("autostart").apply()
                    else prefs.edit().putString("autostart", chosen.pkg).apply()
                    showSettings()
                }
            }
            7 -> {
                resetTrip()
                showSettings()
            }
            8 -> openSettingsScreen(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            9 -> openSettingsScreen(Settings.ACTION_WIFI_SETTINGS)
            10 -> openSettingsScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
            11 -> openSettingsScreen(Settings.ACTION_DISPLAY_SETTINGS)
            12 -> openSettingsScreen(Settings.ACTION_HOME_SETTINGS)
            13 -> openSettingsScreen(Settings.ACTION_SETTINGS)
            14 -> launcherUpdater.check(true)
            15 -> launcherUpdater.retryInstall()
            16 -> {
                prefs.edit().putBoolean("carplayAuto", !prefs.getBoolean("carplayAuto", true)).apply()
                showSettings()
            }
        }
    }

    // ---------------------------------------------------------------- ses ve asistan

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun changeVolume(direction: Int) {
        try {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            // bazı ünitelerde ses MCU üzerinden yönetilir
        }
    }

    private fun startVoice() {
        val intents = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent("android.intent.action.VOICE_ASSIST"),
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        )
        for (i in intents) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (e: Exception) {
                // sıradakini dene
            }
        }
        Toast.makeText(this, "Sesli asistan bulunamadı", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- medya

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
            progress.visibility = View.INVISIBLE
            lastMediaKey = ""
            lastMediaArt = null
            return
        }
        val meta = controller.metadata
        val songTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(R.string.no_media)
        val songArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        if (title.text.toString() != songTitle) title.text = songTitle
        if (artist.text.toString() != songArtist) artist.text = songArtist
        val bmp: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val mediaKey = controller.packageName + "|" + songTitle + "|" + songArtist
        if (mediaKey != lastMediaKey || bmp !== lastMediaArt) {
            if (bmp != null) {
                art.setPadding(0, 0, 0, 0)
                art.setImageBitmap(bmp)
            } else {
                art.setPadding(24, 24, 24, 24)
                art.setImageResource(R.drawable.ic_music)
            }
            lastMediaKey = mediaKey
            lastMediaArt = bmp
        }
        val state = controller.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)

        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (duration > 0L && state != null) {
            var pos = state.position
            if (playing) {
                val delta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                pos += (delta * state.playbackSpeed).toLong()
            }
            progress.progress = (pos.coerceIn(0L, duration) * 1000L / duration).toInt()
            progress.visibility = View.VISIBLE
        } else {
            progress.visibility = View.INVISIBLE
        }
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
        val c = activeController()
        if (c != null) c.transportControls.skipToNext() else sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        handler.postDelayed({ refreshMedia() }, 400)
    }

    private fun mediaPrev() {
        val c = activeController()
        if (c != null) c.transportControls.skipToPrevious() else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        handler.postDelayed({ refreshMedia() }, 400)
    }

    private fun sendMediaKey(code: Int) {
        val am = audio()
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------------------------------------------------------------- hız, yön, gezi (GPS)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val kmhF = if (location.hasSpeed()) location.speed * 3.6f else 0f
            val kmh = if (kmhF < 2f) 0 else kmhF.roundToInt()
            speedText.text = kmh.toString()
            speedGauge.speedKmh = kmh
            if (kmh > maxKmh) maxKmh = kmh

            val prev = lastLoc
            if (prev != null && kmh >= 3) tripKm += prev.distanceTo(location) / 1000f
            lastLoc = location

            if (kmh >= 5 && location.hasBearing()) {
                headingText.text = DIRECTIONS[(((location.bearing + 22.5f) / 45f).toInt()) % 8]
            }
            gpsStatus.text = if (location.hasAltitude()) "GPS • ${location.altitude.roundToInt()} m" else "GPS"

            val limit = LIMITS[prefs.getInt("limit", 0).coerceIn(0, LIMITS.size - 1)]
            val over = limit > 0 && kmh > limit
            if (over && !overLimit) beep()
            overLimit = over
            speedText.setTextColor(if (over) RED else Color.WHITE)
            speedGauge.gaugeColor = if (over) RED else accentColor()

            updateTripText()
            maybeFetchWeather(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            gpsStatus.text = getString(R.string.gps_waiting)
        }
    }

    private fun updateTripText() {
        tripText.text = "Gezi " + String.format(Locale.US, "%.1f", tripKm) + " km · Maks $maxKmh km/h"
    }

    private fun resetTrip() {
        tripKm = 0f
        maxKmh = 0
        prefs.edit().putFloat("trip", 0f).putInt("max", 0).apply()
        updateTripText()
    }

    private fun beep() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        } catch (e: Exception) {
            // sessizce geç
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
            // Hava durumu için en son bilinen konumu hemen kullan.
            val known = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (known != null) maybeFetchWeather(known)
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

    // ---------------------------------------------------------------- hava durumu (Open-Meteo)

    private fun maybeFetchWeather(loc: Location) {
        if (!prefs.getBoolean("weather", true)) return
        val now = SystemClock.elapsedRealtime()
        if (weatherFetching) return
        if (lastWeatherFetch != 0L && now - lastWeatherFetch < 15 * 60 * 1000L) return
        weatherFetching = true
        lastWeatherFetch = now
        val lat = loc.latitude
        val lon = loc.longitude
        Thread {
            var result: String? = null
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                            "&current=temperature_2m,weather_code&timezone=auto"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val cur = JSONObject(body).getJSONObject("current")
                val temp = cur.getDouble("temperature_2m").roundToInt()
                result = "$temp°C · " + weatherName(cur.getInt("weather_code"))
            } catch (e: Exception) {
                // internet yoksa 1 dakika sonra tekrar dene
                lastWeatherFetch = SystemClock.elapsedRealtime() - 14 * 60 * 1000L
            }
            val finalResult = result
            runOnUiThread {
                weatherFetching = false
                if (finalResult != null) {
                    prefs.edit().putString("weather_cached", finalResult).apply()
                    if (prefs.getBoolean("weather", true)) weatherText.text = finalResult
                }
            }
        }.start()
    }

    private fun weatherName(code: Int): String = when (code) {
        0 -> "Açık"
        1 -> "Çoğunlukla açık"
        2 -> "Parçalı bulutlu"
        3 -> "Bulutlu"
        45, 48 -> "Sisli"
        in 51..57 -> "Çiseleme"
        in 61..67 -> "Yağmurlu"
        in 71..77 -> "Karlı"
        in 80..82 -> "Sağanak yağış"
        85, 86 -> "Kar sağanağı"
        in 95..99 -> "Gök gürültülü fırtına"
        else -> ""
    }
}
