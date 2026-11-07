package com.example.carlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
import java.util.Calendar
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
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

    private data class AppItem(val label: String, val pkg: String?, val icon: Drawable?)

    private companion object {
        const val SLOT_COUNT = 6
        const val REQ_LOCATION = 100
        const val REQ_WALLPAPER = 101
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

    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("tr", "TR"))

    private var locationManager: LocationManager? = null
    private var lastLoc: Location? = null
    private var pendingAutostart: Runnable? = null
    private var tripKm = 0f
    private var maxKmh = 0
    private var driveStartedAt = 0L
    private var drivingSeconds = 0L
    private var lastThemeHour = -1
    private var cachedWallpaperUri: String? = null
    private var cachedWallpaper: BitmapDrawable? = null
    private var overLimit = false
    private var tone: ToneGenerator? = null

    private var weatherFetching = false
    private var lastWeatherFetch = 0L

    // ---------------------------------------------------------------- yaşam döngüsü

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("slots", MODE_PRIVATE)

        root = findViewById(R.id.root)
        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.date)
        speedText = findViewById(R.id.speed)
        headingText = findViewById(R.id.heading)
        tripText = findViewById(R.id.trip)
        gpsStatus = findViewById(R.id.gpsStatus)
        weatherText = findViewById(R.id.weather)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        art = findViewById(R.id.art)
        btnPlay = findViewById(R.id.btnPlay)
        progress = findViewById(R.id.progress)
        row1 = findViewById(R.id.row1)
        row2 = findViewById(R.id.row2)

        tripKm = prefs.getFloat("trip", 0f)
        maxKmh = prefs.getInt("max", 0)
        drivingSeconds = prefs.getLong("driveSeconds", 0L)

        findViewById<View>(R.id.btnPrev).setOnClickListener { mediaPrev() }
        findViewById<View>(R.id.btnNext).setOnClickListener { mediaNext() }
        btnPlay.setOnClickListener { mediaPlayPause() }
        findViewById<View>(R.id.quickCarPlay).setOnClickListener { openCarPlay() }
        findViewById<View>(R.id.quickRadio).setOnClickListener { openRadio() }
        findViewById<View>(R.id.quickNav).setOnClickListener { openNavigation() }
        findViewById<View>(R.id.btnAllApps).setOnClickListener { showAllApps() }
        findViewById<View>(R.id.btnAllApps).setOnLongClickListener { showQuickActions(); true }
        clock.setOnClickListener { showQuickActions() }
        clock.setOnLongClickListener { showScreenSaver(); true }
        weatherText.setOnClickListener { showQuickActions() }
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
        findViewById<View>(R.id.speedCard).setOnLongClickListener {
            resetTrip()
            Toast.makeText(this, "Gezi sayacı sıfırlandı", Toast.LENGTH_SHORT).show()
            true
        }

        buildShortcuts()
        val widgetIds = intArrayOf(R.id.weather, R.id.mediaCard, R.id.speedCard)
        widgetIds.forEachIndexed { index, id -> if (!prefs.getBoolean("widget$index", true)) findViewById<View>(id).visibility = View.GONE }
        applyTheme()
        updateTripText()
        requestLocationIfNeeded()

        // Açılışta seçili uygulamayı (ör. Zlink) bir kez otomatik başlat.
        if (savedInstanceState == null && !autoStarted) {
            val pkg = prefs.getString("autostart", null)
            if (pkg != null) {
                autoStarted = true
                pendingAutostart = Runnable { launchPackage(pkg); pendingAutostart = null }
                handler.postDelayed(pendingAutostart!!, 3000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applyTheme()
        startLocation()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        stopLocation()
        handler.removeCallbacks(tick)
        pendingAutostart?.let { handler.removeCallbacks(it) }
        pendingAutostart = null
        prefs.edit().putFloat("trip", tripKm).putInt("max", maxKmh).putLong("driveSeconds", drivingSeconds).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
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

    private fun accentColor(): Int = ACCENTS[prefs.getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)]

    private fun applyTheme() {
        lastThemeHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = prefs.getBoolean("nightAuto", false) && (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 19 || Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 7)
        root.setBackgroundColor(if (night) 0xFF05080E.toInt() else BGS[prefs.getInt("bg", 0).coerceIn(0, BGS.size - 1)])
        val wallpaper = prefs.getString("wallpaper", null)
        if (wallpaper != null) try {
            if (cachedWallpaperUri != wallpaper) {
                val input = contentResolver.openInputStream(Uri.parse(wallpaper))
                val bitmap = input?.use { BitmapFactory.decodeStream(it) }
                cachedWallpaper = bitmap?.let { BitmapDrawable(resources, it) }
                cachedWallpaperUri = wallpaper
            }
            cachedWallpaper?.let { root.background = it }
        } catch (_: Exception) { prefs.edit().remove("wallpaper").apply() }
        if (!overLimit) speedText.setTextColor(accentColor())
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
        if (!prefs.getBoolean("weather", true)) weatherText.text = ""
    }

    // Saniyede bir: tarih (Türkçe) ve medya bilgisi.
    private val tick = object : Runnable {
        override fun run() {
            dateText.text = dateFormat.format(Date())
            refreshMedia()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (prefs.getBoolean("nightAuto", false) && hour != lastThemeHour) applyTheme()
            handler.postDelayed(this, 1000)
        }
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
            "CarPlay / Zlink hızlı aç",
            "Bağlantı durumu",
            "Sürüş geçmişi",
            "Park konumunu kaydet",
            "Park konumunu haritada aç",
            "Radyo uygulamasını seç / aç",
            "Ses profili",
            "Otomatik gece modu: " + (if (prefs.getBoolean("nightAuto", false)) "Açık" else "Kapalı"),
            "Duvar kağıdı seç",
            "Duvar kağıdını kaldır",
            "Ekran koruyucu",
            "Widget görünürlüğü",
            "Araç verileri / OBD2 bilgisi"
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
            14 -> openCarPlay()
            15 -> showConnectionStatus()
            16 -> showTripHistory()
            17 -> saveParkLocation()
            18 -> openParkLocation()
            19 -> openRadio()
            20 -> showSoundProfiles()
            21 -> { prefs.edit().putBoolean("nightAuto", !prefs.getBoolean("nightAuto", false)).apply(); applyTheme(); showSettings() }
            22 -> pickWallpaper()
            23 -> { prefs.edit().remove("wallpaper").apply(); cachedWallpaperUri = null; cachedWallpaper = null; root.background = null; applyTheme(); showSettings() }
            24 -> showScreenSaver()
            25 -> showWidgetOptions()
            26 -> AlertDialog.Builder(this).setTitle("Araç verileri").setMessage("T100 Renault RZ CANBUS bilgilerine standart Android API üzerinden doğrudan erişilemiyor. Motor devri, yakıt ve arıza kodları için uyumlu OBD2 adaptörü veya üretici SDK gerekir. GPS hız ve yol verileri kullanılabilir.").setPositiveButton("Tamam", null).show()
        }
    }


    private fun archiveTrip() {
        val history = org.json.JSONArray(prefs.getString("tripHistory", "[]"))
        val item = JSONObject()
        item.put("date", SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date()))
        item.put("km", tripKm.toDouble())
        item.put("max", maxKmh)
        item.put("seconds", drivingSeconds)
        val updated = org.json.JSONArray().put(item)
        for (i in 0 until minOf(49, history.length())) updated.put(history.get(i))
        prefs.edit().putString("tripHistory", updated.toString()).apply()
    }

    private fun showTripHistory() {
        val history = org.json.JSONArray(prefs.getString("tripHistory", "[]"))
        val lines = (0 until history.length()).map { i ->
            val trip = history.getJSONObject(i)
            "${trip.optString("date")} · ${String.format(Locale.US, "%.1f", trip.optDouble("km"))} km · ${trip.optInt("max")} km/h"
        }
        AlertDialog.Builder(this).setTitle("Sürüş geçmişi").setMessage(
            if (lines.isEmpty()) "Henüz kayıt yok. Gezi sayacını sıfırladığında yolculuk kaydedilir." else lines.joinToString("\n")
        ).setPositiveButton("Tamam", null).show()
    }

    private fun saveParkLocation() {
        val loc = lastLoc
        if (loc == null) { Toast.makeText(this, "Önce GPS konumu bekleniyor", Toast.LENGTH_SHORT).show(); return }
        prefs.edit().putString("park", "${loc.latitude},${loc.longitude}").apply()
        Toast.makeText(this, "Park konumu kaydedildi", Toast.LENGTH_SHORT).show()
    }

    private fun openParkLocation() {
        val coords = prefs.getString("park", null)
        if (coords == null) { Toast.makeText(this, "Kayıtlı park konumu yok", Toast.LENGTH_SHORT).show(); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$coords(Park)"))) }
        catch (_: Exception) { Toast.makeText(this, "Harita uygulaması bulunamadı", Toast.LENGTH_SHORT).show() }
    }

    private fun showConnectionStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = if (Build.VERSION.SDK_INT >= 23) {
            val cap = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi bağlı"
                cap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobil ağ bağlı"
                cap?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet bağlı"
                else -> "İnternet bağlantısı yok"
            }
        } else if (cm.activeNetworkInfo?.isConnected == true) "Ağ bağlı" else "Ağ bağlı değil"
        AlertDialog.Builder(this).setTitle("Bağlantı durumu").setMessage(info + "\nCarPlay bağlantısı Zlink tarafından yönetilir; standart Android API bağlantı durumunu doğrulayamaz.").setPositiveButton("Tamam", null).show()
    }

    private fun openCarPlay() {
        val saved = prefs.getString("carplay", null)
        if (saved != null && launchPackage(saved)) return
        showAppDialog("CarPlay uygulamasını seç (Zlink)", queryApps()) { chosen ->
            chosen.pkg?.let { prefs.edit().putString("carplay", it).apply(); launchPackage(it) }
        }
    }

    private fun openNavigation() {
        val saved = prefs.getString("navigation", null)
        if (saved != null && launchPackage(saved)) return
        showAppDialog("Navigasyon uygulamasını seç", queryApps()) { chosen ->
            chosen.pkg?.let { prefs.edit().putString("navigation", it).apply(); launchPackage(it) }
        }
    }

    private fun openRadio() {
        val saved = prefs.getString("radio", null)
        if (saved != null && launchPackage(saved)) return
        showAppDialog("Radyo uygulamasını seç", queryApps()) { chosen ->
            chosen.pkg?.let { prefs.edit().putString("radio", it).apply(); launchPackage(it) }
        }
    }

    private fun showSoundProfiles() {
        val names = arrayOf("Sessiz (%15)", "Normal (%50)", "Yüksek (%80)")
        AlertDialog.Builder(this).setTitle("Ses profili").setItems(names) { _, index ->
            try {
                val am = audio()
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * intArrayOf(15, 50, 80)[index] / 100).coerceAtLeast(1), 0)
            } catch (_: Exception) { Toast.makeText(this, "Ses seviyesi MCU tarafından yönetiliyor olabilir", Toast.LENGTH_SHORT).show() }
        }.show()
    }

    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(intent, REQ_WALLPAPER) }
        catch (_: Exception) { Toast.makeText(this, "Resim seçici açılamadı", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WALLPAPER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.edit().putString("wallpaper", uri.toString()).apply()
            applyTheme()
        }
    }

    private fun showWidgetOptions() {
        val names = arrayOf("Hava durumu", "Medya paneli", "GPS hız paneli")
        val ids = intArrayOf(R.id.weather, R.id.mediaCard, R.id.speedCard)
        val checked = ids.map { findViewById<View>(it).visibility == View.VISIBLE }.toBooleanArray()
        AlertDialog.Builder(this).setTitle("Widget düzenleme").setMultiChoiceItems(names, checked) { _, which, value ->
            prefs.edit().putBoolean("widget$which", value).apply()
            findViewById<View>(ids[which]).visibility = if (value) View.VISIBLE else View.GONE
        }.setPositiveButton("Tamam", null).show()
    }

    private fun showScreenSaver() {
        val view = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "h:mm a"
            textSize = 90f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF05080E.toInt())
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
            .setView(view).setPositiveButton("Kapat", null).show()
    }

    private fun showQuickActions() {
        val actions = arrayOf("CarPlay / Zlink", "Radyo", "Uygulamalar", "Bağlantı durumu", "Park konumunu kaydet", "Park konumunu aç", "Sürüş geçmişi", "Ekran koruyucu", "Ayarlar")
        AlertDialog.Builder(this).setTitle("Hızlı erişim").setItems(actions) { _, index ->
            when (index) {
                0 -> openCarPlay()
                1 -> openRadio()
                2 -> showAllApps()
                3 -> showConnectionStatus()
                4 -> saveParkLocation()
                5 -> openParkLocation()
                6 -> showTripHistory()
                7 -> showScreenSaver()
                8 -> showSettings()
            }
        }.show()
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
            if (location.hasAccuracy() && location.accuracy > 60f) {
                gpsStatus.text = "GPS sinyali zayıf"
                return
            }
            val kmhF = if (location.hasSpeed()) location.speed * 3.6f else 0f
            val kmh = if (kmhF < 2f) 0 else kmhF.roundToInt()
            if (kmh >= 3 && driveStartedAt == 0L) driveStartedAt = SystemClock.elapsedRealtime()
            if (kmh < 3 && driveStartedAt != 0L) {
                drivingSeconds += ((SystemClock.elapsedRealtime() - driveStartedAt) / 1000L).coerceAtLeast(0L)
                driveStartedAt = 0L
            }
            speedText.text = kmh.toString()
            if (kmh > maxKmh) maxKmh = kmh

            val prev = lastLoc
            if (prev != null) {
                val elapsed = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0
                val distance = prev.distanceTo(location)
                val plausible = elapsed > 0.0 && elapsed <= 15.0 && distance >= 3f &&
                    distance / elapsed <= 65.0 &&
                    (!location.hasAccuracy() || distance > location.accuracy * 0.5f)
                if (plausible && kmh >= 3) tripKm += distance / 1000f
            }
            lastLoc = location

            if (kmh >= 5 && location.hasBearing()) {
                headingText.text = DIRECTIONS[(((location.bearing + 22.5f) / 45f).toInt()) % 8]
            }
            gpsStatus.text = if (location.hasAltitude()) "GPS • ${location.altitude.roundToInt()} m" else "GPS"

            val limit = LIMITS[prefs.getInt("limit", 0).coerceIn(0, LIMITS.size - 1)]
            val over = limit > 0 && kmh > limit
            if (over && !overLimit) beep()
            overLimit = over
            speedText.setTextColor(if (over) RED else accentColor())

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
        val seconds = drivingSeconds + if (driveStartedAt != 0L) (SystemClock.elapsedRealtime() - driveStartedAt) / 1000L else 0L
        tripText.text = "Gezi " + String.format(Locale.US, "%.1f", tripKm) + " km · Maks $maxKmh km/h · ${seconds / 60} dk"
    }

    private fun resetTrip() {
        if (tripKm > 0.05f) archiveTrip()
        tripKm = 0f
        drivingSeconds = 0L
        driveStartedAt = 0L
        maxKmh = 0
        lastLoc = null
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
                if (finalResult != null) weatherText.text = finalResult
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
