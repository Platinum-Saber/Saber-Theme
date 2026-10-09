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
import kotlin.math.exp
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
    var sensorRate by remember { mutableStateOf(EffectsPolicy.SensorRate.Off) }

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
            val wasIdle = sensorRate != EffectsPolicy.SensorRate.Fast
            policy.onInteraction(SystemClock.uptimeMillis())
            if (wasIdle) wake.trySend(Unit)
        }
        while (true) {
            val now = SystemClock.uptimeMillis()
            val state = policy.evaluate(now)
            env.intensity = state.intensity
            env.reducedMotion = policy.reducedMotion
            sensorRate = state.sensor
            val wait = if (state.mode == EffectsPolicy.Mode.Active) policy.idleAtMs() - now else Long.MAX_VALUE
            withTimeoutOrNull(wait.coerceAtLeast(1)) { wake.receive() }
        }
    }

    if (sensorRate != EffectsPolicy.SensorRate.Off && env.effects.tilt) {
        TiltSensor(env, fast = sensorRate == EffectsPolicy.SensorRate.Fast)
    }
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
 * holding angle reads as "level". Registered only while composed; [fast]
 * picks the sampling rate (Active vs Idle). The filter outlives rate
 * switches so the light never jumps when a touch speeds it up.
 */
@Composable
private fun TiltSensor(env: GlassEnvironment, fast: Boolean) {
    val context = LocalContext.current
    val filter = remember(env) { TiltFilter(env) }
    DisposableEffect(env, fast) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        filter.resume()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = filter.onSample(event.values, event.timestamp)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val rate = if (fast) SensorManager.SENSOR_DELAY_GAME else SensorManager.SENSOR_DELAY_UI
        if (sensor != null) manager.registerListener(listener, sensor, rate)
        onDispose { manager.unregisterListener(listener) }
    }
}

/** Time-based smoothing, so the light moves the same at either sensor rate. */
private class TiltFilter(private val env: GlassEnvironment) {
    private val matrix = FloatArray(9)
    private val angles = FloatArray(3)
    private var restPitch = Float.NaN
    private var restRoll = 0f
    private var pitch = 0f
    private var roll = 0f
    private var lastNanos = 0L

    /** The first sample after (re)registering only sets the clock. */
    fun resume() {
        lastNanos = 0L
    }

    fun onSample(values: FloatArray, nanos: Long) {
        SensorManager.getRotationMatrixFromVector(matrix, values)
        SensorManager.getOrientation(matrix, angles)
        if (restPitch.isNaN()) {
            restPitch = angles[1]
            restRoll = angles[2]
            pitch = angles[1]
            roll = angles[2]
        }
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, MAX_DT_S)
        lastNanos = nanos
        val follow = 1f - exp(-dt / FOLLOW_S)
        val drift = 1f - exp(-dt / REST_DRIFT_S)
        pitch += (angles[1] - pitch) * follow
        roll += wrap(angles[2] - roll) * follow
        restPitch += (pitch - restPitch) * drift
        restRoll += wrap(roll - restRoll) * drift
        val dx = (wrap(roll - restRoll) / MAX_TILT).coerceIn(-1f, 1f)
        val dy = ((pitch - restPitch) / MAX_TILT).coerceIn(-1f, 1f)

        // A fixed lamp above the phone: tilting right moves the light left.
        val lx = GlassEnvironment.DEFAULT_LIGHT.x - dx * LIGHT_SWING
        val ly = GlassEnvironment.DEFAULT_LIGHT.y + dy * LIGHT_SWING
        val len = sqrt(lx * lx + ly * ly).coerceAtLeast(0.001f)
        val light = Offset(lx / len, ly / len)
        val reach = (env.backdrop?.overscan ?: 0f) * PARALLAX_SHARE
        val parallax = Offset(dx * reach, -dy * reach)
        // Skip invisible changes: a phone lying still must not redraw home.
        if ((light - env.light).getDistance() < LIGHT_EPSILON &&
            (parallax - env.tiltParallax).getDistance() < PARALLAX_EPSILON_PX
        ) return
        env.light = light
        env.tiltParallax = parallax
    }
}

private fun wrap(a: Float): Float {
    var x = a
    while (x > PI) x -= (2 * PI).toFloat()
    while (x < -PI) x += (2 * PI).toFloat()
    return x
}

/** Time constants matching the old per-sample factors at 50 Hz (0.18 and 0.004). */
private const val FOLLOW_S = 0.1f
private const val REST_DRIFT_S = 5f
private const val MAX_DT_S = 0.25f
private const val MAX_TILT = 0.35f
private const val LIGHT_SWING = 0.9f
private const val LIGHT_EPSILON = 0.01f
private const val PARALLAX_EPSILON_PX = 0.75f
/** Tilt uses this share of the overscan; the rest is left for page parallax. */
private const val PARALLAX_SHARE = 0.55f
