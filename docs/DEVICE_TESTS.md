# Pruebas de dispositivo

La matriz de resultados actual está en `VALIDATION-1.4.md`. Este documento es
el procedimiento reproducible; una casilla de esta lista no significa que se haya
superado la prueba.

## Automatización

Con Android SDK, un teléfono/emulador Android 10+ y depuración USB:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell pm grant com.vibemusic android.permission.POST_NOTIFICATIONS # Android 13+
./gradlew connectedDebugAndroidTest
```

Ejecutar en una instalación de pruebas limpia. `DeviceWorkflowTest` crea un MP3
original en MediaStore, lo importa, borra la fuente, comprueba metadatos y persistencia,
crea una playlist, reproduce en segundo plano y con pantalla apagada, activa modo
avión y vuelve a reproducir. Finalmente borra los datos creados. `UiWorkflowTest`
añade las composiciones originales y recorre la creación de playlists y búsqueda.
No borres datos de una instalación que contenga tu biblioteca personal.

## Matriz manual completa

1. Importar un MP3 propio con **Tu biblioteca → Importar**, verificar nombre,
   artista, álbum y portada. Reproducirlo y mover su posición con la barra.
2. Crear una playlist, añadir dos canciones, subir/bajar una, renombrarla, cambiar
   portada, quitar una canción y confirmar la duración total. Verificar favoritas.
3. Pausar a mitad de canción, cerrar/forzar detención y volver a abrir. Comprobar
   biblioteca, playlists, favoritos, cola y posición. Pulsar reproducir.
4. Apagar la pantalla durante al menos un minuto y escuchar la continuidad.
5. Cambiar a otra app y comprobar el sonido y la notificación multimedia.
6. Buscar por título/artista con el filtro inicial YouTube. Verificar resultados
   de YouTube, Mi música y Descargables (originales incluidos y Wikimedia); probar acentos,
   cambios rápidos de consulta, paginación, errores y reintento. Descargar desde
   una fuente con permiso. También probar un catálogo propio HTTPS. Descargar,
   pausar/reanudar y cancelar. Revisar progreso, calidad, checksum y ausencia de
   duplicados. Probar una fuente sin soporte Range para verificar el reinicio.
7. Activar modo avión, cerrar/abrir la app y reproducir la descarga desde Descargas.
8. Reiniciar el teléfono. Verificar que el archivo sigue disponible sin internet.
9. Eliminar una descarga y comprobar la reducción de almacenamiento y su retirada
   de playlists. El archivo importado original fuera de la app no se elimina.
10. Controlar pausa, reproducción y salto desde bloqueo y audífonos Bluetooth;
    desconectar los audífonos y comprobar pausa. Probar una llamada entrante y
    pérdida de foco. Probar el temporizador y el volumen.

Repetir en Android 8, 10, 13 y 15 o posteriores, distintos tamaños de pantalla y
con políticas de batería del fabricante. Las pruebas de llamadas y Bluetooth
necesitan hardware real. Las fuentes predeterminadas ya no dependen del acceso
al repositorio de GitHub.

## Regresión de la versión 1.1.0

1. Instalar el APK 1.0.0 y crear biblioteca, favoritos y playlists. Actualizar con
   el APK 1.1.0 mediante `adb install -r` o abriéndolo en el teléfono. Verificar que
   Android acepta la firma y conserva todo sin desinstalar ni borrar datos.
2. Exportar una playlist propia de Spotify como CSV de Exportify y usar el selector
   de documentos de Vibe. Comprobar acentos, comillas, álbumes, orden, recuentos y
   títulos pendientes. Repetir con el JSON de datos de cuenta extraído del ZIP.
3. Importar un archivo local que coincida con un pendiente y verificar su vinculación.
   Buscar un pendiente manualmente, elegir una fuente autorizada y verificar que
   la descarga aparece tanto en la lista como en Descargas, también tras reiniciar.
4. Probar búsqueda por lotes, cancelar, cambiar a modo sin conexión y comprobar
   que se detienen las consultas. Los títulos sin audio deben seguir pendientes.
5. Exportar la lista a CSV, reimportarla y comparar su orden y los pendientes.
   Eliminar audio vinculado debe conservar el título; eliminar la playlist debe
   conservar el audio de la biblioteca.

## Conversor nativo de la versión 1.2

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibemusic.NativeConverterTest
```

Este caso ejecuta Python/yt-dlp/FFmpeg en Android con un WAV original incluido,
sin red ni interfaz. Convierte a MP3, valida duración y metadatos con Android,
importa, comprueba deduplicación y limpia sus archivos. No demuestra que YouTube
permita extraer un vídeo real. Para emulador x86_64 instala el APK x86_64; para un
teléfono elige el ABI correspondiente.

