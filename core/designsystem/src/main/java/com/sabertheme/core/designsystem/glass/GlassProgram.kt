package com.sabertheme.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Trace
import androidx.compose.ui.graphics.ShaderBrush

/**
 * One compiled glass shader with its brush, uniform cache and bound backdrop.
 * Compiling AGSL costs ~0.5 ms, so a page of tiles compiling on its first
 * frame blew the frame budget at every swipe; nodes now borrow programs from
 * [GlassPrograms] and hand them back on detach.
 */
internal class GlassProgram {
    val shader = RuntimeShader(GLASS_AGSL)
    val brush = ShaderBrush(shader)
    val uniforms = UniformWriter(shader)
    private var boundBitmap: Bitmap? = null

    /** Binds [bitmap] as the backdrop input; returns true when it changed. */
    fun bind(bitmap: Bitmap): Boolean {
        if (bitmap === boundBitmap) return false
        shader.setInputShader("backdrop", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        })
        boundBitmap = bitmap
        return true
    }
}

/** Process-wide pool of [GlassProgram]s, pre-warmed off the main thread. */
internal object GlassPrograms {
    private val free = ArrayDeque<GlassProgram>()

    fun obtain(): GlassProgram = synchronized(free) { free.removeLastOrNull() } ?: run {
        Trace.beginSection("Glass:compile")
        try {
            GlassProgram()
        } finally {
            Trace.endSection()
        }
    }

    fun recycle(program: GlassProgram) {
        synchronized(free) { if (free.size < MAX_FREE) free.addLast(program) }
    }

    /** Compiles programs until [count] are free; call from a background thread. */
    fun prewarm(count: Int = PREWARM) {
        while (synchronized(free) { free.size } < count) {
            val program = GlassProgram()
            synchronized(free) { free.addLast(program) }
        }
    }

    /** Two pages of a 4x5 grid plus dock, pill and folder overlay. */
    private const val PREWARM = 56
    private const val MAX_FREE = 96
}
