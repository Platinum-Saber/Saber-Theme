package com.sabertheme.feature.drawer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactResult(val id: Long, val lookupKey: String, val name: String, val phone: String?) {
    val uri: Uri get() = Contacts.getLookupUri(id, lookupKey)
}

/** Contacts by name/number through the provider's filter URI (`READ_CONTACTS`). */
@Singleton
class ContactSearch @Inject constructor(@ApplicationContext private val context: Context) {

    fun granted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 3): List<ContactResult> = withContext(Dispatchers.IO) {
        if (!granted() || query.isBlank()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.HAS_PHONE_NUMBER)
        val found = buildList {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (size < limit && c.moveToNext()) {
                    val name = c.getString(2) ?: continue
                    add(ContactResult(c.getLong(0), c.getString(1), name, phone = null) to (c.getInt(3) == 1))
                }
            }
        }
        found.map { (contact, hasPhone) -> if (hasPhone) contact.copy(phone = primaryPhone(contact.id)) else contact }
    }

    /** "Mobile · 077 123 4567" for the primary (else first) number. */
    private fun primaryPhone(contactId: Long): String? = context.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone.NUMBER, Phone.TYPE, Phone.LABEL),
        "${Phone.CONTACT_ID} = ?",
        arrayOf(contactId.toString()),
        "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
    )?.use { c ->
        if (!c.moveToFirst()) return@use null
        val number = c.getString(0) ?: return@use null
        val type = Phone.getTypeLabel(context.resources, c.getInt(1), c.getString(2))
        "$type · $number"
    }
}
