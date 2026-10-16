# Instalador Android

`Vibe-Music-1.1.0-debug.apk` es el APK real compilado desde el código de este
repositorio, firmado con clave de depuración. Android 8.0 o posterior.

Actualiza la versión 1.0.0 sin desinstalar: ambos APK de esta carpeta usan el
mismo certificado y la versión nueva tiene versionCode 2. La migración de Room
conserva la biblioteca. La versión 1.0.0 se conserva como archivo histórico;
Android no permite volver a ella como actualización normal.

Descarga el archivo usando **Download raw file** en GitHub y ábrelo en el teléfono.
Si aparece 404, inicia sesión en GitHub con acceso a este repositorio y vuelve
a abrir el archivo. El código y los recursos predeterminados de la app no
dependen del acceso a este repositorio para buscar o guardar música.
El hash de integridad está en `SHA256SUMS`. No es una firma de producción ni una
publicación en Google Play. Los APK generados por otros equipos/CI tendrán otra
clave de depuración y pueden requerir desinstalar la versión anterior para instalar;
la desinstalación elimina los datos privados de la app. No desinstales tu copia
para esta actualización: usa el APK 1.1.0 de esta carpeta.

Cambios: búsqueda local y pública, audio incluido disponible sin GitHub,
importación CSV/JSON de playlists de Spotify, títulos pendientes y exportación
CSV. Correcciones en cancelación, descargas duplicadas, integridad y persistencia.
Consulta [la validación y sus límites](../docs/VALIDATION.md) y
[la guía de Spotify](../docs/SPOTIFY.md).
