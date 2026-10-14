// Mochi's engine — a port of the PC's windows/src/mochi/engine.ts (itself a
// port of the Mac's BotEngine.swift): the same constants, tweens, easings,
// eyes, hands, particles, slaps and dizzy spell. What the phone does not need
// (outfits, the mailbox morph, badges, dancing) is left out.
//
// Plain Kotlin: no Compose state is touched per frame. The Mochi composable
// calls update() on a frame clock only while `busy`, and draw() in its Canvas;
// widgets call draw() once into a bitmap.

package mx.loperstack.mochi.mochi

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.channels.Channel
import mx.loperstack.mochi.wellness.Breathing
import mx.loperstack.mochi.wellness.breathFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private typealias EaseFn = (Float) -> Float

private object Ease {
    val out: EaseFn = { t -> 1f - (1f - t).pow(3) }
    val inOut: EaseFn = { t -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f }
    val back: EaseFn = { t ->
        val c1 = 1.7f
        val c3 = c1 + 1f
        1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }
    val lin: EaseFn = { it }
}

// ── Constants (MochiConst) ────────────────────────────────────────────────────

private const val EYE_W = 0.25f
private const val EYE_H = 0.27f
private const val EYE_SP = 0.37f
private const val EYE_P = -0.12f
private val BASE_TOP = Color(0xFFEDEDEF)
private val BASE_BOTTOM = Color(0xFFC4C5CA)
private val INK = Color(0xFF1A1412)
private val DIZZY = Color(0.957f, 0.447f, 0.714f)
private val SLEEPING = Color(0.58f, 0.635f, 0.722f)
private val HEART = Color(0xFFFF4D6D)
private val STAR = Color(0xFFF7B32B)
private val TWO_PI = (PI * 2).toFloat()
private const val INF = Double.POSITIVE_INFINITY

private fun deg(rad: Float) = rad * 180f / PI.toFloat()

/**
 * How Mochi is drawn everywhere (app, watch, widgets, tiles): flat 2D — one
 * solid colour and a fine outline, like the PC's mini bots — or the PC's
 * main Mochi with its gradient, shading and highlight. State, so a change
 * repaints every Mochi on screen.
 */
object MochiStyle {
    var flat by androidx.compose.runtime.mutableStateOf(true)
}

class MochiEngine {
    enum class Eye { PILL, WIDE, DOT, LINE, FLAT, HAPPY, CLOSED, SPIRAL, HEART, STAR, TIRED, WINK }
    enum class Emote { LOVE, SURPRISED, PROUD, WINK, YAWN, HAPPY, ANNOYED }
    private enum class P { YAW, PITCH, ROLL, TILT, OPEN, SX, SY, OY, OX, BLUSH, ES, HANDS }
    private enum class Kind { HEART, STAR, SPARK, SWEAT, Z, NOTE }

    private class Key(val target: Float, val ms: Float, val ease: EaseFn)
    private class Tween(val prop: P, val keys: List<Key>, var index: Int, var from: Float, var start: Double, val done: (() -> Unit)?)
    private class Particle(
        val kind: Kind, val x: Float, val y: Float, val vx: Float, val vy: Float,
        var age: Float, val life: Float, val rot: Float, val size: Float,
    )

    private val v = FloatArray(P.entries.size).also {
        it[P.OPEN.ordinal] = 1f; it[P.SX.ordinal] = 1f; it[P.SY.ordinal] = 1f; it[P.ES.ordinal] = 1f
    }
    private operator fun get(p: P) = v[p.ordinal]
    private operator fun set(p: P, x: Float) { v[p.ordinal] = x }

    private val tweens = LinkedHashMap<P, Tween>()
    private val locks = HashSet<P>()
    private val particles = ArrayList<Particle>()
    private val timers = ArrayList<Pair<Double, () -> Unit>>()

    private var tgYaw = 0f
    private var tgPitch = 0f
    private var tgTilt = 0f
    private var tgSx = 1f
    private var tgSy = 1f
    var tgEs = 1f

    private var col = Color(0xFFE6E9EE)
    private var colT = col
    private var tint = 0f
    private var tintT = 0f

    private var eyeOverride: Eye? = null
    private var eyeOverrideUntil = 0.0
    private var permanentEye: Eye? = null

    private val t0 = now() - Random.nextDouble() * 5
    private var nextBlink = now() + 1.5 + Random.nextDouble() * 2
    private var nextWander = now() + 2.0
    private var lastAmbient = 0.0
    private var waveStart = 0.0
    private var waveUntil = 0.0
    private var greetToken = 0
    private val slapTimes = ArrayList<Double>()

    // Where Mochi looks: a finger on him, or a gentle wander. -1…1, y up.
    private var lookX = 0f
    private var lookY = 0f
    private var touchLook: Offset? = null

    // What the app asks for.
    private var configured = false
    private var userColor = Color(0xFFF5F6F8)
    var mood = Mood.IDLE
        private set
    private var session: Breathing? = null
    private var isBreathing = false
    private var breathFill = 0f
    private var breathLevel = 0f
    private var face: Eye? = null

