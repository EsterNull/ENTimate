package com.example.entimate.data.local

const val AUTO_COLOR = Int.MIN_VALUE

data class DocDocument(
    val title: String,
    val paragraphs: List<DocParagraph>,
    val marginTopMm: Float = 25.4f,
    val marginRightMm: Float = 25.4f,
    val marginBottomMm: Float = 25.4f,
    val marginLeftMm: Float = 25.4f,
)

data class DocParagraph(
    val font: String,
    val align: String,
    val indentLeftMm: Float = 0f,
    val indentRightMm: Float = 0f,
    val firstLineMm: Float = 0f,
    val lineSpacing: Float = 1f,
    val spaceBeforeMm: Float = 0f,
    val spaceAfterMm: Float = 0f,
    val elements: List<DocElement> = emptyList(),
)

sealed interface DocElement

data class DocText(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val size: Int,
    val colorArgb: Int = AUTO_COLOR,
    val bgArgb: Int = AUTO_COLOR,
) : DocElement

data class DocCell(val text: String, val colSpan: Int = 1, val weight: Float = 1f, val align: String = "")

data class DocTableEmbed(
    val title: String,
    val headers: List<DocCell>,
    val rows: List<List<DocCell>>,
    val align: String = "LEFT",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val size: Int = 0,
    val colorArgb: Int = AUTO_COLOR,
    val bgArgb: Int = AUTO_COLOR,
    val joinPrevious: Boolean = false,
    val colAligns: List<String> = emptyList(),
    val border: Int = 1,
    val weights: List<Float> = emptyList(),
) : DocElement

data class DocTab(
    val widthMm: Float = 12.5f,
) : DocElement

object DocPageBreak : DocElement

data class TableCell(val text: String, val colSpan: Int = 1, val weight: Float = 0f, val align: String = "")

data class ManualTable(val title: String, val headers: List<TableCell>, val rows: List<List<TableCell>>)

fun manualTableToJson(t: ManualTable): String = org.json.JSONObject().apply {
    put("title", t.title)
    put("headers", org.json.JSONArray(t.headers.map { org.json.JSONObject().apply { put("t", it.text); put("s", it.colSpan); put("w", it.weight); put("a", it.align) } }))
    put("rows", org.json.JSONArray(t.rows.map { row -> org.json.JSONArray(row.map { c -> org.json.JSONObject().apply { put("t", c.text); put("s", c.colSpan); put("w", c.weight); put("a", c.align) } }) }))
}.toString()

fun manualTableFromJson(s: String): ManualTable {
    return try {
        val o = org.json.JSONObject(s)
        val headers = o.optJSONArray("headers")?.let { a -> (0 until a.length()).map { val c = a.optJSONObject(it); TableCell(c.optString("t"), c.optInt("s", 1), c.optDouble("w", 0.0).toFloat(), c.optString("a")) } } ?: emptyList()
        val rows = o.optJSONArray("rows")?.let { a ->
            (0 until a.length()).map { ri ->
                val r = a.optJSONArray(ri)
                (0 until (r?.length() ?: 0)).map { val c = r!!.optJSONObject(it); TableCell(c.optString("t"), c.optInt("s", 1), c.optDouble("w", 0.0).toFloat(), c.optString("a")) }
            }
        } ?: emptyList()
        ManualTable(o.optString("title"), headers, rows)
    } catch (e: Exception) {
        ManualTable("", emptyList(), emptyList())
    }
}
