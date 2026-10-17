# Validación 1.5.0 · Android

16 de octubre de 2026 (America/Bogota).

## Cambios

- Lista compacta bajo un buscador fijo, filas desplazables, portadas y últimos
  12 resultados seleccionados cuando el campo está vacío.
- Tocar un resultado usa el reproductor principal del servicio. La barra inferior
  muestra título, artista, portada, duración, progreso, pausa y descarga; no abre
  un diálogo. El reproductor completo se abre solo al tocar la barra.
- Resolución anticipada del primer resultado cuando no hay selección online;
  consultas en curso compartidas y caché en memoria de hasta 8 enlaces durante
  5 minutos, limitada también por la caducidad indicada en la URL.
- Play no convierte ni guarda MP3: resuelve audio directo/HLS. No inicializa
  FFmpeg. Tiempo de socket 6 s, sin los reintentos de descarga; resolución máxima
  20 s, resolución + preparación del reproductor 25 s, búfer inicial 750 ms.
  El límite hace fallar con reintento; no significa que una canción tarde 25 s
  en empezar ni demuestra una reducción medida de latencia real de YouTube.
- Cambiar de canción o cancelar invalida trabajo anterior. Una selección tardía
  no puede reemplazar la nueva. Errores de reproducción invalidan el enlace;
  reintentar obtiene uno nuevo. El servicio conserva controles y segundo plano.
- Los archivos ya guardados usan el camino local; escuchar online no crea una
  entrada de biblioteca ni persiste URLs firmadas, y conserva la última cola local.
  El botón de audio de la página web alternativa también vuelve a la barra normal.

## Comprobaciones

`source /workspace/.vibe-tools/env.sh` y
`./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain`:

- **93 pruebas JVM/Robolectric aprobadas**, sin fallos, errores ni omitidas.
- **Android Lint: 0 errores, 3 avisos** existentes del manifiesto.
- Compilados APK ARM64, ARMv7, x86_64 y APK de pruebas.
- Tres APK verificados con apksigner: versionCode **7**, versionName **1.5.0**,
  misma huella de firma que las versiones instaladas:
  `33f60a3d570724cf14211f65d91484f51b6e34b2988d3c2a8a67e0321e5c0c70`.
- Room sigue en versión 3. No se borra ni migra destructivamente la biblioteca.
- SHA-256 de los instaladores publicados: `releases/SHA256SUMS`.

Las pruebas de reproducción usan reloj virtual y comprueban reutilización y
caducidad, cambios de selección, cancelación antes de volver a una cola local,
plazos de resolución/búfer y parámetros independientes de descarga. La primera
pasada detectó que addOption no sustituye valores anteriores: el comando ahora
se construye con un único valor por opción y la prueba verifica los valores.

La prueba Compose toca una canción reciente, conserva Buscar, muestra la duración
y descarga desde el minirreproductor sin abrir diálogo ni otra app. Comprueba la
cola en Room y que escuchar no crea un archivo local. Robolectric simula que el
servicio no está disponible; esta prueba no afirma haber reproducido audio.
Las demás pruebas de biblioteca, importación Spotify, descargas, opacidad y
portadas siguen pasando. Registro: `/workspace/.vibe-tools/v15-checks.log`.

## Prueba instrumentada Android

**2/2 pruebas aprobadas**, 19,594 segundos, API29 x86_64, después de instalar la
actualización sobre la versión anterior. Registro:
`/workspace/.vibe-tools/v15-native-audio.log`.

OnlinePlaybackDeviceTest verifica el controlador con ExoPlayer real: el reloj
avanza al menos un segundo, duración, metadatos/portada, seek, pausa y cambio a
archivo local. La segunda prueba conecta un MediaController al PlaybackService
real, envía el comando de selección, comprueba reproducción y cambio a cola local
sin conservar la selección online. Inyecta un resolver de prueba con un WAV
original incluido; no descarga música ni reproduce contra YouTube. La inyección
solo existe en el APK de pruebas. No usa una Activity ni un diálogo.

El emulador no tiene salida de audio al anfitrión: se comprueban decodificación y
reloj, no escucha humana. Lint se repitió tras añadir la prueba del servicio:
`/workspace/.vibe-tools/v15-final-lint.log`, sin nuevos errores ni avisos.
El emulador iniciado por esta tarea se detuvo al terminar.

## Alcance de red y rendimiento

El usuario confirmó que buscar y descargar funcionan en su teléfono con la versión
anterior, pero que la preparación para escuchar demora varios minutos. Se corrige
el camino de reproducción y la interfaz; no se presenta ese reporte como una
prueba de la nueva versión. La nube mantiene el bloqueo CONNECT 403 observado
para YouTube/miniaturas aunque esos dominios figuren en su configuración.
No se eludió el proxy. No se midió aquí la latencia real ni se verificó streaming
contra YouTube con esta versión. Siguen pendientes los pasos de red, pantalla
bloqueada, cambio Wi-Fi/móvil y modo avión de `DEVICE_TESTS.md` en un teléfono.
No se garantiza reproducción instantánea ni disponibilidad de todos los vídeos.

El repositorio es privado. Los enlaces de instalación pasan por login de GitHub;
el usuario debe usar la cuenta con acceso en ese mismo navegador. La validación
autenticada del archivo desde la nube no lo vuelve una descarga pública.
