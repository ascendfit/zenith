package com.zenith.focus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Hosts the Zenith web UI (bundled in assets) and exposes the native blocker to it. */
class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {
                }
                return true
            }
        }
        web.addJavascriptInterface(NativeBridge(this), "ZenithNative")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onResume() {
        super.onResume()
        // Lets the page refresh permission status after the user returns from Settings.
        web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'))", null)
        checkUpdate()
    }

    /**
     * Asks GitHub for the latest published build. If it was published after this copy was installed,
     * offers to download it. Checks at most once an hour.
     */
    private fun checkUpdate() {
        val sp = getSharedPreferences("zenith_upd", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last", 0L) < 3600_000L) return
        sp.edit().putLong("last", now).apply()
        Thread {
            try {
                val c = URL("https://api.github.com/repos/ascendfit/zenith/releases/tags/latest")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val o = JSONObject(body)
                val published = java.time.Instant.parse(o.getString("published_at")).toEpochMilli()
                val installed = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                if (published > installed + 120_000L) {
                    var url = "https://github.com/ascendfit/zenith/releases/tag/latest"
                    val assets = o.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        url = assets.getJSONObject(0).optString("browser_download_url", url)
                    }
                    runOnUiThread { if (!isFinishing) showUpdate(url) }
                }
            } catch (_: Exception) {
                // No internet or GitHub unreachable: try again next time.
            }
        }.start()
    }

    private fun showUpdate(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Zenith update available")
            .setMessage("A newer version is ready. Tap Download, then install it over this one. Your data stays.")
            .setPositiveButton("Download") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
