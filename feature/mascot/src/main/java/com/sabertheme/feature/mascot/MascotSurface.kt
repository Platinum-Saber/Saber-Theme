package com.sabertheme.feature.mascot

import android.content.Context
import android.graphics.BlendMode
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A full-window transparent SurfaceView, composited above Home by the system,
 * that the mascot is drawn into with a hardware canvas. It never moves or
 * resizes: she moves inside it, so her picture and position always arrive in
 * the same buffer (a moving SurfaceView is repositioned on Home's frames and
 * her drawing on the surface's own, which flickered while dragging). Home
 * never redraws because of her animation.
 *
 * Attached behind the Compose view: z-on-top makes it draw above anyway, and
 * behind it never sees touches, so Home and her touch target get them all.
 */
internal class MascotSurface(context: Context) : SurfaceHolder.Callback {
    private val view = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var ready = false
    private val scope = CanvasDrawScope()
    private val location = IntArray(2)

    init {
        view.holder.addCallback(this)
    }

    /** Adds the surface behind [composeRoot]'s top-level content view. */
    fun attach(composeRoot: View) {
        if (view.parent != null) return
        val content = composeRoot.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.addView(view, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun detach() {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /**
     * Draws one frame: [content] in a [w] x [h] box whose top-left is at
     * [topLeftInWindow]. Nothing is drawn when [alpha] is 0 (the buffer is
     * still cleared, so she disappears).
     */
    fun draw(density: Density, topLeftInWindow: Offset, w: Float, h: Float, alpha: Float, content: DrawScope.() -> Unit) {
        if (!ready) return
        val holder = view.holder
        if (!holder.surface.isValid) return
        val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
        try {
            canvas.drawColor(0, BlendMode.CLEAR)
            if (alpha > 0.01f) {
                view.getLocationInWindow(location)
                val layer = if (alpha < 0.99f) canvas.saveLayerAlpha(null, (alpha * 255).toInt()) else -1
                scope.draw(density, LayoutDirection.Ltr, Canvas(canvas), Size(w, h)) {
                    translate(topLeftInWindow.x - location[0], topLeftInWindow.y - location[1]) { content() }
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
}
