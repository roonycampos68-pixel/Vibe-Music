# Validación de la entrega

Este archivo conserva el informe de la versión 1.1. Para la entrega actual consulta
[la validación de Vibe 1.2](VALIDATION-1.2.md).

Versión 1.1.0 · Fecha: 16 de octubre de 2026.

## Ejecutado en el entorno de nube

| Comprobación | Resultado |
| --- | --- |
| Compilar APK nativo Android (`assembleDebug`) | Superada |
| Pruebas JVM/Robolectric (`testDebugUnitTest`) | 54 ejecutadas, 54 superadas, 0 omitidas |
| Android Lint (`lintDebug`) | Superada, sin errores; avisos sobre atributos modernos y el servicio multimedia exportado |
| Compilar pruebas instrumentadas (`assembleDebugAndroidTest`) | Superada |
| Verificar firma APK con `apksigner verify --verbose` | Superada, APK Signature Scheme v2 |
| Repetir `bash scripts/setup-cloud.sh` | Superada; SDK y dependencias reutilizados |
| Abrir catálogo y WAV incluidos sin red ni acceso a GitHub | Superada; huellas SHA-256 coincidentes y cabecera WAV válida |
| Migrar esquema real Room 1 → 2 | Superada; conserva archivos, favoritos, reproducciones, playlist ordenada y descarga pausada |
| Consultar la API real de Wikimedia desde esta nube | Bloqueada por la política de red del entorno (proxy CONNECT 403); no se declara superada |

Las pruebas JVM cubren búsqueda, formatos de duración/tamaño, identificadores de
descarga, autorización/licencias del catálogo, checksum obligatorio, transporte
HTTPS, CSV/JSON de Spotify, duplicados y filas incompletas, playlists y su orden,
entradas pendientes, vinculación de audio, exportación CSV, favoritos tras reabrir Room, deduplicación, estado y
progreso persistente, transferencias sobre TLS, HTTP Range/If-Range, reinicio si el
servidor ignora Range, SHA-1 autorizado más SHA-256 de deduplicación, verificación
de un parcial completo sin provocar HTTP 416, cancelación y descarte de bytes alterados.
Las pruebas del buscador comprueban consulta real sobre un servidor HTTPS local,
paginación, cancelación, respuestas HTTP fallidas y tamaños máximos. Las respuestas
de Commons usadas para parseo son fixtures controlados; no son búsquedas en vivo.
La prueba de TLS usa un servidor
local con un certificado de prueba explícitamente confiado, sin desactivar TLS.

Las pruebas de interfaz ejecutan la Activity y Compose con Robolectric: crean una
playlist, añaden una canción y verifican pantalla y Room; importan un CSV mediante
el contrato del selector de documentos y comprueban los pendientes; buscan audio
incluido en Todo sin conexión y muestran un estado vacío para títulos inexistentes.
Se reinicia la factoría AndroidViewModel entre tests porque Robolectric crea una
Application distinta para cada uno. El servicio de audio se declara no disponible
en esos tests: **no son pruebas de reproducción ni utilizan una canción simulada
para afirmar que el sonido funciona**.

La versión 1.0 dependía de URLs de este repositorio para su catálogo inicial.
Ese acceso desde la nube no demostraba acceso anónimo desde el teléfono; en 1.1
se elimina esa dependencia y se empaquetan los originales en el APK. La búsqueda
pública y sus descargas necesitan validación en un teléfono con conexión. Los
dominios `commons.wikimedia.org` y `upload.wikimedia.org` se guardaron en el
borrador de configuración de nube; guardarlo no aplica la política al entorno en
ejecución. Las pruebas controladas no sustituyen una prueba de WorkManager en
Android con conexión y posterior modo avión.

## Intentos de pruebas instrumentadas

Se prepararon emuladores AOSP Android 15 (API 35) y Android 10 (API 29), x86_64,
mediante emulación de CPU porque el entorno no dispone de `/dev/kvm`. Ambos
instalaron los APK, pero presentaron diálogos de **System UI isn't responding**;
API 35 también bloqueó el proceso del sistema. Se detuvieron los emuladores
iniciados para la entrega anterior tras comprobar los fallos del sistema. No se
repitió esta configuración fallida para la versión 1.1. Los APK tienen firma e
integridad verificadas; la actualización conserva certificado y aumenta versionCode.
El APK 1.1 no se ha instalado en un teléfono desde esta nube. Los intentos de
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
| Descargar audio autorizado | Fixtures de catálogo, TLS, integridad y reanudación superados en JVM; API en vivo y Worker en Android pendientes |
| Importar playlist de Spotify | CSV/JSON y flujos Compose + Room superados; prueba desde cuenta real en teléfono pendiente |
| Modo avión después de descargar | Pendiente en teléfono |
| Reiniciar el teléfono y conservar descargas | Pendiente en teléfono |
| Eliminar descarga y actualizar espacio | Cascada de relaciones probada; archivo y pantalla en Android pendientes |
| Controles de bloqueo y Bluetooth | Pendiente en teléfono, al igual que llamadas y foco real |

El procedimiento reproducible está en [DEVICE_TESTS.md](DEVICE_TESTS.md). Se incluyen
los tests instrumentados de importación, persistencia, reproducción y navegación.
También deben comprobarse distintos códecs y políticas de batería de fabricantes.
No se afirma haber superado ninguna de estas verificaciones físicas.
