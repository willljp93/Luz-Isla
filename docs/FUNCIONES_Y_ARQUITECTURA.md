# Funciones y Arquitectura Técnica – Luz Isla

Documentación técnica de la arquitectura interna, motor de cálculo, formatos de intercambio, sincronización y automatización de compilaciones.

---

## 1. Arquitectura del proyecto

La aplicación sigue una arquitectura **MVVM + capas de dominio limpias** sin dependencias de servicios cerrados en la nube:

```text
app/src/main/java/com/example/
├── domain/
│   ├── model/         # Modelos inmutables (Circuit, ScheduleGroup, RotationCycle, ManualException, etc.)
│   ├── engine/        # ScheduleEngine puro en Kotlin (cálculo determinista por intervalos [inicio, fin))
│   └── version/       # AppVersionConfig (formato YYYY.MM.DD.N y codificación YYMMDDNN)
├── data/
│   ├── local/         # Room AppDatabase (v3), DAOs, Entities y SeedDataProvider
│   ├── preferences/   # DataStore Preferences (apariencia, zona, ciclo solar, sync HTTPS, actualizaciones, widgets)
│   └── repository/    # LuzIslaRepository (transacciones atómicas, fusión segura, respaldos y ciclos)
├── transfer/          # ConfigCodec (JSON + firma SHA-256) y CrockfordBase32 (códigos LUZ1-)
├── sync/              # HttpsSyncManager, ForecastSyncWorker, SunriseSunsetService, AppUpdateService, ShareHelper
├── notifications/     # ScheduleNotificationManager y BroadcastReceivers (boot / cambio de hora)
├── widget/            # PrimaryFavoriteWidgetProvider, ConfigurableCircuitWidgetProvider, LauncherIconManager
└── ui/
    ├── theme/         # Tema Material 3, fuentes locales y tokens semánticos
    ├── components/    # Timeline24hBar, Continuous3DTickerTimeline, DayNightBackdrop, IslaEmblemBadge
    └── screens/       # Onboarding, Home, Calendar, Circuits y Settings
```

---

## 2. Motor de cálculo (`ScheduleEngine`)

`ScheduleEngine` evalúa cualquier instante o ventana temporal trabajando en la zona horaria activa del usuario (por defecto `America/Havana`) mediante intervalos semiabiertos **`[inicioMillis, finMillis)`**.

### Jerarquía estricta de precedencia

Para determinar el estado de un circuito en un instante dado, el motor aplica el siguiente orden de prioridad de mayor a menor:

1. **Excepciones manuales (`ManualException`)**: soporta excepciones de 1 día o rangos continuos (`dateEpochDay` .. `endDateEpochDay`) con cruce de medianoche opcional.
2. **Horarios por fecha (`ExplicitForecast`)**: bloques asignados explícitamente a una fecha y lista de circuitos (`SERVICE` u `OUTAGE`).
3. **Relaciones sincronizadas (`CircuitRelation` con `syncRuleEnabled = true`)**:
   - `MISMO_HORARIO`: replica los segmentos del circuito referencia.
   - `HORARIO_INVERSO`: invierte `SERVICE` ↔ `OUTAGE` respecto al circuito referencia.
   - `DEPENDIENTE`: habilita `SERVICE` únicamente durante los bloques con servicio del circuito referencia.
   - `PERSONALIZADA`: replica los intervalos de estado del circuito referencia conservando notas propias.
   *(Incluye protección anti-ciclos para evitar recursión infinita si dos circuitos se referencian mutuamente).*
4. **Programación base del circuito**:
   - **Reglas fijas (`FixedScheduleRule`)**: máscara semanal de 7 bits (`bit 0 = Lunes` .. `bit 6 = Domingo`), inicio/fin diario (con soporte de cruce de medianoche) y vigencia opcional (`validFromEpochDay` / `validToEpochDay`).
   - **Grupos rotativos (`ScheduleGroup` / `RotationCycle`)**: aritmética modular continua usando `referenceEpochMillis`, `phaseOffsetMinutes`, `serviceDurationMinutes` y `outageDurationMinutes`.
5. **Sin información (`ScheduledState.UNKNOWN`)**.

### Ventana de línea de tiempo y eje horario
- `Timeline24hBar` calcula las marcas del eje mediante `buildTimelineAxisTicks(...)`:
  - **Ticks menores** cada **1 hora**.
  - **Ticks mayores con etiqueta** cada **4 horas** (en modo centrado ±12 h se alinean a horas exactas en punto sin minutos).
  - **Cambio de día**: identifica los cruces de medianoche (`12am` / `00h`) para alternar el tono de las etiquetas del día contiguo.

---

## 3. Formato JSON (`Conf Circuitos/*.json`)

La carpeta pública [`Conf Circuitos/`](../Conf%20Circuitos/) contiene la configuración comunitaria predeterminada sincronizada por la app:
`https://raw.githubusercontent.com/willljp93/Luz-Isla/main/Conf%20Circuitos/luz_isla_config_2026.11.24.1.json`

### Esquema (`schemaVersion = 1`)

