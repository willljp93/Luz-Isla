# Luz Isla – Horarios Eléctricos

Aplicación Android nativa, ligera y **100 % funcional sin conexión (offline-first)** para consultar de forma visual, rápida e intuitiva los horarios programados del servicio eléctrico por circuito en la **Isla de la Juventud, Cuba**.

> **Aviso de independencia:** Esta es una herramienta comunitaria independiente para consultar y gestionar horarios y reglas programadas. **No** es una aplicación oficial de la Empresa Eléctrica ni realiza mediciones físicas en tiempo real de la red eléctrica.

---

## Documentación disponible

| Documento | Contenido |
| :--- | :--- |
| [**Guía de Uso**](./docs/GUIA_DE_USO.md) | Manual paso a paso de todas las pantallas, filtros, rueda 3D, calendario, circuitos, relaciones, excepciones, ajustes y widgets. |
| [**Funciones y Arquitectura**](./docs/FUNCIONES_Y_ARQUITECTURA.md) | Arquitectura MVVM, motor `ScheduleEngine`, prioridad de reglas, formato JSON (`ConfCircuitos/`), código `LUZ1-`, ciclo solar y compilación CI/CD. |
| [**Changelog**](./CHANGELOG.md) | Registro detallado de cambios por versión y plantilla lista para futuras versiones. |
| [**Configuración JSON comunitaria**](./ConfCircuitos/luz_isla_config.json) | Archivo JSON por defecto alojado en `ConfCircuitos/` para sincronización automática desde GitHub. |

---

## Tabla de contenidos

