# Instalador Android

`Vibe-Music-1.3.1-arm64-v8a-debug.apk` es el instalador para la mayoría de teléfonos
actuales. Para Android de 32 bits usa `Vibe-Music-1.3.1-armeabi-v7a-debug.apk`.
Son APK reales compilados desde este repositorio, firmados con la misma clave de
depuración de las versiones anteriores. Android 8.0 o posterior.

Actualiza las versiones 1.0, 1.1, 1.2 y 1.3 sin desinstalar. La versión nueva tiene versionCode
5 y Room 3 migra los datos anteriores. Los APK anteriores se conservan como
histórico; Android no permite volver a ellos como actualización normal.

**[Descargar ARM64 con acceso de GitHub](https://github.com/login?return_to=%2Froonycampos68-pixel%2FVibe-Music%2Fblob%2Fmain%2Freleases%2FVibe-Music-1.3.1-arm64-v8a-debug.apk%3Fraw%3Dtrue)** ·
[ARM de 32 bits](https://github.com/login?return_to=%2Froonycampos68-pixel%2FVibe-Music%2Fblob%2Fmain%2Freleases%2FVibe-Music-1.3.1-armeabi-v7a-debug.apk%3Fraw%3Dtrue).

Inicia sesión con la cuenta que tiene acceso al repositorio. Si GitHub muestra
la ficha del archivo, pulsa **Download raw file** y ábrelo en el teléfono.
No compartas enlaces de raw.githubusercontent.com como descargas públicas: un
archivo privado requiere el acceso de tu cuenta.
Si aparece 404, inicia sesión en GitHub con acceso a este repositorio y vuelve
a abrir el archivo. El código y los recursos predeterminados de la app no
dependen del acceso a este repositorio para buscar o guardar música.
El hash de integridad está en `SHA256SUMS`. No es una firma de producción ni una
publicación en Google Play. Los APK generados por otros equipos/CI tendrán otra
clave de depuración y pueden requerir desinstalar la versión anterior para instalar;
la desinstalación elimina los datos privados de la app. No desinstales tu copia
para esta actualización: usa un APK 1.3.1 de esta carpeta que coincida con tu ABI.

Cambios 1.3.1: botón fijo en la vista previa, selección automática sin
portapapeles, reintentos ante cortes temporales de conexión y reconocimiento de
MP3 ya guardados por deduplicación.

Cambios 1.3: miniaturas y vista previa de YouTube dentro de Vibe, búsqueda web
interna como alternativa y botón de MP3 en la misma pantalla. Se conserva la
descarga y conversión local con yt-dlp/FFmpeg, progreso y cancelación. El MP3
guardado se reproduce sin red ni anuncios.
Se conserva la biblioteca local, las playlists de Spotify y las otras fuentes.
Consulta [la validación y sus límites](../docs/VALIDATION-1.3.1.md) y
[la guía del conversor](../docs/YOUTUBE.md).
El tamaño aumentó porque se empaquetan Python, QuickJS y FFmpeg. El motor también
necesita espacio libre al prepararse por primera vez. La aplicación combinada se
distribuye bajo GPL-3.0 o posterior; código y avisos están incluidos en el repositorio.
