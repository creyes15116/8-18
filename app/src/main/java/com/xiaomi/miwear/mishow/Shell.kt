package com.xiaomi.miwear.mishow
import android.content.Context
import android.provider.Settings
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Habla con el adbd del propio reloj (127.0.0.1) con identidad "shell".
 * Orden de conexión: puerto clásico 5555 -> puertos leídos del sistema -> descubrimiento mDNS (depuración inalámbrica).
 */
object Shell {
    private fun mgr(c: Context): AbsAdbConnectionManager = AdbConnectionManager.getInstance(c.applicationContext)

    fun ensureAdbOn(c: Context) {
        try {
            Settings.Global.putInt(c.contentResolver, "adb_enabled", 1)
            Settings.Global.putInt(c.contentResolver, "adb_wifi_enabled", 1)
        } catch (e: Exception) { Store.log(c, "ensureAdbOn: ${e.javaClass.simpleName}") }
    }

    fun port(c: Context) = Store.port(c)

    private fun propPort(name: String): Int? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
        p.inputStream.bufferedReader().readText().trim().toIntOrNull()?.takeIf { it > 0 }
    } catch (e: Exception) { null }

    private fun candidatePorts(c: Context): List<Int> = (
        listOf(5555, port(c)) +
        listOfNotNull(propPort("service.adb.tls.port"), propPort("service.adb.tcp.port"), propPort("persist.adb.tcp.port"))
    ).distinct()

    private fun connect(c: Context, m: AbsAdbConnectionManager, errs: MutableList<String>): Boolean {
        if (m.isConnected()) return true
        // 1) Descubrimiento automático del puerto (depuración inalámbrica)
        try {
            m.connectTls(c, 10000)
            if (m.isConnected()) return true
        } catch (e: Throwable) { errs += "auto:${e.javaClass.simpleName}" }
        // 2) Respaldo: puertos conocidos / leídos del sistema
        for (p in candidatePorts(c)) {
            try {
                m.connect("127.0.0.1", p)
                if (m.isConnected()) { Store.setPort(c, p); return true }
            } catch (e: Throwable) { errs += "$p:${e.javaClass.simpleName}" }
        }
        return false
    }

    private fun drop(m: AbsAdbConnectionManager) {
        try { (m as? java.io.Closeable)?.close() } catch (e: Throwable) { }
    }

    private fun readAll(s: java.io.Closeable, input: java.io.InputStream): String {
        val sb = StringBuilder()
        try {
            val r = input.bufferedReader()
            val buf = CharArray(2048)
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                sb.append(buf, 0, n)
            }
        } catch (e: java.io.IOException) { /* fin de stream */ }
        finally { try { s.close() } catch (e: Throwable) { } }
        return sb.toString().trim()
    }

    private fun exec(m: AbsAdbConnectionManager, cmd: String): String {
        val s = m.openStream("shell:$cmd")
        return readAll(s, s.openInputStream())
    }

    private fun runAll(m: AbsAdbConnectionManager, cmds: Array<out String>): String =
        cmds.joinToString("\n") { cmd -> "$ $cmd -> ${exec(m, cmd)}" }

    @Synchronized
    fun run(c: Context, vararg cmds: String): String {
        val m = mgr(c)
        val errs = mutableListOf<String>()
        if (!connect(c, m, errs)) {
            ensureAdbOn(c)
            Thread.sleep(3000)
            if (!connect(c, m, errs))
                throw IllegalStateException("Sin conexión ADB (${errs.joinToString(" ")}). Empareja desde la app.")
        }
        return try {
            runAll(m, cmds)
        } catch (e: Throwable) {
            drop(m)
            if (!connect(c, m, errs))
                throw IllegalStateException("Conexión perdida (${errs.joinToString(" ")})")
            runAll(m, cmds)
        }
    }

    /** Busca por mDNS el puerto del diálogo "Vincular con código" (solo existe mientras ese diálogo está abierto). */
    private fun findPairingPort(c: Context, timeoutMs: Long = 15000): Int? {
        val nsd = c.getSystemService(Context.NSD_SERVICE) as NsdManager
        val latch = CountDownLatch(1)
        var found: Int? = null
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                nsd.resolveService(i, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(r: NsdServiceInfo, e: Int) {}
                    override fun onServiceResolved(r: NsdServiceInfo) {
                        if (found == null) { found = r.port; latch.countDown() }
                    }
                })
            }
        }
        nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        try { nsd.stopServiceDiscovery(listener) } catch (e: Throwable) { }
        return found
    }

    /** Empareja con el adbd local. Si port es null/0, busca el puerto automáticamente. Solo hace falta el código. */
    @Synchronized
    fun pair(c: Context, port: Int?, code: String): String {
        val p = if (port != null && port > 0) port else findPairingPort(c)
            ?: return "No encontré el puerto de emparejamiento. Abre 'Vincular con código' y reintenta."
        val r: Any? = mgr(c).pair("127.0.0.1", p, code)
        return if (r == false) "Emparejamiento FALLÓ (revisa el código; puerto $p)"
        else "Emparejado (puerto $p). Ahora pulsa 'Probar shell'."
    }

    /** Pide a adbd reiniciarse en modo TCP clásico (5555): así no depende de Wi-Fi. Se pierde al reiniciar el reloj. */
    @Synchronized
    fun enableTcp(c: Context): String {
        val m = mgr(c)
        val errs = mutableListOf<String>()
        if (!connect(c, m, errs)) throw IllegalStateException("Sin conexión ADB (${errs.joinToString(" ")})")
        val s = m.openStream("tcpip:5555")
        val out = readAll(s, s.openInputStream())
        drop(m)
        Store.setPort(c, 5555)
        return "tcpip: $out"
    }
}