Con conexión real, buscar una grabación propia o con permiso de descarga; descargar
a MP3 y observar sus fases, pausar, reanudar y cancelar. Repetir con enlace corto,
con vídeo privado, retirado y en vivo; los fallos deben ser visibles sin añadir
audio incompleto. Completar una descarga, activar modo avión, cerrar/abrir y
reproducir el MP3. Comprobar Wi-Fi, almacenamiento insuficiente y varias descargas
en cola. La reproducción de una canción aún no descargada debe mostrar el reproductor
oficial dentro de Vibe; la guardada debe sonar en Media3. Estas comprobaciones necesitan ejecución real.

## Vista de YouTube de la versión 1.3

1. Buscar una grabación pública y pulsar reproducir: comprobar miniatura, título
   y reproductor dentro de Vibe. Tocar ▶ en el vídeo y escuchar. No debe abrirse
   YouTube ni otro navegador. Conservar visibles sus controles y anuncios.
2. Probar un vídeo que no permita inserción: leer el error, pulsar **Ver página
   aquí** y comprobar que la reproducción sigue en Vibe. No se exige que vídeos
   privados/restringidos se reproduzcan.
3. Descargar desde la vista previa, consultar porcentaje/fase y abrir **Ver
   descargas**. Volver a la biblioteca tras terminar y escuchar en modo avión.
4. Abrir la búsqueda web alternativa, buscar y abrir dos vídeos distintos. Verificar
   que el botón se refiere al vídeo actual y que se deshabilita al volver a resultados.
5. Cerrar la vista, rotar el teléfono, pulsar atrás y cambiar a otra aplicación.
   Comprobar que el vídeo deja de sonar, que no quedan dos audios y que la biblioteca
   no vuelve a reproducir por sí sola. Probar retrato/paisaje, texto grande y teclado.
6. Probar red caída y WebView ausente/desactualizado: debe mostrarse una explicación
   y mantenerse la posibilidad de descargar desde el motor local cuando haya red.

`EmbeddedPlayerTest` ejecuta el HTML y la comunicación de estado en un WebView
Android real con una API de prueba local interceptada. No consulta YouTube, no
reproduce sonido y no demuestra que los vídeos reales funcionen. Se ejecuta con:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibemusic.EmbeddedPlayerTest
```

Si ddmlib agota el tiempo de lectura de propiedades en un emulador lento y muestra
«Unknown API Level», comprueba primero con ADB el SDK y `sys.boot_completed = 1`.
En esta entrega se ejecutó el caso sin el descubrimiento de dispositivos de Gradle:

```bash
adb -s emulator-5556 install -r -t app/build/outputs/apk/debug/app-x86_64-debug.apk
adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w -r -e class com.vibemusic.EmbeddedPlayerTest com.vibemusic.test/androidx.test.runner.AndroidJUnitRunner
```

El número de serie y ABI deben corresponder al dispositivo de pruebas. Comprueba
`OK (1 test)` y el código de cada caso, no solo la salida del comando ADB: Android
puede terminar el comando aunque haya una aserción fallida.


## Audio y portadas de YouTube (1.4)

1. Buscar por título y pulsar Play. Debe abrir «Escuchar audio» con portada,
   resolver el audio y reproducirlo sin crear una descarga ni abrir un vídeo.
2. Pausar, reanudar y adelantar. Cambiar de app mientras resuelve y mientras suena:
   debe quedar pausado; volver no debe iniciar audio automáticamente.
3. Cerrar la vista mientras prepara y después de reproducir: no debe quedar sonido
   ni una petición que empiece a sonar más tarde. Repetir con otra canción.
4. Probar un error de red y Reintentar audio; debe resolver una URL nueva.
5. Descargar desde esa misma vista, reproducir el archivo y abrir su reproductor:
   fondo opaco, texto blanco, portada del vídeo. No se debe ver Buscar por detrás.
6. Abrir la app con canciones de YouTube descargadas con 1.3: comprobar la llegada
   de portadas y su conservación después en modo avión. Si la imagen falla,
   el audio debe seguir siendo reproducible.
7. Desde la página web interna, abrir otro vídeo y pulsar Escuchar solo el audio:
   confirmar que corresponde al vídeo nuevo, no al resultado anterior.

`AudioPreviewPlaybackTest` prepara un WAV original incluido con ExoPlayer, verifica
que avanza el reloj de audio y que Lifecycle pausa el reproductor. No contacta
YouTube ni sustituye los pasos en red anteriores. Puede ejecutarse sin Activity con:

```bash
adb shell am instrument -w -r -e class com.vibemusic.AudioPreviewPlaybackTest com.vibemusic.test/androidx.test.runner.AndroidJUnitRunner
```
