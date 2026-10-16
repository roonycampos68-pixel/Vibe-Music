# Validación de Vibe Music 1.2.0

16 de octubre de 2026. La aplicación incorpora búsqueda de YouTube, yt-dlp
2026.08.19, Python 3.12, QuickJS y FFmpeg para descarga y conversión local a MP3.

| Comprobación | Resultado |
| --- | --- |
| `testDebugUnitTest` | 71 pruebas superadas, 0 fallos, 0 omitidas |
| `lintDebug` | Sin errores; 3 avisos existentes del manifiesto |
| Compilar APK ARM64, ARM de 32 bits y x86_64 | Superado, versionCode 3 / versionName 1.2.0 |
| Compilar pruebas instrumentadas | Superado |
| Verificar firma de ambos APK de teléfono | Superado; certificado idéntico a las entregas anteriores |
| Verificar script yt-dlp | SHA-256 coincide con la distribución oficial y con el recurso del APK |
| Migración real de esquemas Room 1 y 2 a 3 | Superada; conserva biblioteca, favoritos, descargas y títulos pendientes de Spotify |
| Conversión nativa sin red en AOSP API 29 x86_64 | 1 prueba instrumentada ejecutada, 1 superada, 0 omitidas |
| Buscar y descargar un vídeo real de YouTube desde la nube | No ejecutado: los dominios están en el borrador y la política activa aún no los autoriza |

Las pruebas nuevas cubren validación y normalización de enlaces, rechazo de rutas
locales y dominios falsos, argumentos sin shell, búsqueda paginada, filtros de vídeos
privados/en vivo, el recurso de yt-dlp empaquetado, persistencia de fase/porcentaje,
una sola entrada de descarga, vinculación a playlists, reutilización sin red y
cancelación que elimina parciales y asociaciones. Los tests de WorkManager usan
el planificador de pruebas con restricciones de red sin satisfacer: comprueban
cola y base de datos; no ejecutan Python ni hacen una descarga simulada pasar por real.

Compose/Robolectric verifica la fuente inicial YouTube, la apertura del enlace de
respaldo con el texto real, el bloqueo en modo sin conexión y la búsqueda de audio
incluido en Descargables. El servicio Media3 se declara no disponible en esos tests;
no se consideran pruebas de sonido. Las otras pruebas de la biblioteca, catálogos,
HTTP Range y SHA-256/SHA-1 se conservan.

`NativeConverterTest` es un caso separado que usa los binarios Android reales:
convierte un WAV original a MP3, valida metadatos con Android, lo importa y comprueba
deduplicación. No abre una Activity ni accede a internet. Su resultado no valida
el acceso a YouTube, sus formatos ni sus posibles restricciones.
El AVD API 29 arrancó sin KVM. La ejecución dirigida completó en 7 min 27 s,
incluidos compilación, instalación e inicialización de las bibliotecas. Se detuvo
el emulador creado para esa prueba. Este resultado no convierte en superadas las
pruebas de interfaz, pantalla apagada o Bluetooth que fallaron o siguen pendientes.

Un test adicional reproduce un codificador antiguo escribiendo después de
cancelar: conserva el original parcial en un directorio nuevo y comprueba que
esos bytes tardíos no modifican el MP3 del siguiente intento.

Las búsquedas y descargas públicas de YouTube dependen de sus cambios y de la red
del teléfono. Los vídeos privados, de pago, retirados o sujetos a restricciones no
tienen un resultado garantizado. La app muestra el fallo y permite reintentar o
abrir YouTube. No se declara verificada una extracción en vivo por compilar el APK.

Pantalla apagada, auriculares, llamadas, modo avión tras descargar de YouTube,
reinicio físico, almacenamiento insuficiente y las políticas de batería siguen
pendientes en teléfono. Se documentan en [DEVICE_TESTS.md](DEVICE_TESTS.md).
La entrega anterior y sus intentos fallidos de interfaz en emulación sin KVM
se conservan en [VALIDATION.md](VALIDATION.md).
