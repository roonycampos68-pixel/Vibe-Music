# Pruebas de dispositivo

La matriz de resultados de la entrega está en `VALIDATION.md`. Este documento es
el procedimiento reproducible; una casilla de esta lista no significa que se haya
superado la prueba.

## Automatización

Con Android SDK, un teléfono/emulador Android 10+ y depuración USB:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.vibemusic android.permission.POST_NOTIFICATIONS # Android 13+
./gradlew connectedDebugAndroidTest
```

Ejecutar en una instalación de pruebas limpia. `DeviceWorkflowTest` crea un MP3
original en MediaStore, lo importa, borra la fuente, comprueba metadatos y persistencia,
crea una playlist, reproduce en segundo plano y con pantalla apagada, activa modo
avión y vuelve a reproducir. Finalmente borra los datos creados. `UiWorkflowTest`
añade las composiciones originales y recorre la creación de playlists y búsqueda.
No borres datos de una instalación que contenga tu biblioteca personal.

## Matriz manual completa

1. Importar un MP3 propio con **Tu biblioteca → Importar**, verificar nombre,
   artista, álbum y portada. Reproducirlo y mover su posición con la barra.
2. Crear una playlist, añadir dos canciones, subir/bajar una, renombrarla, cambiar
   portada, quitar una canción y confirmar la duración total. Verificar favoritas.
3. Pausar a mitad de canción, cerrar/forzar detención y volver a abrir. Comprobar
   biblioteca, playlists, favoritos, cola y posición. Pulsar reproducir.
4. Apagar la pantalla durante al menos un minuto y escuchar la continuidad.
5. Cambiar a otra app y comprobar el sonido y la notificación multimedia.
6. Configurar un catálogo propio HTTPS con permiso de distribución. Descargar,
   pausar/reanudar y cancelar. Revisar progreso, calidad, checksum y ausencia de
   duplicados. Probar una fuente sin soporte Range para verificar el reinicio.
7. Activar modo avión, cerrar/abrir la app y reproducir la descarga desde Descargas.
8. Reiniciar el teléfono. Verificar que el archivo sigue disponible sin internet.
9. Eliminar una descarga y comprobar la reducción de almacenamiento y su retirada
   de playlists. El archivo importado original fuera de la app no se elimina.
10. Controlar pausa, reproducción y salto desde bloqueo y audífonos Bluetooth;
    desconectar los audífonos y comprobar pausa. Probar una llamada entrante y
    pérdida de foco. Probar el temporizador y el volumen.

Repetir en Android 8, 10, 13 y 15 o posteriores, distintos tamaños de pantalla y
con políticas de batería del fabricante. Las pruebas de llamadas y Bluetooth
necesitan hardware real. El catálogo del proyecto solo es accesible públicamente
si el repositorio es público; de lo contrario, utilizar un servidor propio.
