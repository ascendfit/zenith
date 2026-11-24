package com.zenith.focus

import android.content.Context
import org.json.JSONObject

/** Parsed copy of the settings the web UI saves from the Blocks tab. */
data class Cfg(
    val block: Set<String>,
    val limits: Map<String, Int>,
    val yt: Boolean,
    val ig: Boolean,
    val notif: Boolean,
    val sites: List<String>,
    val adult: Boolean,
    val uninstall: Boolean,
    val split: Boolean,
    val floating: Boolean,
) {
    companion object {
        val EMPTY = Cfg(emptySet(), emptyMap(), false, false, false, emptyList(), false, false, false, false)

        fun parse(json: String?): Cfg {
            return try {
                val o = JSONObject(json ?: "{}")
                val block = mutableSetOf<String>()
                val limits = mutableMapOf<String, Int>()
                val apps = o.optJSONObject("apps")
                if (apps != null) {
                    for (pkg in apps.keys()) {
                        val a = apps.optJSONObject(pkg) ?: continue
                        if (a.optBoolean("block", false)) block.add(pkg)
                        val lim = a.optInt("limit", 0)
                        if (lim > 0) limits[pkg] = lim
                    }
                }
                val sites = mutableListOf<String>()
                val arr = o.optJSONArray("sites")
                if (arr != null) for (i in 0 until arr.length()) sites.add(arr.optString(i))
                Cfg(
                    block, limits,
                    o.optBoolean("yt", false), o.optBoolean("ig", false), o.optBoolean("notif", false),
                    sites, o.optBoolean("adult", false), o.optBoolean("uninstall", false),
                    o.optBoolean("split", false), o.optBoolean("floating", false),
                )
            } catch (e: Exception) {
                EMPTY
            }
        }
    }
}

/** Focus-session state and saved config, shared by the activity and the services. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("zenith", Context.MODE_PRIVATE)

    fun startBlocking(c: Context, untilMs: Long, strict: Boolean) {
        sp(c).edit().putBoolean("blocking", true).putLong("until", untilMs).putBoolean("strict", strict).apply()
    }

    /** Returns false when strict mode refuses to stop early. */
    fun stopBlocking(c: Context, force: Boolean): Boolean {
        if (!force && isStrictActive(c)) return false
        sp(c).edit().putBoolean("blocking", false).putLong("until", 0L).putBoolean("strict", false).apply()
        return true
    }

    /** until == 0 means open-ended (stopwatch). */
    fun isActive(c: Context): Boolean {
        val s = sp(c)
        if (!s.getBoolean("blocking", false)) return false
        val until = s.getLong("until", 0L)
        return until == 0L || System.currentTimeMillis() < until
    }

    fun isStrictActive(c: Context): Boolean {
        if (!isActive(c)) return false
        val s = sp(c)
        val until = s.getLong("until", 0L)
        if (until != 0L && until - System.currentTimeMillis() < 2000L) return false
        return s.getBoolean("strict", false)
    }

    fun untilMs(c: Context): Long = sp(c).getLong("until", 0L)

    fun configJson(c: Context): String = sp(c).getString("config", "{}") ?: "{}"
    fun setConfig(c: Context, json: String) { sp(c).edit().putString("config", json).apply() }
    fun cfg(c: Context): Cfg = Cfg.parse(configJson(c))
}
