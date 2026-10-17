# Arquitectura

- `ui/`: Compose y Material 3 oscuro, navegación de cuatro secciones, estados vacíos,
  búsqueda local y pública, colecciones y reproductor. `MusicViewModel` expone StateFlow y
  conecta un MediaController al servicio. Media3 se conserva en el servicio. `YouTubePreviewDialog` aloja el reproductor
  oficial en un WebView visible, separado de Media3. `YouTubeWebSession` restringe
  navegación, identifica el vídeo actual y pausa/libera la vista al salir.
- `model/`: entidades y reglas de búsqueda, duración y claves de descarga.
- `data/`: Room (canciones, playlists, relaciones ordenadas y trabajos de descarga),
  DataStore (preferencias y búsquedas recientes), MusicRepository (importación,
  metadatos y archivos), CatalogRepository (fuentes adicionales e incluidas),
  CommonsMusicSource (API pública HTTPS), YouTubeMusic (búsqueda y conversión
  nativa con yt-dlp/FFmpeg) y PlaylistTransferRepository (CSV/JSON).
  Room versión 3 migra explícitamente las versiones 1 y 2 sin borrar datos. Las entradas
  importadas aún sin audio viven en playlist_requests, ordenadas y persistentes.
- `playback/`: un ExoPlayer alojado en MediaSessionService. Media3 publica la sesión,
  notificación, metadatos y controles del sistema. Se administra el foco de audio y
  la desconexión de auriculares. La cola y la posición se conservan cada dos segundos
  y en cambios del reproductor. Recuperar una sesión no reproduce automáticamente.
- `downloads/`: WorkManager, restricciones de red, archivos parciales, HTTP Range,
  progreso persistente y comprobación de la huella autorizada (SHA-256 o SHA-1 de
  Wikimedia). También calcula SHA-256 para deduplicar todos los archivos por contenido.
  Guarda el ID real de la canción completada incluso si ya existía con otro origen.
  Las tareas YouTube se identifican por proveedor y conservan su URL canónica,
  porcentaje y fase. Su huella SHA-256 es local para deduplicación; no se inventa
  un checksum externo. El MP3 solo se registra después de terminar la conversión
  y validar metadatos con Android. Una plaza limita conversiones concurrentes.

Los archivos importados se copian desde el Storage Access Framework. No se piden
permisos de acceso global al almacenamiento ni READ_MEDIA_AUDIO. Se deduplican por
huella de contenido. Las imágenes incrustadas se extraen y guardan por separado.
La importación no conserva una dependencia con el documento original. Eliminar
una canción hace cascade de las relaciones de playlists; borrar una playlist no
borra los archivos. Si la canción proviene de una playlist importada, eliminar el
audio conserva el título solicitado como pendiente. Importar CSV/JSON se realiza
en una transacción: un archivo inválido no deja playlists parcialmente importadas.

Los datos viven en almacenamiento privado. Vibe no añade cuentas, analítica,
telemetría, anuncios propios ni sincronización. La vista de YouTube carga el
servicio, sus cookies y sus anuncios. Los MP3 locales no utilizan esa vista. La desinstalación borra los datos.
La exportación CSV conserva los títulos de una playlist, incluidos los pendientes;
no es una copia de seguridad de los archivos de audio ni de toda la biblioteca.

La compatibilidad de formato depende de los decodificadores Android y Media3. AAC
con DRM y archivos dañados no se importan como audio utilizable. Android controla
el comportamiento exacto durante llamadas, Bluetooth y las políticas de batería
del fabricante; estas variantes necesitan pruebas en dispositivos físicos.


## Audio en línea y portadas (1.5)

SearchScreen muestra filas compactas bajo una cabecera fija y recuerda metadatos
recientes en DataStore. MusicViewModel entrega la selección al MediaSessionService
mediante un comando exclusivo de la app. OnlinePlayback administra una sola
selección con cancelación, error y plazo total; comparte ExoPlayer con la cola local.
La selección temporal alimenta playerTracks y el minirreproductor, sin insertar
archivos vacíos en Room. El servicio no persiste URLs ni IDs temporales.

YouTubeMusic.resolveAudio usa skip-download y selección de audio. YouTubeAudioStream
valida HTTPS, host de medios, códec y cabeceras permitidas. StreamResolver comparte
consultas en curso, precarga solo metadatos/URL del primer resultado y almacena
hasta 8 URLs en memoria, con caducidad. Usa 20 s de resolución y OnlinePlayback
25 s incluyendo búfer inicial; Media3 usa 750 ms de búfer para empezar. FFmpeg se
inicializa solo para convertir descargas. Los límites evitan esperas indefinidas;
no garantizan latencia ni disponibilidad del servicio de YouTube.

La vista web alternativa conserva su propia pausa por ciclo de vida. Su botón de
audio vuelve al reproductor principal. La clase de vista previa de 1.4 queda como
componente anterior; no es el flujo que usa Play en los resultados.

YouTubeArtwork guarda imágenes validadas mediante archivos temporales y renombrado.
El Worker intenta guardar la portada después de registrar el audio. El ViewModel
recupera las portadas faltantes de descargas antiguas al abrir la app con conexión.
MusicDao.updateArtwork solo cambia el campo existente: Room continúa en versión 3.
