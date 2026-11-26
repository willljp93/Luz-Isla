# Guía de Uso – Luz Isla

Manual completo de todas las pantallas, controles y herramientas de **Luz Isla – Horarios Eléctricos**.

---

## Índice

1. [Asistente inicial (primer inicio)](#1-asistente-inicial-primer-inicio)
2. [Pantalla de Inicio](#2-pantalla-de-inicio)
3. [Línea de tiempo 24 h, Rueda 3D y Detalles](#3-línea-de-tiempo-24-h-rueda-3d-y-detalles)
4. [Calendario](#4-calendario)
5. [Circuitos y Programación](#5-circuitos-y-programación)
6. [Ajustes, Sincronización y Respaldos](#6-ajustes-sincronización-y-respaldos)
7. [Widgets e ícono dinámico](#7-widgets-e-ícono-dinámico)

---

## 1. Asistente inicial (primer inicio)

Al abrir la app por primera vez (o cuando eliges **Repetir asistente inicial** en Ajustes), verás una guía rápida de 3 pasos:

1. **Bienvenida y apariencia**: selecciona tema *Sistema*, *Claro* u *Oscuro* y consulta el resumen de capacidades offline.
2. **Zona horaria y formato de hora**: elige detección automática del sistema o zona horaria manual (por ejemplo `America/Havana`) y formato **12 horas (AM/PM)** o **24 horas**.
3. **Confirmación**: acceso directo a la pantalla principal con los circuitos iniciales ya listos.

---

## 2. Pantalla de Inicio

La pantalla **Inicio** está diseñada para saber en segundos qué circuitos tienen electricidad y cuánto falta para el próximo cambio:

- **Cabecera día/noche**: muestra la hora actual en vivo, la fecha, el indicador de cielo día/noche según los horarios solares configurados y el emblema reactivo de la Isla de la Juventud.
- **Resumen general**: conteo rápido de circuitos *Con servicio*, *Sin servicio* y *Sin prog.*
- **Búsqueda y filtros inteligentes**:
  - Barra de búsqueda por código oficial (ej. `A210`), nombre personalizado o localidad.
  - Chips con íconos (*Todos*, *Favoritos*, *Con servicio*, *Sin servicio*, *Sin info*). Los filtros que no tienen circuitos coincidentes se ocultan automáticamente para mantener limpia la interfaz.
- **Tarjetas de circuito**:
  - **Mi vivienda** y los circuitos marcados como **Favoritos** aparecen primero.
  - **Con servicio programado**: muestra **`Quedan HH:MM:SS`** hasta la próxima afectación.
  - **Sin servicio programado**: muestra **`Faltan HH:MM:SS`** hasta el próximo restablecimiento.
  - Toca cualquier tarjeta para abrir su hoja de **Detalles**.
- **Barra de navegación inferior**:
  - Puede configurarse en **Automático (scroll)** o como **Píldora plegable** translúcida que se oculta y deja una flecha discreta para recuperar el espacio vertical.

---

## 3. Línea de tiempo 24 h, Rueda 3D y Detalles

### Barra de 24 horas
- **Modo estándar (`00:00` – `24:00`)**: representa el día completo seleccionado de izquierda a derecha, con etiquetas claras (`12am`, `4am`, `8am`, `12pm`... o formato 24h) y marcas de cada **1 hora** en el borde inferior.
- **Modo centrado en la hora actual** *(activable en Ajustes → Línea de tiempo)*:
  - En el día de **Hoy**, muestra una ventana móvil desde **12 horas antes** hasta **12 horas después** de la hora actual, manteniendo el marcador blanco de **Ahora** fijo al centro.
  - Las etiquetas muestran horas exactas en punto (sin minutos) y alternan el contraste de tono cuando la ventana cruza la medianoche (`12am` / `00h`) para distinguir con facilidad un día del siguiente.

### Hoja de Detalles y Rueda 3D
Al tocar un circuito (o pulsar **Detalles**) se abre su vista extendida:
- **Rueda cilíndrica 3D continua**: desliza horizontalmente para recorrer días anteriores o futuros y ver cómo cambia la programación.
- **Sincronización con el selector de fechas**: mover la rueda 3D actualiza automáticamente la fecha seleccionada arriba, y cambiar la fecha en el selector mueve la rueda 3D al mismo día.
- Lista de segmentos exactos del día, regla aplicada (*Excepción manual*, *Horario por fecha*, *Relación sincronizada*, *Horario fijo*, *Grupo rotativo*) y relaciones asociadas.

---

## 4. Calendario

La pestaña **Calendario** permite planificar por fecha o por circuito:

- **Selector superior de fecha** (con botones rápido a días cercanos y selector de calendario) y filtros con íconos:
  - *Favoritos*
  - *Con servicio*
  - *Sin servicio*
  - *Sin info*
  - *Fijos* (circuitos con regla fija o de grupos fijos)
- **Modos de vista**:
  - **Día**: estado y línea de tiempo de cada circuito filtrado para la fecha elegida.
  - **Semana**: resumen visual de los próximos 7 días.
  - **Por circuito**: foco en un circuito específico junto con su rueda 3D.
  - **Grupos**: resumen ordenado de todos los grupos rotativos y sus horarios del día.

---

## 5. Circuitos y Programación

La pestaña **Circuitos** mantiene fija su barra superior al hacer scroll y se divide en dos áreas:

### A. Edición de circuitos
- Crea circuitos nuevos o edita existentes: código oficial, nombre personalizado, comentario/localidad, estado activo, favorito y **Mi vivienda** (circuito principal).
- Filtra rápidamente con los chips centrados (*Todos*, grupos existentes y *Fijos*).
- Asigna la modalidad de programación:
  - **Fijo**: sigue reglas semanales fijas.
  - **Rotativo**: pertenece a un grupo dentro de un ciclo rotativo.
  - **Por fecha**: se rige por bloques asignados en fechas puntuales.
  - **Sin prog.**: sin horario base asignado.

### B. Programación
Contiene 5 subsecciones:

1. **Ciclos**: define la duración con servicio, la duración sin servicio, la fecha/hora de referencia y el orden de los grupos; el sistema calcula automáticamente el desfase de cada grupo.
2. **Grupos**: crea o edita grupos rotativos, ajusta su fase o asigna varios circuitos a la vez.
3. **Fijos**: reglas por días de la semana (`L M X J V S D`), hora de inicio/fin (incluyendo cruce de medianoche) y vigencia opcional por rango de fechas.
4. **Excepciones**: anulaciones puntuales de **1 día** o por **rango de fechas (Desde / Hasta)** como un registro único, indicando si el circuito estará *Con servicio* o *Sin servicio* y el motivo.
5. **Relaciones**: vincula un **circuito dependiente** con un **circuito referencia**. Existen 5 tipos:

| Tipo | Con sincronización activa | Uso típico |
| :--- | :--- | :--- |
| **Mismo horario** | Copia exactamente los bloques con y sin servicio del circuito referencia. | Ramales o subcircuitos acoplados al mismo alimentador. |
| **Horario inverso** | Invierte el estado del circuito referencia (cuando el referencia tiene servicio, el dependiente queda sin servicio, y viceversa). | Circuitos en alternancia directa o transferencia opuesta. |
| **Dependiente** | Solo tiene servicio en los intervalos donde el circuito referencia tiene servicio (útil para cargas subordinadas). | Subestaciones o circuitos que dependen de otro principal. |
| **Personalizada** | Sigue los intervalos del circuito referencia manteniendo tus notas y motivo propios. | Casos especiales documentados por el usuario. |
| **Informativa** | No modifica la programación; únicamente muestra el vínculo en los **Detalles** del circuito. | Notas de vecindad o respaldo eléctrico manual. |

> **Prioridad de las relaciones:** cuando una relación tiene activada **Aplicar sincronización automática**, tiene prioridad sobre el grupo rotativo o la regla fija del circuito dependiente, pero siempre respeta por encima las **Excepciones manuales** y los **Horarios por fecha**.

---

## 6. Ajustes, Sincronización y Respaldos

- **Estado de la aplicación y configuración**: muestra versión instalada, compilación y versión de datos activos.
- **Zona horaria y formato de hora**: automático o selección manual (GMT / ciudades) y modo 12h AM/PM o 24h.
- **Línea de tiempo**: activa o desactiva *Centrar línea de tiempo en la hora actual* (ventana móvil ±12h en Hoy).
- **Tema, animaciones y barra**: tema Claro/Oscuro/Sistema, color dinámico (Android 12+), tarjetas compactas en Inicio, animaciones eléctricas sutiles y comportamiento de la barra inferior (*Automático* o *Píldora plegable*).
- **Ciclo solar, ícono y bienvenida**: sincronización automática diaria de amanecer/atardecer con `sunrisesunset.io` para Nueva Gerona (o ajuste manual), ícono dinámico del lanzador y acceso para repetir el asistente.
- **Comprobación unificada**: botón y opción automática al abrir la app para validar en un solo paso la configuración JSON remota, el ciclo solar y nuevas versiones en GitHub Releases.
- **Sincronización por URL (HTTPS)**:
  - URL predeterminada: `https://raw.githubusercontent.com/willljp93/Luz-Isla/main/ConfCircuitos/luz_isla_config.json`.
  - Acepta también enlaces `github.com/.../blob/...` (los convierte automáticamente a `raw` y codifica espacios).
  - Crea un respaldo local automático antes de aplicar cambios y **conserva siempre** tus favoritos, *Mi vivienda*, nombres personalizados y comentarios.
- **Exportar e Importar (JSON y Código)**:
  - Exporta archivos `.json` con versionado automático diario (`YYYY.MM.DD.N`) o códigos compactos `LUZ1-...` listos para copiar/compartir por WhatsApp o Telegram.
  - Incluye botón **Restaurar respaldo anterior** para revertir la última importación/sincronización.
- **Notificaciones locales**: alertas antes del inicio del servicio, fin del servicio o próximo restablecimiento (5, 10, 15 o 30 min antes, solo favoritos o todos).
- **Actualizaciones y descargas**: verifica nuevas versiones en **GitHub Releases**, muestra las notas limpias de la versión disponible y abre el APK correspondiente a la arquitectura del dispositivo.

---

## 7. Widgets e ícono dinámico

- **Widget Favorito / Mi vivienda**: muestra automáticamente el estado de tu vivienda principal o primer favorito.
- **Widget Configurable**: al añadirlo a la pantalla de inicio te permite elegir el circuito específico y el estilo visual de tarjeta (*Automático*, *Claro*, *Oscuro*, *Transparente*).
- **Ícono dinámico del lanzador**: cuando está activado en Ajustes, el ícono de la app en el escritorio cambia según el estado actual de *Mi vivienda* (con servicio, sin servicio o neutral).
