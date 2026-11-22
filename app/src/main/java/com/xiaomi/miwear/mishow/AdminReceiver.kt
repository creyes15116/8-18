package com.xiaomi.miwear.mishow
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(c: Context, i: Intent) { Store.log(c, "Device Owner habilitado") }
    override fun onDisabled(c: Context, i: Intent) { Store.log(c, "Device Owner deshabilitado") }
}
