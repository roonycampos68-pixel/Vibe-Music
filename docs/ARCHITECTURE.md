# Arquitectura

- `ui/`: Compose y Material 3 oscuro, navegación de cuatro secciones, estados vacíos,
  búsqueda local, colecciones y reproductor. `MusicViewModel` expone StateFlow y
  conecta un MediaController al servicio. Las pantallas no crean reproductores.
- `model/`: entidades y reglas de búsqueda, duración y claves de descarga.
- `data/`: Room (canciones, playlists, relaciones ordenadas y trabajos de descarga),
  DataStore (preferencias y búsquedas recientes), MusicRepository (importación,
  metadatos y archivos) y CatalogRepository (fuentes autorizadas).
- `playback/`: un ExoPlayer alojado en MediaSessionService. Media3 publica la sesión,
  notificación, metadatos y controles del sistema. Se administra el foco de audio y
  la desconexión de auriculares. La cola y la posición se conservan cada dos segundos
  y en cambios del reproductor. Recuperar una sesión no reproduce automáticamente.
- `downloads/`: WorkManager, restricciones de red, archivos parciales, HTTP Range,
  progreso persistente y comprobación SHA-256 antes de incorporar la música a Room.

Los archivos importados se copian desde el Storage Access Framework. No se piden
permisos de acceso global al almacenamiento ni READ_MEDIA_AUDIO. Se deduplican por
huella de contenido. Las imágenes incrustadas se extraen y guardan por separado.
La importación no conserva una dependencia con el documento original. Eliminar
una canción hace cascade de las relaciones de playlists; borrar una playlist no
borra los archivos.

Los datos viven en almacenamiento privado. No hay cuenta, analítica, anuncios,
telemetría de la aplicación ni sincronización. La desinstalación borra los datos.
El proyecto no implementa copias de seguridad/exportación de la biblioteca.

La compatibilidad de formato depende de los decodificadores Android y Media3. AAC
con DRM y archivos dañados no se importan como audio utilizable. Android controla
el comportamiento exacto durante llamadas, Bluetooth y las políticas de batería
del fabricante; estas variantes necesitan pruebas en dispositivos físicos.
