package com.sabertheme.feature.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Chibi Saber, drawn from vector parts in a 100 x 140 rig box (feet at
 * 50,138). Original drawing for this launcher, after the user's reference
 * style: big head, braided bun with a blue ribbon, ahoge, green eyes, blue
 * dress with silver armour. Back-to-front: legs, skirt, torso, arms (+ sword),
 * then the head group (ribbon, bun, back hair, face, side locks, bangs, ahoge).
 */

private val Line = Color(0xFF4A2A1C)
private val Hair = Color(0xFFF7DA82)
private val HairShade = Color(0xFFE6B95A)
private val Skin = Color(0xFFFFEDDC)
private val Blue = Color(0xFF2F4FA8)
private val BlueDark = Color(0xFF223A80)
private val BlueLight = Color(0xFF4A6BC9)
private val Silver = Color(0xFFD9DEE5)
private val SilverDark = Color(0xFF9AA2AE)
private val Gold = Color(0xFFE2B94B)
private val EyeTop = Color(0xFF16805C)
private val EyeBottom = Color(0xFF79E3B5)
private val Blush = Color(0xFFF59AA0)
private val MouthDark = Color(0xFF8A2A30)
private val Tongue = Color(0xFFF08A8F)
private val Tear = Color(0xFF9CD8FF)
private val Frill = Color(0xFFFFFBF2)
private val Effect = Color(0xFF8FA8FF)

private const val W = 1.3f

/** Draws Saber in [pose], scaled to this scope's height; [time] (s) drives looping effects. */
fun DrawScope.drawSaber(pose: Pose, time: Float) {
    val u = size.height / 140f
    scale(u, u, pivot = Offset.Zero) {
        val sit = if (pose.sitting) 12f else 0f
        withTransform({
            translate(0f, sit - pose.lift)
            scale(pose.facing, 1f, pivot = Offset(50f, 138f))
            rotate(pose.bodyTilt, pivot = Offset(50f, 138f))
            scale(1f / sqrt(pose.squash), pose.squash, pivot = Offset(50f, 138f))
        }) {
            legs(pose)
            skirt()
            torso()
            arm(left = true, angle = pose.armLeft, pose = pose)
            arm(left = false, angle = pose.armRight, pose = pose)
            withTransform({ rotate(pose.headTilt, pivot = Offset(50f, 82f)) }) {
                head(pose, time)
            }
            effects(pose, time)
        }
    }
}

