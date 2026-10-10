package com.sabertheme.feature.mascot

import android.content.Context
import android.graphics.BlendMode
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A small transparent SurfaceView above Home that the mascot is drawn into
 * with a hardware canvas. The system composites it on its own, so her
 * animation never makes Home (and its full-screen glass shaders) redraw;
 * Home only redraws when the surface moves (dragging, throwing, walking).
 */
internal class MascotSurface(context: Context) : SurfaceHolder.Callback {
    val view = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }
    private var ready = false
    private val scope = CanvasDrawScope()

    init {
        view.holder.addCallback(this)
    }

    /** Draws one frame: [content] in a [w] x [h] box, inset by [PAD] of the height on every side. */
    fun draw(density: Density, w: Float, h: Float, alpha: Float, content: DrawScope.() -> Unit) {
        if (!ready) return
        val holder = view.holder
        if (!holder.surface.isValid) return
        val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
        try {
            canvas.drawColor(0, BlendMode.CLEAR)
            if (alpha > 0.01f) {
                val pad = h * PAD
                val full = Size(w + pad * 2, h + pad * 2)
                val layer = if (alpha < 0.99f) canvas.saveLayerAlpha(null, (alpha * 255).toInt()) else -1
                scope.draw(density, LayoutDirection.Ltr, Canvas(canvas), full) {
                    translate(pad, pad) {
                        // The rig scales to the scope height: give it a w x h box.
                        drawContext.size = Size(w, h)
                        content()
                    }
                }
                if (layer >= 0) canvas.restoreToCount(layer)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ready = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ready = false
    }

    companion object {
        /** Room around her box for raised arms, the sword and effects. */
        const val PAD = 0.12f
    }
}