    /** Music is playing: he dances (the island's 112-BPM bounce). */
    private var dancing = false
    private var danceLevel = 0f
    private var lastNote = 0.0

    /** True for the 3.3 s of a dizzy spell (the app shows the "too many hits" bubble). */
    val dizzy = mutableStateOf(false)

    /** Lets the frame loop sleep while Mochi is still, and wake on any input. */
    val wakeups = Channel<Unit>(Channel.CONFLATED)
    private fun wake() { wakeups.trySend(Unit) }

    private fun now() = System.nanoTime() / 1e9

    private fun after(ms: Int, f: () -> Unit) { timers += (now() + ms / 1000.0) to f }

    // ── What the app sets ─────────────────────────────────────────────────────

    /** The body colour and mood, and the breathing session (if any). Cheap to call on every composition. */
    fun configure(color: Color, mood: Mood, breathing: Breathing?, dancing: Boolean = false, face: Eye? = null) {
        val s = breathing?.takeIf { it.startedAt != null }
        session = s
        if (dancing != this.dancing) { this.dancing = dancing; wake() }
        val changed = !configured || color != userColor || mood != this.mood || face != this.face
        this.face = face
        userColor = color
        this.mood = mood
        val on = s != null || mood == Mood.BREATHING
        if (on != isBreathing) {
            isBreathing = on
            applyBase()
            wake()
        } else if (changed) {
            applyBase()
            wake()
        }
        if (!configured) {
            col = colT
            tint = tintT
            configured = true
        }
    }

    /** White is the PC's own Mochi; any other colour tints him as the island's agent states do. */
    private fun tintFor(c: Color) = if (c.luminance() > 0.8f) 0f else 0.72f

    private fun applyBase() {
        if (dizzy.value) return
        val sleeping = mood == Mood.SLEEPY
        colT = if (sleeping) SLEEPING else userColor
        tintT = if (sleeping) 0.32f else tintFor(userColor)
        permanentEye = when {
            isBreathing || sleeping -> Eye.CLOSED
            face != null -> face
            mood == Mood.HAPPY -> Eye.HAPPY
            else -> null
        }
        eyeOverride = permanentEye
        eyeOverrideUntil = if (permanentEye != null) INF else 0.0
    }

    /** A finger on Mochi (-1…1, y up), or null when it lifts. */
    fun lookAt(target: Offset?) {
        touchLook = target
        wake()
    }

    // ── Moves (BotEngine) ─────────────────────────────────────────────────────

    private fun anim(p: P, keys: List<Key>, done: (() -> Unit)? = null) {
        tweens[p] = Tween(p, keys, 0, this[p], now(), done)
        locks += p
    }

    private fun k(target: Float, ms: Int, ease: EaseFn) = Key(target, ms.toFloat(), ease)

    fun blink() {
        if (P.OPEN in locks) return
        anim(P.OPEN, listOf(k(0.06f, 70, Ease.inOut), k(1f, 130, Ease.out)))
    }

    fun squash() {
        anim(P.SY, listOf(k(0.78f, 70, Ease.out), k(1.1f, 130, Ease.out), k(1f, 170, Ease.inOut)))
        anim(P.SX, listOf(k(1.16f, 70, Ease.out), k(0.95f, 130, Ease.out), k(1f, 170, Ease.inOut)))
    }

    private fun doRoll(ms: Int, turns: Int) {
        this[P.ROLL] = 0f
        anim(P.ROLL, listOf(k(TWO_PI * turns, ms, Ease.inOut))) { this[P.ROLL] = 0f }
    }

    /** A tap: squash and a glare. Three inside 1.7 s and he gets dizzy. */
    fun slap() {
        interruptGreet()
        if (dizzy.value) return
        val t = now()
        slapTimes.removeAll { t - it >= 1.7 }
        slapTimes += t
        Sounds.play("slap")
        squash()
        if (slapTimes.size >= 3) {
            slapTimes.clear()
            goDizzy()
        } else {
            eyeOverride = Eye.LINE
            eyeOverrideUntil = t + 0.8
            after(60) { Sounds.play("annoyed") }
        }
        wake()
    }

    /** handleDizzy on the PC: pink, spiral eyes, two rolls, 3.3 s, then a happy blink. */
    private fun goDizzy() {
        dizzy.value = true
        colT = DIZZY
        tintT = 0.7f
        eyeOverride = null
        eyeOverrideUntil = 0.0
        doRoll(1300, 2)
        Sounds.play("dizzy")
        after(3300) {
            dizzy.value = false
            applyBase()
            triggerEmote(Emote.HAPPY)
        }
    }

