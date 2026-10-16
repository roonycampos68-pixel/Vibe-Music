# Validación de la entrega

Fecha: 16 de octubre de 2026.

## Ejecutado en el entorno de nube

| Comprobación | Resultado |
| --- | --- |
| Compilar APK nativo Android (`assembleDebug`) | Superada |
| Pruebas JVM/Robolectric (`testDebugUnitTest`) | 18 ejecutadas, 18 superadas, 0 omitidas |
| Android Lint (`lintDebug`) | Superada, sin errores; avisos sobre atributos modernos y el servicio multimedia exportado |
| Compilar pruebas instrumentadas (`assembleDebugAndroidTest`) | Superada |
| Verificar firma APK con `apksigner verify --verbose` | Superada, APK Signature Scheme v2 |
| Repetir `bash scripts/setup-cloud.sh` | Superada; SDK y dependencias reutilizados |

Las pruebas JVM cubren búsqueda, formatos de duración/tamaño, identificadores de
descarga, autorización/licencias del catálogo, checksum obligatorio, transporte
HTTPS, playlists y su orden, favoritos tras reabrir Room, deduplicación, estado y
progreso persistente, transferencias sobre TLS, HTTP Range/If-Range, reinicio si el
servidor ignora Range y descarte de bytes alterados. La prueba de TLS usa un servidor
local con un certificado de prueba explícitamente confiado, sin desactivar TLS.

## Pruebas en Android

Se instaló el SDK y arrancó un emulador AOSP Android 15 (API 35, x86_64) mediante
emulación de CPU porque el entorno no ofrece `/dev/kvm`. La instalación del APK y
la ejecución instrumentada están en curso al preparar este documento; aún no se
consideran superadas. El procedimiento y los casos automáticos están incluidos
en [DEVICE_TESTS.md](DEVICE_TESTS.md).

## Pendiente en un dispositivo físico

- Llamadas entrantes, pérdida de foco real, pantalla de bloqueo y botones Bluetooth.
- Reinicio real del teléfono con archivos descargados, variaciones de batería de
  fabricantes y formatos de audio distintos del conjunto de pruebas.
- Descarga desde una fuente pública propia con permisos verificables y prueba
  completa de modo avión después de descargar. Las transferencias TLS, integridad y
  reanudación sí se probaron automáticamente; no equivalen a la prueba en un teléfono.

No se afirma haber superado estas pruebas físicas. No hay un catálogo comercial ni
una cuenta de terceros configurada. El conector y un catálogo de audio original CC0
se incluyen para configurar una fuente propia sin credenciales. El funcionamiento
específico de cada códec depende de Android y del dispositivo.
