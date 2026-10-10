package com.sabertheme.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sabertheme.core.data.ScreenLock
import com.sabertheme.core.designsystem.component.GlassSheet
import com.sabertheme.core.designsystem.theme.Saber

/** Explains the accessibility service the first time double-tap to lock is used without it. */
@Composable
internal fun LockPrompt(visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassSheet(visible, onDismiss) {
        BasicText("Double-tap to lock", Modifier.padding(bottom = 8.dp), style = Saber.type.titleLarge.copy(color = Saber.colors.textPrimary))
        BasicText(
            "Saber needs its accessibility service to turn the screen off. It only locks the phone, like the power button " +
                "(fingerprint and face unlock still work). It reads no screen content and receives no events.\n\n" +
                "In Accessibility settings, open Installed apps → Saber and turn it on. You can switch the gesture off in Saber's settings.",
            Modifier.padding(bottom = 20.dp),
            style = Saber.type.body.copy(color = Saber.colors.textSecondary),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Not now", Modifier.weight(1f), primary = false, onClick = onDismiss)
            Button("Open settings", Modifier.weight(1f), primary = true) {
                onDismiss()
                ScreenLock.openSettings(context)
            }
        }
    }
}

@Composable
private fun Button(label: String, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) Saber.colors.accent else Saber.colors.textPrimary.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Saber.type.labelMedium.copy(color = if (primary) Color.White else Saber.colors.textPrimary))
    }
}
