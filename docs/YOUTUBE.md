# YouTube y MP3 en Vibe 1.5

En **Buscar**, la fuente inicial es **YouTube**. Escribe una canción/artista o
pega un enlace de un vídeo público. La búsqueda se ejecuta dentro de Vibe con
yt-dlp, incluye páginas de 20 resultados y permite cancelar consultas antiguas.

- Los resultados aparecen como filas compactas bajo el buscador fijo. La lista
  se desplaza y muestra más páginas; sin texto aparecen las últimas 12 canciones
  seleccionadas, con título, artista y portada.
- Tocar una canción o **Reproducir** usa el mismo Media3/ExoPlayer del servicio
  que los archivos descargados. No abre una vista aparte: el minirreproductor
  muestra portada, duración, progreso, pausa y descarga. Solo al tocar esa barra
  se abre el reproductor completo. Continúa en segundo plano con controles Android.
- Play resuelve una URL temporal (audio directo o HLS); no descarga ni convierte
  un MP3. Se adelanta la resolución del primer resultado cuando no hay selección
  online y se reutilizan hasta 8 enlaces, durante un máximo de 5 minutos o hasta
  un minuto antes de su caducidad. No se guardan esos enlaces en disco.
- El primer inicio depende de YouTube/red. Se limitan resolución a 20 segundos
  y resolución + búfer a 25; Play no hereda los reintentos largos de descarga.
  La X cancela la conexión; después de un error, Play obtiene un enlace nuevo.
  Cambiar de canción cancela el trabajo anterior. No se garantiza inicio instantáneo.
- Si el MP3 ya existe se utiliza el archivo local. Escuchar en línea no añade una
  canción a la biblioteca ni sustituye la última cola local guardada.
- La página de YouTube permanece como alternativa si no hay resultados nativos.
  Allí **Escuchar solo el audio** cierra la página y usa el reproductor principal.
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
descarga en vivo. Consulta [VALIDATION-1.5.md](VALIDATION-1.5.md).

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

Las tres variantes conservan `com.vibemusic`, la firma anterior y versionCode 7.
No desinstales la app para actualizar: Room migra versiones 1 y 2 a 3 sin borrar
tu biblioteca. Para ver arquitecturas soportadas usa
`adb shell getprop ro.product.cpu.abilist`.

## Portadas, vista web y MP3Cow

Las miniaturas oficiales de `i.ytimg.com` se guardan en el directorio privado de
portadas. Se validan tamaño (hasta 2 MB), dimensiones y decodificación antes de
renombrar el archivo temporal. Una portada fallida no impide guardar ni escuchar
el audio. Vibe también recupera portadas de canciones de YouTube descargadas antes
de 1.4 al abrir la app con conexión; puede reintentarse volviendo a abrirla.
Una vez guardadas, funcionan sin red en biblioteca, minirreproductor y reproductor.

El reproductor local usa un fondo completamente opaco y texto blanco. Los colores
intermedios del degradado se mezclan con el fondo, sin bajar su opacidad.

La página alternativa conserva un WebView con HTTPS y sin puente JavaScript a
Android, acceso a archivos locales o intents de otras apps. El código del iframe
anterior permanece disponible, pero Play desde los resultados ya usa audio nativo.
Las restricciones de YouTube y los cambios del servicio siguen pudiendo impedir
resolver o reproducir algunos audios; compilar no prueba el acceso a todos ellos.

El usuario propuso `https://mp3cow.com/?v=7Y-xGsEPnsU` como conversor detrás del
botón. **MP3Cow no está integrado ni automatizado en esta entrega.** La política
activa de la nube no permite consultar ese dominio; no se inspeccionó su web ni
se verificó una API o un flujo de descarga. Los dominios ya constan en la versión publicada del entorno. Al comprobar 1.4, el estado del entorno
ya informa de los dominios permitidos, pero las peticiones reales de esta nube
a YouTube y a las miniaturas siguen recibiendo CONNECT 403. El usuario confirmó
que buscar y descargar sí funciona en su teléfono; no se presenta eso como una
prueba de streaming ejecutada en esta nube. No se
presenta una página precargada como si fuera una conversión automática comprobada.
El botón usa el conversor local documentado arriba, sin copiar al portapapeles ni
enviar enlaces a MP3Cow. En 1.3.1 los errores temporales de red del proceso nativo
se reintentan con WorkManager hasta tres veces; los vídeos restringidos, los límites
de solicitudes y la falta de espacio se explican sin un bucle automático.
