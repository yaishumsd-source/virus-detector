package app.sentinel.mobile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class ScanReport(
    val visibleAppCount: Int,
    val reviewCandidates: List<String>,
    val androidVersion: String,
    val securityPatch: String,
    val unknownSourcesEnabled: Boolean
) {
    val hasReviewCandidates: Boolean get() = reviewCandidates.isNotEmpty()

    fun notificationText(): String = if (hasReviewCandidates) {
        "Review app names: ${reviewCandidates.take(3).joinToString()}. A name match is not proof of malware."
    } else {
        "A background check completed. No basic app-name indicators matched; this does not prove the phone is malware-free."
    }

    fun findings(): List<Pair<String, String>> {
        val appSummary = if (reviewCandidates.isEmpty()) {
            "Reviewed $visibleAppCount visible non-system launcher apps. No basic name/package heuristic matched; this is not proof the apps are safe."
        } else {
            "${reviewCandidates.size} app(s) matched a weak name/package heuristic: ${reviewCandidates.take(5).joinToString()}. Verify each app manually; names alone do not establish malware."
        }
        return listOf(
            "Visible app inventory" to appSummary,
            "Android security patch" to "Android $androidVersion · security patch $securityPatch. Verify Settings → Security & privacy for the latest update available to your device.",
            "Unknown-source install indicator" to if (unknownSourcesEnabled) "This device reports a legacy global unknown-source install flag. Review per-app Install unknown apps access in Settings." else "No legacy global unknown-source install flag was detected. Android may manage this per app; review Special app access in Settings."
        )
    }
}

object DeviceScanner {
    @Suppress("DEPRECATION")
    fun scan(context: Context): ScanReport {
        val pm = context.packageManager
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        val thirdParty = apps.filter { result ->
            result.activityInfo?.applicationInfo?.let { info -> info.flags and ApplicationInfo.FLAG_SYSTEM == 0 } == true
        }.distinctBy { it.activityInfo?.packageName }
        val reviewCandidates = thirdParty.mapNotNull { result ->
            val label = result.loadLabel(pm).toString()
            val packageName = result.activityInfo?.packageName.orEmpty().lowercase()
            val lowerLabel = label.lowercase()
            if (lowerLabel.contains("cleaner") || lowerLabel.contains("booster") ||
                lowerLabel.contains("update service") || packageName.contains("spy") ||
                packageName.contains("stalker")) label else null
        }.distinct()
        val canInstall = runCatching {
            Settings.Secure.getInt(context.contentResolver, "install_non_market_apps", 0) == 1
        }.getOrDefault(false)
        return ScanReport(
            visibleAppCount = thirdParty.size,
            reviewCandidates = reviewCandidates,
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "not reported",
            unknownSourcesEnabled = canInstall
        )
    }
}
