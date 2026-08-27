package com.sitecam.app.core.watermark.engine

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sitecam.app.core.watermark.model.BuiltInWatermarkFieldKeys
import com.sitecam.app.core.watermark.model.WatermarkData
import kotlin.math.min

data class WatermarkTextLine(
    val label: String,
    val value: String,
    val x: Float,
    val y: Float,
    val textSize: Float,
    val textColor: Int,
    val isBold: Boolean = false,
    val isHighlight: Boolean = false
)

data class WatermarkLayoutResult(
    val cardRect: RectF,
    val accentBarRect: RectF?,
    val headerRect: RectF?,
    val lines: List<WatermarkTextLine>,
    val cardColor: Int,
    val accentColor: Int = Color.parseColor("#FFB300"),
    val opacity: Float
)

object WatermarkLayoutEngine {

    private data class RuntimeTextMetrics(
        val top: Float,
        val bottom: Float
    ) {
        val height: Float get() = bottom - top
        val baselineOffset: Float get() = -top
    }

    private data class CompactRow(
        val label: String,
        val value: String,
        val isFirst: Boolean
    )

    private data class CompactRowsMeasurement(
        val rows: List<CompactRow>,
        val metrics: List<RuntimeTextMetrics>,
        val paddingX: Float,
        val paddingY: Float,
        val lineSpacing: Float,
        val margin: Float,
        val maxAllowedCardWidth: Float,
        val maxMeasuredWidth: Float,
        val requiredHeight: Float
    )

