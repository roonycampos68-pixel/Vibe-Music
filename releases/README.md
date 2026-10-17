# Instalador Android

`Vibe-Music-1.3.0-arm64-v8a-debug.apk` es el instalador para la mayoría de teléfonos
actuales. Para Android de 32 bits usa `Vibe-Music-1.3.0-armeabi-v7a-debug.apk`.
Son APK reales compilados desde este repositorio, firmados con la misma clave de
depuración de las versiones anteriores. Android 8.0 o posterior.

Actualiza las versiones 1.0, 1.1 y 1.2 sin desinstalar. La versión nueva tiene versionCode
4 y Room 3 migra los datos anteriores. Los APK anteriores se conservan como
histórico; Android no permite volver a ellos como actualización normal.

Descarga el archivo usando **Download raw file** en GitHub y ábrelo en el teléfono.
Si aparece 404, inicia sesión en GitHub con acceso a este repositorio y vuelve
a abrir el archivo. El código y los recursos predeterminados de la app no
dependen del acceso a este repositorio para buscar o guardar música.
El hash de integridad está en `SHA256SUMS`. No es una firma de producción ni una
publicación en Google Play. Los APK generados por otros equipos/CI tendrán otra
clave de depuración y pueden requerir desinstalar la versión anterior para instalar;
la desinstalación elimina los datos privados de la app. No desinstales tu copia
para esta actualización: usa un APK 1.3.0 de esta carpeta que coincida con tu ABI.

Cambios 1.3: miniaturas y vista previa de YouTube dentro de Vibe, búsqueda web
interna como alternativa y botón de MP3 en la misma pantalla. Se conserva la
descarga y conversión local con yt-dlp/FFmpeg, progreso y cancelación. El MP3
guardado se reproduce sin red ni anuncios.
Se conserva la biblioteca local, las playlists de Spotify y las otras fuentes.
Consulta [la validación y sus límites](../docs/VALIDATION-1.3.md) y
[la guía del conversor](../docs/YOUTUBE.md).
El tamaño aumentó porque se empaquetan Python, QuickJS y FFmpeg. El motor también
necesita espacio libre al prepararse por primera vez. La aplicación combinada se
distribuye bajo GPL-3.0 o posterior; código y avisos están incluidos en el repositorio.
