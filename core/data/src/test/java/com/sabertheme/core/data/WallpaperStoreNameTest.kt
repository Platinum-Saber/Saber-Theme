package com.sabertheme.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class WallpaperStoreNameTest {
    @Test
    fun acceptsOnlyNamesImportMakes() {
        assertThat(WallpaperStore.isStoredName("${UUID.randomUUID()}.webp")).isTrue()
        assertThat(WallpaperStore.isStoredName("d2b28961-0c7e-4b8e-9c55-0123456789ab.webp")).isTrue()
    }

    @Test
    fun rejectsTraversalAndOtherFiles() {
        listOf(
            "../settings.preferences_pb",
            "../../shared_prefs/x.webp",
            "/data/data/com.sabertheme.launcher/files/datastore/settings.preferences_pb",
            "d2b28961-0c7e-4b8e-9c55-0123456789ab.webp/../../x",
            "d2b28961-0c7e-4b8e-9c55-0123456789ab.png",
            "D2B28961-0C7E-4B8E-9C55-0123456789AB.webp",
            "photo.webp",
            "",
        ).forEach { assertThat(WallpaperStore.isStoredName(it)).isFalse() }
    }
}
