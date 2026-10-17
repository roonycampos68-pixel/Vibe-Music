# Validación 1.4.0 · Android

16 de octubre de 2026 (America/Bogota).

## Cambios de esta entrega

- Play en los resultados abre audio nativo, sin depender del reproductor de vídeo
  incrustado. El mismo motor yt-dlp resuelve una pista de audio temporal que Media3
  reproduce directamente (incluido HLS). La descarga a MP3 conserva su flujo.
- La vista de audio muestra portada, carga, pausa, posición, búsqueda dentro de la
  pista y reintento que obtiene una URL nueva. Se pausa al pasar a segundo plano;
  se libera al cerrar. No crea una descarga ni una entrada de biblioteca.
- El reproductor de la biblioteca tiene una Surface opaca, texto blanco y un
  degradado con todos sus colores opacos. Antes su color intermedio tenía alfa .6
  y dejaba ver Buscar a través del diálogo; tampoco establecía color de contenido.
- Las portadas de YouTube se descargan y validan con límite de tamaño/dimensiones,
  se guardan de forma atómica y se reutilizan sin red. Se recuperan también para
  canciones antiguas al abrir Vibe con conexión. Una portada fallida no invalida el
  audio. Ya no se usa el círculo decorativo como portada de una canción de YouTube.

## Pruebas y compilación

`source /workspace/.vibe-tools/env.sh` y
`./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain`:

- **88 pruebas JVM/Robolectric**, 0 fallos, errores u omitidas.
- **Lint: 0 errores y 3 avisos** existentes del manifiesto.
- APK ARM64, ARMv7, x86_64 y APK de pruebas compilados.
- Las tres variantes verificadas con apksigner conservan la firma de las entregas
  anteriores: versionCode 6, versionName 1.4.0. Room sigue en versión 3; solo se
  actualiza el campo existente artworkPath. No hace falta desinstalar.
- SHA-256 de los instaladores en `releases/SHA256SUMS`.

Las pruebas nuevas comprueban selección de audio sin descarga, validación de URLs
y cabeceras, reconocimiento HLS, pausa/liberación del reproductor y resolución que
termina en segundo plano. Comprueban también la opacidad del degradado y el guardado
de imágenes válidas, rechazo de HTML/tamaños excesivos y reutilización sin red.
La prueba de portada usa gráficos nativos de Robolectric: el primer intento con
su decodificador simulado aceptaba HTML como imagen y falló; al usar el decodificador
real, ambos casos pasan sin quitar las comprobaciones.

Las pruebas de interfaz conservan selección del vídeo sin portapapeles, cola de
descargas y deduplicación, y ahora abren «Escuchar audio». El motor nativo de
YouTube no se ejecuta con éxito bajo Robolectric; estos casos no prueban streaming.

Registro de compilación: `/workspace/.vibe-tools/v14-final.log`.
**Prueba instrumentada Android: 1/1 aprobada**, 4,633 segundos, en API29 x86_64.
AudioPreviewPlaybackTest reprodujo un WAV original incluido con ExoPlayer, verificó
que avanzaba al menos un segundo, comprobó la duración y que Lifecycle detenía la
reproducción al pasar a segundo plano. No usó WebView ni una Activity. El emulador
no tenía salida de sonido al anfitrión: se comprobó el reloj del reproductor y la
ausencia de errores de decodificación, no una escucha humana. Se instaló el APK
sobre la versión anterior. El emulador de la tarea se detuvo después de la prueba.
Registro: `/workspace/.vibe-tools/v14-native-audio.log`. La última corrección del
ViewModel espera a leer la preferencia offline real antes de recuperar portadas;
no cambia el controlador de audio probado. La suite JVM se repitió tras ese ajuste.

## Red y límites

El usuario confirmó que búsqueda y descarga de YouTube funcionan en su teléfono,
y que el MP3 descargado se reproduce. Reportó fallo en el vídeo incrustado; esta
entrega cambia ese camino a audio nativo.

El estado del entorno ahora informa de los dominios personalizados, con
observaciones actuales. GitHub API funciona y confirma que el repositorio es
privado. Sin embargo, las peticiones reales a www.youtube.com e i.ytimg.com desde
esta nube siguen recibiendo CONNECT 403 del proxy. No se eludió esa restricción.
**No se verificó aquí el streaming real de YouTube ni la descarga de su miniatura.**
La prueba local de decodificación no sustituye esa comprobación en el teléfono.
Los vídeos restringidos y cambios del servicio pueden seguir causando errores.

Las URLs de streaming temporales no se guardan en la biblioteca ni se muestran en
los mensajes de error de Vibe.
La página web sigue disponible como alternativa interna. MP3Cow no se incorpora.
Pruebas de red, modo avión, portadas anteriores, foco de audio y distintos tamaños
de pantalla: ver `DEVICE_TESTS.md`.
