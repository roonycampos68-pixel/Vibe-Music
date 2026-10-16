# Playlists de Spotify

En Vibe 1.1.0 puedes trasladar las listas de canciones que exportes desde tu
cuenta. No necesitas introducir tu contraseña ni un token de Spotify en Vibe.

## Desde el teléfono

1. Abre **Tu biblioteca → Importar playlist de Spotify**.
2. Pulsa **Exportar CSV con Exportify**. Es un servicio externo: revisa el acceso
   que autorices a Spotify, selecciona tu playlist y guarda el CSV en Descargas.
   Puedes revocar su acceso después desde los ajustes de tu cuenta de Spotify.
3. Vuelve a Vibe, pulsa **Seleccionar CSV o JSON** y elige el archivo.
4. En **Tu biblioteca → Playlists**, abre la lista importada. Verás cuántas
   canciones están disponibles y cuáles siguen pendientes.
5. Pulsa **Buscar y descargar coincidencias** para comprobar fuentes autorizadas.
   Solo se elige automáticamente una grabación si título y artista coinciden
   exactamente tras normalizar mayúsculas, acentos y puntuación, sin ambigüedad.
   Las versiones en directo y remixes no se consideran equivalentes.
6. Para cada pendiente puedes buscar audio manualmente o vincular un archivo
   que hayas importado en tu biblioteca. Las descargas en cola aparecen en Descargas.

También se acepta el JSON de playlists de los datos de cuenta que solicites en
[Spotify → Privacidad](https://www.spotify.com/account/privacy/). Si recibes un
ZIP, extrae y selecciona el JSON de playlists; no el historial de reproducción.
La solicitud a Spotify puede tardar varios días.

La exportación incluye títulos, artistas, álbumes y referencias de Spotify;
**no incluye archivos de audio**. Commons no contiene todas las grabaciones de
Spotify y las canciones comerciales sin una fuente autorizada seguirán pendientes.
Un enlace de Spotify por sí solo no importa la playlist. No se extrae audio,
no se convierten vistas previas en canciones y no se elude DRM.

## Formatos y persistencia

- CSV UTF-8 de Exportify, con `Track Name` y `Artist Name(s)`; opcionales
  `Album Name`, `Track URI` y `Playlist Name`. Acepta BOM, coma o punto y coma,
  campos entre comillas y encabezados en español `Título`/`Artista`.
- JSON de datos de cuenta (`playlists[].items[].track`) y JSON completo de
  playlist (`tracks.items[].track`). Rechaza respuestas paginadas incompletas.
- Máximo 8 MiB y 10000 canciones por archivo. Omite duplicados y filas sin
  título/artista, e informa los recuentos. Conserva el orden de las demás.
- Los títulos pendientes sobreviven al cierre de la app y a la eliminación del
  audio vinculado. Borrar una playlist no elimina los archivos de tu biblioteca.

El icono de exportar dentro de una playlist guarda un CSV con sus canciones y
pendientes. Puedes volver a importarlo en Vibe. Este archivo no copia el audio.
