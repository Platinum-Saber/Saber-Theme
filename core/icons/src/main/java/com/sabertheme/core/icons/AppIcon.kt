package com.sabertheme.core.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Draws an [AppIconSource] as a single-colour line glyph of [size]. */
@Composable
fun AppGlyphIcon(source: AppIconSource, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    when (source) {
        is AppIconSource.Glyph -> Image(
            painter = painterResource(source.glyph.drawable),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier.size(size),
        )
        is AppIconSource.Monochrome -> {
            val drawable = remember(source) { source.drawable.mutate() }
            Canvas(modifier.size(size)) {
                // The monochrome layer is a 108-unit canvas; scale it so its
                // 66-unit safe zone fills the glyph box.
                val full = this.size.minDimension * 108f / 66f
                val inset = ((full - this.size.minDimension) / 2f).toInt()
                drawable.setBounds(-inset, -inset, this.size.width.toInt() + inset, this.size.height.toInt() + inset)
                drawable.setTint(tint.toArgb())
                drawIntoCanvas { drawable.draw(it.nativeCanvas) }
            }
        }
        is AppIconSource.Letter -> Box(modifier.size(size), contentAlignment = Alignment.Center) {
            BasicText(
                text = source.letter,
                style = TextStyle(color = tint, fontSize = (size.value * 0.62f).sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}
