# Vibe Music · Android

Reproductor nativo de música para **Android 8.0 o posterior**, construido con Kotlin
y Jetpack Compose. Interfaz oscura en español, biblioteca privada sin suscripción
y reproducción real con Media3.

## Instalar en el teléfono

El APK de esta entrega se encuentra en
[`releases/Vibe-Music-1.0.0-debug.apk`](releases/Vibe-Music-1.0.0-debug.apk?raw=true).
Descárgalo en tu Android, abre el archivo y permite instalar aplicaciones desde el
navegador/gestor de archivos cuando Android lo solicite. Es una compilación de
pruebas firmada con una clave de depuración, no una publicación de Google Play.

También puedes instalar el APK compilado con depuración USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Al abrir, elige **Importar mi música** y selecciona MP3, M4A, AAC, FLAC, WAV u OGG.
Los códecs disponibles dependen del dispositivo. Cada archivo se copia al espacio
privado de la app: no necesitas conservar permisos de acceso al documento original.
También puedes añadir **Vibe Originals**, tres composiciones ambientales originales
incluidas bajo CC0. Después, toda esa música funciona sin internet.

## Funciones

- Inicio con filtros, colecciones recientes, favoritas y álbumes guardados.
- Buscar instantáneamente canciones, artistas, álbumes y playlists locales.
- Biblioteca con favoritos y playlists persistentes: crear, renombrar, cambiar
  portada, añadir/quitar y reordenar canciones.
- Minirreproductor y reproductor completo con progreso interactivo, cola,
  anterior/siguiente, aleatorio, repetición, volumen y temporizador.
- MediaSessionService con reproducción en segundo plano, notificación y controles
  multimedia de Android. Foco de audio y pausa al desconectar auriculares.
- Recuperación de cola y posición; la reproducción se reanuda por decisión del usuario.
- Descargas persistentes desde catálogos autorizados: progreso, pausa/reanudación,
  cancelación, deduplicación, comprobación SHA-256 y eliminación individual.
- Preferencias de Wi-Fi, calidad disponible en la fuente y modo sin conexión.
- Licencias y atribuciones conservadas en el dispositivo.

Las fuentes externas se configuran mediante un manifiesto HTTPS. No hace falta
ninguna API para la música local. Consulta [el formato y ejemplo real del catálogo](docs/CATALOG.md).
No se descargan canciones de Spotify ni YouTube. La app tiene marca, icono,
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

APK: `app/build/outputs/apk/debug/app-debug.apk`.
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

- [Validación de esta entrega y limitaciones comprobadas](docs/VALIDATION.md)
- [Arquitectura y persistencia](docs/ARCHITECTURE.md)
- [Catálogos legales y descargas](docs/CATALOG.md)
- [Requisitos originales](docs/PRODUCT_REQUIREMENTS.md)

Código MIT. Audio original incluido: CC0-1.0. Las dependencias mantienen sus licencias.
