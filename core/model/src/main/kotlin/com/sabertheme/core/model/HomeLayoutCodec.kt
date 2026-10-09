package com.sabertheme.core.model

/**
 * Line-based text format for [HomeLayout], stored in DataStore. Fields are
 * tab-separated; names and configs are escaped so tabs and newlines
 * round-trip.
 * ```
 * v2
 * D <appKey>
 * P
 * A <col> <row> <appKey>
 * F <col> <row> <id> <name> <appKey>...
 * W <col> <row> <spanX> <spanY> <id> <type> <config>
 * ```
 * `P` starts a page. v1 (M1: every app in page order, no positions) is
 * migrated on read: dock kept, first page placed in reading order (at most
 * one page of items), default widget page prepended; other pages are dropped
 * because those apps live in the drawer.
 */
object HomeLayoutCodec {
    private const val V1 = "v1"
    private const val V2 = "v2"

    fun encode(layout: HomeLayout): String = buildString {
        appendLine(V2)
        layout.dock.forEach { appendLine("D ${it.encode()}") }
        for (page in layout.pages) {
            appendLine("P")
            for (p in page.items) {
                val fields = when (val item = p.item) {
                    is HomeItem.App -> listOf("A", p.col, p.row, item.key.encode())
                    is HomeItem.Folder ->
                        listOf("F", p.col, p.row, escape(item.folderId), escape(item.name)) + item.apps.map { it.encode() }
                    is HomeItem.Widget ->
                        listOf("W", p.col, p.row, p.spanX, p.spanY, escape(item.widgetId), item.type.name, escape(item.config))
                }
                appendLine(fields.joinToString("\t"))
            }
        }
    }

    /** Returns null for anything it cannot read, so callers fall back to a default layout. */
    fun decode(text: String?): HomeLayout? {
        val lines = text?.lines()?.filter { it.isNotEmpty() } ?: return null
        return when (lines.firstOrNull()) {
            V2 -> decodeV2(lines.drop(1))
            V1 -> decodeV1(lines.drop(1))?.let(::migrateV1)
            else -> null
        }
    }

    private fun decodeV2(lines: List<String>): HomeLayout? {
        val dock = mutableListOf<AppKey>()
        val pages = mutableListOf<MutableList<Placed>>()
        for (line in lines) {
            if (line.startsWith("D ")) {
                dock.add(AppKey.decode(line.drop(2)) ?: return null)
                continue
            }
            if (line == "P") {
                pages.add(mutableListOf())
                continue
            }
            val f = line.split('\t')
            val page = pages.lastOrNull() ?: return null
            val col = f.getOrNull(1)?.toIntOrNull() ?: return null
            val row = f.getOrNull(2)?.toIntOrNull() ?: return null
            page += when (f[0]) {
                "A" -> Placed(HomeItem.App(AppKey.decode(f.getOrNull(3) ?: return null) ?: return null), col, row)
                "F" -> {
                    if (f.size < 5) return null
                    val apps = f.drop(5).map { AppKey.decode(it) ?: return null }
                    Placed(HomeItem.Folder(unescape(f[3]), unescape(f[4]), apps), col, row)
                }
                "W" -> {
                    if (f.size < 8) return null
                    val type = WidgetType.entries.firstOrNull { it.name == f[6] } ?: return null
                    val spanX = f[3].toIntOrNull() ?: return null
                    val spanY = f[4].toIntOrNull() ?: return null
                    Placed(HomeItem.Widget(unescape(f[5]), type, unescape(f[7])), col, row, spanX, spanY)
                }
                else -> return null
            }
        }
        return HomeLayout(dock, pages.map(::HomePage))
    }

    /** M1 format: dock + pages of unpositioned items. */
    private fun decodeV1(lines: List<String>): Pair<List<AppKey>, List<List<HomeItem>>>? {
        val dock = mutableListOf<AppKey>()
        val pages = mutableListOf<MutableList<HomeItem>>()
        for (line in lines) {
            val body = line.drop(2)
            when (line.take(1)) {
                "D" -> dock.add(AppKey.decode(body) ?: return null)
                "P" -> pages.add(mutableListOf())
                "A" -> (pages.lastOrNull() ?: return null).add(HomeItem.App(AppKey.decode(body) ?: return null))
                "F" -> {
                    val parts = body.split('\t')
                    if (parts.size < 2) return null
                    val apps = parts.drop(2).map { AppKey.decode(it) ?: return null }
                    (pages.lastOrNull() ?: return null).add(HomeItem.Folder(unescape(parts[0]), unescape(parts[1]), apps))
                }
                else -> return null
            }
        }
        return dock to pages
    }

    private fun migrateV1(v1: Pair<List<AppKey>, List<List<HomeItem>>>): HomeLayout {
        val (dock, pages) = v1
        val first = pages.firstOrNull().orEmpty().take(HomeLayout.PER_PAGE)
        val appsPage = HomePage(first.mapIndexed { i, item -> Placed(item, i % HomeLayout.COLUMNS, i / HomeLayout.COLUMNS) })
        return HomeLayout(dock, listOf(HomeLayoutPolicy.defaultWidgetPage(), appsPage))
    }

    private fun escape(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unescape(s: String) = s.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
