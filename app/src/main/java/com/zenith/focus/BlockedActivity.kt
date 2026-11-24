package com.zenith.focus

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shown after a blocked app is closed. Built in code so it needs no layout file. */
class BlockedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val why = intent.getStringExtra("why") ?: "Focus mode is on"
        val app = intent.getStringExtra("app") ?: ""
        val until = Prefs.untilMs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(64, 64, 64, 64)
        }
        fun tv(text: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor(color))
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
        root.addView(tv("Stay with it", 30f, "#82D65C", true))
        root.addView(tv(if (app.isNotEmpty()) "$app is blocked" else "That app is blocked", 18f, "#F4F6F1"))
        root.addView(tv(why, 15f, "#8E938A"))
        if (until > System.currentTimeMillis()) {
            val mins = ((until - System.currentTimeMillis()) / 60000L).toInt() + 1
            root.addView(tv("$mins min left in this round", 15f, "#FFD640"))
        }
        val btn = Button(this).apply {
            text = "Back to Zenith"
            setOnClickListener {
                startActivity(Intent(this@BlockedActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        }
        root.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
