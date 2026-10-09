package com.sabertheme.widgets.glance

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.sabertheme.core.widgetdata.WidgetPermission
import kotlinx.coroutines.launch

/**
 * Translucent, UI-less step behind a widget's "Tap to allow": asks for the
 * runtime permission (or opens app info once the system stops asking), then
 * redraws the Saber widgets and finishes.
 */
class GlancePermissionActivity : ComponentActivity() {

    private var asked: String? = null

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = asked
        if (!granted && permission != null && !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        done()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val permission = when (intent.getStringExtra(EXTRA_PERMISSION)?.let { name -> WidgetPermission.entries.firstOrNull { it.name == name } }) {
            WidgetPermission.Calendar -> Manifest.permission.READ_CALENDAR
            WidgetPermission.Location -> Manifest.permission.ACCESS_COARSE_LOCATION
            WidgetPermission.NotificationListener, null -> null
        }
        if (permission == null) {
            finish()
            return
        }
        asked = permission
        request.launch(permission)
    }

    private fun done() {
        applicationContext.widgetSources().permissions.recheck()
        lifecycleScope.launch {
            SaberGlanceWidgets.updateAll(applicationContext)
            finish()
        }
    }

    companion object {
        const val EXTRA_PERMISSION = "com.sabertheme.widgets.glance.PERMISSION"
    }
}
