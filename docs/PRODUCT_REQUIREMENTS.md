# PROMPT MAESTRO — CREAR VIBE MUSIC PARA ANDROID

Actúa como un equipo profesional compuesto por un desarrollador senior Android, un diseñador UI/UX especializado en aplicaciones musicales, un arquitecto de software y un ingeniero de calidad.

Quiero que desarrolles una aplicación Android real, completa, moderna e instalable, llamada **Vibe Music**, cuyo diseño visual, navegación y experiencia de usuario sean extremadamente similares a Spotify Premium, pero con identidad propia y sin copiar sus logotipos ni recursos gráficos protegidos.

La prioridad es que funcione como una aplicación musical premium, con reproducción local y sin conexión a internet, sin exigir suscripción.

## 1. DISEÑO VISUAL: ESTILO SPOTIFY PREMIUM

Recrear una experiencia visual muy cercana a la aplicación móvil de Spotify.

**Paleta de colores:**
- Fondo principal: negro profundo #121212.
- Fondo secundario: gris oscuro #181818.
- Tarjetas: gris carbón #282828.
- Color de acento: verde #1DB954.
- Textos principales: blanco #FFFFFF.
- Textos secundarios: gris claro #B3B3B3.

**Estilo general:**
- Interfaz minimalista, elegante y profesional.
- Diseño oscuro por defecto.
- Bordes redondeados en tarjetas y botones.
- Portadas de álbumes grandes y nítidas.
- Transiciones fluidas entre pantallas.
- Animaciones suaves al reproducir música.
- Efectos de degradado utilizando los colores de la portada del álbum.
- Iconografía moderna y uniforme.
- Tipografía muy parecida al estilo visual de Spotify.
- Diseño optimizado para teléfonos Android.
- Distribución y navegación visualmente próximas a Spotify Premium, con una marca independiente.

## 2. PANTALLA DE INICIO

Crear una pantalla principal inspirada en la página de inicio de Spotify.

En la parte superior:
- Saludo personalizado según la hora.
- Botones de filtrado: Todo, Música, Playlists y Descargas.
- Acceso a configuración y perfil.

En el contenido:
- Cuadrícula de playlists escuchadas recientemente.
- Sección "Escuchado recientemente".
- Sección "Tus canciones favoritas".
- Sección "Continúa escuchando".
- Sección "Álbumes guardados".
- Sección "Música descargada".
- Recomendaciones obtenidas únicamente de catálogos musicales autorizados.

Todas las tarjetas deben ser interactivas y permitir abrir álbumes, playlists o reproducir música.

## 3. BARRA DE NAVEGACIÓN INFERIOR

Implementar una barra inferior fija con cuatro secciones:

1. Inicio.
2. Buscar.
3. Tu biblioteca.
4. Descargas.

Utilizar íconos claros, etiquetas en español y animaciones sutiles al cambiar de sección.

Encima de esta barra debe aparecer un minirreproductor cuando exista una canción activa.

## 4. REPRODUCTOR MUSICAL PREMIUM

Crear un reproductor de pantalla completa parecido al de Spotify.

Debe contener:
- Portada del álbum centrada y de gran tamaño.
- Nombre de la canción.
- Nombre del artista.
- Botón para añadir a favoritos.
- Barra de progreso interactiva.
- Tiempo transcurrido y duración total.
- Botón para reproducir o pausar.
- Botones para canción anterior y siguiente.
- Reproducción aleatoria.
- Repetición de canción o playlist.
- Lista de reproducción actual.
- Control de volumen.
- Acceso rápido a la playlist.
- Opción para descargar la canción cuando esté autorizada.
- Indicador visual cuando una canción esté disponible sin conexión.

El fondo debe utilizar un degradado dinámico basado en los colores de la portada.

**Comportamiento obligatorio:**
- La música continúa reproduciéndose al apagar la pantalla.
- La música continúa cuando el usuario cambia de aplicación.
- Control desde pantalla de bloqueo.
- Notificación con botones de reproducción y pausa.
- Compatibilidad con audífonos Bluetooth.
- Pausa automática ante llamadas o pérdida de foco de audio.
- Recuperación de la canción y posición de reproducción al regresar.

## 5. DESCARGAS Y REPRODUCCIÓN SIN INTERNET

