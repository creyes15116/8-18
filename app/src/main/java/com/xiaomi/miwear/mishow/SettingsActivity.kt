package com.xiaomi.miwear.mishow
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.*
import java.io.File
import kotlin.concurrent.thread

class SettingsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var keyInfo: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 60) }
        status = TextView(this).apply { textSize = 11f }
        keyInfo = TextView(this).apply { textSize = 12f }

        // Emparejamiento: oculto, solo para la primera vez
        val pairBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val portIn = EditText(this).apply { hint = "Puerto (opcional)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val codeIn = EditText(this).apply { hint = "Código de 6 dígitos"; inputType = InputType.TYPE_CLASS_NUMBER }
        pairBox.addView(portIn)
        pairBox.addView(codeIn)

        fun btn(t: String, parent: LinearLayout = col, f: () -> Unit) =
            Button(this).apply { text = t; setOnClickListener { f() } }.also { parent.addView(it) }

        col.addView(TextView(this).apply { text = "MiShow Guard"; textSize = 16f })
        col.addView(keyInfo)
        btn("Probar conexión") { bg { Actions.setup(this) } }
        btn("Diagnóstico de hora") { bg { diag() } }
        btn("Simular salto (+10 min, vuelve en 10 s)") { bg { Actions.jump(this, true); "prueba lanzada" } }
        btn("Restaurar hora ahora") { bg { Actions.restore(this); "lanzado" } }
        btn("Actualizar estado") { refresh() }
        btn("Emparejar de nuevo (solo si hace falta)") { pairBox.visibility = View.VISIBLE }
        col.addView(pairBox)
        btn("Enviar código", pairBox) {
            val p = portIn.text.toString().trim().toIntOrNull()
            val code = codeIn.text.toString().trim()
            bg { if (code.length != 6) "Escribe el código de 6 dígitos" else Shell.pair(this, p, code) }
        }
        col.addView(status)
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    /** Muestra la hora del reloj antes y después del salto. Siempre restaura la hora real al final. */
    private fun diag(): String {
        Shell.ensureAdbOn(this)
        val t0 = System.currentTimeMillis()
        val e0 = SystemClock.elapsedRealtime()
        val a = try {
            Shell.run(this,
                "date +%s",
                "settings put global auto_time 0; echo \"[exit=\$?]\"",
                "cmd alarm set-time ${t0 + 600_000}; echo \"[exit=\$?]\"",
                "date +%s")
        } catch (e: Throwable) { "ERROR: ${e.javaClass.simpleName} ${e.message}" }
        Thread.sleep(3000)
        val real = t0 + (SystemClock.elapsedRealtime() - e0)
        val r = try {
            Shell.run(this,
                "date +%s",
                "cmd alarm set-time $real; echo \"[exit=\$?]\"",
                "settings put global auto_time 1; echo \"[exit=\$?]\"",
                "date +%s")
        } catch (e: Throwable) { "ERROR: ${e.javaClass.simpleName} ${e.message}" }
        return "SALTO:\n$a\nRESTAURA:\n$r"
    }

    private fun bg(f: () -> String) = thread {
        val out = try { f() } catch (e: Throwable) { "ERROR: ${e.javaClass.simpleName} ${e.message}" }
        Store.log(this, out.replace('\n', '|'))
        runOnUiThread { refresh() }
    }

    private fun refresh() {
        val hasKey = File(filesDir, "adb_key.pk8").exists() && File(filesDir, "adb_cert.der").exists()
        keyInfo.text = if (hasKey) "Llave guardada: SÍ. No hay que emparejar otra vez."
                       else "Llave guardada: NO. Hay que emparejar."
        status.text = Store.logText(this)
    }
}
