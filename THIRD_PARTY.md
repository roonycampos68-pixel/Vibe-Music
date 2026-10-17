# Componentes de la versión 1.3

La aplicación combinada se distribuye bajo **GPL-3.0 o posterior** (`COPYING`).
El código original de Vibe Music 1.0/1.1 conserva MIT (`LICENSE-MIT`); el audio
original conserva CC0. El código nuevo y las instrucciones completas de compilación
se entregan en este repositorio junto al APK.

| Componente | Versión / fuente | Licencia |
| --- | --- | --- |
| youtubedl-android | `io.github.junkfood02.youtubedl-android:library:0.18.1` | GPL-3.0 |
| FFmpeg para Android | `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` | GPL-3.0, con dependencias de sus bibliotecas nativas |
| yt-dlp | [2026.08.19](https://github.com/yt-dlp/yt-dlp/tree/2026.08.19), recurso Python con código incluido | Unlicense y licencias de componentes incluidos |
| Python | 3.12, empaquetado por youtubedl-android | PSF |
| QuickJS | empaquetado por youtubedl-android | MIT |
| AndroidX / Media3 | versiones fijadas en Gradle | Apache-2.0 |
| OkHttp / Coil | versiones fijadas en Gradle | Apache-2.0 |
| Jackson | 2.18.3 | Apache-2.0 |

Fuentes y construcción del contenedor Android y de sus componentes nativos:
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android),
[BUILD_PYTHON.md](https://github.com/JunkFood02/youtubedl-android/blob/master/BUILD_PYTHON.md),
[BUILD_FFMPEG.md](https://github.com/JunkFood02/youtubedl-android/blob/master/BUILD_FFMPEG.md).
Maven Central publica los JAR de fuentes de las bibliotecas de la versión 0.18.1:

- [library-0.18.1-sources.jar](https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/library/0.18.1/library-0.18.1-sources.jar)
- [ffmpeg-0.18.1-sources.jar](https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/ffmpeg/0.18.1/ffmpeg-0.18.1-sources.jar)
- [common-0.18.1-sources.jar](https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/common/0.18.1/common-0.18.1-sources.jar)

Las bibliotecas nativas de FFmpeg y Python proceden de la distribución de esos
artefactos, sin modificación por Vibe. Sus fuentes y recetas de construcción
están documentadas por el proveedor anterior y en
[Termux packages](https://github.com/termux/termux-packages).
Al redistribuir, conserva los avisos y ofrece el código fuente correspondiente a
los destinatarios bajo sus respectivas licencias. Las licencias de software no
conceden derechos sobre las grabaciones que el usuario descargue.
