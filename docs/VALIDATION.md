# Validación de la entrega

Fecha: 16 de octubre de 2026.

## Ejecutado en el entorno de nube

| Comprobación | Resultado |
| --- | --- |
| Compilar APK nativo Android (`assembleDebug`) | Superada |
| Pruebas JVM/Robolectric (`testDebugUnitTest`) | 19 ejecutadas, 19 superadas, 0 omitidas |
| Android Lint (`lintDebug`) | Superada, sin errores; avisos sobre atributos modernos y el servicio multimedia exportado |
| Compilar pruebas instrumentadas (`assembleDebugAndroidTest`) | Superada |
| Verificar firma APK con `apksigner verify --verbose` | Superada, APK Signature Scheme v2 |
| Repetir `bash scripts/setup-cloud.sh` | Superada; SDK y dependencias reutilizados |
| Descargar el catálogo público y sus tres pistas CC0 por HTTPS | Superada; SHA-256 de los tres archivos coincide, WAV válidos de 32 segundos |
| Instalar compilación previa en AOSP API 35 y API 29 | Superada en ambos; anterior a activar el catálogo inicial |

Las pruebas JVM cubren búsqueda, formatos de duración/tamaño, identificadores de
descarga, autorización/licencias del catálogo, checksum obligatorio, transporte
HTTPS, playlists y su orden, favoritos tras reabrir Room, deduplicación, estado y
progreso persistente, transferencias sobre TLS, HTTP Range/If-Range, reinicio si el
servidor ignora Range y descarte de bytes alterados. La prueba de TLS usa un servidor
local con un certificado de prueba explícitamente confiado, sin desactivar TLS.

La prueba de interfaz ejecuta la Activity y Compose con Robolectric: comprueba el
mensaje de servicio de audio no disponible, lo descarta, crea una playlist desde
la interfaz, añade una canción de prueba y verifica tanto su aparición en pantalla
como la relación guardada en Room. El servicio de audio se declara no disponible
en ese test: **no es una prueba de reproducción ni utiliza una canción simulada
para afirmar que el sonido funciona**.

El acceso al catálogo y audio público se comprobó desde la máquina de nube. El
catálogo de Vibe Originals queda configurado como fuente inicial; solo se consulta
cuando el usuario abre Catálogo legal. Esto no sustituye una prueba de WorkManager
sobre un teléfono con conexión y posterior modo avión.

## Intentos de pruebas instrumentadas

Se prepararon emuladores AOSP Android 15 (API 35) y Android 10 (API 29), x86_64,
mediante emulación de CPU porque el entorno no dispone de `/dev/kvm`. Ambos
instalaron los APK, pero presentaron diálogos de **System UI isn't responding**;
API 35 también bloqueó el proceso del sistema. Se detuvieron los emuladores
iniciados para esta tarea tras comprobar los fallos del sistema. El APK final,
recompilado después de activar el catálogo inicial, tiene firma e integridad
verificadas; no se volvió a instalar tras detener los emuladores. Los intentos de
pruebas instrumentadas no completaron con un resultado válido y **ninguna se
considera superada**. Esto es una limitación observada del entorno; el resultado
del comportamiento en un teléfono permanece sin verificar.

## Matriz de los requisitos de dispositivo

| Caso solicitado | Evidencia disponible / pendiente |
| --- | --- |
| Importar MP3 y reproducir | Test instrumentado incluido; sin resultado completo en Android |
| Crear playlist y añadir canciones | Superado en Compose + Room con Robolectric; pendiente en teléfono |
| Cerrar/reabrir sin perder biblioteca | Persistencia de Room superada; reinicio del proceso Android pendiente |
| Reproducir con pantalla apagada | Pendiente en teléfono |
| Cambiar de aplicación sin interrupción | Pendiente en teléfono |
| Descargar audio autorizado | Catálogo, TLS, integridad y reanudación superados en nube/JVM; Worker en Android pendiente |
| Modo avión después de descargar | Pendiente en teléfono |
| Reiniciar el teléfono y conservar descargas | Pendiente en teléfono |
| Eliminar descarga y actualizar espacio | Cascada de relaciones probada; archivo y pantalla en Android pendientes |
| Controles de bloqueo y Bluetooth | Pendiente en teléfono, al igual que llamadas y foco real |

El procedimiento reproducible está en [DEVICE_TESTS.md](DEVICE_TESTS.md). Se incluyen
los tests instrumentados de importación, persistencia, reproducción y navegación.
También deben comprobarse distintos códecs y políticas de batería de fabricantes.
No se afirma haber superado ninguna de estas verificaciones físicas.
