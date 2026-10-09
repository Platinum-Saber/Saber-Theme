package com.sabertheme.feature.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.component.GlassSheet
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.model.WidgetType

/**
 * Widget picker sheet (Figma "Widget picker"): category chips, then a live
 * preview of every widget at every size. Tapping one calls [onAdd] and closes.
 */
@Composable
fun WidgetPicker(visible: Boolean, onDismiss: () -> Unit, onAdd: (WidgetType, WidgetSize) -> Unit) {
    var category by remember { mutableStateOf<WidgetCategory?>(null) }
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.62f).toDp() }
    GlassSheet(visible, onDismiss) {
        BasicText("Widgets", style = Saber.type.titleLarge.copy(color = Saber.colors.textPrimary))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", category == null) { category = null }
            WidgetCategory.entries.forEach { c -> Chip(c.label, category == c) { category = c } }
        }
        Spacer(Modifier.height(14.dp))
        val options = WidgetCatalog.entries
            .filter { category == null || it.category == category }
            .flatMap { entry -> entry.sizes.map { entry to it } }
        BoxWithConstraints {
            val width = maxWidth
            LazyColumn(Modifier.heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(options, key = { (entry, size) -> "${entry.type}:$size" }) { (entry, size) ->
                    Column {
                        BasicText(
                            "${entry.title} · ${size.spanX}×${size.spanY}",
                            style = Saber.type.labelMedium.copy(color = Saber.colors.textSecondary),
                        )
                        Spacer(Modifier.height(6.dp))
                        WidgetPreview(entry.type, size, Modifier.size(previewWidth(width, size), previewHeight(size))) {
                            onAdd(entry.type, size)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

/** Same column math as the home grid (8 dp tile inset, 12 dp gaps), on the sheet's width. */
private fun previewWidth(available: Dp, size: WidgetSize): Dp {
    val unit = (available - 16.dp - 12.dp * 3) / 4
    return unit * size.spanX + 12.dp * (size.spanX - 1)
}

private fun previewHeight(size: WidgetSize): Dp = 96.dp * size.spanY - 12.dp

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Saber.colors
    BasicText(
        label,
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent else colors.textPrimary.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = Saber.type.labelMedium.copy(color = if (selected) Color.White else colors.textPrimary),
    )
}
