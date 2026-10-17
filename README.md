# Vibe Music · Android

Reproductor nativo de música para **Android 8.0 o posterior**, construido con Kotlin
y Jetpack Compose. Interfaz oscura en español, biblioteca privada sin suscripción
y reproducción real con Media3.

## Instalar en el teléfono

**[Descargar APK para Android (ARM64)](https://github.com/login?return_to=%2Froonycampos68-pixel%2FVibe-Music%2Fblob%2Fmain%2Freleases%2FVibe-Music-1.5.0-arm64-v8a-debug.apk%3Fraw%3Dtrue)**.
Para teléfonos de 32 bits: [descargar ARMv7](https://github.com/login?return_to=%2Froonycampos68-pixel%2FVibe-Music%2Fblob%2Fmain%2Freleases%2FVibe-Music-1.5.0-armeabi-v7a-debug.apk%3Fraw%3Dtrue).
Estos enlaces pasan por el inicio de sesión de GitHub y vuelven al instalador;
usa la cuenta con acceso a este repositorio. Si ya tienes sesión, abre
[el archivo del APK](releases/Vibe-Music-1.5.0-arm64-v8a-debug.apk) y pulsa
**Download raw file**.
Descárgalo en tu Android, abre el archivo y permite instalar aplicaciones desde el
navegador/gestor de archivos cuando Android lo solicite. Es una compilación de
pruebas firmada con una clave de depuración, no una publicación de Google Play.
La versión 1.5.0 actualiza las versiones anteriores conservando la biblioteca; no la desinstales.
Si GitHub muestra 404, inicia sesión con una cuenta que tenga acceso al repositorio
en el mismo navegador. Un enlace directo de raw.githubusercontent.com no reutiliza
la sesión de github.com para archivos privados. La app no necesita acceso a GitHub para buscar música.

También puedes instalar el APK compilado con depuración USB:

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Al abrir, elige **Importar mi música** y selecciona MP3, M4A, AAC, FLAC, WAV u OGG.
Los códecs disponibles dependen del dispositivo. Cada archivo se copia al espacio
privado de la app: no necesitas conservar permisos de acceso al documento original.
También puedes añadir **Vibe Originals**, tres composiciones ambientales originales
incluidas bajo CC0. Después, toda esa música funciona sin internet.

## Funciones

- Inicio con filtros, colecciones recientes, favoritas y álbumes guardados.
- Buscar en **YouTube** (opción inicial), **Mi música** o **Descargables**.
  YouTube muestra resultados dentro de Vibe por título/artista o enlace del vídeo.
- Descargar audio de vídeos públicos y convertirlo a MP3 en el teléfono mediante
  yt-dlp y FFmpeg. Progreso, pausa, reanudación, cancelación y vinculación a
  playlists; los MP3 completos se reproducen sin internet y sin anuncios.
- Resultados compactos y desplazables debajo del buscador, con canciones recientes.
  Tocar una canción inicia **audio en el reproductor principal**, con portada,
  progreso y descarga. No abre un diálogo; conserva controles y segundo plano.
- Consulta anticipada del primer resultado y caché de enlaces temporales para
  reducir la espera. Play no descarga ni convierte el MP3 antes de reproducir.
  La primera conexión depende de YouTube: hay cancelación y reintento, sin
  prometer inicio instantáneo.
- Portadas de YouTube guardadas para las canciones descargadas, incluidas las
  anteriores cuando vuelves a abrir Vibe con conexión. Reproductor con fondo opaco
  y texto blanco. La página de YouTube sigue disponible como alternativa interna.
- Importar playlists de Spotify mediante CSV de Exportify o JSON de los datos
  de tu cuenta. Conserva el orden y los títulos pendientes; vincula archivos
  locales y permite buscar coincidencias autorizadas. Exporta playlists a CSV.
- Biblioteca con favoritos y playlists persistentes: crear, renombrar, cambiar
  portada, añadir/quitar y reordenar canciones.
- Minirreproductor y reproductor completo con progreso interactivo, cola,
  anterior/siguiente, aleatorio, repetición, volumen y temporizador.
- MediaSessionService con reproducción en segundo plano, notificación y controles
  multimedia de Android. Foco de audio y pausa al desconectar auriculares.
- Recuperación de cola y posición; la reproducción se reanuda por decisión del usuario.
- Descargas persistentes desde catálogos autorizados: progreso, pausa/reanudación,
  cancelación, deduplicación, comprobación de integridad y eliminación individual.
- Preferencias de Wi-Fi, calidad disponible en la fuente y modo sin conexión.
- Licencias y atribuciones conservadas en el dispositivo.

Se incluyen tres Vibe Originals en el APK, disponibles desde Buscar incluso sin red.
La pestaña Descargables conserva Wikimedia Commons sin claves de API; puedes añadir otra
fuente autorizada mediante un manifiesto HTTPS. Consulta [las fuentes y las descargas](docs/CATALOG.md)
y [YouTube, MP3 y mantenimiento del motor](docs/YOUTUBE.md).
Commons no contiene todo el catálogo comercial de Spotify. La importación de Spotify
traslada títulos y artistas, no sus archivos de audio; consulta [la guía de importación](docs/SPOTIFY.md).
La app tiene marca, icono,
composiciones y portadas generativas propios.

## Abrir en Android Studio

1. Clona este repositorio y abre su carpeta en Android Studio Ladybug o posterior.
2. Usa JDK 17 o 21, instala **Android SDK Platform 35** y **Build Tools 35.0.0**.
3. Deja que Android Studio cree `local.properties` y sincronice Gradle.
4. Selecciona un teléfono/emulador y ejecuta el módulo `app`.

Se incluyen Gradle Wrapper 8.10.2 con verificación SHA-256, AGP 8.8.2 y Kotlin 2.1.10.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest   # requiere dispositivo/emulador
```

APK por arquitectura: `app/build/outputs/apk/debug/app-<ABI>-debug.apk`.
Para emuladores x86_64 elige `app-x86_64-debug.apk`. Las bibliotecas del conversor
se empaquetan por ABI para reducir la descarga. La primera búsqueda prepara el
motor en segundo plano y necesita espacio adicional en el dispositivo.
Para distribución comercial configura una clave de firma propia y una compilación
release. No añadas claves de firma ni secretos al repositorio.

## Entorno de nube

En Linux x86_64 con Python 3.12+, Java y acceso HTTPS a los repositorios oficiales:

```bash
bash scripts/setup-cloud.sh
source /workspace/.vibe-tools/env.sh
```

El script instala herramientas fuera del checkout, verifica los archivos descargados,
acepta las licencias del SDK para instalar los paquetes solicitados y ejecuta las
comprobaciones. No modifica dependencias ni código de la aplicación. Es repetible
y utiliza el almacén de confianza Java del sistema cuando está disponible. Las
claves/proxy del entorno no se guardan en este repositorio.

La tarea de nube ya está aislada: utiliza este checkout, sin crear un Git worktree.
No hace falta mantener un servidor web. Para ejecutar la aplicación se necesita
Android Studio, un teléfono o un emulador. Las instrucciones detalladas de pruebas
están en [docs/DEVICE_TESTS.md](docs/DEVICE_TESTS.md).

## Documentación

- [Validación de esta entrega y limitaciones comprobadas](docs/VALIDATION-1.5.md)
- [Arquitectura y persistencia](docs/ARCHITECTURE.md)
- [Catálogos legales y descargas](docs/CATALOG.md)
- [Importar y exportar playlists de Spotify](docs/SPOTIFY.md)
- [YouTube y conversión a MP3](docs/YOUTUBE.md)
- [Licencias de los componentes](THIRD_PARTY.md)
- [Requisitos originales](docs/PRODUCT_REQUIREMENTS.md)

Aplicación combinada 1.3: GPL-3.0 o posterior (`COPYING`). Código original 1.0/1.1:
MIT (`LICENSE-MIT`). Audio original: CC0-1.0. Las dependencias mantienen sus licencias.
