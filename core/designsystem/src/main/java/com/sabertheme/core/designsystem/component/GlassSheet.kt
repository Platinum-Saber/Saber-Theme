package com.sabertheme.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Space

/**
 * Bottom sheet of thick glass. It rises from below with the sheet spring and
 * sinks back before leaving composition, so it never teleports.
 */
@Composable
fun GlassSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val env = LocalGlassEnvironment.current
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            progress.animateTo(1f, GlassMotion.sheet(env.reducedMotion))
        } else {
            progress.animateTo(0f, GlassMotion.sheet(env.reducedMotion))
            shown = false
        }
    }
    if (!shown) return
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Space.s3)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - progress.value) * (size.height + 48.dp.toPx())
                },
            shape = GlassShape.Rounded(Radius.xl),
            material = GlassMaterial.Thick,
        ) {
            Column(Modifier.padding(Space.s5), content = content)
        }
    }
}
