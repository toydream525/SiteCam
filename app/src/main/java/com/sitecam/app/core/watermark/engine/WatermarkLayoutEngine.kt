package com.sitecam.app.core.watermark.engine

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sitecam.app.core.watermark.model.*
import kotlin.math.min

data class WatermarkTextLine(
    val label: String, val value: String, val x: Float, val y: Float,
    val textSize: Float, val textColor: Int, val isBold: Boolean = false,
    val isHighlight: Boolean = false, val labelColor: Int = Color.LTGRAY,
    val fieldKey: String? = null, val contentRole: String = "value"
)
data class WatermarkDecoration(val rect: RectF, val color: Int)
data class WatermarkLayoutResult(
    val cardRect: RectF, val accentBarRect: RectF?, val headerRect: RectF?,
    val lines: List<WatermarkTextLine>, val cardColor: Int,
    val accentColor: Int = Color.parseColor("#FFB300"), val opacity: Float,
    val decorations: List<WatermarkDecoration> = emptyList(), val cornerRadius: Float = 0f
)

/** Layout and drawing share this exact paint, including synthetic bold and font top/bottom. */
fun watermarkPaint(size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.DEFAULT; isFakeBoldText = bold
    }

object WatermarkLayoutEngine {
    private data class Field(val key: String, val label: String, val value: String)
    private fun color(hex: String) = Color.parseColor(hex)
    fun measureStringWidth(text: String, textSize: Float): Float = watermarkPaint(textSize).measureText(text)

