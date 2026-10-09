package com.sabertheme.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Home never navigates "back" out of itself.
            BackHandler {}
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0E1424)))),
            )
        }
    }
}
