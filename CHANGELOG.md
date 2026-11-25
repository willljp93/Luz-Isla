# Changelog – Luz Isla

Todos los cambios relevantes de **Luz Isla – Horarios Eléctricos** se documentan en este archivo.
El proyecto sigue el esquema de versionado **`YYYY.MM.DD.N`** definido en `version.properties` (con `versionCode` compacto `YYMMDDNN`).

---

## [Unreleased]

<!--
Plantilla para futuras versiones (copia este bloque debajo de [Unreleased] al publicar):

## [YYYY.MM.DD.N] - YYYY-MM-DD (`Build YYMMDDNN`)
### Agregado
- ...

### Cambiado
- ...

### Corregido
- ...

### Eliminado
- ...
-->

### Preparado para próximas versiones
- Plantilla de notas limpia compatible con GitHub Releases y el lector de actualizaciones dentro de la app.

---

## [2026.11.25.2] - 2026-11-25 (`Build 26112502`)

### Agregado
- Carpeta dedicada `Conf Circuitos/` en el repositorio para alojar la configuración JSON comunitaria (`Conf Circuitos/luz_isla_config_2026.11.24.1.json`).
- Documentación completa para GitHub en `README.md`, `docs/GUIA_DE_USO.md`, `docs/FUNCIONES_Y_ARQUITECTURA.md` y este `CHANGELOG.md` con sección preparada para futuros lanzamientos.
- En la barra de 24 horas: marcas verticales (ticks) cada **1 hora** en el borde inferior (mayores cada 4 h y menores cada 1 h) y diferenciación visual de tono al cruzar la medianoche (`12am` / `00h`) para distinguir dos días consecutivos.
- Soporte en `.github/workflows/android-ci.yml` para definir un `applicationId` personalizado en CI mediante la variable `CUSTOM_APP_ID` (ej. `com.threeare.luzisla`) y mostrarlo en las notas de Release.

### Cambiado
- **Actualización de ruta JSON por defecto**: `HttpsSyncManager` ahora apunta a `https://raw.githubusercontent.com/willljp93/Luz-Isla/main/Conf%20Circuitos/luz_isla_config_2026.11.24.1.json`, codifica espacios automáticamente en URLs copiadas y migra de forma transparente la URL heredada de la raíz del repositorio.
- **Nombres de APK limpios en GitHub Releases**: los instaladores pasan a llamarse `LuzIsla-v<versión>-<arquitectura>.apk` (sin sufijos `-b<build>` ni `-debug`/`-release`).
- **Ajustes y línea de tiempo**: cuando *Centrar línea de tiempo en la hora actual* está activo, las etiquetas muestran únicamente horas exactas en punto (`2pm`, `6pm` o `14h`, `18h`) manteniendo el cursor blanco en el minuto real.
- **Contadores de estado**: las tarjetas y widgets muestran **`Quedan`** mientras el circuito tiene servicio y **`Faltan`** cuando está sin servicio.
- **Unificación de textos**: se ajustó la terminología general de la interfaz hacia *horarios y reglas programadas* y *horario por fecha*.

