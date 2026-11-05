package com.kunming.powerline.data.parser

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 轻量 xlsx 写出器（零第三方依赖）
 *
 * 仅支持本工程需要的场景：多工作表 + 纯文本/数值单元格 + 列宽 + 首行加粗。
 * 输出符合 OOXML 规范，Excel / WPS / Apache POI / openpyxl 均可正常打开。
 */
object XlsxWriter {

    data class Sheet(
        val name: String,
        val headers: List<String> = emptyList(),
        val rows: List<List<Any?>> = emptyList(),
        /** 各列宽度（字符数），索引对齐 headers */
        val widths: List<Int> = emptyList(),
        /** 是否把首行渲染为表头样式（加粗 + 灰底 + 细边框） */
        val headerStyle: Boolean = true
    )

    fun write(sheets: List<Sheet>): ByteArray {
        require(sheets.isNotEmpty()) { "至少需要一个工作表" }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", rootRels())
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", styles())
            sheets.forEachIndexed { i, s ->
                put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s))
            }
        }
        return bos.toByteArray()
    }

    // ------------------------------------------------------------------
    private fun contentTypes(n: Int): String {
        val sheetOverrides = (1..n).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$sheetOverrides<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    }

    private fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>): String {
        val entries = sheets.mapIndexed { i, s ->
            """<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$entries</sheets></workbook>"""
    }

    private fun workbookRels(n: Int): String {
        val rels = (1..n).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        val styleRel = """<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>"""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels$styleRel</Relationships>"""
    }

    /** 样式：0 默认 / 1 表头（加粗、灰底、细边框、居中） */
    private fun styles(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="宋体"/></font><font><b/><sz val="11"/><name val="宋体"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEDE7DE"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border/><border><left style="thin"><color rgb="FFBFB5A8"/></left><right style="thin"><color rgb="FFBFB5A8"/></right><top style="thin"><color rgb="FFBFB5A8"/></top><bottom style="thin"><color rgb="FFBFB5A8"/></bottom></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheetXml(s: Sheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (s.widths.isNotEmpty()) {
            sb.append("<cols>")
            s.widths.forEachIndexed { i, w ->
                sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")

        var rowNo = 0
        if (s.headers.isNotEmpty()) {
            rowNo++
            sb.append("""<row r="$rowNo">""")
            s.headers.forEachIndexed { i, h ->
                sb.append(cell(ref(i, rowNo), h, if (s.headerStyle) 1 else 0))
            }
            sb.append("</row>")
        }
        s.rows.forEach { row ->
            rowNo++
            sb.append("""<row r="$rowNo">""")
            row.forEachIndexed { i, v -> sb.append(cell(ref(i, rowNo), v, 0)) }
            sb.append("</row>")
        }
        // 冻结首行
        if (s.headers.isNotEmpty()) {
            sb.append("""</sheetData><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        } else {
            sb.append("</sheetData>")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun cell(ref: String, value: Any?, style: Int): String {
        val sAttr = if (style > 0) """ s="$style"""" else ""
        return when (value) {
            null -> ""
            is Number -> {
                val d = value.toDouble()
                val v = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
                """<c r="$ref"$sAttr t="n"><v>$v</v></c>"""
            }
            is Boolean -> """<c r="$ref"$sAttr t="b"><v>${if (value) 1 else 0}</v></c>"""
            else -> """<c r="$ref"$sAttr t="inlineStr"><is><t xml:space="preserve">${esc(value.toString())}</t></is></c>"""
        }
    }

    private fun ref(col: Int, row: Int): String = "${colName(col)}$row"

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
