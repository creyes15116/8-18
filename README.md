# MiShow Guard v2 (libadb-android, empareja una vez)

A las 07:57 salta a las 08:05; a las 08:10 reales restaura la hora real. Usa el adbd del propio reloj (127.0.0.1).
NO PROBADO en el reloj: puede necesitar ajustes tras compilar.

## Instalar (no desinstales después: se perdería la llave y habría que emparejar de nuevo)
    adb install -r app-debug.apk
    adb shell pm grant com.xiaomi.miwear.mishow android.permission.WRITE_SECURE_SETTINGS

## Emparejar (una vez)
1. Reloj en Wi-Fi. Opciones de desarrollador > Depuración inalámbrica ON.
2. Abre "Vincular dispositivo con código": muestra IP:PUERTO y un código de 6 dígitos.
3. Introduce solo el código (el puerto se busca solo):
   - en la app (botón 1), si el diálogo sigue abierto; o
   - desde el teléfono/PC mientras el diálogo está abierto:
     adb shell am broadcast -n com.xiaomi.miwear.mishow/.PairReceiver --es code CODIGO
4. Botón "2. Probar shell": debe salir uid=2000(shell).
5. Opcional: botón "3. Activar puerto 5555". La app ya busca sola el puerto de la depuración inalámbrica; este botón solo sirve de respaldo.
6. Botón "Simular salto": confirma que cambia la hora y se restaura.
