package com.example.carlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases -> DownloadManager -> Android package installer.
 * Installation is always confirmed by the user; never a silent install.
 */
class LauncherUpdater(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("launcher_updater", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val downloads = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var checking = false
    private var polling = false

    fun onResume() {
        val id = prefs.getLong("download_id", -1L)
        if (id > 0) poll(id)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0L) > 6 * 60 * 60 * 1000L) {
            prefs.edit().putLong("last_check", now).apply()
            check(false)
        }
    }

    fun onPause() {
        polling = false
        handler.removeCallbacksAndMessages(null)
    }

    fun check(manual: Boolean) {
        if (checking) return
        checking = true
        Thread {
            try {
                val conn = URL("https://api.github.com/repos/nejyoner-glitch/car-launcher/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "CarLauncher-Updater")
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val release = JSONObject(response)
                val tag = release.optString("tag_name")
                val remoteBuild = Regex("^build-(\\d+)$").find(tag)?.groupValues?.get(1)?.toLongOrNull()
                val localBuild = if (Build.VERSION.SDK_INT >= 28) {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
                }
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) for (i in 0 until assets.length()) {
                    val item = assets.getJSONObject(i)
                    if (item.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = item.optString("browser_download_url")
                        break
                    }
                }
                val url = apkUrl
                activity.runOnUiThread {
                    checking = false
                    if (remoteBuild != null && remoteBuild > localBuild && !url.isNullOrBlank()) {
                        if (!manual && prefs.getString("dismissed_tag", "") == tag) return@runOnUiThread
                        AlertDialog.Builder(activity)
                            .setTitle("Yeni launcher güncellemesi: $tag")
                            .setMessage("Yeni APK indirilecek. İndirme bitince Android'in kurulum ekranı açılacak. Kurulumu sen onaylayacaksın.")
                            .setPositiveButton("İndir ve yükle") { _, _ -> download(url, tag) }
                            .setNegativeButton("Daha sonra") { _, _ ->
                                prefs.edit().putString("dismissed_tag", tag).apply()
                            }.show()
                    } else if (manual) {
                        Toast.makeText(activity, "Yeni bir güncelleme bulunamadı", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    checking = false
                    if (manual) AlertDialog.Builder(activity).setTitle("Güncelleme kontrol edilemedi")
                        .setMessage("İnternet bağlantısını kontrol et ve tekrar dene.\\n${e.localizedMessage ?: ""}")
                        .setPositiveButton("Tamam", null).show()
                }
            }
        }.start()
    }

    private fun download(url: String, tag: String) {
        try {
            val previous = prefs.getLong("download_id", -1L)
            if (previous > 0) downloads.remove(previous)
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Car Launcher $tag")
                .setDescription("Güncelleme indiriliyor; bitince kurulum açılacak")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "car-launcher-$tag.apk")
            val id = downloads.enqueue(request)
            prefs.edit().putLong("download_id", id).putBoolean("install_pending", false).apply()
            Toast.makeText(activity, "APK indiriliyor…", Toast.LENGTH_LONG).show()
            poll(id)
        } catch (e: Exception) {
            AlertDialog.Builder(activity).setTitle("İndirme başlatılamadı")
                .setMessage(e.localizedMessage ?: "İndirme yöneticisi kullanılamıyor.")
                .setPositiveButton("Tamam", null).show()
        }
    }

    private fun poll(id: Long) {
        if (polling) return
        polling = true
        fun query(): Pair<Int, Int>? {
            val cursor = downloads.query(DownloadManager.Query().setFilterById(id)) ?: return null
            cursor.use {
                if (!it.moveToFirst()) return null
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                return status to reason
            }
        }
        val task = object : Runnable {
            override fun run() {
                if (!polling) return
                val state = try { query() } catch (_: Exception) { null }
                when (state?.first) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        polling = false
                        if (!prefs.getBoolean("install_pending", false)) {
                            prefs.edit().putBoolean("install_pending", true).apply()
                            install(id)
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        polling = false
                        prefs.edit().remove("download_id").remove("install_pending").apply()
                        AlertDialog.Builder(activity).setTitle("APK indirilemedi")
                            .setMessage("İndirme yöneticisi hata kodu: ${state.second}. Tekrar deneyebilirsin.")
                            .setPositiveButton("Tamam", null).show()
                    }
                    null -> polling = false
                    else -> handler.postDelayed(this, 1500)
                }
            }
        }
        handler.post(task)
    }

    fun retryInstall() {
        val id = prefs.getLong("download_id", -1L)
        if (id > 0) install(id)
    }

    private fun install(id: Long) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(activity).setTitle("Kurulum izni gerekli")
                    .setMessage("Bu uygulamadan APK yüklemeye izin ver. Ardından launcher'a dönüp Ayarlar > İndirilen güncellemeyi yükle seçeneğini kullan.")
                    .setPositiveButton("İzin ver") { _, _ ->
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")))
                    }.setNegativeButton("Sonra", null).show()
                return
            }
            val uri = downloads.getUriForDownloadedFile(id)
            if (uri == null) {
                Toast.makeText(activity, "APK bulunamadı. Yeniden indir.", Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            AlertDialog.Builder(activity).setTitle("Kurulum açılamadı")
                .setMessage("İndirilen APK için kurulum ekranı başlatılamadı: ${e.localizedMessage}")
                .setPositiveButton("Tamam", null).show()
        }
    }
}