    /** The "coucou" wave (BotEngine.greet). */
    fun greet() {
        val t = now()
        val tok = ++greetToken
        waveStart = t + 0.45
        waveUntil = t + 1.55
        eyeOverride = Eye.HAPPY
        eyeOverrideUntil = t + 2.0
        anim(P.OY, listOf(k(-0.06f, 220, Ease.out), k(0f, 220, Ease.back)))
        after(250) {
            if (greetToken != tok) return@after
            anim(P.HANDS, listOf(k(1f, 280, Ease.out)))
            anim(P.SY, listOf(k(0.95f, 100, Ease.out), k(1f, 260, Ease.back)))
            anim(P.SX, listOf(k(1.04f, 100, Ease.out), k(1f, 260, Ease.back)))
            Sounds.play("greet")
        }
        after(550) { if (greetToken == tok) blink() }
        after(1500) { if (greetToken == tok) blink() }
        after(1550) {
            if (greetToken != tok) return@after
            waveUntil = 0.0
            anim(P.HANDS, listOf(k(0f, 200, Ease.inOut)))
        }
        after(1750) {
            if (greetToken != tok) return@after
            eyeOverride = Eye.HAPPY
            eyeOverrideUntil = now() + 0.3
        }
        wake()
    }

    private fun interruptGreet() {
        if (this[P.HANDS] <= 0.01f && now() >= waveUntil) return
        greetToken++
        waveUntil = 0.0
        waveStart = 0.0
        anim(P.HANDS, listOf(k(0f, 150, Ease.inOut)))
    }

    fun triggerEmote(e: Emote, duration: Float = 1.8f) {
        val t = now()
        eyeOverride = when (e) {
            Emote.LOVE -> Eye.HEART; Emote.SURPRISED -> Eye.DOT; Emote.PROUD -> Eye.STAR
            Emote.WINK -> Eye.WINK; Emote.YAWN -> Eye.TIRED; Emote.HAPPY -> Eye.HAPPY; Emote.ANNOYED -> Eye.LINE
        }
        eyeOverrideUntil = t + duration
        val holdMs = ((duration - 0.6f) * 1000).toInt()
        when (e) {
            Emote.LOVE -> {
                anim(P.BLUSH, listOf(k(1f, 300, Ease.out), k(1f, holdMs, Ease.lin), k(0f, 300, Ease.inOut)))
                emit(Kind.HEART, 4)
                anim(P.OY, listOf(k(-0.1f, 160, Ease.out), k(0f, 300, Ease.back)))
            }
            Emote.SURPRISED -> {
                anim(P.OY, listOf(k(-0.3f, 140, Ease.out), k(0f, 380, Ease.back)))
                anim(P.ES, listOf(k(1.25f, 120, Ease.out), k(1f, 500, Ease.inOut)))
            }
            Emote.PROUD -> {
                emit(Kind.STAR, 5)
                val hold = ((duration - 0.5f) * 1000).toInt()
                anim(P.TILT, listOf(k(-0.14f, 220, Ease.out), k(-0.14f, hold, Ease.lin), k(0f, 280, Ease.inOut)))
                anim(P.BLUSH, listOf(k(0.7f, 250, Ease.out), k(0.7f, hold, Ease.lin), k(0f, 300, Ease.inOut)))
            }
            Emote.WINK -> {
                val hold = ((duration - 0.4f) * 1000).toInt()
                anim(P.TILT, listOf(k(0.12f, 160, Ease.out), k(0.12f, hold, Ease.lin), k(0f, 240, Ease.inOut)))
            }
            Emote.YAWN -> {
                anim(P.SY, listOf(k(1.12f, 500, Ease.inOut), k(1f, 500, Ease.inOut)))
                anim(P.SX, listOf(k(0.94f, 500, Ease.inOut), k(1f, 500, Ease.inOut)))
                after(700) { eyeOverride = Eye.CLOSED; emit(Kind.Z, 2) }
            }
            Emote.HAPPY -> anim(P.BLUSH, listOf(k(0.6f, 200, Ease.out), k(0f, 600, Ease.inOut)))
            Emote.ANNOYED -> after(60) { Sounds.play("annoyed") }
        }
        wake()
    }

    /** A task done: a happy hop with sparks (the minis' "happy" hop and the "finished" sparks). */
    fun cheer() {
        interruptGreet()
        if (dizzy.value) return
        triggerEmote(Emote.HAPPY)
        anim(P.OY, listOf(k(-0.3f, 120, Ease.out), k(0.03f, 200, Ease.inOut), k(0f, 160, Ease.back)))
        anim(P.SY, listOf(k(0.82f, 80, Ease.out), k(1.18f, 130, Ease.out), k(0.88f, 160, Ease.inOut), k(1f, 200, Ease.back)))
        anim(P.SX, listOf(k(1.15f, 80, Ease.out), k(0.88f, 130, Ease.out), k(1.06f, 160, Ease.inOut), k(1f, 200, Ease.back)))
        after(250) { emit(Kind.SPARK, 5) }
        wake()
    }

