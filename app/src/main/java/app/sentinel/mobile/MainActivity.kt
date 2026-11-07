package app.sentinel.mobile

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
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

        label("✳  SENTINEL SECURITY", 14, green, true)
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
        root.addView(text("Sentinel runs locally. Findings are indicators, not a malware verdict.", 12, muted, false, 18).apply { gravity = Gravity.CENTER })
    }

    private fun requestConsent() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Allow a supported device check?")
            .setMessage("With your consent, Sentinel will read launcher app names and package IDs visible to it, plus Android version and the security patch date. It will not read app data, photos, messages, or documents; change settings; remove apps; or send scan data anywhere. Results are heuristic and cannot guarantee spyware detection. Continue?")
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
            val report = runCatching { performScan() }.getOrElse { e ->
                listOf("Scan error" to (e.message ?: "Android denied a requested check."))
            }
            runOnUiThread {
                results.removeAllViews()
                report.forEach { (title, detail) -> addResult(title, "Reviewed", detail) }
                status.text = "Completed · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())} · On device"
                scanButton.isEnabled = true
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun performScan(): List<Pair<String, String>> {
        val pm = packageManager
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        val thirdParty = apps.filter { it.activityInfo?.applicationInfo?.let { info -> info.flags and ApplicationInfo.FLAG_SYSTEM == 0 } == true }
        val unknownApps = thirdParty.filter { info ->
            val name = info.loadLabel(pm).toString().lowercase()
            val pkg = info.activityInfo.packageName.lowercase()
            name.contains("cleaner") || name.contains("booster") || name.contains("update service") || pkg.contains("spy") || pkg.contains("stalker")
        }
        val version = android.os.Build.VERSION.RELEASE ?: "unknown"
        val patch = android.os.Build.VERSION.SECURITY_PATCH ?: "not reported"
        val packageInstallers = thirdParty.mapNotNull { it.activityInfo?.packageName }.distinct().size
        val canInstall = runCatching { Settings.Secure.getInt(contentResolver, "install_non_market_apps", 0) == 1 }.getOrDefault(false)
        val appSummary = if (unknownApps.isEmpty()) {
            "Reviewed $packageInstallers visible non-system launcher apps. No basic name/package heuristic matched; this is not proof the apps are safe."
        } else {
            "${unknownApps.size} app(s) matched a weak name/package heuristic: ${unknownApps.take(5).joinToString { it.loadLabel(pm).toString() }}. Verify each app manually; names alone do not establish malware."
        }
        return listOf(
            "Visible app inventory" to appSummary,
            "Android security patch" to "Android $version · security patch $patch. Verify Settings → Security & privacy for the latest update available to your device.",
            "Unknown-source install indicator" to if (canInstall) "This device reports a legacy global unknown-source install flag. Review per-app Install unknown apps access in Settings." else "No legacy global unknown-source install flag was detected. Android may manage this per app; review Special app access in Settings."
        )
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

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
