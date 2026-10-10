package com.sabertheme.feature.mascot

enum class Outfit { Armor, Winter, Casual }

enum class Eyes { Open, Closed, Happy, Blank, Squeeze, Sleepy, Dizzy, LookAway, LookDown, Hidden }

enum class Brows { None, Calm, Angry, Worried }

enum class Mouth { Neutral, Smile, Shock, Wail, Pout, Open, Puff }

/** Things held in the hands. */
enum class Prop { None, Sword, Gift, HeartHands, ChinHand }

/**
 * Everything [drawSaber] needs for one frame, in rig units (the rig box is
 * 100 x 140, feet at the bottom centre). Angles are degrees; arm and leg
 * angles are outward from hanging straight down (negative swings inward, in
 * front of the body). Numeric fields blend with [lerp]; variants (eyes,
 * mouth, props, effects) switch at the halfway point.
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
    val brows: Brows = Brows.None,
    val mouth: Mouth = Mouth.Neutral,
    val prop: Prop = Prop.None,
    val swordAngle: Float = -30f,
    val sitting: Boolean = false,
    // Effects.
    val tears: Boolean = false,
    val zzz: Boolean = false,
    val dizzy: Boolean = false,
    val notes: Boolean = false,
    val hearts: Boolean = false,
    val sparkles: Boolean = false,
    val sweat: Boolean = false,
    val sigh: Boolean = false,
    val fidget: Boolean = false,
) {
    fun lerp(to: Pose, t: Float): Pose {
        fun f(a: Float, b: Float) = a + (b - a) * t
        val p = if (t >= 0.5f) to else this
        return p.copy(
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
            blush = f(blush, to.blush),
            swordAngle = f(swordAngle, to.swordAngle),
        )
    }

    companion object {
        const val REST_ARM = 18f

        /** Named expressions; the gallery shows each one. */
        val Neutral = Pose()
        val Blink = Pose(eyes = Eyes.Closed)
        val Surprised = Pose(eyes = Eyes.Blank, mouth = Mouth.Shock, armLeft = 55f, armRight = 55f, lift = 6f, ahoge = -12f)
        val Pout = Pose(eyes = Eyes.LookAway, mouth = Mouth.Pout, blush = 1f, headTurn = -0.7f, headTilt = -6f, armLeft = 8f, armRight = 8f)
        val Puffed = Pose(eyes = Eyes.LookAway, brows = Brows.Angry, mouth = Mouth.Puff, blush = 1f, headTurn = -0.4f, headTilt = -7f, armLeft = 6f, armRight = 6f)
        val Crying = Pose(eyes = Eyes.Squeeze, mouth = Mouth.Wail, tears = true, armLeft = 40f, armRight = 40f, headTilt = 4f, ahoge = 10f)
        val Happy = Pose(eyes = Eyes.Happy, mouth = Mouth.Smile, blush = 1f, armLeft = 35f, armRight = 35f, headTilt = 6f)
        val ShyGift = Pose(eyes = Eyes.LookDown, mouth = Mouth.Smile, blush = 1f, prop = Prop.Gift, armLeft = -24f, armRight = -24f, headTilt = 7f, headTurn = -0.3f, hearts = true, sparkles = true)
        val HeartHands = Pose(eyes = Eyes.Open, brows = Brows.Calm, mouth = Mouth.Smile, prop = Prop.HeartHands, armLeft = -34f, armRight = -34f, headTilt = 5f, hearts = true)
        val Thinking = Pose(eyes = Eyes.LookAway, brows = Brows.Worried, mouth = Mouth.Neutral, blush = 0.6f, prop = Prop.ChinHand, armLeft = 8f, armRight = -130f, headTurn = -0.5f, headTilt = -5f, sweat = true)
        val Embarrassed = Pose(eyes = Eyes.Hidden, mouth = Mouth.Pout, blush = 1f, headTurn = 1f, headTilt = 10f, armLeft = 6f, armRight = 6f, sweat = true, fidget = true)
        val Huff = Pose(eyes = Eyes.Closed, brows = Brows.Angry, mouth = Mouth.Pout, blush = 0.7f, headTilt = -4f, armLeft = 8f, armRight = 8f, sigh = true)
        val Curious = Pose(eyes = Eyes.Open, brows = Brows.Calm, mouth = Mouth.Open, headTilt = 13f, headTurn = 0.3f, ahoge = 14f)
        val Sleepy = Pose(eyes = Eyes.Closed, mouth = Mouth.Open, zzz = true, headTilt = 10f, ahoge = 20f, armLeft = 6f, armRight = 6f, sitting = true)
        val Dizzy = Pose(eyes = Eyes.Dizzy, mouth = Mouth.Open, dizzy = true, headTilt = -8f, bodyTilt = 4f, ahoge = -25f)
        val Held = Pose(eyes = Eyes.Blank, mouth = Mouth.Open, armLeft = 70f, armRight = 70f, legLeft = 18f, legRight = -12f, squash = 1.06f)
        val SwordReady = Pose(prop = Prop.Sword, swordAngle = 25f, armRight = 55f, headTurn = 0.3f, brows = Brows.Calm)
        val SwordSwing = Pose(prop = Prop.Sword, swordAngle = 95f, armRight = -20f, bodyTilt = 6f, mouth = Mouth.Open, headTurn = 0.4f, brows = Brows.Angry)
        val Dancing = Pose(eyes = Eyes.Happy, mouth = Mouth.Smile, notes = true, armLeft = 50f, armRight = 30f, bodyTilt = -6f, headTilt = -8f)
        val Sitting = Pose(sitting = true, legLeft = 60f, legRight = 50f, armLeft = 10f, armRight = 10f)

        val gallery = listOf(
            "Neutral" to Neutral, "Blink" to Blink, "Surprised" to Surprised, "Pout" to Pout,
            "Puffed" to Puffed, "Crying" to Crying, "Happy" to Happy, "Shy gift" to ShyGift,
            "Heart hands" to HeartHands, "Thinking" to Thinking, "Embarrassed" to Embarrassed, "Huff" to Huff,
            "Curious" to Curious, "Sleepy" to Sleepy, "Dizzy" to Dizzy, "Held" to Held,
            "Sword ready" to SwordReady, "Sword swing" to SwordSwing, "Dancing" to Dancing, "Sitting" to Sitting,
        )
    }
}
