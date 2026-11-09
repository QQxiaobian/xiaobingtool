package com.kunming.powerline.util

import com.kunming.powerline.data.remote.model.ServerFileDto

/**
 * 服务器文件库的文件夹视图与搜索（1.6.2）。
 *
 * 服务端 /api/files 返回扁平列表，每个文件带相对路径 path（如「220kV/某片区/甲线.kml」）。
 * 这里把扁平列表按「/」切出层级：当前文件夹 = 子文件夹列表 + 文件列表；
 * 搜索则跨全库按文件名或路径模糊匹配。纯 Kotlin，可单元测试。
 */
object ServerFileBrowser {

    /** 文件的完整相对路径；老服务端无 path 字段时退化为文件名（视为根目录文件） */
    fun pathOf(f: ServerFileDto): String = f.path.trim().ifBlank { f.name }

    /** 文件所在目录路径，根目录返回 "" */
    fun dirOf(f: ServerFileDto): String = pathOf(f).substringBeforeLast('/', "")

    /** 当前目录下的直接子文件夹名列表（有序、去重） */
    fun subdirs(files: List<ServerFileDto>, dir: String): List<String> =
        files.map { dirOf(it) }
            .filter { it.isNotBlank() && parentOf(it) == dir }
            .map { it.substringAfterLast('/') }
            .distinct()
            .sorted()

    /** 当前目录下的文件列表（不含子文件夹内容） */
    fun filesIn(files: List<ServerFileDto>, dir: String): List<ServerFileDto> =
        files.filter { dirOf(it) == dir }

    /** 跨全库搜索：文件名或完整路径包含关键字（忽略大小写），命中按路径排序 */
    fun search(files: List<ServerFileDto>, query: String): List<ServerFileDto> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return files.filter {
            pathOf(it).contains(q, ignoreCase = true)
        }.sortedBy { pathOf(it).lowercase() }
    }

    /** 子目录的父目录路径：顶层目录返回 "" */
    fun parentOf(dir: String): String = dir.substringBeforeLast('/', "")

    /** 面包屑：把当前目录路径拆成 (目录名, 完整路径) 序列，第一项始终是根目录 */
    fun breadcrumb(dir: String): List<Pair<String, String>> {
        val out = mutableListOf("根目录" to "")
        var acc = ""
        dir.split('/').filter { it.isNotBlank() }.forEach { seg ->
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            out.add(seg to acc)
        }
        return out
    }
}
