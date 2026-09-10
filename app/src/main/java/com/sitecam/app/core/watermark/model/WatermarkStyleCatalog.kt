package com.sitecam.app.core.watermark.model

data class WatermarkStyle(val id: String, val name: String, val description: String)

/** Stable persisted identifiers; unknown historical styles render as CLASSIC. */
object WatermarkStyleCatalog {
    val styles: List<WatermarkStyle> = listOf(
        WatermarkStyle("CLASSIC", "经典工程水印", "深色信息卡，黄色工程标题与侧边线"),
        WatermarkStyle("MINIMAL", "极简水印", "紧凑轻底，简洁纵向文字"),
        WatermarkStyle("INFO_BOARD", "工程信息板", "琥珀标题栏，完整工程字段"),
        WatermarkStyle("ENGINEERING_BLUE", "蓝白工程记录", "蓝色工程与时间标题，浅底深色标签和横线"),
        WatermarkStyle("CONSTRUCTION_TABLE", "施工表格", "蓝灰表头，标签与内容分列表格"),
        WatermarkStyle("INSPECTION", "巡检记录", "青绿巡检标题，逐项分隔的记录清单"),
        WatermarkStyle("ACCEPTANCE", "验收记录", "深蓝分区表头与规整表格，仅呈现已填写内容"),
        WatermarkStyle("TIME_LOCATION", "大字时间地点", "突出拍摄时间，按字段设置顺序排列")
    )
    // Retired selection remains readable without rewriting templates or historical snapshots.
    fun resolve(id: String?): WatermarkStyle =
        styles.firstOrNull { it.id == if (id == "SITE_PHOTO") "MINIMAL" else id } ?: styles.first()
}