ESTA ES LA FUNCIÓN MÁS IMPORTANTE DE LA APLICACIÓN.

Permitir que el usuario descargue archivos musicales únicamente desde fuentes que ofrezcan autorización legal para hacerlo.

También permitir importar música propia del almacenamiento del celular.

**Funciones:**
- Descargar canciones autorizadas con un botón.
- Mostrar progreso de descarga.
- Pausar, reanudar o cancelar descargas cuando el servidor lo permita.
- Guardar la música de forma persistente en el dispositivo.
- Reproducir canciones descargadas sin Wi-Fi ni datos móviles.
- Conservar canciones descargadas después de cerrar o reiniciar la aplicación.
- Permitir descargar álbumes y playlists cuando todos sus archivos estén autorizados.
- Evitar descargas duplicadas.
- Mostrar tamaño de los archivos.
- Eliminar descargas individualmente.
- Consultar espacio utilizado.
- Elegir calidad de descarga cuando existan distintas calidades disponibles.
- Activar opción "Descargar solo con Wi-Fi".
- Crear una biblioteca exclusiva de canciones disponibles sin internet.

**Regla fundamental:** El modo sin conexión debe funcionar realmente. No depender de streaming, caché temporal o una URL que necesite internet.

No descargar canciones protegidas de Spotify, YouTube Music ni otros servicios sin autorización. No utilizar extracción ilegal, elusión de DRM ni APIs privadas. Integrar fuentes legales con licencias verificables y, si no existen credenciales, dejar preparado el sistema de integración.

## 6. BUSCADOR MUSICAL

Crear un buscador moderno inspirado en Spotify.

Incluir:
- Barra de búsqueda superior.
- Búsquedas recientes.
- Resultados por canción.
- Resultados por artista.
- Resultados por álbum.
- Resultados por playlist.
- Filtros para música local y música de catálogos legales.
- Búsqueda instantánea dentro de la biblioteca local.
- Portadas de los resultados.
- Botones para reproducir, guardar o descargar cuando esté permitido.

Mostrar estados elegantes de carga, error y resultados vacíos.

## 7. BIBLIOTECA PERSONAL

Permitir organizar toda la música guardada.

Crear categorías:
- Canciones favoritas.
- Playlists creadas.
- Álbumes.
- Artistas.
- Canciones descargadas.
- Archivos musicales importados.

Permitir ordenar por nombre, artista, fecha de incorporación y reproducciones recientes.

## 8. CREACIÓN DE PLAYLISTS

Crear un sistema de playlists funcional.

El usuario debe poder:
- Crear playlists ilimitadas.
- Asignar nombres.
- Elegir imágenes de portada.
- Agregar y eliminar canciones.
- Reordenar canciones.
- Editar títulos.
- Eliminar playlists.
- Reproducir toda una playlist.
- Activar modo aleatorio.
- Consultar duración total.

Todas las playlists deben permanecer guardadas incluso después de reiniciar el celular.

## 9. IMPORTACIÓN DE ARCHIVOS MP3

Permitir al usuario seleccionar música existente en su dispositivo.

Compatibilidad con:
- MP3.
- M4A.
- AAC.
- FLAC.
- WAV.
- OGG, cuando el dispositivo lo soporte.

Leer automáticamente:
- Nombre de canción.
- Artista.
- Álbum.
- Duración.
- Portada incrustada.

Utilizar el selector de archivos del sistema Android y permisos modernos de almacenamiento. No solicitar acceso innecesario a todos los archivos del usuario.

## 10. CONFIGURACIÓN

Incluir una pantalla con:
- Calidad de audio.
- Descargas solo con Wi-Fi.
- Almacenamiento utilizado.
- Administración de descargas.
- Temporizador para detener la música.
- Preferencia de tema oscuro.
- Información de licencias musicales.
- Acerca de Vibe Music.

## 11. TECNOLOGÍA Y ARQUITECTURA

Desarrollar una aplicación nativa Android.

Utilizar preferentemente:
- Kotlin como lenguaje principal.
- Jetpack Compose para la interfaz.
- Material 3 personalizado.
- Media3 ExoPlayer para reproducción.
- MediaSessionService para reproducción en segundo plano.
- Room para guardar música, playlists y favoritos.
- DataStore para preferencias.
- WorkManager o un mecanismo de descargas persistentes apropiado.
- Retrofit y OkHttp para APIs legales cuando sean necesarias.
- Coil para cargar portadas.
- Arquitectura MVVM.
- Kotlin Coroutines y Flow.
- Repositorios separados para música local, descargas y catálogos externos.

