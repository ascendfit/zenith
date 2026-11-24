package com.zenith.focus

/**
 * View ids and package names used for detection.
 * Apps rename these between releases. If a block stops working after an update,
 * inspect the app with Android Studio's Layout Inspector or the uiautomatorviewer
 * and update the lists below.
 */
object Targets {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    val YT_SHORTS_IDS = listOf(
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/reel_recycler",
    )
    val IG_REELS_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_viewer_video_layout",
    )

    /** Browser package -> id of its address bar. */
    val BROWSER_URL_IDS = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    )

    val ADULT_KEYWORDS = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "onlyfans", "porn", "hentai",
    )

    /** Settings and installer screens that could be used to remove or switch off Zenith. */
    val SETTINGS_PKGS = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter",
    )

    /** Never blocked, whatever the user selects. */
    val NEVER_BLOCK = setOf(
        "com.android.systemui",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
    )

    val NOTIF_ALLOW = NEVER_BLOCK + setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.android.server.telecom",
    )
}
