# Fuentes autorizadas

Vibe Music reproduce archivos locales; no contiene una integración con Spotify ni
YouTube ni descarga audio de esas plataformas. La biblioteca no necesita cuenta,
claves de API o internet.

El conector de catálogo lee un manifiesto JSON público por HTTPS. En **Ajustes →
Fuente de música autorizada**, introduce su URL. En **Buscar → Catálogo legal**
puedes descargar canciones o álbumes completos. Cada calidad se muestra antes de
descargar; la selección preferida para álbumes se configura en Ajustes.

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
una licencia admitida: `CC0-1.0`, `CC-BY-4.0`, `CC-BY-SA-4.0` o `PDM-1.0`. Se exige
atribución, autorización explícita y URL HTTPS de licencia. Se rechazan catálogos
de más de 2 MiB o 1000 canciones y URLs de audio sin HTTPS. Las URLs deben devolver
el contenido directamente (HTTP 200), sin redirecciones ni páginas HTML. Nunca
introduzcas secretos en la URL pública.

El audio se guarda en almacenamiento interno permanente, no en caché. La huella
SHA-256 se valida antes de registrar el archivo como reproducible. La metainformación
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

La opción Wi-Fi exige transporte Wi-Fi y una red no medida. En modo sin conexión
no se inicia ninguna descarga y se pausan las que estén en curso. La preferencia
Wi-Fi se aplica al reanudar las tareas. Los álbumes se descargan como archivos
individuales autorizados; las playlists locales ya contienen archivos completos
sin conexión y no necesitan descargarse otra vez.

## Proveedor con API

Para integrar un servicio de terceros, implementa un adaptador junto a
`CatalogRepository` que produzca estos mismos modelos y preserve las licencias y
la autorización de descarga. Si requiere una clave secreta, usa un backend propio;
no la incluyas en el APK. El conector actual utiliza manifiestos públicos sin claves.

## Catálogo de Vibe Originals

`catalog/vibe-originals.json` contiene tres archivos originales del propio proyecto,
con huellas reales y licencia CC0. Si el repositorio en GitHub es público, se puede
usar esta URL para probar descargas:

```
https://raw.githubusercontent.com/roonycampos68-pixel/Vibe-Music/main/catalog/vibe-originals.json
```

Si el repositorio es privado, publica únicamente estos recursos CC0 en un servidor
HTTPS propio y actualiza las URLs. La app no contiene ni debe contener tokens de
GitHub. El catálogo no se activa automáticamente porque la visibilidad pública del
repositorio no se presupone. Para probar la descarga, usa una instalación limpia o
elimina antes el archivo original correspondiente: la deduplicación evita descargar
por segunda vez audio que ya está guardado.
