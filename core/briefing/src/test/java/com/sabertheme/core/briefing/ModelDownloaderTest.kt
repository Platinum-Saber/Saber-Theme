package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val content = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    /** Serves [content]; honours ranges unless [ignoreRange]. Records each `from`. */
    private class Server(val content: ByteArray, val ignoreRange: Boolean = false, val cutAt: Int? = null) : Fetcher {
        val asked = mutableListOf<Long>()
        override fun open(url: String, from: Long): Fetched {
            asked += from
            val start = if (ignoreRange) 0 else from.toInt()
            val end = cutAt?.coerceAtLeast(start) ?: content.size
            return Fetched(if (start > 0) 206 else 200, start.toLong(), ByteArrayInputStream(content, start, end - start))
        }
    }

    @Test
    fun `resumes from the part file with a range request`() = runTest {
        val part = tmp.root.resolve("m.part")
        part.writeBytes(content.copyOf(120_000))
        val server = Server(content)
        ModelDownloader(server).fetch("https://x", part, content.size.toLong()) {}
        assertThat(server.asked).containsExactly(120_000L)
        assertThat(part.readBytes()).isEqualTo(content)
    }

    @Test
    fun `starts over when the server ignores the range`() = runTest {
        val part = tmp.root.resolve("m.part")
        part.writeBytes(content.copyOf(120_000))
        ModelDownloader(Server(content, ignoreRange = true)).fetch("https://x", part, content.size.toLong()) {}
        assertThat(part.readBytes()).isEqualTo(content)
    }

    @Test
    fun `a dropped connection keeps the part for the next try`() = runTest {
        val part = tmp.root.resolve("m.part")
        val error = runCatching { ModelDownloader(Server(content, cutAt = 50_000)).fetch("https://x", part, content.size.toLong()) {} }
        assertThat(error.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(part.length()).isEqualTo(50_000L)

        ModelDownloader(Server(content)).fetch("https://x", part, content.size.toLong()) {}
        assertThat(part.readBytes()).isEqualTo(content)
    }

    @Test
    fun `a complete part needs no request`() = runTest {
        val part = tmp.root.resolve("m.part").apply { writeBytes(content) }
        val server = Server(content)
        ModelDownloader(server).fetch("https://x", part, content.size.toLong()) {}
        assertThat(server.asked).isEmpty()
    }

    @Test
    fun `verify keeps a good file and deletes a bad one`() = runTest {
        val good = tmp.root.resolve("good").apply { writeBytes(content) }
        ModelDownloader(Server(content)).verify(good, sha)
        assertThat(good.exists()).isTrue()

        val bad = tmp.root.resolve("bad").apply { writeBytes(content.copyOf().also { it[7] = 0 }) }
        val error = runCatching { ModelDownloader(Server(content)).verify(bad, sha) }.exceptionOrNull()
        assertThat(error).isInstanceOf(CorruptDownload::class.java)
        assertThat(bad.exists()).isFalse()
    }

    @Test
    fun `unpacks nested folders and skips links`() = runTest {
        val archive = tarBz2(
            "pack/" to null,
            "pack/espeak-ng-data/phontab" to "ph".toByteArray(),
            "pack/model.int8.onnx" to "m".toByteArray(),
        ) { tar ->
            tar.putArchiveEntry(TarArchiveEntry("pack/link", TarArchiveEntry.LF_SYMLINK).apply { linkName = "/etc/passwd" })
            tar.closeArchiveEntry()
        }
        val into = tmp.root.resolve("out")
        ModelDownloader(Server(content)).unpack(archive, into)
        assertThat(into.resolve("pack/espeak-ng-data/phontab").readText()).isEqualTo("ph")
        assertThat(into.resolve("pack/model.int8.onnx").readText()).isEqualTo("m")
        assertThat(into.resolve("pack/link").exists()).isFalse()
    }

    @Test
    fun `refuses entries that escape the folder`() {
        val archive = tarBz2("pack/../../evil" to "x".toByteArray())
        val into = tmp.root.resolve("out")
        assertThrows(CorruptDownload::class.java) { kotlinx.coroutines.runBlocking { ModelDownloader(Server(content)).unpack(archive, into) } }
        assertThat(tmp.root.parentFile!!.resolve("evil").exists()).isFalse()
    }

    @Test
    fun `downloaded copy wins over the adb one`() {
        val downloaded = tmp.root.resolve("new")
        val legacy = tmp.root.resolve("old").apply { writeText("x") }
        assertThat(ModelFiles.pick(downloaded, legacy)).isEqualTo(legacy)
        downloaded.writeText("y")
        assertThat(ModelFiles.pick(downloaded, legacy)).isEqualTo(downloaded)
    }

    @Test
    fun `kokoro is complete only with every file sherpa needs`() {
        val dir = tmp.newFolder("kokoro")
        ModelFiles.KOKORO_FILES.drop(1).forEach { dir.resolve(it).writeText("x") }
        assertThat(ModelFiles.complete(ModelPack.Kokoro, dir)).isFalse()
        dir.resolve(ModelFiles.KOKORO_FILES.first()).writeText("x")
        assertThat(ModelFiles.complete(ModelPack.Kokoro, dir)).isTrue()
    }

    @Test
    fun `content range start is read`() {
        assertThat(HttpsFetcher.rangeStart("bytes 100-999/1000")).isEqualTo(100L)
        assertThat(HttpsFetcher.rangeStart(null)).isEqualTo(-1L)
    }

    private fun tarBz2(vararg entries: Pair<String, ByteArray?>, extra: (TarArchiveOutputStream) -> Unit = {}): File {
        val file = tmp.newFile()
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            for ((name, bytes) in entries) {
                val entry = TarArchiveEntry(name)
                if (bytes != null) entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                bytes?.let(tar::write)
                tar.closeArchiveEntry()
            }
            extra(tar)
        }
        return file
    }
}
