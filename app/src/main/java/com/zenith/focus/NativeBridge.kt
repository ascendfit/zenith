package com.zenith.focus

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Calendar

/** Everything the web UI can ask the phone to do. Exposed to JavaScript as window.ZenithNative. */
class NativeBridge(private val act: Activity) {
    private val ctx: Context = act.applicationContext

    @JavascriptInterface
    fun getState(): String {
        val perms = JSONObject()
            .put("accessibility", accessibilityOn())
            .put("notif", notifListenerOn())
            .put("usage", usageGranted())
            .put("admin", dpm().isAdminActive(adminComponent()))
        return JSONObject()
            .put("perms", perms)
            .put("cfg", JSONObject(Prefs.configJson(ctx)))
            .put("active", Prefs.isActive(ctx))
            .put("strict", Prefs.isStrictActive(ctx))
            .toString()
    }

    @JavascriptInterface
    fun getApps(): String {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val list = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName && seen.add(it.activityInfo.packageName) }
            .map { Pair(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.second.lowercase() }
        val arr = JSONArray()
        for ((pkg, name) in list) arr.put(JSONObject().put("pkg", pkg).put("name", name))
        return arr.toString()
    }

    @JavascriptInterface
    fun getIcon(pkg: String): String {
        return try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 64, 64)
            d.draw(Canvas(bmp))
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun setConfig(json: String) {
        Prefs.setConfig(ctx, json)
    }

    @JavascriptInterface
    fun startBlocking(untilMs: Long, strict: Boolean) {
        Prefs.startBlocking(ctx, untilMs, strict)
    }

    @JavascriptInterface
    fun stopBlocking(force: Boolean): Boolean = Prefs.stopBlocking(ctx, force)

    /** Minutes in the foreground today for every app that has a limit. */
    @JavascriptInterface
    fun getUsage(): String {
        val out = JSONObject()
        if (!usageGranted()) return out.toString()
        try {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
            for ((pkg, st) in stats) {
                val min = (st.totalTimeInForeground / 60000L).toInt()
                if (min > 0) out.put(pkg, min)
            }
        } catch (_: Exception) {
        }
        return out.toString()
    }

    @JavascriptInterface
    fun openSettings(which: String) {
        val intent = when (which) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notif" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "admin" -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Keeps Zenith from being uninstalled while strict mode is running.")
            else -> return
        }
        act.runOnUiThread {
            try {
                act.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun notifListenerOn(): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }

    private fun dpm() = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun adminComponent() = ComponentName(ctx, AdminReceiver::class.java)

    private fun accessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    @Suppress("DEPRECATION")
    private fun usageGranted(): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
