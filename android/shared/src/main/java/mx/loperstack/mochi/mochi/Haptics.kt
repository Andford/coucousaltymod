// Mochi's haptics, on the Pixel 9a and the Pixel Watch: crisp taps for
// touches and navigation (the vibrator's own "primitives" when it has them),
// a thud and a spin for his slaps and dizzy spell, and the breathing guide —
// pulses that swell while you breathe in and fade while you breathe out.

package mx.loperstack.mochi.mochi

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.pow

object Haptics {
    /** Taps and navigation (Ajustes → Vibración háptica). */
    @Volatile var enabled = true

    private fun vibrator(context: Context): Vibrator? =
        runCatching { context.getSystemService(VibratorManager::class.java)?.defaultVibrator }.getOrNull()

    /** One primitive if the vibrator has it, else the closest predefined effect. */
    private fun play(context: Context, primitive: Int, scale: Float, fallback: Int) {
        if (!enabled) return
        val v = vibrator(context) ?: return
        runCatching {
            val effect = if (v.areAllPrimitivesSupported(primitive)) {
                VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
            } else VibrationEffect.createPredefined(fallback)
            v.vibrate(effect)
        }
    }

    /** A button, a checkbox, a tab. */
    fun click(context: Context) = play(context, VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, VibrationEffect.EFFECT_CLICK)

    /** Something light: a slider step, a swipe. */
    fun tick(context: Context) = play(context, VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.8f, VibrationEffect.EFFECT_TICK)

    /** A slap on Mochi. */
    fun thud(context: Context) = play(context, VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, VibrationEffect.EFFECT_HEAVY_CLICK)

    /** Three slaps: he spins. */
    fun spin(context: Context) = play(context, VibrationEffect.Composition.PRIMITIVE_SPIN, 1f, VibrationEffect.EFFECT_DOUBLE_CLICK)

    /** Done: a task, a session, a save. */
    fun success(context: Context) {
        if (!enabled) return
        val v = vibrator(context) ?: return
        runCatching {
            val c = VibrationEffect.Composition.PRIMITIVE_CLICK
            v.vibrate(
                if (v.areAllPrimitivesSupported(c)) {
                    VibrationEffect.startComposition().addPrimitive(c, 0.5f).addPrimitive(c, 1f, 90).compose()
                } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK),
            )
        }
    }

    /**
     * The breathing guide for one phase, whatever `enabled` says (it is the
     * session's own setting). `guide`: pulses through the whole phase — rising
     * on the way in, fading on the way out; otherwise one buzz in, two out.
     */
    fun breath(context: Context, inhale: Boolean, seconds: Double, guide: Boolean) {
        val v = vibrator(context) ?: return
        runCatching {
            if (!guide || !v.hasAmplitudeControl()) {
                v.vibrate(
                    if (inhale) VibrationEffect.createOneShot(70, 140)
                    else VibrationEffect.createWaveform(longArrayOf(0, 45, 90, 45), intArrayOf(0, 110, 0, 110), -1),
                )
                return
            }
            val total = (seconds * 1000).toLong().coerceIn(1000, 20_000)
            val period = if (inhale) 250L else 400L
            val on = if (inhale) 55L else 70L
            val n = (total / period).toInt().coerceAtLeast(2)
            val timings = LongArray(n * 2)
            val amps = IntArray(n * 2)
            for (i in 0 until n) {
                val p = i / (n - 1f)
                val level = if (inhale) p.pow(1.3f) else (1f - p).pow(1.1f)
                timings[i * 2] = if (i == 0) 0 else period - on
                amps[i * 2] = 0
                timings[i * 2 + 1] = on
                amps[i * 2 + 1] = (25 + 200 * level).toInt().coerceIn(1, 255)
            }
            v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        }
    }

    fun stop(context: Context) { runCatching { vibrator(context)?.cancel() } }
}