### Corregido
- Eliminada la opción duplicada *Centrar línea de tiempo en la hora actual* dentro de *Zona horaria y formato de hora* (conservándola únicamente en *Línea de tiempo / Apariencia*).
- Corregida la maquetación en *Actualizaciones y descargas* y *Comprobación unificada* para evitar textos partidos letra por letra o recortados, y limpieza automática de sintaxis Markdown cruda (`**`, `` ` ``, encabezados) en las notas de Release.

### Eliminado
- Retirado el canal *Google Play Store (Próximamente)* de la sección de canales de descarga para centrar la distribución en **GitHub Releases**.

---

## [2026.11.25.1] - 2026-11-25 (`Build 26112501`)

### Agregado
- Sincronización bidireccional entre el selector de fechas y la rueda cilíndrica 3D continua al deslizar días pasados o futuros.
- Barra superior de selector de fechas fija en la pantalla de **Circuitos** al hacer scroll.
- Explicación detallada dentro del editor de **Relaciones** para cada tipo de relación (*Mismo horario*, *Horario inverso*, *Dependiente*, *Personalizada*, *Informativa*) y su prioridad frente a grupos o reglas fijas.
- Íconos sencillos en todos los chips de filtro que no tenían ícono y centrado horizontal uniforme cuando caben en el ancho de pantalla.

### Cambiado
- Renombrado el botón **"Ver 3D"** a **"Detalles"** en los circuitos.
- Renombrado el chip **"Todos los grupos"** a **"Todos"** en *Edición de circuitos*.

### Corregido
- Eliminada la franja negra superior residual que aparecía en **Circuitos** y **Ajustes**.
- Ajustado el control en **Ajustes** para alternar *Centrar línea de tiempo en la hora actual*.

---

## [2026.11.24.1] - 2026-11-24 (`Build 26112401`)

### Agregado
- Archivo de configuración comunitaria inicial `luz_isla_config_2026.11.24.1.json` con los 19 circuitos, 4 grupos, ciclo rotativo y reglas fijas configurado como URL predeterminada de GitHub.
- Filtros completos con íconos en **Calendario** (*Favoritos*, *Con servicio*, *Sin servicio*, *Sin info* y *Fijos*), además de chip *Fijos* en Circuitos.
- Soporte para **Excepciones manuales con rango de fechas (Desde / Hasta)** guardadas como un único registro (migración de esquema Room v2 → v3).
- Degradado de cielo día/noche en cabeceras y tarjetas de circuitos sincronizado con horarios solares reales de `sunrisesunset.io`.
- Opción **Centrar línea de tiempo en la hora actual** (ventana móvil de 12 h antes y 12 h después en *Hoy*).
- Indicadores `am` / `pm` en las etiquetas de la línea de tiempo de 24 horas.

### Cambiado
- Píldora plegable de la barra inferior rediseñada con mayor transparencia y suavidad visual, especialmente al estar oculta.
- Ocultación automática de chips de filtro en Inicio cuando no existen circuitos que coincidan con ese criterio.

### Corregido
- Corregida la evaluación y aplicación de **Relaciones sincronizadas** en `ScheduleEngine` para que tengan precedencia sobre el grupo rotativo o regla fija del circuito dependiente.

---

## [2026.11.23.1] - 2026-11-23 (`Build 26112301`)

### Agregado
- Sistema de versionado diario unificado `YYYY.MM.DD.N` (`version.properties`) sincronizado entre compilaciones, exportaciones JSON y Releases.
- Comprobación unificada al abrir la app (sincronización JSON HTTPS, ciclo solar `sunrisesunset.io` y búsqueda de nuevas versiones en GitHub Releases).
- Servicio `AppUpdateService` para detectar nuevas versiones en GitHub Releases y seleccionar automáticamente el APK adecuado según las ABIs del dispositivo.

---

## [1.0.0] - Lanzamiento inicial

### Agregado
- Base de datos local Room con los **19 circuitos** iniciales de la Isla de la Juventud, grupos rotativos `4h / 12h`, horarios fijos, excepciones y relaciones.
- Motor de cálculo determinista `ScheduleEngine` 100 % offline con intervalos `[inicio, fin)` y soporte de zona horaria `America/Havana` o del sistema.
- Pantallas **Inicio**, **Calendario**, **Circuitos**, **Ajustes** y asistente de bienvenida (**Onboarding**).
- Exportación/importación en **JSON** (con suma SHA-256) y códigos alfanuméricos compactos **`LUZ1-`** (Deflate + Base32 Crockford) con respaldo local previo y preservación de datos personales.
- Notificaciones locales anticipadas (5, 10, 15, 30 min), widgets configurables de escritorio e ícono dinámico del lanzador vinculado a *Mi vivienda*.
