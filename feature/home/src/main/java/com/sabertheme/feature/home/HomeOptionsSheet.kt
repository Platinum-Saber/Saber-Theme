package com.sabertheme.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.component.GlassSheet
import com.sabertheme.core.designsystem.component.GlassSlider
import com.sabertheme.core.designsystem.glass.GlassBackdrop
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.Space
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.model.GlassSettings
import com.sabertheme.core.model.WallpaperChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THUMB_W = 72.dp
private val THUMB_H = 136.dp

/**
 * Long-press-on-home sheet: wallpaper (bundled or your own photos) and glass
 * intensity. [extra] lets the app add debug tools such as the Glass Lab.
 */
@Composable
fun HomeOptionsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    settings: GlassSettings,
    photos: List<String>,
    importing: Boolean,
    viewModel: HomeViewModel,
    /** Called while dragging so the glass responds before the value is saved. */
    onIntensityPreview: (Float) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri)
    }
    GlassSheet(visible, onDismiss) {
        SectionTitle("Wallpaper")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            item(key = "add") {
                AddPhotoTile(importing) { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
            }
            items(AuroraWallpaper.bundled, key = { it.id }) { spec ->
                val choice = WallpaperChoice.Bundled(spec.id)
                val thumb = auroraThumb(spec)
                WallpaperTile(thumb, spec.id, selected = settings.wallpaper == choice, onDelete = null) {
                    viewModel.selectWallpaper(choice)
                }
            }
            items(photos, key = { it }) { name ->
                val choice = WallpaperChoice.Photo(name)
                val thumb = photoThumb(viewModel, name)
                WallpaperTile(thumb, "Photo", selected = settings.wallpaper == choice, onDelete = { viewModel.deletePhoto(name) }) {
                    viewModel.selectWallpaper(choice)
                }
            }
        }
        Spacer(Modifier.height(Space.s5))
        SectionTitle("Glass intensity")
        GlassSlider(
            value = settings.intensity,
            onValueChange = onIntensityPreview,
            onValueChangeFinished = viewModel::setIntensity,
        )
        extra()
    }
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, Modifier.padding(bottom = Space.s3), style = Saber.type.titleMedium.copy(color = Saber.colors.textPrimary))
}

@Composable
private fun auroraThumb(spec: AuroraWallpaper): ImageBitmap? {
    val density = LocalDensity.current
    val w = with(density) { THUMB_W.roundToPx() }
    val h = with(density) { THUMB_H.roundToPx() }
    return produceState<ImageBitmap?>(null, spec, w, h) {
        value = withContext(Dispatchers.Default) { GlassBackdrop.auroraThumbnail(spec, w, h).asImageBitmap() }
    }.value
}

@Composable
private fun photoThumb(viewModel: HomeViewModel, name: String): ImageBitmap? {
    val w = with(LocalDensity.current) { THUMB_W.roundToPx() }
    return produceState<ImageBitmap?>(null, name, w) {
        value = viewModel.photoThumbnail(name, w)?.asImageBitmap()
    }.value
}

@Composable
private fun WallpaperTile(
    thumb: ImageBitmap?,
    description: String,
    selected: Boolean,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    val colors = Saber.colors
    val shape = RoundedCornerShape(Radius.sm)
    Box(
        Modifier
            .size(THUMB_W, THUMB_H)
            .clip(shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.accent else colors.glassBorder, shape)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = description },
    ) {
        if (thumb != null) {
            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (onDelete != null) {
            GlassSurface(
                Modifier.align(Alignment.TopEnd).padding(Space.s1).size(26.dp),
                shape = GlassShape.Pill,
                material = GlassMaterial.Thick,
                onClick = onDelete,
            ) {
                Image(
                    painterResource(UiGlyph.CLOSE.drawable),
                    contentDescription = "Remove photo",
                    colorFilter = ColorFilter.tint(colors.glyph),
                    modifier = Modifier.align(Alignment.Center).size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AddPhotoTile(importing: Boolean, onClick: () -> Unit) {
    val colors = Saber.colors
    GlassSurface(
        Modifier.size(THUMB_W, THUMB_H),
        shape = GlassShape.Rounded(Radius.sm),
        material = GlassMaterial.Regular,
        onClick = if (importing) null else onClick,
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(if (importing) UiGlyph.WALLPAPER.drawable else UiGlyph.PLUS.drawable),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.glyph),
                modifier = Modifier.size(28.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(Space.s2))
            BasicText(
                if (importing) "Adding…" else "Photo",
                style = Saber.type.labelMedium.copy(color = colors.textSecondary),
            )
        }
    }
}
