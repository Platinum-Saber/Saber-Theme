package com.sabertheme.core.designsystem.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Feeds [EffectsPolicy] from the system (lifecycle, Power Saving, thermal
 * status, "Remove animations", touches) and applies its decisions to [env]:
 * effect intensity, reduced motion, and whether the tilt sensor runs.
 */
@Composable
fun GlassEffectsController(env: GlassEnvironment, userIntensity: Float) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val policy = remember { EffectsPolicy() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    var sensorsOn by remember { mutableStateOf(false) }

    LaunchedEffect(userIntensity) {
        policy.userIntensity = userIntensity
        wake.trySend(Unit)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    policy.resumed = true
                    policy.reducedMotion = Settings.Global.getFloat(
                        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
                    ) == 0f
                    policy.onInteraction(SystemClock.uptimeMillis())
                }
                Lifecycle.Event.ON_PAUSE -> policy.resumed = false
                else -> return@LifecycleEventObserver
            }
            wake.trySend(Unit)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val power = context.getSystemService(PowerManager::class.java)
        fun readPower() {
            policy.powerSave = power.isPowerSaveMode
            policy.thermalThrottled = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            wake.trySend(Unit)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = readPower()
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val thermal = PowerManager.OnThermalStatusChangedListener { readPower() }
        power.addThermalStatusListener(context.mainExecutor, thermal)
        readPower()
        onDispose {
            context.unregisterReceiver(receiver)
            power.removeThermalStatusListener(thermal)
        }
    }

    LaunchedEffect(policy) {
        env.onInteraction = {
            val wasIdle = !sensorsOn
            policy.onInteraction(SystemClock.uptimeMillis())
            if (wasIdle) wake.trySend(Unit)
        }
        while (true) {
            val now = SystemClock.uptimeMillis()
            val state = policy.evaluate(now)
            env.intensity = state.intensity
            env.reducedMotion = policy.reducedMotion
            sensorsOn = state.sensorsOn
            val wait = if (state.mode == EffectsPolicy.Mode.Active) policy.idleAtMs() - now else Long.MAX_VALUE
            withTimeoutOrNull(wait.coerceAtLeast(1)) { wake.receive() }
        }
    }

    if (sensorsOn && env.effects.tilt) TiltSensor(env)
}

/** Reports touches to the effects policy without consuming them. Put on the root. */
fun Modifier.glassInteractionTracker(env: GlassEnvironment): Modifier = pointerInput(env) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            env.onInteraction()
        }
    }
}

/**
 * Game rotation vector -> virtual light direction and wallpaper parallax.
 * Tilt is measured against a slowly drifting rest pose, so any natural
 * holding angle reads as "level". Registered only while composed.
 */
@Composable
private fun TiltSensor(env: GlassEnvironment) {
    val context = LocalContext.current
    DisposableEffect(env) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var restPitch = Float.NaN
        var restRoll = 0f
        var pitch = 0f
        var roll = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                if (restPitch.isNaN()) {
                    restPitch = angles[1]
                    restRoll = angles[2]
                    pitch = angles[1]
                    roll = angles[2]
                }
                pitch += (angles[1] - pitch) * SMOOTHING
                roll += (wrap(angles[2] - roll)) * SMOOTHING
                restPitch += (pitch - restPitch) * REST_DRIFT
                restRoll += wrap(roll - restRoll) * REST_DRIFT
                val dx = (wrap(roll - restRoll) / MAX_TILT).coerceIn(-1f, 1f)
                val dy = ((pitch - restPitch) / MAX_TILT).coerceIn(-1f, 1f)

                // A fixed lamp above the phone: tilting right moves the light left.
                val lx = GlassEnvironment.DEFAULT_LIGHT.x - dx * LIGHT_SWING
                val ly = GlassEnvironment.DEFAULT_LIGHT.y + dy * LIGHT_SWING
                val len = sqrt(lx * lx + ly * ly).coerceAtLeast(0.001f)
                env.light = Offset(lx / len, ly / len)
                val reach = (env.backdrop?.overscan ?: 0f) * PARALLAX_SHARE
                env.tiltParallax = Offset(dx * reach, -dy * reach)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
}

private fun wrap(a: Float): Float {
    var x = a
    while (x > PI) x -= (2 * PI).toFloat()
    while (x < -PI) x += (2 * PI).toFloat()
    return x
}

private const val SMOOTHING = 0.18f
private const val REST_DRIFT = 0.004f
private const val MAX_TILT = 0.35f
private const val LIGHT_SWING = 0.9f
/** Tilt uses this share of the overscan; the rest is left for page parallax. */
private const val PARALLAX_SHARE = 0.55f