```json
{
  "schemaVersion": 1,
  "configVersion": 26112401,
  "configVersionName": "2026.11.24.1",
  "generatedAtIso": "2026-11-24T12:00:00Z",
  "sourceName": "Luz Isla",
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
  "cycles": [
    {
      "id": "cyc_1",
      "name": "Ciclo principal",
      "description": "",
      "serviceDurationMinutes": 240,
      "outageDurationMinutes": 720,
      "referenceEpochMillis": 1795064400000,
      "orderedGroupIds": ["grp_1", "grp_2", "grp_3", "grp_4"],
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
  "explicitForecasts": [],
  "manualExceptions": [
    {
      "id": "exc_1",
      "circuitCode": "A210",
      "dateEpochDay": 20780,
      "endDateEpochDay": 20782,
      "startMinuteOfDay": 480,
      "endMinuteOfDay": 720,
      "hasService": false,
      "reason": "Mantenimiento programado"
    }
  ],
  "relations": [
    {
      "id": "rel_1",
      "sourceCircuitCode": "A080",
      "targetCircuitCode": "A060",
      "relationType": "MISMO_HORARIO",
      "syncRuleEnabled": true,
      "notes": ""
    }
  ],
  "sha256": "<opcional_sha256_hex_sobre_json_compacto_con_sha256_vacio>"
}
```

### Reglas de sincronización segura
- **Normalización automática**: `HttpsSyncManager.normalizeSyncUrl` convierte enlaces `github.com/.../blob/...` a `raw.githubusercontent.com/...`, codifica espacios (`%20` para `Conf Circuitos`) y migra automáticamente la ruta heredada de la raíz del repositorio hacia `Conf Circuitos/`.
- **Respaldo previo**: toda sincronización o importación guarda primero una copia local restaurable en Room.
- **Preservación personal**: el modo `MERGE_KEEP_PERSONAL` mantiene intactos favoritos, *Mi vivienda*, nombres personalizados y comentarios locales.

---

## 4. Código compacto `LUZ1-`

1. Serializa el `LuzIslaConfigPayload` a JSON compacto UTF-8 (sin datos personales cuando se comparte programación pública).
2. Calcula el SHA-256 de los bytes JSON y toma los primeros **4 bytes** como verificación de integridad.
3. Comprime los bytes JSON con `Deflate` (`BEST_COMPRESSION`).
4. Codifica `[4 bytes SHA-256] + [payload comprimido]` en **Base32 Crockford** y antepone el prefijo **`LUZ1-`**.

---

## 5. Integraciones externas opcionales (solo HTTPS de lectura)

| Servicio | Propósito | Comportamiento offline |
| :--- | :--- | :--- |
| **GitHub Raw (`Conf Circuitos/*.json`)** | Descarga de horarios comunitarios configurados por URL. | Conserva programación local sin sobrescribir nada. |
| **GitHub Releases API** (`willljp93/Luz-Isla`) | Comprobación de nuevas versiones de la app y selección del APK según ABI del teléfono. | Muestra la última información cacheada sin bloquear la app. |
| **SunriseSunset.io** | Horas reales de amanecer y atardecer para Nueva Gerona (`21.8867, -82.8056`) usadas en el degradado cielo día/noche. | Usa caché del día o los valores locales configurados (`06:30`–`18:30` por defecto). |

---

## 6. Versionado, CI/CD y Firma Release

### Versionado único (`version.properties`)
`version.properties` centraliza el versionado diario `YYYY.MM.DD.N`:
- `versionName`: `2026.11.25.2`
- `versionCode`: `26112502` (`YY * 1_000_000 + MM * 10_000 + DD * 100 + N`)

Tanto Gradle (`app/build.gradle.kts`) como GitHub Actions (`.github/workflows/android-ci.yml`) leen estas propiedades para mantener sincronizados los builds locales, las etiquetas de Release y las comparaciones de actualización de `AppUpdateService`.

### Flujo de GitHub Actions
En cada `push`, `pull_request`, etiqueta `v*` o ejecución manual:
1. Lee `versionName`, `versionCode` y calcula `release_tag`.
2. Aplica `vars.CUSTOM_APP_ID` (si está definido, p. ej. `com.threeare.luzisla`) validando la sintaxis Android.
3. Ejecuta toda la suite `gradle :app:testDebugUnitTest`.
4. Si existen `secrets.KEYSTORE_BASE64`, `secrets.STORE_PASSWORD` y `secrets.KEY_PASSWORD`, decodifica la llave `.jks` temporalmente y compila los APK en modo **`release`** con división por ABI (`arm64-v8a`, `armeabi-v7a`, `universal`); en caso contrario compila en `debug`.
5. Publica los instaladores limpios (`LuzIsla-v<version>-<abi>.apk`) y sus checksums (`LuzIsla-v<version>-sha256.txt`) en **GitHub Releases**.

---

## 7. Limitaciones propias de Android en Widgets

- `AppWidgetManager` fija un período mínimo de actualización automática de **30 minutos** (`1800000 ms`).
- El modo **Doze** y las restricciones de batería del fabricante pueden retrasar tareas en segundo plano de `WorkManager`.
- Por ello, los widgets muestran el estado calculado, la hora del próximo cambio, un botón de **actualización manual inmediata** y acceso directo a la app para el conteo segundo a segundo.
