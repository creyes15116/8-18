package com.xiaomi.miwear.mishow
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Emparejar sin teclear en el reloj (con el diálogo "Vincular con código" abierto):
 * adb shell am broadcast -n com.xiaomi.miwear.mishow/.PairReceiver --es code 123456   (el puerto es opcional: --es port 41234)
 */
class PairReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val port = i.getStringExtra("port")?.toIntOrNull()
        val code = i.getStringExtra("code") ?: ""
        val pr = goAsync()
        thread {
            try { Store.log(c, "PAIR: " + Shell.pair(c, port, code)) }
            catch (e: Throwable) { Store.log(c, "PAIR error: ${e.javaClass.simpleName} ${e.message}") }
            finally { pr.finish() }
        }
    }
}