    fun calculateLayout(canvasWidth: Float, canvasHeight: Float, data: WatermarkData): WatermarkLayoutResult {
        val width = canvasWidth.coerceAtLeast(1f); val height = canvasHeight.coerceAtLeast(1f)
        val style = WatermarkStyleCatalog.resolve(data.styleType).id
        val fields = listOf(
            BuiltInWatermarkFieldKeys.PROJECT_NAME to "工程名称",
            BuiltInWatermarkFieldKeys.PROJECT_CATEGORY to "工程类型",
            BuiltInWatermarkFieldKeys.DATE_TIME to "拍摄时间",
            BuiltInWatermarkFieldKeys.ADDRESS to "现场位置",
            BuiltInWatermarkFieldKeys.GPS to "GPS定位",
            BuiltInWatermarkFieldKeys.USER_NAME to "拍摄人员"
        ).mapNotNull { (key, label) ->
            data.builtInValue(key)?.takeIf { key in data.enabledSystemFields && it.isNotBlank() }
                ?.let { Field(key, data.fieldLabels[key] ?: label, it) }
        } + data.customFields.filter { it.isEnabled && it.value.isNotBlank() }
            .map { Field(it.key, data.fieldLabels[it.key] ?: it.label, it.value) }
        val orderIndex = data.fieldOrder?.withIndex()?.associate { it.value to it.index }
        val ordered = if (orderIndex != null) fields.sortedBy {
            orderIndex[it.key] ?: Int.MAX_VALUE
        } else if (style == "ENGINEERING_BLUE") fields.sortedBy {
            when(it.key) { BuiltInWatermarkFieldKeys.PROJECT_NAME -> 0; BuiltInWatermarkFieldKeys.DATE_TIME -> 1; else -> 2 }
        } else if (style == "TIME_LOCATION") fields.sortedBy {
            when(it.key) { BuiltInWatermarkFieldKeys.DATE_TIME -> 0; BuiltInWatermarkFieldKeys.ADDRESS -> 1; else -> 2 }
        } else fields
        val base = (min(width, height) / 1080f).coerceAtLeast(.45f) * data.fontSizeScale.coerceIn(.5f, 2.2f)
        val margin = (data.marginDp.coerceAtLeast(0) * 3f * base).coerceAtMost(min(width,height) * .08f)
        val availableWidth = width - margin * 2
        val availableHeight = height - margin * 2
        val light = style in setOf("ENGINEERING_BLUE", "CONSTRUCTION_TABLE", "ACCEPTANCE")
        val table = light
        val accent = color(when(style) {
            "ENGINEERING_BLUE" -> "#1262AE"; "CONSTRUCTION_TABLE" -> "#455F78"
            "INSPECTION" -> "#168D80"; "ACCEPTANCE" -> "#173C68"; else -> "#FFB300"
        })
        val foreground = if(light) color("#172D42") else Color.WHITE
        val labelColor = if(light) color("#315776") else color("#CFD8DC")
        val title = when(style) {
            "INFO_BOARD" -> "工程施工现场留档记录"; "CONSTRUCTION_TABLE" -> "施工记录"
            "INSPECTION" -> "巡检记录"; "ACCEPTANCE" -> "工程验收 · 现场记录"
            else -> null
        }
        fun build(scale: Float): WatermarkLayoutResult {
            val s = base * scale
            val legacy = style in setOf("CLASSIC", "MINIMAL", "INFO_BOARD")
            val size = (if(legacy) 26f else 30f) * s; val pad = 22f * s; val gap = (if(legacy) 14f else 7f) * s
            // Keep the ordinary card compact, expanding across the viewport only as text demands.
            val preferred = min(availableWidth, min(width,height) * if(table) .82f else .9f)
            val cardWidth = preferred.coerceAtLeast(pad * 3)
            val inner = (cardWidth - pad * 2).coerceAtLeast(.001f)
            val lines = mutableListOf<WatermarkTextLine>()
            val decorations = mutableListOf<WatermarkDecoration>()
            var y = pad
            var headerBottom: Float? = null
            fun addText(text: String, x: Float, top: Float, maxWidth: Float, font: Float,
                        ink: Int, bold: Boolean, key: String?, role: String, highlight: Boolean = false): Float {
                val paint = watermarkPaint(font, ink, bold)
                val metrics = paint.fontMetrics
                var cursor = top
                for (part in wrap(text, paint, maxWidth)) {
                    lines += WatermarkTextLine("", part, x, cursor - metrics.top, font, ink, bold,
                        highlight, labelColor, key, role)
                    cursor += metrics.bottom - metrics.top + gap
                }
                return cursor
            }
            if(title != null) {
                y = addText(title, pad, y, inner, if(style == "INFO_BOARD") 32f*s else size * 1.12f,
                    if(style == "INFO_BOARD") color("#17212A") else Color.WHITE,
                    true, null, "heading") + pad * .5f
                headerBottom = y
                y += pad * .5f
            }
            for ((index, field) in ordered.withIndex()) {
                val rowTop = y
                val blueHeader = style == "ENGINEERING_BLUE" && field.key in setOf(BuiltInWatermarkFieldKeys.PROJECT_NAME, BuiltInWatermarkFieldKeys.DATE_TIME)
                val large = style == "TIME_LOCATION" && field.key == BuiltInWatermarkFieldKeys.DATE_TIME
                val highlight = style == "CLASSIC" && index == 0
                val font = if(highlight) 34f*s else size * if(large) 1.75f else if(blueHeader) 1.12f else 1f
                val renamed = data.fieldLabels[field.key]?.let {
                    it != BuiltInWatermarkFieldKeys.defaultLabels[field.key]
                } == true
                when {
                    blueHeader -> {
                        if (renamed) {
                            y = addText(field.label,pad,y,inner,size,Color.WHITE,true,field.key,"label")
                        }
                        y = addText(field.value,pad,y,inner,font,Color.WHITE,true,field.key,"value")
                        if (data.fieldOrder == null) {
                            headerBottom = y + gap
                        } else {
                            decorations += WatermarkDecoration(RectF(0f,rowTop-gap/2,cardWidth,y),accent)
                        }
                    }
                    table -> {
                        val labelWidth = inner * if(style == "ACCEPTANCE") .32f else .27f
                        val labelEnd = addText(field.label,pad,y,labelWidth-gap,size,labelColor,true,field.key,"label")
                        val valueEnd = addText(field.value,pad+labelWidth+gap,y,inner-labelWidth-gap,size,foreground,false,field.key,"value")
                        y = maxOf(labelEnd,valueEnd)
                        decorations += WatermarkDecoration(RectF(pad+labelWidth,rowTop-gap/2,pad+labelWidth+s,y), color("#C0CEDA"))
                    }
                    style == "MINIMAL" || style == "TIME_LOCATION" -> {
                        // Custom labels remain present even in compact styles.
                        if((field.key !in BuiltInWatermarkFieldKeys.all || renamed) && field.label.isNotEmpty())
                            y = addText(field.label,pad,y,inner,size*.85f,labelColor,true,field.key,"label")
                        y = addText(field.value,pad,y,inner,font,foreground,large,field.key,"value")
                    }
                    else -> {
                        val prefix = field.label + ": "
                        val lp = watermarkPaint(font,labelColor,true)
                        val vp = watermarkPaint(font,foreground,highlight)
                        if(lp.measureText(prefix) + vp.measureText(field.value) <= inner && !field.value.contains('\n')) {
                            val fm = vp.fontMetrics
                            lines += WatermarkTextLine(prefix,field.value,pad,y-fm.top,font,
                                if(highlight) accent else foreground,highlight,highlight,labelColor,field.key)
                            y += fm.bottom-fm.top+gap
                        } else {
                            y = addText(field.label,pad,y,inner,font,labelColor,true,field.key,"label")
                            y = addText(field.value,pad,y,inner,font,if(highlight) accent else foreground,highlight,field.key,"value",highlight)
                        }
                    }
                }
                if(table || style == "INSPECTION") {
                    decorations += WatermarkDecoration(RectF(pad,y,cardWidth-pad,y+s),if(light) color("#C0CEDA") else color("#528E89"))
                    y += gap
                }
            }
            val cardHeight = y + pad
            val x = if(data.position.endsWith("RIGHT")) width-margin-cardWidth else margin
            val top = if(data.position.startsWith("TOP")) margin else height-margin-cardHeight
            fun shifted(rect: RectF) = RectF(rect).apply { offset(x,top) }
            val bg = if(light) color("#F5F8FB") else color(if(style == "MINIMAL") "#18232C" else "#16212B")
            val alpha = (data.opacity.coerceIn(0f,1f)*255).toInt()
            return WatermarkLayoutResult(RectF(x,top,x+cardWidth,top+cardHeight),
                if(style == "CLASSIC") shifted(RectF(0f,0f,5f*s,cardHeight)) else null,
                headerBottom?.let { shifted(RectF(0f,0f,cardWidth,it)) },
                lines.map { it.copy(x=it.x+x,y=it.y+top) },
                Color.argb(alpha,Color.red(bg),Color.green(bg),Color.blue(bg)),accent,data.opacity,
                decorations.map { it.copy(rect=shifted(it.rect)) },if(style == "MINIMAL") 12f*s else 3f*s)
        }
        val normal = build(1f)
        if(normal.cardRect.height() <= availableHeight) return normal
        var low = 0f; var high = 1f
        repeat(32) {
            val mid = (low+high)/2
            if(build(mid).cardRect.height() <= availableHeight) low=mid else high=mid
        }
        return build(low.coerceAtLeast(.000001f))
    }

    /** Break only at code point boundaries; never replace or discard source content. */
    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            if(paragraph.isEmpty()) { result += ""; continue }
            var start=0
            while(start < paragraph.length) {
                var end=start
                while(end < paragraph.length) {
                    val next=end+Character.charCount(paragraph.codePointAt(end))
                    if(end>start && paint.measureText(paragraph,start,next)>width) break
                    end=next
                }
                result += paragraph.substring(start,end); start=end
            }
        }
        return result
    }
}
