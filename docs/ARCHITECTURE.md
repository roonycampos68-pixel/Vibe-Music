# Arquitectura

- `ui/`: Compose y Material 3 oscuro, navegación de cuatro secciones, estados vacíos,
  búsqueda local y pública, colecciones y reproductor. `MusicViewModel` expone StateFlow y
  conecta un MediaController al servicio. Las pantallas no crean reproductores.
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

Los datos viven en almacenamiento privado. No hay cuenta, analítica, anuncios,
telemetría de la aplicación ni sincronización. La desinstalación borra los datos.
La exportación CSV conserva los títulos de una playlist, incluidos los pendientes;
no es una copia de seguridad de los archivos de audio ni de toda la biblioteca.

La compatibilidad de formato depende de los decodificadores Android y Media3. AAC
con DRM y archivos dañados no se importan como audio utilizable. Android controla
el comportamiento exacto durante llamadas, Bluetooth y las políticas de batería
del fabricante; estas variantes necesitan pruebas en dispositivos físicos.
