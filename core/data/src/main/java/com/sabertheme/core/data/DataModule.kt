package com.sabertheme.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Device-only usage data (launch counts); kept out of backups, unlike settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UsageStore

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** Layout and settings: the only file backups include (`data_extraction_rules.xml`). */
    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    @UsageStore
    fun usage(@ApplicationContext context: Context, settings: DataStore<Preferences>): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(migrations = listOf(LaunchCountsMigration(settings))) {
            context.preferencesDataStoreFile("usage")
        }
}
