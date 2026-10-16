# Fuentes autorizadas

Vibe Music reproduce archivos locales y permite guardar audio con licencia de
descarga compatible. La biblioteca no necesita cuenta, claves de API o internet.
La importación de playlists de Spotify traslada metadatos; véase [SPOTIFY.md](SPOTIFY.md).

En **Buscar → Todo** o **En línea**, escribe un título o artista. El buscador
consulta la API pública de Wikimedia Commons, con resultados paginados. Solo
presenta audio de `upload.wikimedia.org` con licencia reconocida, tamaño y SHA-1
publicados por la API. Conserva autor, fuente y licencia; puedes abrir la página
original antes de guardar. El servicio no equivale al catálogo comercial de Spotify.

También puedes añadir un manifiesto JSON HTTPS en **Ajustes → Fuente adicional
(opcional)**. No sustituye las fuentes incluidas. Cada calidad disponible se muestra
antes de descargar. El audio original incluido funciona incluso sin internet.

El responsable del catálogo debe tener permiso para distribuir cada grabación.
Una etiqueta de licencia es una declaración del proveedor; la aplicación no puede
probar por sí sola que el proveedor sea titular. Verifica la fuente y sus permisos.
No se incluye un proveedor comercial ni credenciales inventadas.

## Formato versión 1

```json
{
  "version": 1,
  "tracks": [
    {
      "id": "mi-composicion-1",
      "title": "Mi composición",
      "artist": "Su autor",
      "album": "Mi álbum",
      "downloadAuthorized": true,
      "license": "CC-BY-4.0",
      "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
      "attribution": "Su autor — Mi composición, CC BY 4.0; fuente: https://tu-dominio.example/obra",
      "qualities": [
        {
          "label": "Original",
          "url": "https://tu-dominio.example/audio/obra.mp3",
          "sha256": "REEMPLAZAR_POR_EL_SHA256_REAL_DE_64_CARACTERES",
          "sizeBytes": 1234567
        }
      ]
    }
  ]
}
```

Este ejemplo documenta el esquema; la URL y huella se deben reemplazar antes de
usarlo. Calcula la huella con `sha256sum obra.mp3`. Todos los archivos deben tener
una licencia admitida: `CC0-1.0`, `CC-BY-4.0`, `CC-BY-SA-4.0`,
`CC-BY-3.0`, `CC-BY-SA-3.0`, `CC-BY-2.0`, `CC-BY-SA-2.0` o `PDM-1.0`. Se exige
atribución, autorización explícita y URL HTTPS de licencia. Se rechazan catálogos
de más de 2 MiB o 1000 canciones y URLs de audio sin HTTPS. Las URLs deben devolver
el contenido directamente (HTTP 200), sin redirecciones ni páginas HTML. Nunca
introduzcas secretos en la URL pública.

El audio se guarda en almacenamiento interno permanente, no en caché. La huella
SHA-256 del manifiesto se valida antes de registrar el archivo como reproducible.
Para Commons se valida el SHA-1 autorizado de su API y se calcula además SHA-256
para deduplicación; no se omite la comprobación de integridad. La metainformación
y la atribución se conservan en Room y en **Ajustes → Licencias y atribuciones**.
La app no transcodifica archivos ni inventa calidades que la fuente no ofrece.

## Pausa y reanudación

WorkManager conserva las tareas y Room conserva progreso y estado. El archivo
parcial se guarda en `files/pending`. Con un ETag fuerte se envían `Range` e
`If-Range`. Si el servidor devuelve 206 con el intervalo esperado, se continúa;
si devuelve 200 se vuelve a descargar de forma segura. Sin ETag fuerte se reinicia
el archivo. Cancelar borra el parcial; eliminar la canción borra el archivo final.
Los errores de conexión se reintentan hasta tres veces con la política de espera
de WorkManager. Los fallos definitivos se pueden reintentar desde Descargas.

La opción Wi-Fi exige transporte Wi-Fi, aunque esa red se declare medida. En modo sin conexión
no se inicia ninguna descarga y se pausan las que estén en curso. La preferencia
Wi-Fi se consulta nuevamente al ejecutar la tarea. Las playlists locales ya
contienen archivos completos sin conexión. Las playlists importadas pueden
contener metadatos pendientes; su búsqueda por lotes solo añade coincidencias
autorizadas únicas y conserva los demás títulos para resolverlos manualmente.

## Proveedor con API

Para integrar un servicio de terceros, implementa un adaptador junto a
`CatalogRepository` que produzca estos mismos modelos y preserve las licencias y
la autorización de descarga. Si requiere una clave secreta, usa un backend propio;
no la incluyas en el APK. Commons y los manifiestos públicos no necesitan claves.

## Catálogo de Vibe Originals

`app/src/main/assets/catalog/originals.json` y los tres WAV originales se empaquetan
en el APK bajo CC0. Guardarlos desde Buscar importa los bytes incluidos y no
solicita GitHub. Sus huellas se comprueban en las pruebas. La preferencia antigua
que apuntaba a este repositorio se migra a las fuentes nuevas; una URL personalizada
del usuario se conserva.

`catalog/vibe-originals.json` permanece como ejemplo de manifiesto para alojar en
un servidor propio. Sus URLs antiguas dependen del acceso al repositorio y no
son la fuente predeterminada de la versión 1.1.0. No se incluyen tokens de GitHub
en el APK. La deduplicación evita guardar varias copias del mismo audio.
