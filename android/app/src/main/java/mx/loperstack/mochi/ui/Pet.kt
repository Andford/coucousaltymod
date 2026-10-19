// Mimos — Mochi as a stress-relief pet. Every gesture has its own reaction
// and its own feel in your hand:
//   tap: a poke (thud) · double tap: a jump with a flip (quick rise)
//   hold: a hug (heartbeats) · stroke: he purrs (soft ticks)
//   rub fast back and forth: he laughs (giggle) · pinch: squish or stretch him
//   fling: he flies and bounces off the walls (thud) · shake the phone: dizzy (spin)

package mx.loperstack.mochi.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.mochi.Haptics
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.MochiEngine
import mx.loperstack.mochi.mochi.Sounds
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

private val GESTURES = listOf(
    "👉 Toca: piquete", "✨ Doble toque: salto", "🤗 Mantén: abrazo", "💜 Acarícialo: ronronea",
    "🤭 Frota rápido: cosquillas", "🤏 Pellizca: apachúrralo", "🚀 Lánzalo: rebota", "📳 Agita el teléfono: se marea",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetScreen(color: Color, actions: Actions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val engine = remember { MochiEngine() }
    val pos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var count by remember { mutableIntStateOf(Store.petsToday()) }
    var said by remember { mutableStateOf("Tócalo, acarícialo, abrázalo…") }
    val mochiW = with(density) { 210.dp.toPx() }
    val mochiH = with(density) { 176.dp.toPx() }
    val music by rememberUpdatedState(actions.music)

    fun pet(text: String) {
        said = text
        count = Store.addPet()
    }

    // Shake the phone: dizzy.
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        var lastShake = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val t = System.currentTimeMillis()
                if (g > 17f && t - lastShake > 2500) {
                    lastShake = t
                    engine.shake()
                    Haptics.spin(context)
                    pet("¡Me mareaste! 😵‍💫")
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(l) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Mimos", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, start = 6.dp))
        Text("Tu Mochi antiestrés · $count mimos hoy", color = Ink.Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(36.dp)).background(Ink.Card)
                .pointerInput(Unit) {
                    var lastTap = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startT = down.uptimeMillis
                        val downWall = System.currentTimeMillis()
                        val center = Offset(size.width / 2f, size.height / 2f) + pos.value
                        val onBody = (down.position - center).getDistance() < mochiW * 0.42f
                        val vt = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        var last = down.position
                        var travel = 0f
                        var lastDx = 0f
                        var flips = 0
                        var flipStart = startT
                        var lastPurr = 0L
                        var pinchStart = 0f
                        var pinching = false
                        var lastStep = 0
                        var hug: Job? = null

                        while (true) {
                            val waiting = hug == null && !pinching && travel < 14f && onBody
                            val left = 450L - (System.currentTimeMillis() - downWall)
                            val ev = if (waiting) withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent() } else awaitPointerEvent()
                            if (ev == null) {
                                // Held still on him: a hug, with heartbeats while you hold.
                                engine.hug(true)
                                said = "Aww… 💜"
                                hug = scope.launch { while (isActive) { Haptics.heartbeat(context); delay(900) } }
                                continue
                            }
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                if (!pinching) { pinching = true; pinchStart = d.coerceAtLeast(1f); hug?.cancel(); hug = null }
                                val amount = (d / pinchStart).let { 1f + (it - 1f) * 0.8f }
                                engine.squeeze(amount)
                                val step = ((amount - 1f) * 10).roundToInt()
                                if (step != lastStep) { lastStep = step; Haptics.tick(context) }
                                ev.changes.forEach { it.consume() }
                                continue
                            }
                            val c = pressed.first()
                            vt.addPosition(c.uptimeMillis, c.position)
                            val d = c.position - last
                            last = c.position
                            travel += d.getDistance()
                            if (onBody && hug == null && travel > 14f) {
                                engine.stroke()
                                val t = c.uptimeMillis
                                if (t - lastPurr > 110) {
                                    lastPurr = t
                                    Haptics.purr(context, min(1f, d.getDistance() / 25f + 0.3f))
                                }
                                // Rubbing back and forth fast: a tickle.
                                if (abs(d.x) > 5f && sign(d.x) != sign(lastDx)) {
                                    if (t - flipStart > 1200) { flips = 0; flipStart = t }
                                    flips++
                                }
                                if (d.x != 0f) lastDx = d.x
                                if (flips >= 6) {
                                    flips = 0
                                    engine.tickle()
                                    Haptics.giggle(context)
                                    pet("¡Jajaja, cosquillas! 🤭")
                                }
                            }
                            c.consume()
                        }

                        val now = System.currentTimeMillis()
                        when {
                            hug != null -> {
                                hug?.cancel()
                                engine.hug(false)
                                pet("Gracias por el abrazo 🤗")
                            }
                            pinching -> {
                                engine.unsqueeze()
                                Haptics.click(context)
                                pet("¡Boing!")
                            }
                            travel < 14f && onBody -> {
                                if (now - lastTap < 320) {
                                    engine.flip()
                                    Haptics.rise(context)
                                    pet("¡Wiii! ✨")
                                } else {
                                    val wasDizzy = engine.dizzy.value
                                    engine.slap()
                                    if (!wasDizzy) { if (engine.dizzy.value) Haptics.spin(context) else Haptics.thud(context) }
                                    pet(if (engine.dizzy.value) "Demasiados piquetes 😵" else "¡Oye! 😠")
                                }
                                lastTap = now
                            }
                            travel >= 14f -> {
                                val v = vt.calculateVelocity()
                                if (hypot(v.x, v.y) > 2200f) {
                                    pet("¡Allá va! 🚀")
                                    Sounds.play("open")
                                    scope.launch {
                                        val bx = (size.width - mochiW) / 2f
                                        val by = (size.height - mochiH) / 2f
                                        pos.updateBounds(Offset(-bx, -by), Offset(bx, by))
                                        var vel = Offset(v.x, v.y)
                                        for (bounce in 0 until 5) {
                                            val res = pos.animateDecay(vel, exponentialDecay(frictionMultiplier = 1.4f))
                                            if (res.endReason != AnimationEndReason.BoundReached) break
                                            engine.bump()
                                            Haptics.thud(context)
                                            Sounds.play("pop")
                                            val p = pos.value
                                            val end = res.endState.velocity
                                            vel = Offset(
                                                if (abs(p.x) >= bx - 1f) -end.x * 0.55f else end.x * 0.8f,
                                                if (abs(p.y) >= by - 1f) -end.y * 0.55f else end.y * 0.8f,
                                            )
                                            if (hypot(vel.x, vel.y) < 400f) break
                                        }
                                        pos.animateTo(Offset.Zero, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                                    }
                                } else if (onBody) {
                                    pet("Prrr… 💜")
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Aurora(color, Modifier.fillMaxSize())
            Mochi(
                Modifier.offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }.size(210.dp, 176.dp),
                color = color, engine = engine, dancing = music, greet = true,
            )
            Text(
                said, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Ink.CardHigh).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (g in GESTURES) {
                Text(
                    g, fontSize = 13.sp, color = Ink.Text,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Ink.VioletDeep).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row { Label("Cada gesto vibra distinto. Puedes apagar la vibración en Ajustes.", Ink.Dim2, 12.sp) }
        Spacer(Modifier.height(24.dp))
    }
}
