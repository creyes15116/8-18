package com.xiaomi.miwear.mishow
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import kotlin.concurrent.thread

class SettingsActivity : Activity() {
    private lateinit var status: TextView
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 60) }
        status = TextView(this).apply { textSize = 11f }
        val portIn = EditText(this).apply { hint = "Puerto (opcional, se busca solo)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val codeIn = EditText(this).apply { hint = "Código de 6 dígitos"; inputType = InputType.TYPE_CLASS_NUMBER }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { col.addView(it) }
        col.addView(TextView(this).apply { text = "MiShow Guard v2"; textSize = 16f })
        col.addView(portIn)
        col.addView(codeIn)
        btn("1. Emparejar") {
            val p = portIn.text.toString().trim().toIntOrNull()
            val code = codeIn.text.toString().trim()
            bg { if (code.length != 6) "Escribe el código de 6 dígitos" else Shell.pair(this, p, code) }
        }
        btn("2. Probar shell") { bg { Actions.setup(this) } }
        btn("3. Activar puerto 5555") { bg { Shell.enableTcp(this) } }
        btn("Simular salto (+10 min, restaura en 10 s)") { bg { Actions.jump(this, true); "prueba lanzada" } }
        btn("Restaurar hora ahora") { bg { Actions.restore(this); "lanzado" } }
        btn("Actualizar estado") { refresh() }
        col.addView(status)
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }
    private fun bg(f: () -> String) = thread {
        val out = try { f() } catch (e: Throwable) { "ERROR: ${e.javaClass.simpleName} ${e.message}" }
        Store.log(this, out.replace('\n', '|'))
        runOnUiThread { refresh() }
    }
    private fun refresh() { status.text = Store.logText(this) }
}
