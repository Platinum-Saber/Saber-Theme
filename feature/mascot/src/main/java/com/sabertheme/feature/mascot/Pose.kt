package com.sabertheme.feature.mascot

enum class Eyes { Open, Closed, Happy, Blank, Squeeze, Sleepy, Dizzy, LookAway }

enum class Mouth { Neutral, Smile, Shock, Wail, Pout, Open }

/**
 * Everything [drawSaber] needs for one frame, in rig units (the rig box is
 * 100 x 140, feet at the bottom centre). Angles are degrees; arm and leg
 * angles are outward from hanging straight down. Numeric fields blend with
 * [lerp]; variants (eyes, mouth, props) switch at the halfway point.
 */
data class Pose(
    val bodyTilt: Float = 0f,
    val headTilt: Float = 0f,
    /** -1 (looking left) .. 1 (looking right): shifts the face inside the head. */
    val headTurn: Float = 0f,
    val ahoge: Float = 0f,
    val armLeft: Float = REST_ARM,
    val armRight: Float = REST_ARM,
    val legLeft: Float = 0f,
    val legRight: Float = 0f,
    /** Vertical scale around the feet; < 1 squashes, > 1 stretches. */
    val squash: Float = 1f,
    /** Lift off the ground in rig units (hops). */
    val lift: Float = 0f,
    /** 1 faces the viewer / right, -1 mirrored (walking left). */
    val facing: Float = 1f,
    val blush: Float = 0f,
    val eyes: Eyes = Eyes.Open,
    val mouth: Mouth = Mouth.Neutral,
    val tears: Boolean = false,
    val zzz: Boolean = false,
    val dizzy: Boolean = false,
    val notes: Boolean = false,
    val sword: Boolean = false,
    val swordAngle: Float = -30f,
    val sitting: Boolean = false,
) {
    fun lerp(to: Pose, t: Float): Pose {
        fun f(a: Float, b: Float) = a + (b - a) * t
        val late = t >= 0.5f
        return Pose(
            bodyTilt = f(bodyTilt, to.bodyTilt),
            headTilt = f(headTilt, to.headTilt),
            headTurn = f(headTurn, to.headTurn),
            ahoge = f(ahoge, to.ahoge),
            armLeft = f(armLeft, to.armLeft),
            armRight = f(armRight, to.armRight),
            legLeft = f(legLeft, to.legLeft),
            legRight = f(legRight, to.legRight),
            squash = f(squash, to.squash),
            lift = f(lift, to.lift),
            facing = if (late) to.facing else facing,
            blush = f(blush, to.blush),
            eyes = if (late) to.eyes else eyes,
            mouth = if (late) to.mouth else mouth,
            tears = if (late) to.tears else tears,
            zzz = if (late) to.zzz else zzz,
            dizzy = if (late) to.dizzy else dizzy,
            notes = if (late) to.notes else notes,
            sword = if (late) to.sword else sword,
            swordAngle = f(swordAngle, to.swordAngle),
            sitting = if (late) to.sitting else sitting,
        )
    }

    companion object {
        const val REST_ARM = 18f

        /** Named expressions; the gallery shows each one. */
        val Neutral = Pose()
        val Blink = Pose(eyes = Eyes.Closed)
        val Surprised = Pose(eyes = Eyes.Blank, mouth = Mouth.Shock, armLeft = 55f, armRight = 55f, lift = 6f, ahoge = -12f)
        val Pout = Pose(eyes = Eyes.LookAway, mouth = Mouth.Pout, blush = 1f, headTurn = -0.7f, headTilt = -6f, armLeft = 8f, armRight = 8f)
        val Crying = Pose(eyes = Eyes.Squeeze, mouth = Mouth.Wail, tears = true, armLeft = 40f, armRight = 40f, headTilt = 4f, ahoge = 10f)
        val Happy = Pose(eyes = Eyes.Happy, mouth = Mouth.Smile, blush = 1f, armLeft = 35f, armRight = 35f, headTilt = 6f)
        val Sleepy = Pose(eyes = Eyes.Closed, mouth = Mouth.Open, zzz = true, headTilt = 10f, ahoge = 20f, armLeft = 6f, armRight = 6f, sitting = true)
        val Dizzy = Pose(eyes = Eyes.Dizzy, mouth = Mouth.Open, dizzy = true, headTilt = -8f, bodyTilt = 4f, ahoge = -25f)
        val Held = Pose(eyes = Eyes.Blank, mouth = Mouth.Open, armLeft = 70f, armRight = 70f, legLeft = 18f, legRight = -12f, squash = 1.06f)
        val SwordReady = Pose(sword = true, swordAngle = 25f, armRight = 55f, headTurn = 0.3f)
        val SwordSwing = Pose(sword = true, swordAngle = 95f, armRight = -20f, bodyTilt = 6f, mouth = Mouth.Open, headTurn = 0.4f)
        val Dancing = Pose(eyes = Eyes.Happy, mouth = Mouth.Smile, notes = true, armLeft = 50f, armRight = 30f, bodyTilt = -6f, headTilt = -8f)
        val Sitting = Pose(sitting = true, legLeft = 60f, legRight = 50f, armLeft = 10f, armRight = 10f)

        val gallery = listOf(
            "Neutral" to Neutral, "Blink" to Blink, "Surprised" to Surprised, "Pout" to Pout,
            "Crying" to Crying, "Happy" to Happy, "Sleepy" to Sleepy, "Dizzy" to Dizzy,
            "Held" to Held, "Sword ready" to SwordReady, "Sword swing" to SwordSwing, "Dancing" to Dancing,
            "Sitting" to Sitting,
        )
    }
}
