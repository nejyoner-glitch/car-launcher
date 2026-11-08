package com.example.carlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

/** Separate app drawer. The original home-screen layout remains untouched. */
class AllAppsActivity : Activity() {
    private data class App(val name: String, val pkg: String, val icon: android.graphics.drawable.Drawable)
    private lateinit var grid: GridLayout
    private lateinit var count: TextView
    private var apps: List<App> = emptyList()
    private val white = Color.WHITE
    private val muted = Color.rgb(171, 195, 227)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun panel(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff1452a4.toInt(), 0xff083274.toInt())
    ).apply { cornerRadius = dp(15).toFloat(); setStroke(dp(1), 0xff438de0.toInt()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(16), dp(26), dp(12))
            setBackgroundColor(0xff062c67.toInt())
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply {
            text = "‹  Geri"; textSize = 19f; setTextColor(white)
            setPadding(dp(12), dp(8), dp(20), dp(8))
            setOnClickListener { finish() }
        }
        top.addView(back)
        val title = TextView(this).apply { text = "Tüm Uygulamalar"; textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(white) }
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        count = TextView(this).apply { textSize = 14f; setTextColor(muted) }
        top.addView(count)
        root.addView(top)
        val search = EditText(this).apply {
            hint = "Uygulama ara…"
            setSingleLine(true)
            textSize = 18f
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(17), dp(8), dp(17), dp(8))
            background = panel()
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        grid = GridLayout(this).apply { columnCount = 6; alignmentMode = GridLayout.ALIGN_BOUNDS }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val pm = packageManager
        apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }.sortedBy { it.name.lowercase(Locale.ROOT) }
        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, c: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun render(filter: String) {
        grid.removeAllViews()
        val matches = apps.filter { it.name.contains(filter.trim(), ignoreCase = true) }
        count.text = "${matches.size} uygulama"
        val tileWidth = (resources.displayMetrics.widthPixels - dp(52 + 20)) / 6
        for (app in matches) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = panel()
                setPadding(dp(6), dp(9), dp(6), dp(7))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val intent = packageManager.getLaunchIntentForPackage(app.pkg)
                    if (intent != null) startActivity(intent) else Toast.makeText(this@AllAppsActivity, "Uygulama açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
            val icon = ImageView(this).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER }
            tile.addView(icon, LinearLayout.LayoutParams(dp(49), dp(49)))
            tile.addView(TextView(this).apply {
                text = app.name; textSize = 13f; setTextColor(white); gravity = Gravity.CENTER
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, dp(25)))
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = tileWidth; height = dp(105)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        if (matches.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = "Uygulama bulunamadı"; textSize = 19f; setTextColor(muted); setPadding(dp(18), dp(30), 0, 0)
            })
        }
    }
}
