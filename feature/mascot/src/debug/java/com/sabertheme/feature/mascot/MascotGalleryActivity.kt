package com.sabertheme.feature.mascot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug-only: every named pose, large, for reviewing the rig on the phone.
 * `adb shell am start -n com.sabertheme.launcher/com.sabertheme.feature.mascot.MascotGalleryActivity`
 */
class MascotGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var time by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameMillis { time = it / 1000f } }
            LazyVerticalGrid(
                GridCells.Fixed(3),
                Modifier.fillMaxSize().background(Color(0xFF1B2140)).padding(top = 40.dp),
            ) {
                items(Pose.gallery) { (name, pose) ->
                    Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Canvas(Modifier.size(100.dp, 140.dp)) { drawSaber(pose, time) }
                        BasicText(name, style = TextStyle(color = Color.White, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}
