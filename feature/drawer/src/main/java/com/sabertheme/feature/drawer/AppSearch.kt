package com.sabertheme.feature.drawer

import java.text.Normalizer
import java.util.Locale

/**
 * App ranking for search: exact > prefix > word-start (incl. initials, "gm" →
 * Google Maps) > substring > fuzzy subsequence, on the label; the package
 * counts one notch lower and never fuzzily, without boilerplate segments.
 */
internal object AppSearch {
    private const val EXACT = 5
    private const val PREFIX = 4
    private const val WORD = 3
    private const val SUBSTRING = 2
    private const val FUZZY = 1

    private val COMMON_SEGMENTS = setOf("com", "org", "net", "android", "google", "apps", "app", "samsung", "sec", "mobile")
    private val WORD_SPLIT = Regex("(?<=\\p{Ll})(?=\\p{Lu})|[^\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{M}+")

    fun <T> rank(query: String, items: List<T>, label: (T) -> String, packageName: (T) -> String): List<T> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return items
            .map { it to score(q, label(it), packageName(it)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { normalize(label(it.first)) })
            .map { it.first }
    }

    /** 0 when nothing matches; label tiers score 2x, package tiers 2x - 1. */
    fun score(query: String, label: String, packageName: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val labelTier = tier(q, normalize(label), words(label))
        val segments = packageName.lowercase(Locale.ROOT).split('.').filter { it.isNotEmpty() && it !in COMMON_SEGMENTS }
        val pkgTier = tier(q, segments.joinToString("."), segments).takeIf { it > FUZZY } ?: 0
        return maxOf(labelTier * 2, pkgTier * 2 - 1, 0)
    }

    /** Rail entries for A–Z sorted [labels]: (letter, index of its first label); non-letters are "#". */
    fun sections(labels: List<String>): List<Pair<String, Int>> {
        val seen = LinkedHashMap<String, Int>()
        labels.forEachIndexed { i, label ->
            val first = normalize(label).firstOrNull()
            val letter = if (first != null && first.isLetter()) first.uppercaseChar().toString() else "#"
            seen.putIfAbsent(letter, i)
        }
        return seen.toList()
    }

    private fun tier(q: String, text: String, words: List<String>): Int = when {
        text.isEmpty() -> 0
        text == q -> EXACT
        text.startsWith(q) -> PREFIX
        words.any { it.startsWith(q) } -> WORD
        q.length >= 2 && words.size >= 2 && words.joinToString("") { it.take(1) }.startsWith(q) -> WORD
        text.contains(q) -> SUBSTRING
        isSubsequence(q, text) -> FUZZY
        else -> 0
    }

    private fun isSubsequence(q: String, text: String): Boolean {
        var i = 0
        for (c in text) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    private fun words(text: String): List<String> = text.split(WORD_SPLIT).map(::normalize).filter { it.isNotEmpty() }

    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
}
