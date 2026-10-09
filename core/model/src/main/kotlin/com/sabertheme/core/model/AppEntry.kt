package com.sabertheme.core.model

/**
 * A launchable activity. [userSerial] distinguishes the same app in the main
 * profile, the work profile and Secure Folder.
 */
data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val userSerial: Long,
) {
    val key: AppKey get() = AppKey(packageName, className, userSerial)
}

data class AppKey(val packageName: String, val className: String, val userSerial: Long) {
    fun encode(): String = "$packageName/$className#$userSerial"

    companion object {
        fun decode(value: String): AppKey? {
            val slash = value.indexOf('/')
            val hash = value.lastIndexOf('#')
            if (slash <= 0 || hash <= slash + 1) return null
            val serial = value.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(value.substring(0, slash), value.substring(slash + 1, hash), serial)
        }
    }
}
