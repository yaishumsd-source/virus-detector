package app.sentinel.mobile

import android.app.Activity
import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var backgroundStatus: TextView
    private lateinit var backgroundButton: Button
    private val green = Color.rgb(8, 118, 83)
    private val ink = Color.rgb(23, 36, 31)
    private val muted = Color.rgb(104, 119, 110)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 248, 246)
        window.navigationBarColor = Color.rgb(245, 248, 246)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildScreen()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(25), dp(22), dp(28))
            setBackgroundColor(Color.rgb(245, 248, 246))
        }
        scroll.addView(root)
        setContentView(scroll)

        label("✳  YXSHU SECURITY", 14, green, true)
        label("Mobile threat check", 29, ink, true, 18)
        label("A transparent, on-device review of supported Android security signals.", 15, muted, false, 5)

        val panel = card()
        root.addView(panel, params(top = 22))
        panel.addView(text("Your privacy comes first", 19, ink, true))
        panel.addView(text("No account, network access, or file upload is used. The scan only checks launcher apps visible to this app and a few public device settings. Android protects other apps’ private data, so this cannot find every virus or spyware implant.", 14, muted, false, 9))

        status = text("Ready · No scan has run", 14, green, true, 5)
        panel.addView(status)
        scanButton = Button(this).apply {
            text = "Review scan scope & continue"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(green))
            setOnClickListener { requestConsent() }
        }
        panel.addView(scanButton, params(top = 12))

        val backgroundCard = card()
        root.addView(backgroundCard, params(top = 16))
        backgroundCard.addView(text("Background checks", 19, ink, true))
        backgroundCard.addView(text("When enabled, Android schedules a quick on-device check about every 12 hours. Battery saving may delay it. This is not continuous protection and does not block or remove malware.", 14, muted, false, 7))
        backgroundStatus = text("", 13, green, true, 8)
        backgroundCard.addView(backgroundStatus)
        backgroundButton = Button(this).apply {
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(green))
            setOnClickListener { requestBackgroundConsent() }
        }
        backgroundCard.addView(backgroundButton, params(top = 8))
        updateBackgroundUi()

        val findingsCard = card()
        root.addView(findingsCard, params(top = 16))
        findingsCard.addView(text("Scan results", 19, ink, true))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        findingsCard.addView(results, params(top = 8))
        addResult("App inventory", "Not checked", "Lists apps that expose a launcher activity; not all installed packages.")
        addResult("Risky app indicators", "Not checked", "Uses basic heuristics only. No antivirus reputation database is included.")
        addResult("System update status", "Not checked", "Android version and security patch date, when provided by the device.")
        addResult("Unknown-source install setting", "Not checked", "A setting indicator; it does not establish that an app is malicious.")

        val tips = card()
        root.addView(tips, params(top = 16))
        tips.addView(text("For stronger protection", 18, ink, true))
        tips.addView(text("• Run Google Play Protect from the Play Store.\n• Install Android and app updates.\n• Review Accessibility, Device admin, VPN, and app permissions in Settings.\n• Uninstall apps you don’t recognize and avoid sideloaded apps.\n• If you suspect targeted spyware, use a trusted specialist; a normal app cannot verify a clean device.", 14, muted, false, 8))
        root.addView(text("YXSHU runs locally. Findings are indicators, not a malware verdict.", 12, muted, false, 18).apply { gravity = Gravity.CENTER })
    }

    private fun requestConsent() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Allow a supported device check?")
            .setMessage("With your consent, YXSHU will read launcher app names and package IDs visible to it, plus Android version and the security patch date. It will not read app data, photos, messages, or documents; change settings; remove apps; or send scan data anywhere. Results are heuristic and cannot guarantee spyware detection. Continue?")
            .setNegativeButton("Not now", null)
            .setPositiveButton("I consent · Start scan") { _, _ -> startScan() }
            .show()
    }

    private fun startScan() {
        scanButton.isEnabled = false
        status.text = "Scanning visible app inventory on this device…"
        results.removeAllViews()
        results.addView(text("Checking on device…", 14, muted, false))
        executor.execute {
            val scan = runCatching { DeviceScanner.scan(this) }
            runOnUiThread {
                results.removeAllViews()
                val report = scan.getOrNull()
                if (report == null) {
                    addResult("Scan error", "Could not complete", scan.exceptionOrNull()?.message ?: "Android denied a requested check.")
                } else {
                    report.findings().forEach { (title, detail) -> addResult(title, "Reviewed", detail) }
                }
                status.text = "Completed · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())} · On device"
                scanButton.isEnabled = true
            }
        }
    }

    private fun requestBackgroundConsent() {
        val enabled = getSharedPreferences(BackgroundChecks.PREFS, MODE_PRIVATE)
            .getBoolean(BackgroundChecks.ENABLED, false)
        if (enabled) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Turn off background checks?")
                .setMessage("YXSHU will stop scheduling periodic app-name and security-setting checks. Android's built-in protections remain available.")
                .setNegativeButton("Keep enabled", null)
                .setPositiveButton("Turn off") { _, _ ->
                    BackgroundChecks.disable(this)
                    updateBackgroundUi()
                }
                .show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Enable scheduled background checks?")
            .setMessage("With your consent, YXSHU will schedule a short on-device check about every 12 hours. Android may delay checks to save battery. It checks only launcher apps visible to YXSHU and basic device indicators. It cannot inspect other apps' private data, continuously block threats, or remove malware. Results stay on this phone. Allow checks?")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Allow background checks") { _, _ ->
                BackgroundChecks.enable(this)
                BackgroundChecks.createNotificationChannel(this)
                updateBackgroundUi()
                if (Build.VERSION.SDK_INT >= 33 && !BackgroundChecks.notificationsAllowed(this)) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
                }
            }
            .show()
    }

    private fun updateBackgroundUi() {
        if (!::backgroundStatus.isInitialized) return
        val prefs = getSharedPreferences(BackgroundChecks.PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean(BackgroundChecks.ENABLED, false)
        backgroundButton.text = if (enabled) "Turn off background checks" else "Enable background checks"
        val last = prefs.getLong(BackgroundChecks.LAST_CHECK, 0L)
        val notificationText = if (enabled && !BackgroundChecks.notificationsAllowed(this)) " · notifications are off" else ""
        backgroundStatus.text = if (!enabled) {
            "Off · No background scan is scheduled"
        } else if (last == 0L) {
            "On · First check is scheduled · Android may defer it$notificationText"
        } else {
            "On · Last check ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))}$notificationText"
        }
    }

    override fun onResume() {
        super.onResume()
        updateBackgroundUi()
    }

    @Deprecated("Deprecated by Android; kept for compatibility with the minimum SDK")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST) updateBackgroundUi()
    }

    private fun addResult(title: String, state: String, detail: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        row.addView(text("$title · $state", 14, green, true))
        row.addView(text(detail, 13, muted, false, 3))
        results.addView(row)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        setBackgroundColor(Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(229, 236, 231))
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean, top: Int = 0) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (top > 0) setPadding(0, dp(top), 0, 0)
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean, top: Int = 0) { root.addView(text(value, size, color, bold, top)) }
    private fun params(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (top > 0) topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_REQUEST = 1002
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