    private fun emit(kind: Kind, count: Int) {
        for (i in 0 until count) {
            val isZ = kind == Kind.Z
            particles += Particle(
                kind,
                x = (Random.nextFloat() - 0.5f) * 0.9f + if (isZ) 0.55f else 0f,
                y = -0.7f - Random.nextFloat() * 0.2f,
                vx = (Random.nextFloat() - 0.5f) * 0.35f + if (isZ) 0.18f else 0f,
                vy = -(0.45f + Random.nextFloat() * 0.35f),
                age = -i * 0.14f,
                life = 1.3f + Random.nextFloat() * 0.5f,
                rot = Random.nextFloat() * TWO_PI,
                size = 0.15f + Random.nextFloat() * 0.08f,
            )
        }
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun near(a: Float, b: Float) = abs(a - b) <= 0.002f

    /** Anything still moving? When not, the frame loop sleeps until idleWaitMs(). */
    val busy: Boolean
        get() {
            val n = now()
            return tweens.isNotEmpty() || particles.isNotEmpty() ||
                isBreathing || breathLevel > 0.001f || dizzy.value || mood == Mood.SLEEPY ||
                dancing || danceLevel > 0.001f ||
                (n > waveStart && n < waveUntil) ||
                !near(tgYaw, this[P.YAW]) || !near(tgPitch, this[P.PITCH]) || !near(tgTilt, this[P.TILT]) ||
                !near(tgSx, this[P.SX]) || !near(tgSy, this[P.SY]) || !near(tgEs, this[P.ES]) ||
                abs(this[P.OY]) > 0.002f || abs(tint - tintT) > 0.003f ||
                abs(col.red - colT.red) > 0.003f || abs(col.green - colT.green) > 0.003f || abs(col.blue - colT.blue) > 0.003f
        }

    /** How long the loop may sleep: until the next blink, glance or timer (at most a second). */
    fun idleWaitMs(): Long {
        var next = min(nextBlink, nextWander)
        for ((at, _) in timers) next = min(next, at)
        return ((next - now()) * 1000).toLong().coerceIn(16, 1000)
    }

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        val n = now()

        if (timers.isNotEmpty()) {
            val due = timers.filter { it.first <= n }
            if (due.isNotEmpty()) {
                timers.removeAll(due.toSet())
                for ((_, f) in due) f()
            }
        }

        if (tweens.isNotEmpty()) {
            for (tw in tweens.values.toList()) {
                val key = tw.keys[tw.index]
                val p = (((n - tw.start) * 1000) / key.ms).toFloat().coerceIn(0f, 1f)
                this[tw.prop] = tw.from + (key.target - tw.from) * key.ease(p)
                if (p >= 1f) {
                    tw.from = key.target
                    tw.index += 1
                    tw.start = n
                    if (tw.index >= tw.keys.size) {
                        tweens.remove(tw.prop)
                        locks.remove(tw.prop)
                        tw.done?.invoke()
                    }
                }
            }
        }

        val t = (n - t0).toFloat()
        val sleeping = mood == Mood.SLEEPY

        // A glance somewhere now and then, back to you more often than not.
        if (touchLook == null && n > nextWander) {
            if (Random.nextFloat() < 0.45f) {
                lookX = 0f; lookY = 0f
            } else {
                lookX = (Random.nextFloat() - 0.5f) * 1.4f
                lookY = (Random.nextFloat() - 0.5f) * 0.8f
            }
            nextWander = n + 2.5 + Random.nextDouble() * 3.0
        }
        val look = touchLook ?: Offset(lookX, lookY)
        var ty = look.x * 0.62f
        var tp = look.y * 0.5f
        if (sleeping || isBreathing) { ty = 0f; tp = -0.14f * if (sleeping) 1f else 0.4f }
        if (dizzy.value) ty = sin(t * 9f) * 0.25f
        tgYaw = ty
        tgPitch = tp
        tgTilt = 0f
        if (n > waveStart && n < waveUntil) {
            val wt = (n - waveStart).toFloat()
            tgTilt = -0.06f + sin(TWO_PI * 1.2f * wt) * 0.07f
        }

        val kGen = (1 - 0.0008.pow(dt.toDouble())).toFloat()
        if (P.OY !in locks) this[P.OY] += (0f - this[P.OY]) * kGen

        if (sleeping) {
            tgSy = 1f + sin(t * 1.8f) * 0.035f
            tgSx = 1f - sin(t * 1.8f) * 0.035f * 0.57f
        } else {
            tgSy = 1f
            tgSx = 1f
        }

        val kLook = (1 - 0.0025.pow(dt.toDouble())).toFloat()
        if (P.YAW !in locks) this[P.YAW] += (tgYaw - this[P.YAW]) * kLook
        if (P.PITCH !in locks) this[P.PITCH] += (tgPitch - this[P.PITCH]) * kLook
        if (P.TILT !in locks) this[P.TILT] += (tgTilt - this[P.TILT]) * kGen
        if (P.SY !in locks) this[P.SY] += (tgSy - this[P.SY]) * kGen
        if (P.SX !in locks) this[P.SX] += (tgSx - this[P.SX]) * kGen
        if (P.ES !in locks) this[P.ES] += (tgEs - this[P.ES]) * kGen

        val kCol = (1 - 0.002.pow(dt.toDouble())).toFloat()
        col = lerp(col, colT, kCol)
        tint += (tintT - tint) * kCol

        if (n > nextBlink) {
            if (!sleeping && !dizzy.value) {
                blink()
                if (Random.nextFloat() < 0.22f) after(230) { blink() }
            }
            nextBlink = n + 2.2 + Random.nextDouble() * 3.2
        }

        val ov = eyeOverride
        if (ov != null && n > eyeOverrideUntil) {
            eyeOverride = permanentEye
            if (permanentEye != null) eyeOverrideUntil = INF
        }

        if (n - lastAmbient > 1.3) {
            lastAmbient = n
            if (sleeping) emit(Kind.Z, 1)
        }

        if (particles.isNotEmpty()) {
            for (p in particles) p.age += dt
            particles.removeAll { it.age >= it.life }
        }

        // The dance fades in over 0.3 s and out over 0.5 s; a note now and then.
        danceLevel = if (dancing) min(1f, danceLevel + dt / 0.3f) else max(0f, danceLevel - dt / 0.5f)
        if (dancing && n - lastNote > 0.9) {
            lastNote = n
            particles += Particle(
                Kind.NOTE, x = (Random.nextFloat() - 0.5f) * 1.4f, y = -0.6f, vx = (Random.nextFloat() - 0.5f) * 0.3f,
                vy = -0.5f, age = 0f, life = 1.6f, rot = 0f, size = 0.2f,
            )
        }

        val s = session
        breathFill = if (s != null) breathFrame(s, System.currentTimeMillis()).fill else 0f
        breathLevel = if (isBreathing) min(1f, breathLevel + dt / 0.6f) else max(0f, breathLevel - dt / 0.8f)
    }

