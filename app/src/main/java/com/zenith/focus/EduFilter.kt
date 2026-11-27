package com.zenith.focus

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * "Educational videos only" mode for YouTube.
 * Android cannot understand a video, so this reads the title and text shown in the top part of the
 * watch page and checks it against word lists. Bad words block, then education words allow,
 * anything else is blocked. Users can add their own words in the Blocks tab.
 */
object EduFilter {
    private val DEFAULT_EDU = listOf(
        "lecture", "tutorial", "course", "lesson", "chapter", "explained", "explanation", "how to", "learn",
        "study", "exam", "revision", "physics", "chemistry", "maths", "mathematics", "biology", "science",
        "history", "geography", "economics", "coding", "programming", "python", "java", "javascript", "sql",
        "algorithm", "data structure", "engineering", "neet", "jee", "upsc", "gate", "cbse", "ncert",
        "syllabus", "documentary", "khan academy", "university", "grammar", "english", "finance",
        "accounting", "medicine", "anatomy", "mit opencourse", "crash course", "full course", "notes",
    )
    private val DEFAULT_BAD = listOf(
        "prank", "funny", "comedy", "meme", "roast", "reaction", "vlog", "gameplay", "gaming", "highlights",
        "trailer", "music video", "remix", "challenge", "bigg boss", "web series", "full movie", "celebrity",
    )

    class Rules(val enabled: Boolean, val edu: List<String>, val bad: List<String>)

    fun rules(c: Context): Rules {
        return try {
            val o = JSONObject(Prefs.configJson(c))
            val enabled = o.optBoolean("ytEdu", false)
            fun words(key: String): List<String> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val w = arr.optString(i).trim().lowercase()
                    if (w.isNotEmpty()) out.add(w)
                }
                return out
            }
            Rules(enabled, DEFAULT_EDU + words("eduWords"), DEFAULT_BAD + words("badWords"))
        } catch (e: Exception) {
            Rules(false, DEFAULT_EDU, DEFAULT_BAD)
        }
    }

    private class Scan {
        var watch = false
        var nodes = 0
        val text = StringBuilder()
    }

    /** True when a YouTube video page is open and its title does not look educational. */
    fun blocked(root: AccessibilityNodeInfo, screenH: Int, r: Rules): Boolean {
        val st = Scan()
        scan(root, screenH, st, 0)
        if (!st.watch) return false
        val text = st.text.toString().lowercase()
        if (text.isBlank()) return false
        if (r.bad.any { text.contains(it) }) return true
        return !r.edu.any { text.contains(it) }
    }

    private fun scan(n: AccessibilityNodeInfo, h: Int, s: Scan, depth: Int) {
        if (depth > 25 || s.nodes > 600) return
        s.nodes++
        val id = n.viewIdResourceName
        if (id != null && id.startsWith("com.google.android.youtube:id/") && id.contains("player") &&
            !id.contains("inline") && !id.contains("reel") && !id.contains("short")
        ) s.watch = true
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (t != null && t.length > 3) {
            val rect = Rect()
            n.getBoundsInScreen(rect)
            if (rect.top >= 0 && rect.top < (h * 0.6).toInt()) s.text.append(t).append(' ')
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            scan(c, h, s, depth + 1)
        }
    }
}
