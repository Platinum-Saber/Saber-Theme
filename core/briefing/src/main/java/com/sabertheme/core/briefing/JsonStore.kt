package com.sabertheme.core.briefing

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal val BriefingJson = Json { ignoreUnknownKeys = true }

/**
 * A JSON file shared by Home and the `:brain` process, so it uses the
 * multi-process DataStore. It lives in `noBackupFilesDir`: what it holds
 * (mail previews, the Moodle token) stays on this phone. A corrupt file
 * starts over from [default] rather than failing the briefing.
 */
internal fun <T> jsonStore(context: Context, name: String, serializer: KSerializer<T>, default: T): DataStore<T> =
    MultiProcessDataStoreFactory.create(
        serializer = JsonSerializer(serializer, default),
        corruptionHandler = ReplaceFileCorruptionHandler { default },
        produceFile = { File(context.noBackupFilesDir, "$name.json") },
    )

private class JsonSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        BriefingJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable briefing store", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable briefing store", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(BriefingJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