    /** Skips every animation: the still pose, for widgets. */
    fun settle() {
        tweens.clear(); locks.clear(); particles.clear(); timers.clear()
        col = colT; tint = tintT
        breathLevel = if (isBreathing) 1f else 0f
        this[P.OPEN] = 1f; this[P.SX] = 1f; this[P.SY] = 1f; this[P.OY] = 0f; this[P.YAW] = 0f; this[P.PITCH] = 0f
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    private var pathRx = -1f
    private var pathRy = -1f
    private var bodyPath = Path()

    private fun body(rx: Float, ry: Float): Path {
        if (rx == pathRx && ry == pathRy) return bodyPath
        val e = 2.0 / 2.7
        val p = Path()
        val steps = 72
        for (i in 0..steps) {
            val a = i.toDouble() / steps * PI * 2
            val ca = cos(a)
            val sa = sin(a)
            val x = (rx * (if (ca >= 0) ca.pow(e) else -(-ca).pow(e))).toFloat()
            val y = (ry * (if (sa >= 0) sa.pow(e) else -(-sa).pow(e))).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        pathRx = rx; pathRy = ry; bodyPath = p
        return p
    }

    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(209, 219, 235)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(196, 181, 253)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /** Draws Mochi to fill the scope's width (keep it about 1.2 : 1). */
    fun draw(ds: DrawScope) = with(ds) {
        val w = size.width
        val h = size.height
        val r = min(w * 0.3f, h * 0.36f)
        val rx = r * 1.14f
        val ry = r * 0.88f
        val cx = w / 2f + this@MochiEngine[P.OX] * r
        val cy = h / 2f + this@MochiEngine[P.OY] * r + r * 0.06f
        val sx = this@MochiEngine[P.SX]
        val sy = this@MochiEngine[P.SY]
        val tilt = this@MochiEngine[P.TILT]

        // The breath grows him from his base, up to 16 % wider and 20 % taller.
        val f = (breathFill.coerceIn(0f, 1f) * breathLevel.coerceIn(0f, 1f))
        val px = cx
        val py = cy + r * 0.88f
        withTransform({
            if (danceLevel > 0.001f) {
                // danceTransform (core/anim on the PC): sway, hop, tilt and a squash on landing.
                val beat = (((now() - t0) * 112.0) / 60.0).toFloat()
                val hop = abs(sin(PI.toFloat() * beat))
                val land = (1f - hop).pow(6)
                translate(px + 0.08f * r * sin(PI.toFloat() * beat) * danceLevel, py - 0.2f * r * hop * danceLevel)
                rotate(deg(0.1f * sin(PI.toFloat() * beat) * danceLevel), pivot = Offset.Zero)
                scale(1f + 0.045f * land * danceLevel, 1f - 0.06f * land * danceLevel, pivot = Offset.Zero)
                translate(-px, -py)
            }
            if (f > 0.001f) scale(1f + 0.16f * f, 1f + 0.2f * f, pivot = Offset(px, py))
        }) {
            drawHands(r, rx, ry, cx, cy)
            withTransform({
                translate(cx, cy)
                if (tilt != 0f) rotate(deg(tilt), pivot = Offset.Zero)
                scale(sx, sy, pivot = Offset.Zero)
            }) {
                val path = body(rx, ry)
                drawBody(path, r, rx, ry)
                val blush = max(this@MochiEngine[P.BLUSH], tint * 0.5f)
                if (blush > 0.01f) {
                    clipPath(path) {
                        val yo = sin(this@MochiEngine[P.YAW]) * rx * 0.8f
                        for (sd in SIDES) {
                            drawOval(
                                Color(1f, 120 / 255f, 150 / 255f, 0.5f * blush),
                                topLeft = Offset(sd * rx * 0.55f + yo - r * 0.17f, ry * 0.2f - r * 0.1f),
                                size = Size(r * 0.34f, r * 0.2f),
                            )
                        }
                    }
                }
                drawEyes(path, r, rx, ry)
            }
        }
        drawParticles(r, cx, cy)
    }

    /** The flat style's fill: white stays white, a colour fills the body. */
    private fun flatFill(): Color = lerp(Color(0xFFF2F3F5), col, (tint / 0.72f).coerceIn(0f, 1f))

    private fun DrawScope.drawBody(path: Path, r: Float, rx: Float, ry: Float) {
        if (MochiStyle.flat) {
            val fill = flatFill()
            drawPath(path, fill)
            drawPath(path, lerp(fill, Color.Black, 0.35f).copy(alpha = 0.55f), style = Stroke(width = r * 0.045f))
            return
        }
        drawPath(path, Brush.linearGradient(listOf(BASE_TOP, BASE_BOTTOM), Offset(rx * 0.7f, -ry * 0.85f), Offset(-rx * 0.8f, ry * 0.9f)))
        if (tint > 0.01f) {
            drawPath(path, Brush.linearGradient(listOf(col.copy(alpha = 0.72f * tint), col.copy(alpha = 0f)), Offset(0f, ry), Offset(0f, -ry)))
        }
        drawPath(
            path,
            Brush.radialGradient(
                0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f),
                center = Offset.Zero, radius = r * 1.25f,
            ),
        )
        drawPath(
            path,
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.55f), 1f to Color.White.copy(alpha = 0f),
                center = Offset(rx * 0.34f, -ry * 0.46f), radius = r * 0.42f,
            ),
        )
    }

    private class EyePos(val x: Float, val y: Float, val fx: Float, val fy: Float, val side: Float)

    private fun eyePositions(rx: Float, ry: Float): List<EyePos> {
        val out = ArrayList<EyePos>(2)
        for (sd in SIDES) {
            val eyeYaw = sd * EYE_SP + this[P.YAW]
            var eyePitch = EYE_P + this[P.PITCH] + this[P.ROLL]
            eyePitch = (((eyePitch + PI.toFloat()) % TWO_PI) + TWO_PI) % TWO_PI - PI.toFloat()
            val cp = cos(eyePitch)
            if (cos(eyeYaw) * cp <= 0.04f) continue
            out += EyePos(sin(eyeYaw) * cp * rx, -sin(eyePitch) * ry, max(0.18f, cos(eyeYaw)), max(0.18f, cp), sd)
        }
        return out
    }

    private fun DrawScope.drawEyes(path: Path, r: Float, rx: Float, ry: Float) {
        var shape = eyeOverride ?: if (dizzy.value) Eye.SPIRAL else Eye.PILL
        if (danceLevel > 0.15f && shape == Eye.PILL && !dizzy.value) shape = Eye.HAPPY
        val es = this@MochiEngine[P.ES]
        val ew = r * EYE_W * es
        val eh = r * EYE_H * es
        val list = eyePositions(rx, ry)
        clipPath(path) {
            for (e in list) {
                withTransform({
                    translate(e.x, e.y)
                    scale(e.fx, e.fy, pivot = Offset.Zero)
                }) { drawEyeShape(shape, ew, eh, e.side) }
            }
            if (mood == Mood.FOCUS && !isBreathing && !dizzy.value) drawGlasses(list, r, rx)
        }
    }

    private fun DrawScope.roundRect(x: Float, y: Float, w: Float, h: Float, rr: Float, color: Color = INK) {
        val c = max(0f, min(rr, min(w / 2f, h / 2f)))
        drawRoundRect(color, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(c, c))
    }

    private fun DrawScope.arc(cxA: Float, cyA: Float, radius: Float, fromRad: Float, toRad: Float, width: Float) {
        drawArc(
            INK, startAngle = deg(fromRad), sweepAngle = deg(toRad - fromRad), useCenter = false,
            topLeft = Offset(cxA - radius, cyA - radius), size = Size(radius * 2, radius * 2),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    private fun DrawScope.drawEyeShape(shape: Eye, w: Float, h: Float, sd: Float) {
        val t = (now() - t0).toFloat()
        val pi = PI.toFloat()
        when (shape) {
            Eye.WIDE -> drawEyeShape(Eye.PILL, w * 1.16f, h * 1.12f, sd)
            Eye.PILL -> {
                val hh = max(h * this@MochiEngine[P.OPEN], w * 0.3f)
                roundRect(-w / 2f, -hh / 2f, w, hh, min(w / 2f, hh / 2f))
            }
            Eye.DOT -> drawCircle(INK, radius = w * 0.45f, center = Offset.Zero)
            Eye.LINE -> withTransform({ rotate(deg(-sd * 0.2f), pivot = Offset.Zero) }) {
                roundRect(-w * 0.78f, -w * 0.21f, w * 1.56f, w * 0.42f, w * 0.21f)
            }
            Eye.FLAT -> roundRect(-w * 0.72f, -w * 0.2f, w * 1.44f, w * 0.4f, w * 0.2f)
            Eye.HAPPY -> arc(0f, h * 0.18f, w * 0.82f, pi * 1.12f, pi * 1.88f, w * 0.5f)
            Eye.CLOSED -> arc(0f, -h * 0.08f, w * 0.78f, pi * 0.15f, pi * 0.85f, w * 0.36f)
            Eye.SPIRAL -> {
                val p = Path()
                var a = 0f
                var first = true
                while (a < 4.4f * pi) {
                    val rr = w * 0.06f + a * w * 0.058f
                    val aa = a + t * 9f * sd
                    val px = cos(aa) * rr
                    val py = sin(aa) * rr
                    if (first) { p.moveTo(px, py); first = false } else p.lineTo(px, py)
                    a += 0.2f
                }
                drawPath(p, INK, style = Stroke(width = w * 0.22f, cap = StrokeCap.Round))
            }
            Eye.HEART -> drawPath(heartPath(w * 1.2f), HEART)
            Eye.STAR -> withTransform({ rotate(deg(t * 1.5f * sd), pivot = Offset.Zero) }) {
                drawPath(starPath(w * 1.05f, w * 0.46f), STAR)
            }
            Eye.TIRED -> {
                roundRect(-w / 2f, -h * 0.02f, w, h * 0.38f, w / 2f)
                roundRect(-w * 0.62f, -h * 0.1f, w * 1.24f, w * 0.22f, w * 0.11f)
            }
            Eye.WINK -> if (sd < 0) drawEyeShape(Eye.PILL, w, h, sd) else drawEyeShape(Eye.HAPPY, w, h, sd)
        }
    }

    /** Round reading glasses for a focus block. */
    private fun DrawScope.drawGlasses(list: List<EyePos>, r: Float, rx: Float) {
        val d = r * 0.56f
        val frame = Color(0xFF9A5A1A)
        val arm = Color(0xFF8A4B12)
        if (list.size == 2) {
            val l = list[0]
            val rr = list[1]
            val bridge = Path().apply {
                moveTo(l.x + d / 2f * l.fx, l.y - d * 0.08f)
                quadraticTo((l.x + rr.x) / 2f, (l.y + rr.y) / 2f - d * 0.3f, rr.x - d / 2f * rr.fx, rr.y - d * 0.08f)
            }
            drawPath(bridge, arm, style = Stroke(width = r * 0.055f, cap = StrokeCap.Round))
        }
        for (e in list) {
            drawLine(arm, Offset(e.x + e.side * d / 2f * e.fx, e.y - d * 0.1f), Offset(e.side * rx * 1.05f, e.y - d * 0.25f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
            withTransform({
                translate(e.x, e.y)
                scale(e.fx, e.fy, pivot = Offset.Zero)
            }) {
                drawCircle(Color(0x2EBEE1FF), radius = d / 2f, center = Offset.Zero)
                drawCircle(frame, radius = d / 2f, center = Offset.Zero, style = Stroke(width = r * 0.065f))
            }
        }
    }

    /** The hands sit behind the body, only during the wave. */
    private fun DrawScope.drawHands(r: Float, rx: Float, ry: Float, cx: Float, cy: Float) {
        val hands = this@MochiEngine[P.HANDS]
        if (hands <= 0.01f || r <= 14f) return
        val n = now()
        val bodyH = 2 * ry
        val hew = 0.3f * ry * hands
        val heh = 0.26f * ry * hands
        val hwB = rx * this@MochiEngine[P.SX]
        val hhB = ry * this@MochiEngine[P.SY]
        val waving = n >= waveStart && waveStart > 0 && n < waveUntil
        val tilt = this@MochiEngine[P.TILT]
        for (sd in SIDES) {
            var lx: Float
            var ly: Float
            var rot = 0f
            if (sd > 0 && waving) {
                val wt = (n - waveStart).toFloat()
                val rise = min(1f, wt / 0.18f)
                val eased = 1f - (1f - rise).pow(3)
                val restX = hwB * 1.08f
                val restY = hhB * 0.7f
                val waveX = hwB * 1.1f + cos(13 * wt) * 0.06f * bodyH
                val waveY = -hhB * 0.15f - sin(13 * wt) * 0.14f * bodyH
                lx = restX + (waveX - restX) * eased
                ly = restY + (waveY - restY) * eased
                rot = (-0.5f + sin(13 * wt) * 0.35f) * eased
            } else if (sd < 0 && waving) {
                val wt = (n - waveStart).toFloat()
                lx = -hwB * 1.08f
                ly = hhB * 0.7f + sin(6 * wt) * 0.04f * bodyH
            } else {
                lx = sd * hwB * 1.08f
                ly = hhB * 0.7f
            }
            val wx = cx + cos(tilt) * lx - sin(tilt) * ly
            val wy = cy + sin(tilt) * lx + cos(tilt) * ly
            withTransform({
                translate(wx, wy)
                if (rot != 0f) rotate(deg(rot), pivot = Offset.Zero)
            }) {
                if (MochiStyle.flat) {
                    val fill = flatFill()
                    drawOval(fill, topLeft = Offset(-hew, -heh), size = Size(hew * 2, heh * 2))
                    drawOval(lerp(fill, Color.Black, 0.35f).copy(alpha = 0.55f), topLeft = Offset(-hew, -heh), size = Size(hew * 2, heh * 2), style = Stroke(r * 0.035f))
                } else {
                    drawOval(
                        Brush.linearGradient(listOf(BASE_TOP, BASE_BOTTOM), Offset(hew * 0.7f, -heh * 0.85f), Offset(-hew * 0.8f, heh * 0.9f)),
                        topLeft = Offset(-hew, -heh), size = Size(hew * 2, heh * 2),
                    )
                    drawOval(Color.Black.copy(alpha = 0.08f), topLeft = Offset(-hew, -heh), size = Size(hew * 2, heh * 2), style = Stroke(1f))
                }
            }
        }
    }

    private fun DrawScope.drawParticles(r: Float, cx: Float, cy: Float) {
        for (p in particles) {
            if (p.age <= 0f) continue
            val k = p.age / p.life
            val a = (if (k < 0.2f) k / 0.2f else 1f - (k - 0.2f) / 0.8f).coerceIn(0f, 1f)
            val px = cx + (p.x + p.vx * p.age) * r * 1.3f
            val py = cy + (p.y + p.vy * p.age) * r * 1.3f
            val sz = r * p.size * (1f + k * 0.4f)
            withTransform({ translate(px, py) }) {
                when (p.kind) {
                    Kind.HEART -> withTransform({ rotate(deg(sin(p.age * 6f) * 0.3f), pivot = Offset.Zero) }) {
                        drawPath(heartPath(sz), HEART, alpha = a)
                    }
                    Kind.STAR -> withTransform({ rotate(deg(p.rot + p.age * 2f), pivot = Offset.Zero) }) {
                        drawPath(starPath(sz, sz * 0.45f), STAR, alpha = a)
                    }
                    Kind.SPARK -> withTransform({ rotate(deg(p.rot), pivot = Offset.Zero) }) {
                        drawPath(starPath(sz * 0.8f, sz * 0.18f), Color.White, alpha = a)
                    }
                    Kind.SWEAT -> drawPath(
                        Path().apply {
                            moveTo(0f, -sz)
                            quadraticTo(sz * 0.8f, sz * 0.2f, 0f, sz * 0.6f)
                            quadraticTo(-sz * 0.8f, sz * 0.2f, 0f, -sz)
                        },
                        Color(0xFF7CC7FF), alpha = a,
                    )
                    Kind.NOTE -> {
                        notePaint.textSize = sz * 2.2f
                        notePaint.alpha = (a * 255).toInt()
                        drawContext.canvas.nativeCanvas.drawText(if (p.x > 0) "♪" else "♫", 0f, sz * 0.7f, notePaint)
                    }
                    Kind.Z -> {
                        zPaint.textSize = sz * 1.9f
                        zPaint.alpha = (a * 255).toInt()
                        drawContext.canvas.nativeCanvas.drawText("z", 0f, sz * 0.65f, zPaint)
                    }
                }
            }
        }
    }

    companion object {
        private val SIDES = floatArrayOf(-1f, 1f)

        private fun heartPath(s: Float) = Path().apply {
            moveTo(0f, s * 0.38f)
            cubicTo(-s * 1.05f, -s * 0.15f, -s * 0.5f, -s * 0.95f, 0f, -s * 0.38f)
            cubicTo(s * 0.5f, -s * 0.95f, s * 1.05f, -s * 0.15f, 0f, s * 0.38f)
            close()
        }

        private fun starPath(ro: Float, ri: Float) = Path().apply {
            for (i in 0 until 10) {
                val rr = if (i % 2 == 1) ri else ro
                val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                if (i == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
            }
            close()
        }

        /** An ARGB int, for widgets and notifications. */
        fun argb(c: Color) = c.toArgb()
    }
}
