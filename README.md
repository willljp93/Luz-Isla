# Luz Isla – Horarios Eléctricos

Aplicación Android nativa, ligera y **100 % funcional sin conexión (offline-first)** para consultar de forma visual, rápida e intuitiva los horarios programados del servicio eléctrico por circuito en la **Isla de la Juventud, Cuba**.

> **Aviso de independencia:** Esta es una herramienta comunitaria independiente de pronósticos configurables. **No** es una aplicación oficial de la Empresa Eléctrica ni realiza mediciones físicas en tiempo real de la red eléctrica.

---

## Tabla de contenidos

1. [Características principales](#características-principales)
2. [Compatibilidad y requisitos](#compatibilidad-y-requisitos)
3. [Arquitectura del proyecto](#arquitectura-del-proyecto)
4. [Motor de cálculo de horarios](#motor-de-cálculo-de-horarios)
5. [Formato JSON de configuración (`configuracion.json`)](#formato-json-de-configuración-configuracionjson)
6. [Formato de código alfanumérico (`LUZ1-`) y QR multiparte (`LUZ1P|`)](#formato-de-código-alfanumérico-luz1--y-qr-multiparte-luz1p)
7. [Limitaciones de App Widgets en Android](#limitaciones-de-app-widgets-en-android)
8. [Compilación y generación de APKs](#compilación-y-generación-de-apks)
9. [Pruebas unitarias y de integración](#pruebas-unitarias-y-de-integración)
10. [Flujo recomendado con GitHub](#flujo-recomendado-con-github)

---

## Características principales

- **Pantalla de inicio prioritaria**: reloj en vivo (`America/Havana`), resumen general, favoritos y vivienda principal destacados al principio, cuenta regresiva en vivo hacia el próximo cambio, búsqueda rápida por código/nombre/reparto, filtros por estado y alternancia entre tarjetas y vista compacta.
- **Línea de tiempo visual de 24 horas**: barras proporcionales interactivas (modalidad **individual** y **comparativa** multifila con filtro de favoritos), manejo exacto de cruces de medianoche e inspección de segmentos al tocarlos.
- **Calendario completo**: vista diaria, semanal (próximos 7 días), por circuito y vista general de grupos. Indica explícitamente cuando una fecha no posee pronóstico sin inventar horarios.
- **Catálogo completamente dinámico (Room SQLite)**: sin límite fijo de circuitos ni grupos. Permite crear, editar, reordenar, desactivar/recuperar y eliminar circuitos, asignar nombres y comentarios locales, configurar horarios fijos, grupos rotativos arbitrarios (4h/12h, 3h/9h, 6h/18h, etc.), pronósticos explícitos por fecha, excepciones manuales y relaciones entre circuitos.
- **Transferencia offline (WhatsApp / Telegram / QR / JSON)**:
  - Códigos alfanuméricos compactos `LUZ1-...` con compresión Deflate, Base32 Crockford y suma de verificación SHA-256.
  - Generación y escaneo de QR 100 % sin conexión (CameraX + ZXing Core), con división automática en **QR multiparte numerado** cuando la configuración es grande (nunca trunca datos).
- **Sincronización HTTPS opcional (WorkManager)**: descarga manual o periódica desde cualquier archivo JSON publicado en HTTPS, con respaldo automático previo y preservación de favoritos, nombres y comentarios locales.
- **Notificaciones locales anticipadas**: avisos configurables (5, 10, 15 o 30 min) para inicio, fin y restablecimiento de servicio, reprogramados automáticamente tras reinicio o cambio de zona horaria.
- **Widgets de pantalla de inicio**: variante compacta para el favorito principal y variante configurable por circuito.
- **Compartir**: texto formateado para WhatsApp/Telegram e imagen resumen generada localmente.

---

## Compatibilidad y requisitos

- **Android mínimo**: Android 7.0 Nougat (**API 24**) en el paquete para Google Play (requerido por *Play automatic protection*; compatible con API 23 en compilaciones locales manuales sin Play Store).
- **SDK objetivo / compilación**: API **36**.
- **Arquitecturas soportadas**:
  - Universal (`universal`)
  - ARM 32 bits (`armeabi-v7a`)
  - ARM 64 bits (`arm64-v8a`)
- **Sin dependencia de Google Play Services**: no utiliza Firebase, AdMob ni ML Kit remoto.
- **Tecnologías**: Kotlin, Jetpack Compose (Material Design 3), MVVM, Coroutines/StateFlow, Room (KSP), DataStore Preferences, WorkManager, CameraX + ZXing Core, `desugar_jdk_libs` para `java.time`.

---

## Arquitectura del proyecto

```text
app/src/main/java/com/example/
├── domain/
│   ├── model/         # Modelos inmutables de dominio (Circuit, ScheduleGroup, etc.)
│   └── engine/        # ScheduleEngine puro en Kotlin (America/Havana)
├── data/
│   ├── local/         # Entidades Room, DAOs, AppDatabase y SeedDataProvider
│   ├── preferences/   # DataStore Preferences (tema, sync, notificaciones, widgets)
│   └── repository/    # LuzIslaRepository (transacciones, backups y merge seguro)
├── transfer/          # CrockfordBase32, ConfigCodec, MultiPartQrCollector, QrCodeUtils
├── sync/              # HttpsSyncManager, ForecastSyncWorker, ShareHelper
├── notifications/     # ScheduleNotificationManager y BroadcastReceivers
├── widget/            # AppWidgetProviders, WidgetConfigActivity y WidgetRefreshWorker
└── ui/
    ├── theme/         # Tema M3 "Solar & Isla", fuentes locales y tokens semánticos
    ├── components/    # Timeline24hBar, CircuitDetailBottomSheet, QrScannerDialog
    └── screens/       # Home, Calendar, Circuits (Admin) y Settings
```

---

## Motor de cálculo de horarios

El motor (`ScheduleEngine`) opera de forma independiente a la interfaz gráfica en la zona horaria **`America/Havana`** usando intervalos semiabiertos **`[inicio, fin)`** (inicio incluido, fin excluido) y resolviendo cada instante según la siguiente prioridad estricta:

1. **Excepciones manuales específicas** (`ManualException`).
2. **Pronósticos explícitos por fecha** (`ExplicitForecast`).
3. **Programación recurrente** (`FixedScheduleRule`, `ScheduleGroup` rotativo mediante aritmética modular sobre fecha/hora de referencia y fase, o `CircuitRelation` con sincronización habilitada).
4. **Sin información disponible** (`ScheduledState.UNKNOWN`).

---

## Formato JSON de configuración (`configuracion.json`)

La aplicación puede importar, exportar y sincronizar archivos JSON con el siguiente esquema versionado (`schemaVersion = 1`):

```json
{
  "schemaVersion": 1,
  "configVersion": 12,
  "generatedAtIso": "2026-11-21T22:00:00Z",
  "sourceName": "Pronóstico Comunitario Isla",
  "includesPersonalData": false,
  "circuits": [
    {
      "code": "A210",
      "customName": "",
      "comment": "",
      "groupId": "grp_1",
      "scheduleType": "ROTATING",
      "isActive": true,
      "isFavorite": false,
      "isPrimaryHome": false,
      "displayOrder": 0,
      "createdAt": 1795300000000,
      "updatedAt": 1795300000000
    }
  ],
  "groups": [
    {
      "id": "grp_1",
      "name": "Grupo 1",
      "description": "Ciclo 4h con servicio / 12h sin servicio",
      "serviceDurationMinutes": 240,
      "outageDurationMinutes": 720,
      "referenceEpochMillis": 1795064400000,
      "phaseOffsetMinutes": 0,
      "isActive": true
    }
  ],
  "fixedRules": [
    {
      "id": "fixed_a030",
      "circuitCode": "A030",
      "daysOfWeekMask": 127,
      "startMinuteOfDay": 540,
      "endMinuteOfDay": 1020,
      "validFromEpochDay": null,
      "validToEpochDay": null,
      "label": "Horario fijo 09:00 - 17:00"
    }
  ],
  "explicitForecasts": [
    {
      "id": "fc_20261122_1",
      "dateEpochDay": 20779,
      "startMinuteOfDay": 480,
      "endMinuteOfDay": 720,
      "circuitCodes": ["A210", "A177"],
      "intervalKind": "SERVICE",
      "notes": "Bloque diurno",
      "isHistoricalTest": false
    }
  ],
  "manualExceptions": [],
  "relations": [
    {
      "id": "rel_a080_a060",
      "sourceCircuitCode": "A080",
      "targetCircuitCode": "A060",
      "relationType": "INFORMATIVA",
      "syncRuleEnabled": false,
      "notes": "A080 se afecta cuando A060 tiene servicio."
    }
  ],
  "sha256": "<opcional_sha256_hex_del_json_con_campo_sha256_vacio>"
}
```

### Notas sobre campos clave

- **`daysOfWeekMask`**: máscara de bits de 7 días (`bit 0 = Lunes` ... `bit 6 = Domingo`; `127` = todos los días).
- **`startMinuteOfDay` / `endMinuteOfDay`**: minutos desde las `00:00`. Si `endMinuteOfDay <= startMinuteOfDay` o `> 1440`, el motor interpreta cruce de medianoche hacia el día siguiente.
- **`sha256`**: si está presente, se calcula sobre el JSON compacto con `"sha256": ""`. Si el hash no coincide, la importación se rechaza sin alterar los datos locales.
- **Separación de datos personales**: al sincronizar desde HTTPS o importar con modo de fusión seguro, los favoritos, vivienda principal, nombres personalizados y comentarios locales se conservan intactos.

---

## Formato de código alfanumérico (`LUZ1-`) y QR multiparte (`LUZ1P|`)

1. **Serialización**: convierte el payload a JSON compacto UTF-8.
2. **Integridad**: calcula el SHA-256 de los bytes JSON y toma los primeros **4 bytes** como firma de integridad binaria.
3. **Compresión**: comprime los bytes JSON usando `Deflate` (`BEST_COMPRESSION`).
4. **Envoltura y codificación**: concatena `[4 bytes SHA-256] + [payload comprimido]` y lo codifica en **Base32 Crockford** (alfabeto `0-9A-HJKMNP-TV-Z`, tolerante a errores visuales `O/0`, `I/L/1`), anteponiendo el prefijo de versión **`LUZ1-`**.
5. **QR único o multiparte**:
   - Si el código `LUZ1-...` mide `<= 900` caracteres, se genera **1 solo código QR**.
   - Si supera los 900 caracteres, se divide automáticamente en fragmentos numerados con formato:
     ```text
     LUZ1P|<sessionId4>|<parteActual>|<totalPartes>|<sha256_8chars>|<fragmento>
     ```
   - El escáner recolecta los fragmentos en cualquier orden y verifica la suma `sha256_8chars` al completar todas las partes.

---

## Limitaciones de App Widgets en Android

Por restricciones propias del sistema operativo Android (desde API 23 en adelante):

- `AppWidgetManager` impone un intervalo mínimo de actualización automática (`updatePeriodMillis`) de **30 minutos** (`1800000 ms`).
- El modo **Doze** y los optimizadores de batería del fabricante pueden diferir tanto `updatePeriodMillis` como tareas periódicas de `WorkManager`.
- Debido a estas limitaciones, el widget **no** actualiza una cuenta regresiva segundo a segundo en la pantalla de inicio; muestra el último estado calculado, la hora exacta del próximo cambio, un botón de **actualización inmediata** y acceso directo al detalle completo dentro de la app.

---

## Compilación y generación de APKs

### Requisitos previos

- JDK 17
- Android SDK (compileSdk 36, build-tools compatibles)
- Gradle instalado o `./gradlew`

### Comandos para compilar

```bash
# Ejecutar pruebas unitarias y de Robolectric
gradle :app:testDebugUnitTest

# Compilar APK estándar único (para pruebas rápidas / Android Studio)
gradle :app:assembleDebug

# Compilar los 3 APKs Debug por arquitectura (Universal, armeabi-v7a, arm64-v8a)
gradle :app:assembleDebug -PenableAbiSplits=true

# Compilar los 3 APKs Release (requiere variables KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD)
gradle :app:assembleRelease -PenableAbiSplits=true
```

### Ubicación de los APKs generados

En `app/build/outputs/apk/debug/` (o `release/`):

1. `app-universal-debug.apk` — **APK universal**
2. `app-armeabi-v7a-debug.apk` — **APK ARM 32 bits (`armeabi-v7a`)**
3. `app-arm64-v8a-debug.apk` — **APK ARM 64 bits (`arm64-v8a`)**

---

## Pruebas unitarias y de integración

Las pruebas se ejecutan sin necesidad de emulador ni ADB:

- `ScheduleEngineTest`: ciclos 4h/12h, ciclos 3h/9h y 6h/18h, desplazamientos, cruces de medianoche/mes/año, prioridades 1..4, horarios fijos, circuitos sin información, intervalos consecutivos y relaciones.
- `TransferCodecTest`: codificación/decodificación Base32 Crockford, detección de códigos dañados o versiones incompatibles, exportación sin datos personales y QR multiparte con catálogos grandes (120+ circuitos).
- `ExampleRobolectricTest`: inicialización de Room con los 19 circuitos, crecimiento dinámico del catálogo y preservación de datos personales al importar pronósticos.

---

## Flujo recomendado con GitHub

El repositorio incluye:

- `.gitignore` configurado para proyectos Android Studio / Gradle (excluye `.gradle`, `build/`, `local.properties`, `.env`, keystores y cachés del IDE).
- `.github/workflows/android-ci.yml` para ejecutar pruebas automáticamente y compilar los APKs en cada `push` o `pull_request`.

Ejemplo para inicializar y subir a GitHub:

```bash
git init
git add .
git commit -m "feat: implementación inicial de Luz Isla – Horarios Eléctricos"
git branch -M main
git remote add origin https://github.com/<tu-usuario>/luz-isla-android.git
git push -u origin main
```
