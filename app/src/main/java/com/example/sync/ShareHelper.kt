package com.example.sync

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.engine.ScheduleEngine
import com.example.domain.model.CircuitStatusEvaluation
import com.example.domain.model.ScheduledState
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ShareHelper {

    fun buildSingleCircuitMessage(
        eval: CircuitStatusEvaluation,
        use24h: Boolean = false,
        zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE
    ): String {
        val header = "⚡ *Luz Isla – Circuito ${eval.circuit.displayTitle()}*"
        val stateLabel = when (eval.state) {
            ScheduledState.SERVICE_SCHEDULED -> "✅ Estado programado: CON SERVICIO"
            ScheduledState.OUTAGE_SCHEDULED -> "🔌 Estado programado: SIN SERVICIO (Afectado)"
            ScheduledState.UNKNOWN -> "❔ Estado programado: Sin información disponible"
        }
        val currentWindow = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
            "🕒 Período actual: ${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
        } else {
            "🕒 Período actual: Según programación"
        }
        val remaining = eval.remainingMillis?.let {
            "⏳ Tiempo restante: ${ScheduleEngine.formatDurationHms(it)}"
        } ?: ""
        val nextService = if (eval.nextServiceStartMillis != null && eval.nextServiceEndMillis != null) {
            "🔜 Próximo servicio: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(eval.nextServiceEndMillis, use24h, zoneId)}"
        } else {
            "🔜 Próximo servicio: Sin datos registrados"
        }
        val comment = if (eval.circuit.comment.isNotBlank()) "📍 ${eval.circuit.comment}" else ""
        return listOf(header, comment, stateLabel, currentWindow, remaining, nextService, "\n_Horario programado, sujeto a cambios de red._")
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun buildListMessage(
        title: String,
        evaluations: List<CircuitStatusEvaluation>,
        use24h: Boolean = false,
        zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE
    ): String {
        val today = LocalDate.now(zoneId)
        val header = "⚡ *Luz Isla – $title*\n📅 ${ScheduleEngine.formatDateEs(today)} (Hora local)\n"
        if (evaluations.isEmpty()) {
            return "$header\nNo hay circuitos en esta selección."
        }
        val lines = evaluations.joinToString("\n") { eval ->
            val icon = when (eval.state) {
                ScheduledState.SERVICE_SCHEDULED -> "✅"
                ScheduledState.OUTAGE_SCHEDULED -> "🔴"
                ScheduledState.UNKNOWN -> "⚪"
            }
            val stateWord = when (eval.state) {
                ScheduledState.SERVICE_SCHEDULED -> "Con servicio"
                ScheduledState.OUTAGE_SCHEDULED -> "Sin servicio"
                ScheduledState.UNKNOWN -> "Sin info"
            }
            val window = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                " (${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)}–${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)})"
            } else ""
            val next = if (eval.state != ScheduledState.SERVICE_SCHEDULED && eval.nextServiceStartMillis != null) {
                " | Próx: ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}"
            } else ""
            "$icon *${eval.circuit.displayTitle()}*: $stateWord$window$next"
        }
        return "$header\n$lines\n\n_Generado desde Luz Isla (herramienta comunitaria)._"
    }

    fun buildUpcomingMessage(
        evaluations: List<CircuitStatusEvaluation>,
        use24h: Boolean = false,
        zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE
    ): String {
        val header = "🕒 *Luz Isla – Próximos horarios de servicio*\n"
        val lines = evaluations.joinToString("\n") { eval ->
            val nextStr = if (eval.nextServiceStartMillis != null && eval.nextServiceEndMillis != null) {
                val startDay = Instant.ofEpochMilli(eval.nextServiceStartMillis).atZone(zoneId).toLocalDate()
                "${ScheduleEngine.formatShortDate(startDay)} ${ScheduleEngine.formatTime(eval.nextServiceStartMillis, use24h, zoneId)}–${ScheduleEngine.formatTime(eval.nextServiceEndMillis, use24h, zoneId)}"
            } else {
                "Sin horario próximo"
            }
            "• *${eval.circuit.displayTitle()}*: $nextStr"
        }
        return "$header\n$lines"
    }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareSummaryImage(
        context: Context,
        title: String,
        evaluations: List<CircuitStatusEvaluation>,
        use24h: Boolean = false,
        zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE
    ) {
        val uri = renderSummaryBitmapToUri(context, title, evaluations, use24h, zoneId)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$title — Luz Isla")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir imagen resumen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun renderSummaryBitmapToUri(
        context: Context,
        title: String,
        evaluations: List<CircuitStatusEvaluation>,
        use24h: Boolean = false,
        zoneId: ZoneId = ScheduleEngine.HAVANA_ZONE
    ): Uri {
        val items = evaluations.take(18)
        val width = 1080
        val rowHeight = 96
        val headerHeight = 210
        val footerHeight = 90
        val height = headerHeight + (items.size.coerceAtLeast(1) * rowHeight) + footerHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F5F8F6"))

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B6E4F") }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8EFE6")
            textSize = 28f
        }
        val now = Instant.now().atZone(zoneId)
        canvas.drawText("Luz Isla – $title", 48f, 88f, titlePaint)
        canvas.drawText(
            "${ScheduleEngine.formatDateEs(now.toLocalDate())} • ${ScheduleEngine.formatTime(now.toInstant().toEpochMilli(), use24h, zoneId)}",
            48f,
            142f,
            subtitlePaint
        )

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE6E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#11211C")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D5E58")
            textSize = 24f
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        items.forEachIndexed { index, eval ->
            val top = headerHeight + 18f + index * rowHeight
            val rect = RectF(36f, top, width - 36f, top + rowHeight - 14f)
            canvas.drawRoundRect(rect, 18f, 18f, cardPaint)
            canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

            canvas.drawText(eval.circuit.displayTitle().take(28), 64f, top + 38f, codePaint)
            val subText = if (eval.currentIntervalStartMillis != null && eval.currentIntervalEndMillis != null) {
                "${ScheduleEngine.formatTime(eval.currentIntervalStartMillis, use24h, zoneId)} – ${ScheduleEngine.formatTime(eval.currentIntervalEndMillis, use24h, zoneId)}"
            } else {
                "Sin intervalo registrado"
            }
            canvas.drawText(subText, 64f, top + 68f, detailPaint)

            val (badgeColor, badgeLabel) = when (eval.state) {
                ScheduledState.SERVICE_SCHEDULED -> Color.parseColor("#0B6E4F") to "CON SERVICIO"
                ScheduledState.OUTAGE_SCHEDULED -> Color.parseColor("#C63D2F") to "SIN SERVICIO"
                ScheduledState.UNKNOWN -> Color.parseColor("#606D76") to "SIN INFO"
            }
            badgePaint.color = badgeColor
            val badgeRect = RectF(width - 310f, top + 18f, width - 58f, top + 64f)
            canvas.drawRoundRect(badgeRect, 12f, 12f, badgePaint)
            canvas.drawText(badgeLabel, badgeRect.centerX(), badgeRect.centerY() + 8f, badgeTextPaint)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5C6C66")
            textSize = 22f
        }
        canvas.drawText(
            "Estado programado de referencia • Herramienta comunitaria independiente",
            48f,
            height - 34f,
            footerPaint
        )

        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val outFile = File(dir, "luz_isla_resumen_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }
}
