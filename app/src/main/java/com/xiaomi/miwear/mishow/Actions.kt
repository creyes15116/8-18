package com.xiaomi.miwear.mishow
import android.content.Context
import java.util.Calendar

/** Salta la HORA (no la zona) vía shell local (cmd alarm set-time), igual que la prueba manual con Bugjaeger. */
object Actions {
    fun jump(c: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now !in Scheduler.at(7, 50, 0)..Scheduler.at(7, 59, 40)) {
            Store.log(c, "Salto omitido: fuera de 07:50-07:59:40"); Scheduler.scheduleNextJump(c); return
        }
        val fakeTarget = if (force) now + 10 * 60_000L else Scheduler.at(8, 5, 0)
        try {
            Shell.ensureAdbOn(c)
            val before = System.currentTimeMillis()
            val out = Shell.run(c,
                "settings put global auto_time 0",
                "settings put global clockwork_auto_time 0",
                "cmd alarm set-time $fakeTarget")
            Store.setOffset(c, fakeTarget - before)
            Store.setPending(c, true)
            Store.log(c, "JUMP ok: $out")
            Scheduler.scheduleRestore(c, if (force) 10_000L else Scheduler.at(8, 10, 0) - now)
        } catch (e: Exception) {
            Store.log(c, "JUMP falló: ${e.message}")
            Scheduler.scheduleNextJump(c) // reintenta mañana
        }
    }

    fun restore(c: Context) {
        if (!Store.pending(c)) { Scheduler.scheduleNextJump(c); return }
        try {
            val real = System.currentTimeMillis() - Store.offset(c)
            val out = Shell.run(c,
                "cmd alarm set-time $real",
                "settings put global auto_time 1",
                "settings put global clockwork_auto_time 1")
            Store.setPending(c, false)
            Store.log(c, "RESTORE ok: $out")
            Scheduler.scheduleNextJump(c)
        } catch (e: Exception) {
            Store.log(c, "RESTORE falló (reintento 45s): ${e.message}")
            Scheduler.scheduleRestore(c, 45_000)
        }
    }

    fun setup(c: Context): String {
        Shell.ensureAdbOn(c)
        return Shell.run(c,
            "id",
            "settings get global auto_time",
            "cmd deviceidle whitelist +com.xiaomi.miwear.mishow")
    }
}