private fun DrawScope.shape(path: Path, fill: Brush, outline: Boolean = true, width: Float = W) {
    drawPath(path, fill)
    if (outline) drawPath(path, Line, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.shape(path: Path, fill: Color, outline: Boolean = true, width: Float = W) =
    shape(path, androidx.compose.ui.graphics.SolidColor(fill), outline, width)

private fun DrawScope.line(path: Path, color: Color = Line, width: Float = W) =
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun path(block: Path.() -> Unit) = Path().apply(block)

// ---------------------------------------------------------------- body

private fun DrawScope.legs(pose: Pose) {
    for ((x, angle) in listOf(44f to pose.legLeft, 56f to -pose.legRight)) {
        rotate(angle, pivot = Offset(x, 120f)) {
            val boot = path {
                moveTo(x - 4f, 120f); lineTo(x + 4f, 120f); lineTo(x + 4.2f, 133f)
                quadraticTo(x + 4.5f, 137.5f, x, 137.5f); quadraticTo(x - 4.5f, 137.5f, x - 4.2f, 133f); close()
            }
            shape(boot, Brush.horizontalGradient(listOf(Silver, SilverDark), x - 4f, x + 4f))
            line(path { moveTo(x - 4f, 128f); lineTo(x + 4f, 128f) }, SilverDark, 0.9f)
            line(path { moveTo(x - 3.8f, 135.5f); lineTo(x + 3.8f, 135.5f) }, Line, 1.6f)
        }
    }
}

private fun DrawScope.skirt() {
    // Frill peeking under the hem.
    val frill = path {
        moveTo(26f, 126f)
        for (i in 0 until 8) {
            val x0 = 26f + i * 6f
            quadraticTo(x0 + 3f, 134f, x0 + 6f, 126.5f)
        }
        lineTo(74f, 124f); lineTo(26f, 124f); close()
    }
    shape(frill, Frill, width = 0.9f)
    val body = path {
        moveTo(37f, 100f); lineTo(63f, 100f)
        quadraticTo(71f, 116f, 75f, 127f); quadraticTo(50f, 132f, 25f, 127f)
        quadraticTo(29f, 116f, 37f, 100f); close()
    }
    shape(body, Brush.verticalGradient(listOf(Blue, BlueDark), 100f, 130f))
    val panel = path { moveTo(45.5f, 102f); lineTo(54.5f, 102f); lineTo(58f, 129.5f); quadraticTo(50f, 130.5f, 42f, 129.5f); close() }
    shape(panel, BlueLight, outline = false)
    line(path { moveTo(45.5f, 102f); lineTo(42f, 129.5f) }, Gold, 0.9f)
    line(path { moveTo(54.5f, 102f); lineTo(58f, 129.5f) }, Gold, 0.9f)
    line(path { moveTo(25.5f, 126.6f); quadraticTo(50f, 131.4f, 74.5f, 126.6f) }, Gold, 1f)
    // Armour plates on the hips.
    for (mirror in listOf(false, true)) {
        val m = { x: Float -> if (mirror) 100f - x else x }
        val plate = path { moveTo(m(35f), 101f); lineTo(m(43f), 102.5f); lineTo(m(39f), 119f); lineTo(m(28.5f), 116.5f); close() }
        shape(plate, Brush.verticalGradient(listOf(Silver, SilverDark), 101f, 119f), width = 1f)
        line(path { moveTo(m(33f), 107.5f); lineTo(m(41.5f), 108.5f) }, SilverDark, 0.8f)
        line(path { moveTo(m(31f), 112.5f); lineTo(m(40.5f), 114f) }, SilverDark, 0.8f)
    }
}

private fun DrawScope.torso() {
    shape(path { moveTo(46f, 76f); lineTo(54f, 76f); lineTo(54f, 85f); lineTo(46f, 85f); close() }, Skin, outline = false)
    val bodice = path { moveTo(38f, 85f); quadraticTo(50f, 80f, 62f, 85f); lineTo(63.5f, 102f); lineTo(36.5f, 102f); close() }
    shape(bodice, Brush.verticalGradient(listOf(BlueLight, Blue), 82f, 102f))
    val plate = path { moveTo(41f, 86.5f); quadraticTo(50f, 83f, 59f, 86.5f); lineTo(58.2f, 96.5f); quadraticTo(50f, 100.5f, 41.8f, 96.5f); close() }
    shape(plate, Brush.verticalGradient(listOf(Color.White, Silver, SilverDark), 84f, 99f), width = 1f)
    // Blue crest on the breastplate.
    line(path { moveTo(50f, 87f); lineTo(50f, 95f) }, BlueLight, 1f)
    line(path { moveTo(46f, 92f); quadraticTo(50f, 89.5f, 54f, 92f) }, BlueLight, 0.9f)
    line(path { moveTo(44.5f, 87.2f); quadraticTo(50f, 85f, 55.5f, 87.2f) }, Gold, 0.9f)
    // Belt with lacing.
    shape(path { moveTo(37f, 98.5f); lineTo(63f, 98.5f); lineTo(63.3f, 102.5f); lineTo(36.7f, 102.5f); close() }, Color(0xFF2B2B33), width = 1f)
    line(path { moveTo(47.5f, 99f); lineTo(52.5f, 102f); moveTo(52.5f, 99f); lineTo(47.5f, 102f) }, Silver, 0.7f)
}

private fun DrawScope.arm(left: Boolean, angle: Float, pose: Pose) {
    val sx = if (left) 38.5f else 61.5f
    val sy = 87.5f
    rotate(if (left) angle else -angle, pivot = Offset(sx, sy)) {
        val sleeve = path {
            moveTo(sx - 3.8f, sy - 1f); lineTo(sx + 3.8f, sy - 1f); lineTo(sx + 3.6f, sy + 9f); lineTo(sx - 3.6f, sy + 9f); close()
        }
        shape(sleeve, Blue, width = 1f)
        val gauntlet = path {
            moveTo(sx - 3.9f, sy + 8.5f); lineTo(sx + 3.9f, sy + 8.5f); lineTo(sx + 3.6f, sy + 17f)
            quadraticTo(sx + 3.6f, sy + 20.5f, sx, sy + 20.5f); quadraticTo(sx - 3.6f, sy + 20.5f, sx - 3.6f, sy + 17f); close()
        }
        shape(gauntlet, Brush.horizontalGradient(listOf(Color.White, Silver, SilverDark), sx - 4f, sx + 4f), width = 1f)
        line(path { moveTo(sx - 3.7f, sy + 12f); lineTo(sx + 3.7f, sy + 12f) }, SilverDark, 0.8f)
        line(path { moveTo(sx - 3.6f, sy + 15.2f); lineTo(sx + 3.6f, sy + 15.2f) }, SilverDark, 0.8f)
        if (!left && pose.sword) sword(Offset(sx, sy + 19f), pose.swordAngle + angle)
        // Puffed shoulder.
        drawCircle(Blue, 4.6f, Offset(sx, sy))
        drawCircle(Line, 4.6f, Offset(sx, sy), style = Stroke(1f))
        line(path { moveTo(sx - 3f, sy - 1.6f); quadraticTo(sx, sy - 3.4f, sx + 3f, sy - 1.6f) }, Gold, 0.8f)
    }
}

private fun DrawScope.sword(hand: Offset, angle: Float) {
    rotate(angle, pivot = hand) {
        val (x, y) = hand
        val blade = path { moveTo(x - 2.4f, y - 4f); lineTo(x + 2.4f, y - 4f); lineTo(x + 2.2f, y - 34f); lineTo(x, y - 39f); lineTo(x - 2.2f, y - 34f); close() }
        shape(blade, Brush.horizontalGradient(listOf(Color.White, Color(0xFFDCEBFF)), x - 2.4f, x + 2.4f), width = 1f)
        line(path { moveTo(x, y - 6f); lineTo(x, y - 33f) }, Color(0xFFB9D3F5), 0.7f)
        shape(path { moveTo(x - 6f, y - 5.5f); lineTo(x + 6f, y - 5.5f); lineTo(x + 5f, y - 3f); lineTo(x - 5f, y - 3f); close() }, Gold, width = 0.9f)
        shape(path { moveTo(x - 1.5f, y - 3f); lineTo(x + 1.5f, y - 3f); lineTo(x + 1.5f, y + 4f); lineTo(x - 1.5f, y + 4f); close() }, BlueDark, width = 0.8f)
        drawCircle(Gold, 1.6f, Offset(x, y + 5f))
    }
}

// ---------------------------------------------------------------- head

private fun DrawScope.head(pose: Pose, time: Float) {
    val turn = pose.headTurn
    // Ribbon and braided bun behind the head (upper right).
    for ((dx, dy, rot) in listOf(Triple(7f, -7f, -30f), Triple(8f, 8f, 25f))) {
        rotate(rot, pivot = Offset(83f, 35f)) {
            val loop = path {
                moveTo(83f, 35f); quadraticTo(83f + dx * 1.5f, 35f + dy - 8f, 83f + dx * 2.2f, 35f + dy)
                quadraticTo(83f + dx * 1.3f, 35f + dy + 6f, 83f, 35f); close()
            }
            shape(loop, Brush.linearGradient(listOf(BlueLight, Blue), Offset(83f, 30f), Offset(100f, 45f)), width = 1f)
        }
    }
    drawCircle(Blue, 2.6f, Offset(83f, 35f))
    shape(path { moveTo(81f, 37f); lineTo(88f, 58f); lineTo(84.5f, 57f); lineTo(80f, 40f); close() }, Blue, width = 1f)
    shape(path { moveTo(80f, 38f); lineTo(81.5f, 61f); lineTo(78f, 59.5f); lineTo(78.5f, 40f); close() }, Blue, width = 1f)
    drawCircle(Hair, 12.5f, Offset(73f, 25f))
    drawCircle(Line, 12.5f, Offset(73f, 25f), style = Stroke(W))
    for (i in 0 until 4) {
        val r = 4f + i * 2.5f
        line(path { moveTo(73f - r, 25f + r * 0.2f); quadraticTo(73f, 25f - r * 0.9f, 73f + r, 25f + r * 0.2f) }, HairShade, 0.9f)
    }

    val backHair = path {
        moveTo(16f, 54f); quadraticTo(16f, 15f, 50f, 15f); quadraticTo(84f, 15f, 84f, 54f)
        lineTo(83f, 73f); quadraticTo(77f, 78f, 71f, 72f); lineTo(29f, 72f); quadraticTo(23f, 78f, 17f, 73f); close()
    }
    shape(backHair, Brush.verticalGradient(listOf(Hair, HairShade), 15f, 78f))

    val face = path {
        moveTo(26f, 50f); quadraticTo(25f, 79f, 50f, 81f); quadraticTo(75f, 79f, 74f, 50f)
        quadraticTo(74f, 30f, 50f, 30f); quadraticTo(26f, 30f, 26f, 50f); close()
    }
    shape(face, Skin)
    face(pose, turn)
    if (pose.tears) tears(turn, time)

    // Side locks framing the face.
    for (mirror in listOf(false, true)) {
        val m = { x: Float -> if (mirror) 100f - x else x }
        val lock = path {
            moveTo(m(27f), 42f); quadraticTo(m(19f), 62f, m(22f), 81f); quadraticTo(m(26f), 72f, m(31.5f), 64f)
            quadraticTo(m(29.5f), 54f, m(33f), 44f); close()
        }
        shape(lock, Brush.verticalGradient(listOf(Hair, HairShade), 42f, 81f))
    }
    val shift = turn * 2f
    val bangs = path {
        // Strands hang to just above the eyes; short valleys keep the forehead covered.
        moveTo(22f, 58f); quadraticTo(19f, 19f, 50f, 18f); quadraticTo(81f, 19f, 78f, 58f)
        quadraticTo(76f, 50f, 73f + shift, 45f); lineTo(70.5f + shift, 59f)
        quadraticTo(68f, 51f, 66f + shift, 44f); lineTo(62f + shift, 51.5f)
        quadraticTo(60f, 47f, 58f + shift, 43f); lineTo(54.5f + shift, 52f)
        quadraticTo(52f, 46.5f, 50f + shift, 42f); lineTo(46f + shift, 52f)
        quadraticTo(44f, 47f, 42f + shift, 43f); lineTo(38f + shift, 51.5f)
        quadraticTo(36f, 48f, 34f + shift, 44f); lineTo(29.5f + shift, 59f)
        quadraticTo(27f, 50f, 27f + shift, 45f); quadraticTo(24f, 50f, 22f, 58f); close()
    }
    shape(bangs, Brush.verticalGradient(listOf(Hair, Hair, HairShade), 18f, 58f))
    line(path { moveTo(36f, 22f); quadraticTo(43f, 19.5f, 50f, 20f) }, Color.White.copy(alpha = 0.7f), 1.4f)

    rotate(pose.ahoge, pivot = Offset(50f, 18f)) {
        val ahoge = path {
            moveTo(48.5f, 18.5f); quadraticTo(41f, 3f, 54f, 1f); quadraticTo(63f, 1f, 59.5f, 10f)
            quadraticTo(58.5f, 4f, 53.5f, 5f); quadraticTo(47f, 7f, 52f, 18.5f); close()
        }
        shape(ahoge, Hair, width = 1.1f)
    }
}

private fun DrawScope.face(pose: Pose, turn: Float) {
    val dx = turn * 4f
    val eyeY = 60f
    eye(39.5f + dx, eyeY, pose.eyes, leftEye = true)
    eye(60.5f + dx, eyeY, pose.eyes, leftEye = false)
    if (pose.blush > 0f) {
        for (cx in listOf(35f + dx * 0.8f, 65f + dx * 0.8f)) {
            drawOval(Blush.copy(alpha = 0.55f * pose.blush), Offset(cx - 4.5f, 66f), Size(9f, 4.5f))
            for (i in -1..1) line(path { moveTo(cx + i * 2f - 0.6f, 69f); lineTo(cx + i * 2f + 0.6f, 66.5f) }, Blush.copy(alpha = 0.8f * pose.blush), 0.6f)
        }
    }
    mouth(50f + dx, 71f, pose.mouth)
}

private fun DrawScope.eye(cx: Float, cy: Float, eyes: Eyes, leftEye: Boolean) {
    when (eyes) {
        Eyes.Open, Eyes.LookAway -> {
            val look = if (eyes == Eyes.LookAway) -1.4f else 0f
            drawOval(Brush.verticalGradient(listOf(EyeTop, EyeBottom), cy - 6f, cy + 6f), Offset(cx - 4.2f + look, cy - 6f), Size(8.4f, 12f))
            drawOval(Line, Offset(cx - 4.2f + look, cy - 6f), Size(8.4f, 12f), style = Stroke(0.9f))
            drawCircle(Color.White, 1.7f, Offset(cx + 1.5f + look, cy - 2.6f))
            drawCircle(Color.White.copy(alpha = 0.9f), 0.8f, Offset(cx - 1.6f + look, cy + 2.8f))
            if (eyes == Eyes.LookAway) {
                // Half-lidded: annoyed.
                drawRect(Skin, Offset(cx - 5.2f, cy - 7.2f), Size(10.4f, 5.6f))
                line(path { moveTo(cx - 5f, cy - 1.8f); lineTo(cx + 5f, cy - 1.4f) }, Line, 1.6f)
            } else {
                line(path { moveTo(cx - 5.2f, cy - 4.6f); quadraticTo(cx, cy - 8.6f, cx + 5.2f, cy - 4.6f) }, Line, 1.7f)
            }
        }
        Eyes.Closed -> line(path { moveTo(cx - 5f, cy + 0.5f); quadraticTo(cx, cy + 4f, cx + 5f, cy + 0.5f) }, Line, 1.5f)
        Eyes.Happy -> line(path { moveTo(cx - 5f, cy + 2f); quadraticTo(cx, cy - 4.5f, cx + 5f, cy + 2f) }, Line, 1.6f)
        Eyes.Sleepy -> line(path { moveTo(cx - 5f, cy + 1f); lineTo(cx + 5f, cy + 1f) }, Line, 1.5f)
        Eyes.Blank -> {
            drawCircle(Color.White, 4.6f, Offset(cx, cy))
            drawCircle(Line, 4.6f, Offset(cx, cy), style = Stroke(1.5f))
        }
        Eyes.Squeeze -> {
            val s = if (leftEye) 1f else -1f
            line(path { moveTo(cx - 4f * s, cy - 3.5f); lineTo(cx + 3f * s, cy); lineTo(cx - 4f * s, cy + 3.5f) }, Line, 1.7f)
        }
        Eyes.Dizzy -> {
            drawCircle(Line, 4.2f, Offset(cx, cy), style = Stroke(1.1f))
            drawCircle(Line, 2.2f, Offset(cx + 0.6f, cy - 0.4f), style = Stroke(1.1f))
            drawCircle(Line, 0.6f, Offset(cx + 0.9f, cy - 0.6f))
        }
    }
}

private fun DrawScope.mouth(cx: Float, cy: Float, mouth: Mouth) {
    when (mouth) {
        Mouth.Neutral -> line(path { moveTo(cx - 1.8f, cy); lineTo(cx + 1.8f, cy) }, MouthDark, 1.1f)
        Mouth.Smile -> line(path { moveTo(cx - 3f, cy - 0.6f); quadraticTo(cx, cy + 2.6f, cx + 3f, cy - 0.6f) }, MouthDark, 1.1f)
        Mouth.Shock -> shape(path { moveTo(cx, cy - 2.6f); lineTo(cx + 3.1f, cy + 2.2f); lineTo(cx - 3.1f, cy + 2.2f); close() }, Tongue, width = 1f)
        Mouth.Wail -> {
            val m = path { moveTo(cx - 5.5f, cy - 2f); quadraticTo(cx, cy - 4f, cx + 5.5f, cy - 2f); quadraticTo(cx + 5f, cy + 7f, cx, cy + 7f); quadraticTo(cx - 5f, cy + 7f, cx - 5.5f, cy - 2f); close() }
            shape(m, MouthDark, width = 1f)
            drawOval(Tongue, Offset(cx - 3.2f, cy + 2.4f), Size(6.4f, 4f))
        }
        Mouth.Pout -> line(path { moveTo(cx - 2.6f, cy); quadraticTo(cx - 1.3f, cy - 1.4f, cx, cy); quadraticTo(cx + 1.3f, cy + 1.4f, cx + 2.6f, cy) }, MouthDark, 1.1f)
        Mouth.Open -> {
            drawOval(MouthDark, Offset(cx - 1.6f, cy - 1.6f), Size(3.2f, 3.6f))
            drawOval(Line, Offset(cx - 1.6f, cy - 1.6f), Size(3.2f, 3.6f), style = Stroke(0.7f))
        }
    }
}

private fun DrawScope.tears(turn: Float, time: Float) {
    val dx = turn * 4f
    for (cx in listOf(39.5f + dx, 60.5f + dx)) {
        val wobble = sin(time * 9f + cx) * 0.6f
        val stream = path {
            moveTo(cx - 2f, 63f); lineTo(cx + 2f, 63f); lineTo(cx + 2.2f + wobble, 80f); quadraticTo(cx + wobble, 83f, cx - 2.2f + wobble, 80f); close()
        }
        drawPath(stream, Tear.copy(alpha = 0.85f))
        val drop = (time * 1.6f + cx * 0.01f) % 1f
        drawCircle(Tear, 1.3f, Offset(cx + wobble + (if (cx < 50f) -4f else 4f) * drop, 82f + drop * 14f))
    }
}

// ---------------------------------------------------------------- effects

private fun DrawScope.effects(pose: Pose, time: Float) {
    if (pose.zzz) {
        for (i in 0 until 3) {
            val phase = (time * 0.45f + i / 3f) % 1f
            val x = 72f + i * 5f + phase * 6f
            val y = 22f - phase * 22f
            val s = 3f + i * 1.2f
            val z = path { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s) }
            line(z, Effect.copy(alpha = 1f - phase), 1.3f)
        }
    }
    if (pose.dizzy) {
        for (i in 0 until 3) {
            val a = time * 3.2f + i * (2f * PI.toFloat() / 3f)
            star(Offset(50f + cos(a) * 20f, 13f + sin(a) * 4.5f), 2.6f, Color(0xFFFFD45C))
        }
    }
    if (pose.notes) {
        for (i in 0 until 2) {
            val phase = (time * 0.5f + i * 0.5f) % 1f
            val x = if (i == 0) 84f - phase * 4f else 14f + phase * 4f
            val y = 52f - phase * 30f
            val c = Effect.copy(alpha = 1f - phase)
            drawOval(c, Offset(x - 2.4f, y - 1.6f), Size(4.2f, 3.2f))
            line(path { moveTo(x + 1.6f, y); lineTo(x + 1.6f, y - 8f); quadraticTo(x + 4.5f, y - 7f, x + 4.5f, y - 4f) }, c, 1.1f)
        }
    }
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = path {
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f - PI.toFloat() / 2f
            val rr = if (k % 2 == 0) r else r * 0.4f
            val pt = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
            if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
        close()
    }
    drawPath(p, color)
}
