package com.sabertheme.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.Space

internal data class OpenFolder(val folder: HomeCell.Folder, val origin: Rect)

/**
 * Open folder: a thick glass panel that grows out of the folder tile and
 * shrinks back into it. [progress] is shared with the home content so it
 * can blur in step.
 */
@Composable
internal fun FolderOverlay(
    open: OpenFolder?,
    progress: Animatable<Float, *>,
    onDismiss: () -> Unit,
    onLaunch: (HomeApp, Rect) -> Unit,
    onAppMenu: (HomeApp, Rect, Offset) -> Unit,
) {
    val env = LocalGlassEnvironment.current
    var shown by remember { mutableStateOf<OpenFolder?>(null) }
    LaunchedEffect(open) {
        if (open != null) {
            shown = open
            progress.animateTo(1f, GlassMotion.morph(env.reducedMotion))
        } else if (shown != null) {
            progress.animateTo(0f, GlassMotion.sheet(env.reducedMotion))
            shown = null
        }
    }
    val current = shown ?: return
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    var startScale by remember { mutableStateOf(0.2f) }
    Layout(
        content = {
            GlassSurface(
                Modifier.graphicsLayer {
                    val p = progress.value
                    transformOrigin = origin
                    val s = startScale + (1f - startScale) * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 1.6f).coerceIn(0f, 1f)
                },
                shape = GlassShape.Rounded(Radius.xl),
                material = GlassMaterial.Thick,
            ) {
                Column(
                    Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    BasicText(current.folder.name, style = Saber.type.titleLarge.copy(color = Saber.colors.textPrimary))
                    current.folder.apps.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                            row.forEach { app ->
                                HomeAppIcon(
                                    app,
                                    onClick = { bounds -> onLaunch(app, bounds) },
                                    onLongClick = { bounds, at -> onAppMenu(app, bounds, at) },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val panel = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val x = (constraints.maxWidth - panel.width) / 2
        val y = (constraints.maxHeight - panel.height) / 2 - 20.dp.roundToPx()
        val tile = current.origin
        origin = TransformOrigin(
            ((tile.center.x - x) / panel.width).coerceIn(0f, 1f),
            ((tile.center.y - y) / panel.height).coerceIn(0f, 1f),
        )
        startScale = (tile.width / panel.width).coerceIn(0.05f, 1f)
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(IntOffset(x, y)) }
    }
}
