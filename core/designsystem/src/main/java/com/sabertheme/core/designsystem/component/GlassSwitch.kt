package com.sabertheme.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.Saber

/** 44 x 26 toggle (Figma Toggle): accent track when on, thick-glass tint when off, white knob. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Saber.colors
    val reduced = LocalGlassEnvironment.current.reducedMotion
    val position by animateFloatAsState(if (checked) 1f else 0f, GlassMotion.press(reduced), label = "switch")
    val track by animateColorAsState(if (checked) colors.accent else colors.glassTint, label = "switch-track")
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .size(44.dp, 26.dp)
            .background(track, shape)
            .then(if (checked) Modifier else Modifier.border(1.dp, colors.glassBorder, shape))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = 18.dp * position)
                .size(22.dp)
                .shadow(1.5.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}