    private fun measurePaint(textSize: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Measure with the same runtime Typeface/paint family used by the renderers. */
    fun measureStringWidth(text: String, textSize: Float): Float =
        measureTextWidth(text, textSize, bold = false)

    private fun measureTextWidth(text: String, textSize: Float, bold: Boolean): Float =
        measurePaint(textSize, bold).measureText(text)

    private fun labeledLineWidth(
        label: String,
        value: String,
        textSize: Float,
        valueBold: Boolean = false
    ): Float = measureTextWidth(label, textSize, bold = label.isNotEmpty()) +
        measureTextWidth(value, textSize, bold = valueBold)

    private fun runtimeTextMetrics(textSize: Float, includeBold: Boolean): RuntimeTextMetrics {
        val normal = measurePaint(textSize, bold = false).fontMetrics
        if (!includeBold) return RuntimeTextMetrics(normal.top, normal.bottom)
        val bold = measurePaint(textSize, bold = true).fontMetrics
        return RuntimeTextMetrics(
            top = minOf(normal.top, bold.top),
            bottom = maxOf(normal.bottom, bold.bottom)
        )
    }

    fun calculateLayout(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData
    ): WatermarkLayoutResult {
        return when (data.styleType) {
            "MINIMAL" -> layoutMinimal(canvasWidth, canvasHeight, data)
            "INFO_BOARD" -> layoutInfoBoard(canvasWidth, canvasHeight, data)
            else -> layoutClassic(canvasWidth, canvasHeight, data)
        }
    }

    private fun layoutClassic(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData
    ): WatermarkLayoutResult {
        val baseDimension = min(canvasWidth, canvasHeight)
        // Expanded font size scale from 0.6x to 2.0x
        val scale = (baseDimension / 1080f).coerceAtLeast(0.45f) * data.fontSizeScale.coerceIn(0.5f, 2.2f)
        val margin = (data.marginDp * 3f * scale).coerceAtLeast(16f)
        val paddingX = 26f * scale
        val paddingY = 22f * scale
        val titleTextSize = 34f * scale
        val bodyTextSize = 26f * scale
        val lineSpacing = 14f * scale
        val totalLineHeight = bodyTextSize + lineSpacing

        val rawLines = mutableListOf<Pair<String, String>>()
        if (BuiltInWatermarkFieldKeys.PROJECT_NAME in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_NAME)?.let { rawLines.add("工程名称" to it) }
        }
        if (BuiltInWatermarkFieldKeys.PROJECT_CATEGORY in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_CATEGORY)?.takeIf { it.isNotBlank() }?.let { rawLines.add("工程类型" to it) }
        }
        if (BuiltInWatermarkFieldKeys.DATE_TIME in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.DATE_TIME)?.let { rawLines.add("拍摄时间" to it) }
        }
        if (BuiltInWatermarkFieldKeys.ADDRESS in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.ADDRESS)?.takeIf { it.isNotBlank() }?.let { rawLines.add("拍摄地点" to it) }
        }
        // Address and GPS are independent switches.  A GPS-only template
        // must still show coordinates when no address is available.
        if (BuiltInWatermarkFieldKeys.GPS in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.GPS)?.takeIf { it.isNotBlank() }?.let { rawLines.add("GPS坐标" to it) }
        }
        if (BuiltInWatermarkFieldKeys.USER_NAME in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.USER_NAME)?.takeIf { it.isNotBlank() }?.let { rawLines.add("拍摄人员" to it) }
        }
        for (field in data.customFields) {
            if (field.isEnabled && field.value.isNotBlank()) {
                rawLines.add(field.label to field.value)
            }
        }

        // Allow card width to adapt up to 92% of the canvas width
        val maxAllowedCardWidth = (canvasWidth * 0.92f).coerceAtLeast(baseDimension * 0.60f)
        val maxAvailableTextWidth = maxAllowedCardWidth - paddingX * 2 - 16f * scale

        // Split/wrap long lines using exact string pixel measurements
        val formattedLines = mutableListOf<Triple<String, String, Boolean>>() // label, value, isFirst

        for (index in rawLines.indices) {
            val (label, value) = rawLines[index]
            val isFirst = index == 0
            val labelPrefix = if (label.isNotEmpty()) "$label: " else ""
            val activeTextSize = if (isFirst) titleTextSize else bodyTextSize
            val labelWidth = measureTextWidth(labelPrefix, activeTextSize, bold = labelPrefix.isNotEmpty())

            if (isFirst && (labelWidth + measureTextWidth(value, titleTextSize, bold = true)) > maxAvailableTextWidth) {
                // The project title is highlighted, but it must not punch
                // through the card on narrow/portrait output. Keep at most two
                // title lines and use an ellipsis for the remainder.
                val firstWidth = (maxAvailableTextWidth - labelWidth).coerceAtLeast(titleTextSize * 2f)
                val first = takePrefixByWidth(value, firstWidth, titleTextSize, bold = true)
                val remaining = value.removePrefix(first)
                formattedLines.add(Triple(labelPrefix, first.ifBlank { "…" }, true))
                if (remaining.isNotBlank()) {
                    formattedLines.add(
                        Triple(
                            "       ",
                            ellipsizeByWidth(remaining, maxAvailableTextWidth, bodyTextSize, bold = false),
                            false
                        )
                    )
                }
            } else if (!isFirst && (labelWidth + measureTextWidth(value, bodyTextSize, bold = false)) > maxAvailableTextWidth) {
                // Multi-line wrap for long value (e.g. detailed project address or long notes)
                val availableFirstLineWidth = (maxAvailableTextWidth - labelWidth).coerceAtLeast(bodyTextSize * 4f)
                var remaining = value
                var isFirstChunk = true
                val wrappedStart = formattedLines.size

                while (remaining.isNotEmpty()) {
                    val allowedWidth = if (isFirstChunk) availableFirstLineWidth else maxAvailableTextWidth
                    val chunk = takePrefixByWidth(remaining, allowedWidth, bodyTextSize, bold = false)
                    formattedLines.add(Triple(if (isFirstChunk) labelPrefix else "       ", chunk, false))
                    remaining = remaining.removePrefix(chunk)
                    isFirstChunk = false
                }
                avoidSingleCharacterTailClassic(formattedLines, wrappedStart)
            } else {
                formattedLines.add(Triple(labelPrefix, value, isFirst))
            }
        }

        formattedLines.replaceAll { (label, value, isFirst) ->
            val textSize = if (isFirst) titleTextSize else bodyTextSize
            val valueBold = isFirst
            boundLabeledLine(label, value, maxAvailableTextWidth, textSize, valueBold)
                .let { (boundedLabel, boundedValue) -> Triple(boundedLabel, boundedValue, isFirst) }
        }

        // Compute actual max text width across all lines to size card tightly
        var maxMeasuredLineWidth = 0f
        val lineMetrics = formattedLines.map { (label, value, isFirst) ->
            val textSize = if (isFirst) titleTextSize else bodyTextSize
            val totalW = labeledLineWidth(label, value, textSize, valueBold = isFirst)
            if (totalW > maxMeasuredLineWidth) maxMeasuredLineWidth = totalW
            runtimeTextMetrics(textSize, includeBold = label.isNotEmpty() || isFirst)
        }

        val cardWidth = (maxMeasuredLineWidth + paddingX * 2 + 16f * scale).coerceIn(
            baseDimension * 0.45f,
            maxAllowedCardWidth
        )
        val contentHeight = lineMetrics.sumOf { it.height.toDouble() }.toFloat() +
            lineSpacing * (lineMetrics.size - 1).coerceAtLeast(0)
        val availableHeight = (canvasHeight - margin * 2).coerceAtLeast(1f)
        val requiredHeight = paddingY * 2 + contentHeight
        if (requiredHeight > availableHeight) {
            // Wrapping is useful on a normal canvas, but clipping rows here
            // silently removed GPS and later custom fields on short previews.
            // Rebuild one compact row per logical field and reduce its scale
            // until every field fits.
            return layoutClassicCompact(canvasWidth, canvasHeight, data, rawLines)
        }
        val cardHeight = requiredHeight
        val (cardLeft, cardTop) = boundedCardPosition(
            canvasWidth, canvasHeight, cardWidth, cardHeight, margin, data.position
        )

        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val accentBarRect = RectF(cardLeft, cardTop, cardLeft + 8f * scale, cardTop + cardHeight)

        val resultLines = mutableListOf<WatermarkTextLine>()
        var currentTop = cardTop + paddingY

        for ((index, line) in formattedLines.withIndex()) {
            val (label, value, isFirst) = line
            val x = cardLeft + paddingX + 8f * scale
            val metrics = lineMetrics[index]
            resultLines.add(
                WatermarkTextLine(
                    label = label,
                    value = value,
                    x = x,
                    y = currentTop + metrics.baselineOffset,
                    textSize = if (isFirst) titleTextSize else bodyTextSize,
                    textColor = if (isFirst) Color.parseColor("#FFD54F") else Color.WHITE,
                    isBold = isFirst,
                    isHighlight = isFirst
                )
            )
            currentTop += metrics.height + lineSpacing
        }

        val cardColor = Color.argb((data.opacity * 255).toInt(), 18, 18, 18)

        return WatermarkLayoutResult(
            cardRect = cardRect,
            accentBarRect = accentBarRect,
            headerRect = null,
            lines = resultLines,
            cardColor = cardColor,
            opacity = data.opacity
        )
    }

    /**
     * Short-canvas fallback for the classic style. It deliberately creates
     * exactly one row for each populated logical field, then adapts the
     * rendered scale instead of dropping rows from the bottom of the card.
     */
    private fun layoutClassicCompact(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData,
        rawLines: List<Pair<String, String>>
    ): WatermarkLayoutResult {
        val baseDimension = min(canvasWidth, canvasHeight)
        val initialScale = (baseDimension / 1080f).coerceAtLeast(0.45f) *
            data.fontSizeScale.coerceIn(0.5f, 2.2f)
        fun measure(scale: Float): CompactRowsMeasurement {
            val margin = (data.marginDp * 3f * scale).coerceAtLeast(16f)
            val paddingX = 26f * scale
            val paddingY = 22f * scale
            val titleTextSize = 34f * scale
            val bodyTextSize = 26f * scale
            val lineSpacing = 14f * scale
            val maxAllowedCardWidth = (canvasWidth * 0.92f).coerceAtLeast(baseDimension * 0.60f)
            val maxTextWidth = (maxAllowedCardWidth - paddingX * 2 - 16f * scale)
                .coerceAtLeast(1f)
            val rows = rawLines.mapIndexed { index, (label, value) ->
                val isFirst = index == 0
                val textSize = if (isFirst) titleTextSize else bodyTextSize
                val labelPrefix = if (label.isNotEmpty()) "$label: " else ""
                val bounded = boundLabeledLine(
                    label = labelPrefix,
                    value = value,
                    maxWidth = maxTextWidth,
                    textSize = textSize,
                    valueBold = isFirst
                )
                CompactRow(bounded.first, bounded.second, isFirst)
            }
            val metrics = rows.map { row ->
                runtimeTextMetrics(
                    if (row.isFirst) titleTextSize else bodyTextSize,
                    includeBold = row.label.isNotEmpty() || row.isFirst
                )
            }
            val maxMeasuredWidth = rows.zip(metrics).maxOfOrNull { (row, _) ->
                labeledLineWidth(
                    row.label,
                    row.value,
                    if (row.isFirst) titleTextSize else bodyTextSize,
                    valueBold = row.isFirst
                )
            } ?: 0f
            val requiredHeight = paddingY * 2 +
                metrics.sumOf { it.height.toDouble() }.toFloat() +
                lineSpacing * (metrics.size - 1).coerceAtLeast(0)
            return CompactRowsMeasurement(
                rows = rows,
                metrics = metrics,
                paddingX = paddingX,
                paddingY = paddingY,
                lineSpacing = lineSpacing,
                margin = margin,
                maxAllowedCardWidth = maxAllowedCardWidth,
                maxMeasuredWidth = maxMeasuredWidth,
                requiredHeight = requiredHeight
            )
        }
        val scale = fitScale(
            initialScale = initialScale,
            minScale = 0.01f,
            canvasHeight = canvasHeight,
            measure = { candidateScale -> measure(candidateScale).requiredHeight },
            margin = { candidateScale -> measure(candidateScale).margin }
        )
        val measured = measure(scale)
        val cardWidth = (measured.maxMeasuredWidth + measured.paddingX * 2 + 16f * scale)
            .coerceIn(baseDimension * 0.45f, measured.maxAllowedCardWidth)
        val availableHeight = (canvasHeight - measured.margin * 2).coerceAtLeast(1f)
        val cardHeight = measured.requiredHeight.coerceAtMost(availableHeight)
        val (cardLeft, cardTop) = boundedCardPosition(
            canvasWidth,
            canvasHeight,
            cardWidth,
            cardHeight,
            measured.margin,
            data.position
        )
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val accentBarRect = RectF(cardLeft, cardTop, cardLeft + 8f * scale, cardTop + cardHeight)
        val resultLines = mutableListOf<WatermarkTextLine>()
        var currentTop = cardTop + measured.paddingY
        for (index in measured.rows.indices) {
            val row = measured.rows[index]
            val metrics = measured.metrics[index]
            resultLines += WatermarkTextLine(
                label = row.label,
                value = row.value,
                x = cardLeft + measured.paddingX + 8f * scale,
                y = currentTop + metrics.baselineOffset,
                textSize = if (row.isFirst) 34f * scale else 26f * scale,
                textColor = if (row.isFirst) Color.parseColor("#FFD54F") else Color.WHITE,
                isBold = row.isFirst,
                isHighlight = row.isFirst
            )
            currentTop += metrics.height + measured.lineSpacing
        }
        return WatermarkLayoutResult(
            cardRect = cardRect,
            accentBarRect = accentBarRect,
            headerRect = null,
            lines = resultLines,
            cardColor = Color.argb((data.opacity * 255).toInt(), 18, 18, 18),
            opacity = data.opacity
        )
    }

    private fun layoutMinimal(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData
    ): WatermarkLayoutResult {
        val baseDimension = min(canvasWidth, canvasHeight)
        val scale = (baseDimension / 1080f).coerceAtLeast(0.45f) * data.fontSizeScale.coerceIn(0.5f, 2.2f)
        val margin = (data.marginDp * 3f * scale).coerceAtLeast(16f)
        val paddingX = 22f * scale
        val paddingY = 18f * scale
        val textSize = 26f * scale
        val lineSpacing = 12f * scale

        val rawLines = buildList {
            if (BuiltInWatermarkFieldKeys.PROJECT_NAME in data.enabledSystemFields) data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_NAME)?.let { add(it) }
            if (BuiltInWatermarkFieldKeys.PROJECT_CATEGORY in data.enabledSystemFields) {
                data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_CATEGORY)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (BuiltInWatermarkFieldKeys.DATE_TIME in data.enabledSystemFields) {
                data.builtInValue(BuiltInWatermarkFieldKeys.DATE_TIME)?.let { add(it) }
            }
            if (BuiltInWatermarkFieldKeys.ADDRESS in data.enabledSystemFields) {
                data.builtInValue(BuiltInWatermarkFieldKeys.ADDRESS)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (BuiltInWatermarkFieldKeys.GPS in data.enabledSystemFields) {
                data.builtInValue(BuiltInWatermarkFieldKeys.GPS)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (BuiltInWatermarkFieldKeys.USER_NAME in data.enabledSystemFields) {
                data.builtInValue(BuiltInWatermarkFieldKeys.USER_NAME)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            data.customFields.filter { it.isEnabled && it.value.isNotBlank() }.forEach {
                add("${it.label}: ${it.value}")
            }
        }.filter { it.isNotBlank() }

        val maxAllowedCardWidth = (canvasWidth * 0.92f).coerceAtLeast(baseDimension * 0.50f)
        val maxAvailableTextWidth = maxAllowedCardWidth - paddingX * 2

        val formattedLines = mutableListOf<String>()
        for (line in rawLines) {
            if (measureStringWidth(line, textSize) > maxAvailableTextWidth) {
                var remaining = line
                val wrappedStart = formattedLines.size
                while (remaining.isNotEmpty()) {
                    var chunkEnd = 0
                    var currentChunkWidth = 0f
                    for (charIndex in remaining.indices) {
                        val char = remaining[charIndex]
                        val charW = if (char.code < 128) textSize * 0.55f else textSize * 1.05f
                        if (currentChunkWidth + charW > maxAvailableTextWidth && chunkEnd > 0) {
                            break
                        }
                        currentChunkWidth += charW
                        chunkEnd = charIndex + 1
                    }
                    if (chunkEnd == 0) chunkEnd = 1
                    formattedLines.add(remaining.substring(0, chunkEnd))
                    remaining = remaining.substring(chunkEnd)
                }
                avoidSingleCharacterTailMinimal(formattedLines, wrappedStart)
            } else {
                formattedLines.add(line)
            }
        }

        formattedLines.replaceAll { line ->
            ellipsizeByWidth(line, maxAvailableTextWidth, textSize)
        }

        var maxMeasuredWidth = 0f
        for (line in formattedLines) {
            val w = measureStringWidth(line, textSize)
            if (w > maxMeasuredWidth) maxMeasuredWidth = w
        }

        val totalLineHeight = textSize + lineSpacing
        val cardWidth = (maxMeasuredWidth + paddingX * 2).coerceIn(baseDimension * 0.40f, maxAllowedCardWidth)
        val availableHeight = (canvasHeight - margin * 2).coerceAtLeast(1f)
        val requiredHeight = paddingY * 2 + formattedLines.size * totalLineHeight
        if (requiredHeight > availableHeight) {
            return layoutMinimalCompact(canvasWidth, canvasHeight, data, rawLines)
        }
        val cardHeight = requiredHeight
        val (cardLeft, cardTop) = boundedCardPosition(
            canvasWidth, canvasHeight, cardWidth, cardHeight, margin, data.position
        )

        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val resultLines = mutableListOf<WatermarkTextLine>()
        var currentY = cardTop + paddingY + textSize

        for (line in formattedLines) {
            resultLines.add(
                WatermarkTextLine(
                    label = "",
                    value = line,
                    x = cardLeft + paddingX,
                    y = currentY,
                    textSize = textSize,
                    textColor = Color.WHITE,
                    isBold = false
                )
            )
            currentY += totalLineHeight
        }

        val cardColor = Color.argb((data.opacity * 0.75f * 255).toInt(), 0, 0, 0)

        return WatermarkLayoutResult(
            cardRect = cardRect,
            accentBarRect = null,
            headerRect = null,
            lines = resultLines,
            cardColor = cardColor,
            opacity = data.opacity
        )
    }

    /** Compact minimal-style fallback with one ellipsized row per field. */
    private fun layoutMinimalCompact(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData,
        rawLines: List<String>
    ): WatermarkLayoutResult {
        val baseDimension = min(canvasWidth, canvasHeight)
        val initialScale = (baseDimension / 1080f).coerceAtLeast(0.45f) *
            data.fontSizeScale.coerceIn(0.5f, 2.2f)
        data class Measurement(
            val rows: List<String>,
            val textSize: Float,
            val paddingX: Float,
            val paddingY: Float,
            val lineSpacing: Float,
            val margin: Float,
            val maxAllowedCardWidth: Float,
            val maxMeasuredWidth: Float,
            val requiredHeight: Float
        )
        fun measure(scale: Float): Measurement {
            val margin = (data.marginDp * 3f * scale).coerceAtLeast(16f)
            val paddingX = 22f * scale
            val paddingY = 18f * scale
            val textSize = 26f * scale
            val lineSpacing = 12f * scale
            val maxAllowedCardWidth = (canvasWidth * 0.92f).coerceAtLeast(baseDimension * 0.50f)
            val maxTextWidth = (maxAllowedCardWidth - paddingX * 2).coerceAtLeast(1f)
            val rows = rawLines.map { ellipsizeByWidth(it, maxTextWidth, textSize) }
            val maxMeasuredWidth = rows.maxOfOrNull { measureStringWidth(it, textSize) } ?: 0f
            val requiredHeight = paddingY * 2 + rows.size * (textSize + lineSpacing)
            return Measurement(
                rows = rows,
                textSize = textSize,
                paddingX = paddingX,
                paddingY = paddingY,
                lineSpacing = lineSpacing,
                margin = margin,
                maxAllowedCardWidth = maxAllowedCardWidth,
                maxMeasuredWidth = maxMeasuredWidth,
                requiredHeight = requiredHeight
            )
        }
        val scale = fitScale(
            initialScale = initialScale,
            minScale = 0.01f,
            canvasHeight = canvasHeight,
            measure = { candidateScale -> measure(candidateScale).requiredHeight },
            margin = { candidateScale -> measure(candidateScale).margin }
        )
        val measured = measure(scale)
        val cardWidth = (measured.maxMeasuredWidth + measured.paddingX * 2)
            .coerceIn(baseDimension * 0.40f, measured.maxAllowedCardWidth)
        val availableHeight = (canvasHeight - measured.margin * 2).coerceAtLeast(1f)
        val cardHeight = measured.requiredHeight.coerceAtMost(availableHeight)
        val (cardLeft, cardTop) = boundedCardPosition(
            canvasWidth,
            canvasHeight,
            cardWidth,
            cardHeight,
            measured.margin,
            data.position
        )
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val resultLines = measured.rows.mapIndexed { index, line ->
            WatermarkTextLine(
                label = "",
                value = line,
                x = cardLeft + measured.paddingX,
                y = cardTop + measured.paddingY + measured.textSize + index * (measured.textSize + measured.lineSpacing),
                textSize = measured.textSize,
                textColor = Color.WHITE,
                isBold = false
            )
        }
        return WatermarkLayoutResult(
            cardRect = cardRect,
            accentBarRect = null,
            headerRect = null,
            lines = resultLines,
            cardColor = Color.argb((data.opacity * 0.75f * 255).toInt(), 0, 0, 0),
            opacity = data.opacity
        )
    }

    private fun layoutInfoBoard(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData
    ): WatermarkLayoutResult {
        val baseDimension = min(canvasWidth, canvasHeight)
        val scale = (baseDimension / 1080f).coerceAtLeast(0.45f) * data.fontSizeScale.coerceIn(0.5f, 2.2f)
        val margin = (data.marginDp * 3f * scale).coerceAtLeast(16f)
        val paddingX = 24f * scale
        val paddingY = 20f * scale
        val headerHeight = 56f * scale
        val titleTextSize = 32f * scale
        val bodyTextSize = 26f * scale
        val lineSpacing = 14f * scale

        val rawLines = mutableListOf<Pair<String, String>>()
        if (BuiltInWatermarkFieldKeys.PROJECT_NAME in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_NAME)?.let { rawLines.add("工程名称" to it) }
        }
        if (BuiltInWatermarkFieldKeys.PROJECT_CATEGORY in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.PROJECT_CATEGORY)?.takeIf { it.isNotBlank() }?.let { rawLines.add("工程类型" to it) }
        }
        if (BuiltInWatermarkFieldKeys.DATE_TIME in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.DATE_TIME)?.let { rawLines.add("拍摄时间" to it) }
        }
        if (BuiltInWatermarkFieldKeys.ADDRESS in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.ADDRESS)?.takeIf { it.isNotBlank() }?.let { rawLines.add("现场位置" to it) }
        }
        if (BuiltInWatermarkFieldKeys.GPS in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.GPS)?.takeIf { it.isNotBlank() }?.let { rawLines.add("GPS定位" to it) }
        }
        if (BuiltInWatermarkFieldKeys.USER_NAME in data.enabledSystemFields) {
            data.builtInValue(BuiltInWatermarkFieldKeys.USER_NAME)?.takeIf { it.isNotBlank() }?.let { rawLines.add("拍摄人员" to it) }
        }
        for (field in data.customFields) {
            if (field.isEnabled && field.value.isNotBlank()) {
                rawLines.add(field.label to field.value)
            }
        }

        val maxAllowedCardWidth = (canvasWidth * 0.92f).coerceAtLeast(baseDimension * 0.60f)
        val maxAvailableTextWidth = maxAllowedCardWidth - paddingX * 2
        val headerTitle = ellipsizeByWidth(
            "工程施工现场留档记录",
            maxAvailableTextWidth.coerceAtLeast(titleTextSize),
            titleTextSize,
            bold = true
        )

        val formattedLines = mutableListOf<Pair<String, String>>()
        for ((label, value) in rawLines) {
            val labelPrefix = "$label: "
            val labelWidth = measureStringWidth(labelPrefix, bodyTextSize)
            if ((labelWidth + measureStringWidth(value, bodyTextSize)) > maxAvailableTextWidth) {
                val availableFirstLineWidth = (maxAvailableTextWidth - labelWidth).coerceAtLeast(bodyTextSize * 4f)
                var remaining = value
                var isFirstChunk = true
                val wrappedStart = formattedLines.size
                while (remaining.isNotEmpty()) {
                    val allowedWidth = if (isFirstChunk) availableFirstLineWidth else maxAvailableTextWidth
                    var chunkEnd = 0
                    var currentChunkWidth = 0f
                    for (charIndex in remaining.indices) {
                        val char = remaining[charIndex]
                        val charW = if (char.code < 128) bodyTextSize * 0.55f else bodyTextSize * 1.05f
                        if (currentChunkWidth + charW > allowedWidth && chunkEnd > 0) {
                            break
                        }
                        currentChunkWidth += charW
                        chunkEnd = charIndex + 1
                    }
                    if (chunkEnd == 0) chunkEnd = 1
                    val chunk = remaining.substring(0, chunkEnd)
                    formattedLines.add(Pair(if (isFirstChunk) labelPrefix else "       ", chunk))
                    remaining = remaining.substring(chunkEnd)
                    isFirstChunk = false
                }
                avoidSingleCharacterTailInfo(formattedLines, wrappedStart)
            } else {
                formattedLines.add(Pair(labelPrefix, value))
            }
        }

        formattedLines.replaceAll { (label, value) ->
            boundLabeledLine(label, value, maxAvailableTextWidth, bodyTextSize)
        }

        var maxMeasuredWidth = measureTextWidth(headerTitle, titleTextSize, bold = true)
        for ((label, value) in formattedLines) {
            val w = measureStringWidth(label + value, bodyTextSize)
            if (w > maxMeasuredWidth) maxMeasuredWidth = w
        }

        val cardWidth = (maxMeasuredWidth + paddingX * 2).coerceIn(baseDimension * 0.60f, maxAllowedCardWidth)
        val totalLineHeight = bodyTextSize + lineSpacing
        val availableHeight = (canvasHeight - margin * 2).coerceAtLeast(1f)
        val requiredHeight = headerHeight + paddingY * 2 + formattedLines.size * totalLineHeight
        if (requiredHeight > availableHeight) {
            return layoutInfoCompact(canvasWidth, canvasHeight, data, rawLines)
        }
        val cardHeight = requiredHeight
        val (cardLeft, cardTop) = boundedCardPosition(
            canvasWidth, canvasHeight, cardWidth, cardHeight, margin, data.position
        )

        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val actualHeaderHeight = headerHeight.coerceAtMost(cardHeight)
        val headerRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + actualHeaderHeight)
        val accentBarRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + 6f * scale)

        val resultLines = mutableListOf<WatermarkTextLine>()
        // Header title line
        val headerMetrics = runtimeTextMetrics(titleTextSize, includeBold = true)
        if (actualHeaderHeight >= headerMetrics.height) {
            val minHeaderBaseline = cardTop - headerMetrics.top
            val maxHeaderBaseline = (cardTop + actualHeaderHeight - headerMetrics.bottom)
                .coerceAtLeast(minHeaderBaseline)
            resultLines.add(
                WatermarkTextLine(
                    label = "",
                    value = headerTitle,
                    x = cardLeft + paddingX,
                    y = (cardTop + actualHeaderHeight * 0.65f)
                        .coerceIn(minHeaderBaseline, maxHeaderBaseline),
                    textSize = titleTextSize,
                    textColor = Color.parseColor("#121212"),
                    isBold = true,
                    isHighlight = true
                )
            )
        }

        val bodyMetrics = runtimeTextMetrics(bodyTextSize, includeBold = true)
        var currentY = cardTop + actualHeaderHeight + paddingY - bodyMetrics.top
        for ((label, value) in formattedLines) {
            resultLines.add(
                WatermarkTextLine(
                    label = label,
                    value = value,
                    x = cardLeft + paddingX,
                    y = currentY,
                    textSize = bodyTextSize,
                    textColor = Color.WHITE,
                    isBold = false
                )
            )
            currentY += bodyMetrics.height + lineSpacing
        }

        val cardColor = Color.argb((data.opacity * 255).toInt(), 26, 26, 26)

        return WatermarkLayoutResult(
            cardRect = cardRect,
            accentBarRect = accentBarRect,
            headerRect = headerRect,
            lines = resultLines,
            cardColor = cardColor,
            accentColor = Color.parseColor("#FFB300"),
            opacity = data.opacity
        )
    }

    /** Compact info-board fallback that preserves one row for every field. */
    private fun layoutInfoCompact(
        canvasWidth: Float,
        canvasHeight: Float,
        data: WatermarkData,
        rawLines: List<Pair<String, String>>
    ): WatermarkLayoutResult {
        val baseDimension = min(canvasWidth, canvasHeight)
        val initialScale = (baseDimension / 1080f).coerceAtLeast(0.45f) *
            data.fontSizeScale.coerceIn(0.5f, 2.2f)
        data class Measurement(
            val rows: List<CompactRow>,
            val metrics: List<RuntimeTextMetrics>,
            val headerTitle: String,
            val headerMetrics: RuntimeTextMetrics,
            val headerHeight: Float,
            val titleTextSize: Float,
            val bodyTextSize: Float,
            val paddingX: Float,
            val paddingY: Float,
            val lineSpacing: Float,
            val margin: Float,
            val maxAllowedCardWidth: Float,
            val maxMeasuredWidth: Float,
            val requiredHeight: Float
        )
        fun measure(scale: Float): Measurement {
            val margin = (data.marginDp * 3f * scale).coerceAtLeast(16f)
            val paddingX = 24f * scale
            val paddingY = 20f * scale
            val headerHeightBase = 56f * scale
            val titleTextSize = 32f * scale
            val bodyTextSize = 26f * scale
            val lineSpacing = 14f * scale
            val maxAllowedCardWidth = (canvasWidth * 0.92f).coerceAtLeast(baseDimension * 0.60f)
            val maxTextWidth = (maxAllowedCardWidth - paddingX * 2).coerceAtLeast(1f)
            val headerTitle = ellipsizeByWidth(
                "工程施工现场留档记录",
                maxTextWidth,
                titleTextSize,
                bold = true
            )
            val headerMetrics = runtimeTextMetrics(titleTextSize, includeBold = true)
            val headerHeight = maxOf(headerHeightBase, headerMetrics.height)
            val rows = rawLines.map { (label, value) ->
                val bounded = boundLabeledLine(
                    label = "$label: ",
                    value = value,
                    maxWidth = maxTextWidth,
                    textSize = bodyTextSize
                )
                CompactRow(bounded.first, bounded.second, isFirst = false)
            }
            val metrics = rows.map { runtimeTextMetrics(bodyTextSize, includeBold = true) }
            val maxMeasuredWidth = maxOf(
                measureTextWidth(headerTitle, titleTextSize, bold = true),
                rows.zip(metrics).maxOfOrNull { (row, _) ->
                    labeledLineWidth(row.label, row.value, bodyTextSize)
                } ?: 0f
            )
            val requiredHeight = headerHeight + paddingY * 2 +
                metrics.sumOf { it.height.toDouble() }.toFloat() +
                lineSpacing * (metrics.size - 1).coerceAtLeast(0)
            return Measurement(
                rows = rows,
                metrics = metrics,
                headerTitle = headerTitle,
                headerMetrics = headerMetrics,
                headerHeight = headerHeight,
                titleTextSize = titleTextSize,
                bodyTextSize = bodyTextSize,
                paddingX = paddingX,
                paddingY = paddingY,
                lineSpacing = lineSpacing,
                margin = margin,
                maxAllowedCardWidth = maxAllowedCardWidth,
                maxMeasuredWidth = maxMeasuredWidth,
                requiredHeight = requiredHeight
            )
        }
        val scale = fitScale(
            initialScale = initialScale,
            minScale = 0.01f,
            canvasHeight = canvasHeight,
            measure = { candidateScale -> measure(candidateScale).requiredHeight },
            margin = { candidateScale -> measure(candidateScale).margin }
        )
        val measured = measure(scale)
        val cardWidth = (measured.maxMeasuredWidth + measured.paddingX * 2)
            .coerceIn(baseDimension * 0.60f, measured.maxAllowedCardWidth)
        val availableHeight = (canvasHeight - measured.margin * 2).coerceAtLeast(1f)
        val cardHeight = measured.requiredHeight.coerceAtMost(availableHeight)
        val (cardLeft, cardTop) = boundedCardPosition(
            canvasWidth,
            canvasHeight,
            cardWidth,
            cardHeight,
            measured.margin,
            data.position
        )
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val headerRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + measured.headerHeight)
        val accentBarRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + 6f * scale)
        val resultLines = mutableListOf<WatermarkTextLine>()
        val minHeaderBaseline = cardTop - measured.headerMetrics.top
        val maxHeaderBaseline = (cardTop + measured.headerHeight - measured.headerMetrics.bottom)
            .coerceAtLeast(minHeaderBaseline)
        resultLines += WatermarkTextLine(
            label = "",
            value = measured.headerTitle,
            x = cardLeft + measured.paddingX,
            y = (cardTop + measured.headerHeight * 0.65f)
                .coerceIn(minHeaderBaseline, maxHeaderBaseline),
            textSize = measured.titleTextSize,
            textColor = Color.parseColor("#121212"),
            isBold = true,
            isHighlight = true
        )
        var currentY = cardTop + measured.headerHeight + measured.paddingY
        for (index in measured.rows.indices) {
            val row = measured.rows[index]
            val metrics = measured.metrics[index]
            resultLines += WatermarkTextLine(
                label = row.label,
                value = row.value,
                x = cardLeft + measured.paddingX,
                y = currentY + metrics.baselineOffset,
                textSize = measured.bodyTextSize,
                textColor = Color.WHITE,
                isBold = false
            )
            currentY += metrics.height + measured.lineSpacing
        }
        return WatermarkLayoutResult(
            cardRect = cardRect,
            accentBarRect = accentBarRect,
            headerRect = headerRect,
            lines = resultLines,
            cardColor = Color.argb((data.opacity * 255).toInt(), 26, 26, 26),
            accentColor = Color.parseColor("#FFB300"),
            opacity = data.opacity
        )
    }

    private fun avoidSingleCharacterTailClassic(
        lines: MutableList<Triple<String, String, Boolean>>,
        start: Int
    ) {
        if (lines.size - start < 2) return
        val lastIndex = lines.lastIndex
        val previousIndex = lastIndex - 1
        val previous = lines[previousIndex]
        val last = lines[lastIndex]
        if (last.second.length == 1 && previous.second.length > 1) {
            lines[previousIndex] = previous.copy(second = previous.second.dropLast(1))
            lines[lastIndex] = last.copy(second = previous.second.last() + last.second)
        }
    }

    private fun avoidSingleCharacterTailMinimal(lines: MutableList<String>, start: Int) {
        if (lines.size - start < 2) return
        val lastIndex = lines.lastIndex
        val previousIndex = lastIndex - 1
        val previous = lines[previousIndex]
        val last = lines[lastIndex]
        if (last.length == 1 && previous.length > 1) {
            lines[previousIndex] = previous.dropLast(1)
            lines[lastIndex] = previous.last() + last
        }
    }

    private fun avoidSingleCharacterTailInfo(
        lines: MutableList<Pair<String, String>>,
        start: Int
    ) {
        if (lines.size - start < 2) return
        val lastIndex = lines.lastIndex
        val previousIndex = lastIndex - 1
        val previous = lines[previousIndex]
        val last = lines[lastIndex]
        if (last.second.length == 1 && previous.second.length > 1) {
            lines[previousIndex] = previous.copy(second = previous.second.dropLast(1))
            lines[lastIndex] = last.copy(second = previous.second.last() + last.second)
        }
    }

    private fun takePrefixByWidth(
        text: String,
        maxWidth: Float,
        textSize: Float,
        bold: Boolean = false
    ): String {
        val paint = measurePaint(textSize, bold)
        var width = 0f
        var end = 0
        for ((index, character) in text.withIndex()) {
            val charWidth = paint.measureText(character.toString())
            if (width + charWidth > maxWidth && end > 0) break
            width += charWidth
            end = index + 1
        }
        return text.substring(0, end.coerceAtLeast(1).coerceAtMost(text.length))
    }

    private fun ellipsizeByWidth(
        text: String,
        maxWidth: Float,
        textSize: Float,
        bold: Boolean = false
    ): String {
        if (measureTextWidth(text, textSize, bold) <= maxWidth) return text
        val ellipsisWidth = measureTextWidth("…", textSize, bold)
        val prefix = takePrefixByWidth(
            text,
            (maxWidth - ellipsisWidth).coerceAtLeast(textSize),
            textSize,
            bold
        )
        return prefix + "…"
    }

    private fun fitScale(
        initialScale: Float,
        minScale: Float,
        canvasHeight: Float,
        measure: (Float) -> Float,
        margin: (Float) -> Float
    ): Float {
        fun fits(scale: Float): Boolean =
            measure(scale) <= (canvasHeight - margin(scale) * 2f).coerceAtLeast(1f)

        if (fits(initialScale)) return initialScale
        if (!fits(minScale)) return minScale
        var low = minScale
        var high = initialScale
        repeat(24) {
            val middle = (low + high) / 2f
            if (fits(middle)) low = middle else high = middle
        }
        return low
    }

    private fun boundLabeledLine(
        label: String,
        value: String,
        maxWidth: Float,
        textSize: Float,
        valueBold: Boolean = false
    ): Pair<String, String> {
        if (labeledLineWidth(label, value, textSize, valueBold) <= maxWidth) {
            return label to value
        }
        val boundedLabel = if (measureTextWidth(label, textSize, bold = label.isNotEmpty()) > maxWidth) {
            ellipsizeByWidth(label, maxWidth, textSize, bold = label.isNotEmpty())
        } else {
            label
        }
        val remainingWidth = (maxWidth - measureTextWidth(boundedLabel, textSize, bold = boundedLabel.isNotEmpty()))
            .coerceAtLeast(1f)
        return boundedLabel to ellipsizeByWidth(value, remainingWidth, textSize, bold = valueBold)
    }

    private fun boundedCardPosition(
        canvasWidth: Float,
        canvasHeight: Float,
        cardWidth: Float,
        cardHeight: Float,
        margin: Float,
        position: String
    ): Pair<Float, Float> {
        val maxLeft = (canvasWidth - cardWidth).coerceAtLeast(0f)
        val maxTop = (canvasHeight - cardHeight).coerceAtLeast(0f)
        val left = if (position.endsWith("RIGHT")) canvasWidth - cardWidth - margin else margin
        val top = if (position.startsWith("TOP")) margin else canvasHeight - cardHeight - margin
        return left.coerceIn(0f, maxLeft) to top.coerceIn(0f, maxTop)
    }
}