Mantener el código organizado, escalable y documentado.

La aplicación debe administrar correctamente permisos, interrupciones, procesos de fondo y errores de conexión.

## 12. FUNCIONAMIENTO SIN INTERNET

Cuando el celular no tenga conexión:

- La aplicación debe abrir normalmente.
- La biblioteca local debe funcionar.
- Deben visualizarse las portadas guardadas.
- Las playlists deben mantenerse disponibles.
- Se debe reproducir toda la música descargada e importada.
- Los controles del reproductor deben funcionar.
- No deben aparecer errores que bloqueen el uso.
- Las funciones que necesitan internet deben mostrar un mensaje apropiado.
- El usuario debe poder activar un modo exclusivamente sin conexión.

## 13. EXPERIENCIA DE USUARIO

Quiero una aplicación que se sienta como un producto comercial terminado, no como una demostración.

Implementar:
- Animaciones fluidas.
- Diseño adaptable a distintas pantallas.
- Estados de carga.
- Pantallas vacías bien diseñadas.
- Mensajes de error claros.
- Navegación consistente.
- Botones funcionales.
- Gestos para minimizar el reproductor.
- Interacciones táctiles naturales.
- Soporte para pantallas con diferentes densidades.
- Buen rendimiento y consumo razonable de batería.

No utilizar botones decorativos sin funcionamiento.

## 14. PRUEBAS OBLIGATORIAS

Comprobar los siguientes casos:

1. Importar una canción MP3 y reproducirla.
2. Crear una playlist y agregar canciones.
3. Cerrar y volver a abrir la aplicación sin perder la biblioteca.
4. Apagar la pantalla durante la reproducción.
5. Cambiar de aplicación sin interrumpir la música.
6. Descargar una canción legalmente autorizada.
7. Activar el modo avión y reproducir la canción descargada.
8. Reiniciar el celular y comprobar que las descargas siguen disponibles.
9. Eliminar una descarga y actualizar el almacenamiento.
10. Utilizar los controles de pantalla de bloqueo y Bluetooth.

No declarar una prueba como superada sin haberla ejecutado.

## 15. ENTREGA FINAL

Quiero recibir:

- Proyecto Android Studio completo.
- Código fuente editable.
- Todas las pantallas implementadas.
- Base de datos y almacenamiento funcionales.
- Reproductor real, no simulado.
- Sistema real de importación de archivos.
- Sistema de descargas legales persistentes.
- Archivo APK de instalación, si el entorno permite compilarlo.
- Instrucciones precisas para instalarlo en Android.
- Explicación de cualquier API externa que deba configurar.

Si no puedes compilar el APK, proporciona el proyecto íntegro y los comandos necesarios para compilarlo; no afirmes haber creado un APK inexistente.

## 16. PRIORIDADES DE DESARROLLO

Desarrollar por fases:

**Fase 1:** Interfaz visual completa, navegación y reproductor.

**Fase 2:** Importación de música local y almacenamiento permanente.

**Fase 3:** Playlists, favoritos y biblioteca.

**Fase 4:** Integración de fuentes musicales legales y sistema de descarga real.

**Fase 5:** Funcionamiento offline, reproducción en segundo plano y pruebas.

**Fase 6:** Optimización, corrección de errores y generación del APK.

## RESULTADO ESPERADO

Quiero que Vibe Music sea una aplicación Android con una experiencia visual muy similar a Spotify Premium, pero totalmente independiente, capaz de administrar mi propia música y descargar canciones desde fuentes autorizadas.

La aplicación debe ser rápida, elegante, intuitiva, estable y poder usarse sin pagar una suscripción por la reproducción de mi biblioteca personal.

Prioriza que todas las funciones principales estén realmente implementadas. No entregues únicamente imágenes, maquetas, pseudocódigo o una página web simulando Android.

Comienza creando el proyecto funcional y su estructura completa. Utiliza componentes reutilizables y continúa el desarrollo siguiendo las fases indicadas.