package com.sabertheme.core.briefing

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** One HTTP reply: [start] is where [body] begins in the whole file. */
class Fetched(val code: Int, val start: Long, val body: InputStream?, private val onClose: () -> Unit = {}) : Closeable {
    override fun close() {
        runCatching { body?.close() }
        onClose()
    }
}

fun interface Fetcher {
    /** GETs [url] from byte [from] on (a Range request when [from] > 0). */
    fun open(url: String, from: Long): Fetched
}

/** A download whose bytes did not match the pinned size or hash. */
class CorruptDownload(message: String) : IOException(message)

/**
 * Resumable, verified downloads for [ModelPack]s. Plain JVM so it is unit
 * tested; [ModelDownloadWorker] runs it.
 */
class ModelDownloader(private val fetcher: Fetcher = HttpsFetcher) {

    /**
     * Brings [part] up to [total] bytes from [url], resuming from what is
     * already there. [onProgress] gets the bytes on disk so far.
     */
    suspend fun fetch(url: String, part: File, total: Long, onProgress: suspend (Long) -> Unit) {
        part.parentFile?.mkdirs()
        if (part.length() > total) part.delete()
        var have = if (part.exists()) part.length() else 0L
        if (have < total) {
            fetcher.open(url, have).use { reply ->
                when (reply.code) {
                    206 -> if (reply.start != have) {
                        // Not where the part ends: start over on the next try.
                        part.delete()
                        throw IOException("Range reply started at ${reply.start}, not $have")
                    }
                    200 -> have = 0 // The server ignored the range: start over now.
                    else -> throw IOException("HTTP ${reply.code}")
                }
                val body = reply.body ?: throw IOException("Empty reply")
                FileOutputStream(part, have > 0).use { out ->
                    val buffer = ByteArray(BUFFER)
                    while (have < total) {
                        currentCoroutineContext().ensureActive()
                        val n = body.read(buffer)
                        if (n < 0) break
                        val keep = minOf(n.toLong(), total - have).toInt()
                        out.write(buffer, 0, keep)
                        have += keep
                        onProgress(have)
                    }
                }
            }
        }
        if (part.length() != total) throw IOException("Stopped at ${part.length()} of $total bytes")
    }

    /** Checks [file] against [sha256]; a mismatch deletes it (it cannot be resumed into shape). */
    suspend fun verify(file: File, sha256: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(sha256, ignoreCase = true)) {
            file.delete()
            throw CorruptDownload("SHA-256 mismatch")
        }
    }

    /**
     * Unpacks a `.tar.bz2` into [into] (emptied first). Only plain files and
     * folders are written; links, devices and any entry that would land
     * outside [into] are refused.
     */
    suspend fun unpack(archive: File, into: File) {
        into.deleteRecursively()
        into.mkdirs()
        val base = into.canonicalPath + File.separator
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream(), BUFFER))).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextEntry ?: break
                val target = File(into, entry.name).canonicalFile
                if (!(target.path + File.separator).startsWith(base)) throw CorruptDownload("Entry outside the folder: ${entry.name}")
                when {
                    // Links, devices and FIFOs are never written (isFile is true for some of them).
                    entry.isSymbolicLink || entry.isLink || entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO -> Unit
                    entry.isDirectory -> target.mkdirs()
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { tar.copyTo(it, BUFFER) }
                    }
                }
            }
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

/** HTTPS only: plain URLs are refused, and a redirect off HTTPS is refused too. */
object HttpsFetcher : Fetcher {
    override fun open(url: String, from: Long): Fetched {
        if (!url.startsWith("https://")) throw IOException("Refused non-HTTPS URL")
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
        val code = connection.responseCode
        if (connection.url.protocol != "https") {
            connection.disconnect()
            throw IOException("Refused redirect off HTTPS")
        }
        val start = if (code == 206) rangeStart(connection.getHeaderField("Content-Range")) else 0L
        val body = if (code in 200..299) connection.inputStream else null
        return Fetched(code, start, body) { connection.disconnect() }
    }

    /** `bytes 100-999/1000` → 100; anything unreadable → -1 (treated as a mismatch). */
    fun rangeStart(header: String?): Long =
        header?.removePrefix("bytes ")?.substringBefore('-')?.trim()?.toLongOrNull() ?: -1L
}
