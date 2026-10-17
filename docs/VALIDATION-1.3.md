# Validación de Vibe Music 1.3.0

Esta entrega cambia la reproducción en línea: el botón de reproducir muestra el
reproductor oficial dentro de Vibe. La búsqueda web alternativa y la página del
vídeo también permanecen en la app, con el botón de conversión local a MP3 debajo.
Se mantienen el motor y los formatos de la versión 1.2, sin cambios de esquema Room.

| Comprobación | Resultado |
| --- | --- |
| `testDebugUnitTest` | 79 pruebas superadas, 0 fallos, 0 errores, 0 omitidas |
| `lintDebug` | 0 errores, 3 avisos existentes del manifiesto |
| `assembleDebug` | APK ARM64, ARM de 32 bits y x86_64 compilados |
| `assembleDebugAndroidTest` | Compilado, incluida la prueba de WebView |
| Identidad Android | `com.vibemusic`, versionCode 4, versionName 1.3.0, minSdk 26 |
| Firma ARM64 y ARM de 32 bits | Verificada, coincide con las entregas anteriores |
| WebView Android API 29 x86_64 con API local de prueba | 1 prueba instrumentada ejecutada, 1 superada, 0 omitidas |
| Reproducción, búsqueda y descarga de un vídeo real de YouTube | No verificadas: los dominios no están autorizados por la política activa de esta nube |
| Automatización de MP3Cow | No implementada ni verificada: su dominio también está bloqueado |

## Qué demuestran las pruebas

Las pruebas Compose recorren la búsqueda web dentro de la aplicación sin lanzar
intents externos, comprueban que no se cree una descarga solo por abrir esa vista,
abren la vista integrada pegando un enlace y pulsan **Descargar MP3**. Verifican
la entrada persistente de WorkManager/Room y navegan a Descargas desde el diálogo.
Las restricciones de red del planificador de prueba permanecen sin satisfacer:
esta parte no ejecuta el descargador nativo ni descarga un vídeo de prueba.

Ese recorrido detectó y corrigió un fallo en Descargas: capacidad de almacenamiento
igual a cero producía `0/0` y una barra con `NaN` que cerraba la pantalla. Ahora se
muestra un valor finito cuando Android no informa la capacidad. También se filtran
porcentajes no finitos antes de dibujar las barras.

Los tests de la sesión web comprueban origen/Referer del documento, selección del
vídeo, navegación de YouTube mediante history.pushState, rechazo de URLs falsas,
rutas locales e intents, permisos de WebView, mensajes de estado y errores de
inserción. Comprueban pausa, liberación y que cambiar de vídeo desde el reproductor
actualice la grabación que se va a descargar, tanto con navegación completa como
al elegir un vídeo relacionado dentro del iframe. No ejecutan el servicio de YouTube.

Las pruebas previas de metadatos, migraciones Room, biblioteca, playlists de Spotify,
HTTP Range, huellas, cancelación y cola de MP3 se conservan. Media3 se declara no
disponible en Robolectric; estos resultados no verifican sonido real.

`EmbeddedPlayerTest` está diseñado para ejecutar el HTML en el WebView real de
Android, interceptando la API y el iframe con respuestas locales. Verifica el
Referer enviado al iframe, la señal de preparado, el cambio de selección al
abrir otro vídeo en el iframe y la orden de pausa JavaScript.
No envía peticiones a YouTube ni reproduce audio.

El primer intento con `connectedDebugAndroidTest` falló antes de ejecutar el caso:
el lector de propiedades de ddmlib agotó su tiempo en el emulador sin KVM y lo
clasificó como «Unknown API Level». Tras comprobar API 29 y arranque completo con
ADB, se instalaron los APK mediante ADB y se ejecutó directamente el mismo caso
con AndroidJUnitRunner. Resultado: `OK (1 test)`, 26,759 segundos. No se desactivó
ninguna aserción. El emulador iniciado para esta tarea se detuvo después de la prueba.

La conversión con los binarios Android reales pasó en la entrega 1.2 (1 prueba
instrumentada). No se atribuye a la versión 1.3 una repetición que no se haya hecho.
El código del motor y sus binarios se mantienen sin cambios. La evidencia anterior
está en [VALIDATION-1.2.md](VALIDATION-1.2.md).

## Límites y comprobaciones pendientes

Guardar dominios en el borrador no activa la red. Hace falta revisar y guardar
la configuración, publicar el entorno y volver a consultar su estado antes de
probar servicios en vivo. La conexión HTTPS a MP3Cow devolvió `CONNECT 403` en el proxy, sin llegar al
servidor. MP3Cow no se inspeccionó y no se supone una API o un
flujo automático a partir del parámetro `v` del enlace propuesto. El botón sigue
usando yt-dlp/FFmpeg en el teléfono; no envía el enlace a MP3Cow.

La reproducción de YouTube puede depender de permisos de inserción, restricciones
del vídeo y versión de Android System WebView. La app ofrece **Ver página aquí**,
reintento y mensajes para errores 100/101/150/153. Ese manejo no demuestra que
todos los vídeos sean reproducibles. La publicidad del reproductor/página de
YouTube se conserva; los MP3 locales se reproducen con Media3 sin esa vista web.

Quedan pendientes en un teléfono la reproducción/descarga real de YouTube, modo
avión tras esa descarga, foco de audio, rotación, texto grande, teclado, segundo
plano, pantalla apagada, Bluetooth y llamadas. Los procedimientos se encuentran
en [DEVICE_TESTS.md](DEVICE_TESTS.md). La firma coincide para actualizar sin
borrar la biblioteca; no se hizo aquí una actualización física de cada teléfono.
