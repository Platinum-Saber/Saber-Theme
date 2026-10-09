package com.sabertheme.iconpack

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Shown from the app drawer; the pack itself is applied in the launcher's settings. */
class InfoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(
            TextView(this).apply {
                setText(R.string.info)
                gravity = Gravity.CENTER
                textSize = 16f
                setPadding(pad, pad, pad, pad)
            },
        )
    }
}
