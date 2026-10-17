# Validación 1.3.1 · Android

Comprobaciones del 16 de octubre de 2026 (America/Bogota).

## Cambios

- Descargar MP3 queda fijo debajo de la vista previa. Usa el vídeo seleccionado,
  tanto en el reproductor integrado como al navegar en la página de YouTube.
  No necesita copiar ni pegar enlaces ni modificar el portapapeles.
- Un audio reutilizado por deduplicación se reconoce mediante `savedTrackId` y
  muestra Escuchar sin conexión, aunque su ID y fuente sean distintos.
- Los errores temporales de red emitidos por el proceso nativo se reintentan con
  WorkManager hasta tres veces. Antes solo se reintentaban `IOException`; el
  wrapper emite otra excepción para los fallos del proceso. Los errores de
  restricciones, cuota y almacenamiento tienen mensajes específicos y no crean
  reintentos automáticos. Un error tardío no sustituye una pausa por un fallo.
- La vista previa distingue pausa, espera y fallo del porcentaje de transferencia.

## Ejecutado

`source /workspace/.vibe-tools/env.sh` y
`./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain`:

- **82 pruebas JVM/Robolectric**, 0 fallos, 0 errores, 0 omitidas.
- **Lint: 0 errores, 3 avisos** existentes del manifiesto.
- Compilación de APK ARM64, ARMv7 y x86_64 completada en la misma ejecución.
- Las tres firmas verificadas con `apksigner` coinciden con las versiones anteriores;
  paquete `com.vibemusic`, versionCode 5, versionName 1.3.1. Room sigue en versión 3.

La prueba de interfaz escribe un nombre de canción, abre la búsqueda web dentro
de Vibe, simula la navegación de YouTube a un vídeo y pulsa el botón visible sin
desplazarse. Comprueba URL y título de la tarea persistida, que el portapapeles
permanece intacto y que no se abre otra aplicación. Después comprueba el estado
offline de una descarga que reutiliza un audio existente. La navegación web se
simula; WorkManager conserva las restricciones sin satisfacer y no descarga audio.
Tres casos adicionales cubren clasificación de errores transitorios, restricciones
y mensajes que no exponen rutas o URLs del proceso.

Evidencia: `/workspace/.vibe-tools/v131-final.log`, XML de JUnit y reporte Lint en
`app/build/`. Huellas de los dos instaladores en `releases/SHA256SUMS`.
Las pruebas instrumentadas de conversión local (1.2) y WebView con respuestas
locales (1.3) son evidencia histórica; no se repitieron en esta entrega.

## Enlaces y entorno

El acceso Git autenticado funciona. La página del repositorio y la ruta del archivo
sin una sesión web devolvieron HTTP 404. La ruta de inicio de sesión devolvió HTTP
200. README y releases/README enlazan ahora al inicio de sesión de GitHub con
retorno al instalador; no se promete acceso público ni se cambia la visibilidad.
La descarga después de iniciar sesión en el navegador del teléfono está pendiente
de comprobar en ese dispositivo. No se incluyen tokens ni enlaces privados firmados.

La configuración publicada sí contiene los 16 destinos personalizados, incluidos
YouTube, Googlevideo y MP3Cow. Sin embargo, `environment_status` sigue informando
de la versión inicial `cecfgver_6ad24a7dbef48194b78b3ef1ace813cb`, con observaciones
actuales, política aplicada y `allowed_hosts: []`. La versión publicada leída es
`cecfgver_6ad2bfc854548194a8fb0ab8fa8fe4b1`. Las peticiones a YouTube, MP3Cow y
api.github.com reciben CONNECT 403 del proxy antes de contactar esos sitios.
No se alteró el proxy ni se eludió esa restricción.

**No se verificaron búsqueda, reproducción ni descarga real de YouTube. MP3Cow no
está integrado.** El botón usa yt-dlp y FFmpeg locales. Para inspeccionar MP3Cow y
probar la red hace falta una tarea vinculada a la versión publicada; volver a guardar
los mismos dominios no demuestra que esta sesión los haya recibido. Las pruebas
locales no sustituyen una descarga real ni la reproducción posterior en modo avión.
