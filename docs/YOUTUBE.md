# YouTube y MP3 en Vibe 1.2

En **Buscar**, la fuente inicial es **YouTube**. Escribe una canción/artista o
pega un enlace de un vídeo público. La búsqueda se ejecuta dentro de Vibe con
yt-dlp, incluye páginas de 20 resultados y permite cancelar consultas antiguas.

- **Reproducir** abre la grabación en YouTube (o el navegador), con el reproductor
  y la publicidad de YouTube. Si el MP3 ya está guardado, se reproduce en Vibe.
- **Descargar MP3** añade una tarea persistente a **Descargas**. El teléfono
  descarga el audio y FFmpeg lo convierte a MP3 VBR de alta calidad. No se usa
  una página de conversión ni un servidor intermedio.
- Al terminar, el MP3 aparece en la biblioteca y en Descargas. Se reproduce en
  Vibe sin conexión y sin publicidad. Transcodificar a MP3 no mejora la calidad
  original: se eligió este formato por la petición de esta entrega.
- Puedes pausar, reanudar o cancelar. Pausar conserva el original y los parciales;
  una conversión interrumpida se reinicia y no se publica como canción completa.
  Solo se ejecuta una conversión a la vez para contener el consumo de memoria.
- Se respetan modo sin conexión y Wi-Fi. Usa únicamente grabaciones que tengas
  derecho a guardar. No se proporcionan cuentas, cookies, credenciales, claves
  de DRM ni acceso a vídeos privados o de pago. Un vídeo restringido puede fallar.

Desde una playlist importada de Spotify, el botón de buscar de un pendiente
rellena título y artista en YouTube. Elige la grabación correcta y pulsa descargar;
al terminar se vincula a la posición original de la lista. La búsqueda automática
del botón **Buscar coincidencias en Descargables** conserva el comportamiento
anterior sobre fuentes con licencia declarada; no elige arbitrariamente un vídeo.

**Mi música** busca archivos ya guardados. **Descargables** conserva Commons, los
originales incluidos y el manifiesto opcional del usuario.

## Motor y mantenimiento

Se usan `youtubedl-android` y `ffmpeg` **0.18.1**, de Maven Central, con Python 3.12
y QuickJS empaquetados. El script de yt-dlp se sustituye por la distribución
oficial **2026.08.19**, incluidos los componentes EJS. Su SHA-256 se comprobó
contra `SHA2-256SUMS` de la publicación oficial:

```
1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6  yt-dlp
```

El recurso `app/src/main/res/raw/ytdlp` es el ZIP Python ejecutable oficial, con
su código fuente. Se vuelve a verificar al instalarlo en el almacenamiento de la
app. No hay actualizaciones de código remotas silenciosas: actualizar el motor
requiere actualizar ese recurso y su huella y publicar otro APK. Los cambios de
YouTube pueden romper la extracción; una compilación exitosa no prueba una
descarga en vivo. Consulta [VALIDATION.md](VALIDATION.md).

Los comandos se pasan como listas de argumentos, sin shell. Los enlaces de vídeo
se validan y normalizan a un ID de YouTube; no se envían rutas locales ni URLs
arbitrarias al descargador. La habilitación de URL `file:` solo existe en la
prueba instrumentada con audio original incluido.

El audio de YouTube no tiene una huella pública fiable comparable al manifiesto
del catálogo. Se usa TLS y SHA-256 local para deduplicación; no se afirma que la
huella local sea una verificación independiente del proveedor. La licencia del
audio permanece en manos de su titular y no se etiqueta como Creative Commons.

Para la nube, las búsquedas requieren permitir los dominios de YouTube, las
miniaturas y `*.googlevideo.com` en la configuración. El motor empaquetado no
necesita GitHub en el teléfono. Guardar el borrador de red no activa sus permisos.

## Instaladores

Las bibliotecas nativas aumentan el tamaño de instalación y necesitan espacio
adicional al prepararse por primera vez. Se generan APK por arquitectura:

- `arm64-v8a`: la mayoría de teléfonos actuales.
- `armeabi-v7a`: teléfonos Android de 32 bits.
- `x86_64`: pruebas en emulador.

Las tres variantes conservan `com.vibemusic`, la firma anterior y versionCode 3.
No desinstales la app para actualizar: Room migra versiones 1 y 2 a 3 sin borrar
tu biblioteca. Para ver arquitecturas soportadas usa
`adb shell getprop ro.product.cpu.abilist`.
