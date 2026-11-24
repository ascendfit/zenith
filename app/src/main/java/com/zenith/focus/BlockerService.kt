package com.zenith.focus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.app.usage.UsageStatsManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.Calendar

/**
 * Watches which app is on screen and sends the user home when something blocked opens.
 * It only looks at package names, view ids and the browser address bar. It stores nothing.
 */
class BlockerService : AccessibilityService() {
    private var cfg: Cfg = Cfg.EMPTY
    private var cfgAt = 0L
    private var launcherPkg: String? = null
    private var lastAction = 0L
    private var lastHeavy = 0L
    private var lastBack = 0L
    private val usageCache = HashMap<String, Pair<Long, Int>>()

    override fun onServiceConnected() {
        launcherPkg = packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        if (now - cfgAt > 2000L) {
            cfg = Prefs.cfg(this)
            cfgAt = now
        }
        val active = Prefs.isActive(this)
        val strict = Prefs.isStrictActive(this)

        // 1. Uninstall protection: keep Settings and installer screens that mention Zenith closed.
        if (strict && cfg.uninstall && pkg in Targets.SETTINGS_PKGS) {
            if (screenMentionsUs()) {
                goHome(pkg, "Zenith is protected while strict mode is running")
                return
            }
        }

        if (pkg in Targets.NEVER_BLOCK || pkg == launcherPkg) return

        // 2. Daily limits apply all day, focus round or not.
        val limit = cfg.limits[pkg]
        if (limit != null && limit > 0 && usageMinutes(pkg, now) >= limit) {
            goHome(pkg, "You have used your $limit minute limit for today")
            return
        }

        if (!active) return

        // 3. Apps blocked during focus.
        if (pkg in cfg.block) {
            goHome(pkg, "Focus mode is on")
            return
        }

        // 4. Blocked app visible in a split-screen or floating window while another app has focus.
        if (strict && (cfg.split || cfg.floating) && blockedWindowVisible()) {
            goHome(pkg, "Blocked apps cannot be opened in split screen or a floating window")
            return
        }

        // Heavier checks below are throttled.
        if (now - lastHeavy < 250L) return
        lastHeavy = now

        // 5. Shorts and Reels.
        if ((pkg == Targets.YOUTUBE && cfg.yt && anyVisible(Targets.YT_SHORTS_IDS)) ||
            (pkg == Targets.INSTAGRAM && cfg.ig && anyVisible(Targets.IG_REELS_IDS))
        ) {
            leaveFeed(now)
            return
        }

        // 6. Websites.
        val urlId = Targets.BROWSER_URL_IDS[pkg]
        if (urlId != null && (cfg.sites.isNotEmpty() || cfg.adult)) {
            val url = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(urlId)?.firstOrNull()?.text?.toString()
            if (url != null && urlBlocked(url)) leaveFeed(now)
        }
    }

    private fun goHome(pkg: String, why: String) {
        val now = System.currentTimeMillis()
        if (now - lastAction < 1500L) return
        lastAction = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            ""
        }
        try {
            startActivity(
                Intent(this, BlockedActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("why", why)
                    .putExtra("app", label)
            )
        } catch (_: Exception) {
            // Some devices refuse background activity launches. Going home still blocks the app.
        }
    }

    /** First press goes Back out of the feed. If it is still showing a few seconds later, go home. */
    private fun leaveFeed(now: Long) {
        if (now - lastAction < 1200L) return
        lastAction = now
        if (now - lastBack < 4000L) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        lastBack = now
    }

    private fun anyVisible(ids: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    private fun urlBlocked(raw: String): Boolean {
        val u = raw.lowercase()
        for (d in cfg.sites) {
            val site = d.trim().lowercase().removePrefix("www.")
            if (site.isNotEmpty() && u.contains(site)) return true
        }
        if (cfg.adult) for (k in Targets.ADULT_KEYWORDS) if (u.contains(k)) return true
        return false
    }

    private fun screenMentionsUs(): Boolean {
        val label = getString(R.string.app_name).lowercase()
        val root = rootInActiveWindow ?: return false
        return mentions(root, label, 0)
    }

    private fun mentions(node: AccessibilityNodeInfo, needle: String, depth: Int): Boolean {
        if (depth > 12) return false
        val t = node.text?.toString()?.lowercase()
        if (t != null && t.contains(needle)) return true
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (mentions(c, needle, depth + 1)) return true
        }
        return false
    }

    /**
     * Best effort. Compares each app window's size with the screen:
     * both sides smaller means a floating window, one side smaller means split screen.
     */
    private fun blockedWindowVisible(): Boolean {
        val ws = windows ?: return false
        val dm = resources.displayMetrics
        for (w in ws) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val p = w.root?.packageName?.toString() ?: continue
            if (p !in cfg.block) continue
            val r = Rect()
            w.getBoundsInScreen(r)
            val rw = r.width().toFloat() / dm.widthPixels
            val rh = r.height().toFloat() / dm.heightPixels
            val floating = rw <= 0.9f && rh <= 0.9f
            val split = !floating && (rw <= 0.9f || rh <= 0.9f)
            if ((cfg.floating && floating) || (cfg.split && split)) return true
        }
        return false
    }

    private fun usageMinutes(pkg: String, now: Long): Int {
        val cached = usageCache[pkg]
        if (cached != null && now - cached.first < 15000L) return cached.second
        var minutes = 0
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val ms = usm.queryAndAggregateUsageStats(cal.timeInMillis, now)[pkg]?.totalTimeInForeground ?: 0L
            minutes = (ms / 60000L).toInt()
        } catch (_: Exception) {
        }
        usageCache[pkg] = Pair(now, minutes)
        return minutes
    }
}