1. [Características principales](#características-principales)
2. [Descarga e instalación (GitHub Releases)](#descarga-e-instalación-github-releases)
3. [Compatibilidad y requisitos](#compatibilidad-y-requisitos)
4. [Estructura del repositorio](#estructura-del-repositorio)
5. [Compilación y firma Release en GitHub Actions](#compilación-y-firma-release-en-github-actions)
6. [Pruebas unitarias y de integración](#pruebas-unitarias-y-de-integración)

---

## Características principales

- **Pantalla de Inicio interactiva**: reloj en vivo en la zona horaria elegida, fondo y emblema reactivos al cielo día/noche (`sunrisesunset.io` o manual), resumen general, vivienda principal y favoritos destacados al inicio, cuenta regresiva en vivo (**Quedan** cuando hay servicio / **Faltan** cuando no hay servicio), búsqueda rápida y filtros inteligentes que se ocultan cuando no aplican.
- **Línea de tiempo de 24 horas y Rueda 3D continua**:
  - Modo clásico (`00:00`–`24:00`) o **centrado en la hora actual** (ventana móvil de 12 h antes y 12 h después con `Ahora` fijo al centro, etiquetas limpias en punto, marcas cada 1 hora y contraste visual al cruzar la medianoche).
  - Rueda cilíndrica 3D sincronizada bidireccionalmente con el selector de fechas para explorar días pasados y futuros.
- **Calendario completo**: vistas por **Día**, **Semana** (7 días), **Por circuito** (con rueda 3D) y **Grupos**, con filtros por *Favoritos*, *Con servicio*, *Sin servicio*, *Sin info* y *Fijos*.
- **Gestión avanzada de Circuitos y Programación**:
  - **Edición de circuitos**: alta/edición de código, nombre personalizado, localidad/comentario, modalidad (`Fijo`, `Rotativo`, `Por fecha`, `Sin prog.`), grupo, favoritos y vivienda principal.
  - **Programación**: **Ciclos** rotativos con cálculo automático de desfases, **Grupos**, **Horarios Fijos** (`L M X J V S D` y vigencia opcional), **Excepciones** puntuales o por **rango de fechas (Desde / Hasta)**, y **Relaciones** entre circuitos (*Mismo horario*, *Horario inverso*, *Dependiente*, *Personalizada* e *Informativa*).
- **Sincronización y transferencia**:
  - Sincronización HTTPS directa desde GitHub (`ConfCircuitos/luz_isla_config.json` por defecto) con respaldo local previo y preservación de favoritos, vivienda principal, nombres y comentarios propios.
  - Exportación e importación offline en **JSON** o códigos alfanuméricos compactos **`LUZ1-`** (Deflate + Base32 Crockford + SHA-256).
  - Comprobación unificada al abrir la app (JSON remoto, ciclo solar y nueva versión en GitHub Releases).
- **Notificaciones locales, Widgets e Ícono dinámico**: avisos configurables (Sin antelación, 5, 10, 15, 30 min o 1 hora), barra inferior automática o píldora plegable translúcida, widgets de escritorio e ícono dinámico vinculado a *Mi vivienda*.

---

## Descarga e instalación (GitHub Releases)

Las compilaciones oficiales de este repositorio se distribuyen directamente en [**GitHub Releases**](https://github.com/willljp93/Luz-Isla/releases):

- `LuzIsla-v<versión>-arm64-v8a.apk` — Recomendado para la gran mayoría de teléfonos Android modernos (64 bits).
- `LuzIsla-v<versión>-armeabi-v7a.apk` — Para dispositivos ARM más antiguos (32 bits).
- `LuzIsla-v<versión>-universal.apk` — Compatible con cualquier arquitectura (archivo de mayor tamaño).
- `LuzIsla-v<versión>-sha256.txt` — Sumas de verificación SHA-256 de los instaladores.

> La propia app incluye en **Ajustes → Actualizaciones y descargas** la comprobación automática contra GitHub Releases y abre directamente el APK recomendado para la arquitectura de tu teléfono.

---

## Compatibilidad y requisitos

- **Android mínimo**: Android 7.0 Nougat (**API 24**).
- **SDK objetivo / compilación**: API **36**.
- **Sin dependencias propietarias de tiendas**: no utiliza Google Play Services, Firebase, anuncios ni permisos invasivos (solo red, notificaciones opcionales y arranque para reprogramar alertas).
- **Tecnologías**: Kotlin, Jetpack Compose (Material Design 3), MVVM, Coroutines/StateFlow, Room (KSP, esquema v3), DataStore Preferences, WorkManager y `desugar_jdk_libs` para `java.time`.

---

## Estructura del repositorio

```text
.
├── ConfCircuitos/                      # Configuraciones JSON públicas sincronizables por HTTPS
│   └── luz_isla_config.json
├── docs/                               # Documentación completa para usuarios y colaboradores
│   ├── GUIA_DE_USO.md
│   └── FUNCIONES_Y_ARQUITECTURA.md
├── .github/workflows/android-ci.yml    # CI/CD: pruebas, compilación APK y GitHub Release
├── app/                                # Código fuente Android (Kotlin + Jetpack Compose)
├── version.properties                  # Fuente de verdad del versionado YYYY.MM.DD.N
├── CHANGELOG.md                        # Historial de versiones y plantilla de futuros cambios
└── README.md
```

---

## Compilación y firma Release en GitHub Actions

### Requisitos locales

- JDK 21
- Android SDK (`compileSdk 36`)
- Gradle 9.3.1 (en GitHub Actions se configura automáticamente)

### Comandos principales

```bash
# Ejecutar pruebas unitarias y de Robolectric
gradle :app:testDebugUnitTest

# Compilar APK estándar (Android Studio / pruebas rápidas)
gradle :app:assembleDebug

# Compilar los 3 APKs por arquitectura (Universal, armeabi-v7a, arm64-v8a)
gradle :app:assembleDebug -PenableAbiSplits=true
gradle :app:assembleRelease -PenableAbiSplits=true
```

### Configuración en GitHub (Firma Release e ID de paquete)

El flujo `.github/workflows/android-ci.yml` soporta:

1. **Secrets** (**Settings → Secrets and variables → Actions → Secrets**):
   - `KEYSTORE_BASE64`: archivo `.jks` (con alias `upload`) codificado en Base64 en una sola línea.
   - `STORE_PASSWORD`: contraseña del almacén de llaves.
   - `KEY_PASSWORD`: contraseña de la llave privada.
   *(Si alguno falta, CI compila en modo `debug` como respaldo).*
2. **Variables** (**Settings → Secrets and variables → Actions → Variables**):
   - `CUSTOM_APP_ID`: *(opcional)* ID de paquete personalizado para los APK compilados en GitHub (por ejemplo `com.threeare.luzisla`). Cada segmento separado por puntos debe comenzar con una letra.

---

## Pruebas unitarias y de integración

Las pruebas se ejecutan en JVM mediante Robolectric sin necesidad de emulador ni ADB:

```bash
gradle :app:testDebugUnitTest
```

Incluye cobertura para `ScheduleEngineTest`, `TransferCodecTest`, `TimelineAxisTicksTest`, `AppUpdateServiceTest`, `SunriseSunsetServiceTest`, `SolarAndWidgetUiTest` y `ExampleRobolectricTest`.
