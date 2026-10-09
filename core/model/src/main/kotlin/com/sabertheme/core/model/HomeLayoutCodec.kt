package com.sabertheme.core.model

/**
 * Line-based text format for [HomeLayout], stored in DataStore:
 * ```
 * v1
 * D <appKey>
 * P
 * A <appKey>
 * F <id>\t<name>\t<appKey>\t<appKey>...
 * ```
 * `P` starts a page. Names are escaped so tabs and newlines round-trip.
 */
object HomeLayoutCodec {
    private const val VERSION = "v1"

    fun encode(layout: HomeLayout): String = buildString {
        appendLine(VERSION)
        layout.dock.forEach { appendLine("D ${it.encode()}") }
        for (page in layout.pages) {
            appendLine("P")
            for (item in page) when (item) {
                is HomeItem.App -> appendLine("A ${item.key.encode()}")
                is HomeItem.Folder -> appendLine(
                    (listOf(escape(item.id), escape(item.name)) + item.apps.map { it.encode() })
                        .joinToString("\t", prefix = "F "),
                )
            }
        }
    }

    /** Returns null for anything it cannot read, so callers fall back to a default layout. */
    fun decode(text: String?): HomeLayout? {
        val lines = text?.lines()?.filter { it.isNotEmpty() } ?: return null
        if (lines.firstOrNull() != VERSION) return null
        val dock = mutableListOf<AppKey>()
        val pages = mutableListOf<MutableList<HomeItem>>()
        for (line in lines.drop(1)) {
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
        return HomeLayout(dock, pages)
    }

    private fun escape(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unescape(s: String) = s.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
