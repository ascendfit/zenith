package com.zenith.focus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Active device admin apps cannot be uninstalled until admin is switched off first. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Turning this off removes uninstall protection for Zenith."
    }
}
