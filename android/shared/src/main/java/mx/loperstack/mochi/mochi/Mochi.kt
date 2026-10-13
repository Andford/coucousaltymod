// Mochi in Jetpack Compose — a port of the PC's windows/src/mochi/engine.ts
// (itself a port of the Mac's BotEngine.swift): the same superellipse body,
// gradient, shading and highlight, eyes projected on a sphere that follow a
// look target and blink, the closed eyes and the swell of a breathing session,
// the reading glasses of a focus block, and a body colour of your choosing.

package mx.loperstack.mochi.mochi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** What Mochi is up to. */
enum class Mood { IDLE, HAPPY, FOCUS, BREATHING, SLEEPY }

/** The PC's pill palette (windows/src/core/pill-colors.ts), the same ten colours. */
val MOCHI_PALETTE = listOf(
    0xFFF5F6F8, 0xFFF4505E, 0xFFF29B38, 0xFFFACC15, 0xFF4ADE80,
    0xFF2DD4BF, 0xFF38BDF8, 0xFF818CF8, 0xFFC084FC, 0xFFE879F9,
).map { Color(it) } + Color(0xFFA78BFA)

private val BASE_TOP = Color(0xFFEDEDEF)
private val BASE_BOTTOM = Color(0xFFC4C5CA)
private val INK = Color(0xFF1A1412)

private const val EYE_W = 0.25f
private const val EYE_H = 0.27f
private const val EYE_SP = 0.37f
private const val EYE_P = -0.12f

/**
 * Mochi, drawn to fill the composable's width (keep it about 1.25 : 1).
 *
 * @param color body colour; white is the classic Mochi.
 * @param breathFill 0…1, how full Mochi is during a breathing session.
 * @param look where Mochi looks, -1…1 on both axes (null: a gentle wander).
 * @param jump change it to make Mochi hop (a task done).
 */
@Composable
fun Mochi(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFA78BFA),
    mood: Mood = Mood.IDLE,
    breathFill: Float = 0f,
    look: Offset? = null,
    jump: Int = 0,
) {
    var time by remember { mutableFloatStateOf(0f) }
    var open by remember { mutableFloatStateOf(1f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var breathLevel by remember { mutableFloatStateOf(0f) }
    var fill by remember { mutableFloatStateOf(0f) }
    val hop = remember { Animatable(0f) }
    val currentLook by rememberUpdatedState(look)
    val currentMood by rememberUpdatedState(mood)
    val currentFill by rememberUpdatedState(breathFill)

    // One clock for blinking, wandering eyes and the breath's fade.
    LaunchedEffect(Unit) {
        var last = 0L
        var nextBlink = 2.5f
        var blinkAt = -1f
        var wanderX = 0f
        var wanderY = 0f
        var nextWander = 1.5f
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (last == 0L) 0f else ((nanos - last) / 1e9f).coerceAtMost(0.05f)
                last = nanos
                time += dt
                // Blink: 160 ms closed-and-open, every 2.5–5.5 s.
                if (time >= nextBlink && blinkAt < 0f) blinkAt = time
                if (blinkAt >= 0f) {
                    val p = (time - blinkAt) / 0.16f
                    open = if (p < 0.5f) 1f - p * 1.8f else 0.1f + (p - 0.5f) * 1.8f
                    if (p >= 1f) {
                        open = 1f
                        blinkAt = -1f
                        nextBlink = time + 2.5f + Random.nextFloat() * 3f
                    }
                }
                if (time >= nextWander) {
                    wanderX = (Random.nextFloat() - 0.5f) * 1.2f
                    wanderY = (Random.nextFloat() - 0.5f) * 0.8f
                    nextWander = time + 1.8f + Random.nextFloat() * 2.5f
                }
                val target = currentLook ?: Offset(wanderX, wanderY)
                // Spring-ish follow, as the island's tgYaw/tgPitch.
                val k = min(1f, dt * 6f)
                yaw += (target.x.coerceIn(-1f, 1f) * 0.38f - yaw) * k
                pitch += (-target.y.coerceIn(-1f, 1f) * 0.22f - pitch) * k
                val breathing = currentMood == Mood.BREATHING
                breathLevel = if (breathing) min(1f, breathLevel + dt / 0.6f) else max(0f, breathLevel - dt / 0.8f)
                fill += (currentFill - fill) * min(1f, dt * 10f)
            }
        }
    }

    // A hop: up and back with a squash on landing.
    LaunchedEffect(jump) {
        if (jump == 0) return@LaunchedEffect
        hop.snapTo(0f)
        hop.animateTo(1f, tween(130))
        hop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }

    Canvas(modifier) {
        drawMochi(
            color = color,
            mood = mood,
            time = time,
            open = open,
            yaw = yaw,
            pitch = pitch,
            breathLevel = breathLevel,
            fill = fill,
            hop = hop.value,
        )
    }
}

private fun superellipse(rx: Float, ry: Float): Path {
    val n = 72
    val e = 2.0 / 2.7
    val p = Path()
    for (i in 0..n) {
        val a = i.toDouble() / n * PI * 2
        val ca = cos(a)
        val sa = sin(a)
        val x = (rx * (if (ca >= 0) ca.pow(e) else -(-ca).pow(e))).toFloat()
        val y = (ry * (if (sa >= 0) sa.pow(e) else -(-sa).pow(e))).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun DrawScope.drawMochi(
    color: Color, mood: Mood, time: Float, open: Float, yaw: Float, pitch: Float,
    breathLevel: Float, fill: Float, hop: Float,
) {
    val w = size.width
    val r = min(w * 0.3f, size.height * 0.36f)
    val rx = r * 1.14f
    val ry = r * 0.88f
    val cx = w / 2f
    // Idle: a slow float. Hop: up to a fifth of the radius.
    val bob = sin(time * 1.7f) * r * 0.025f * (1f - breathLevel)
    val cy = size.height / 2f + r * 0.06f + bob - hop * r * 0.35f

    val b = (fill * breathLevel).coerceIn(0f, 1f)
    val sx = 1f + 0.16f * b + 0.06f * (1f - hop) * hopSquash(hop)
    val sy = 1f + 0.2f * b - 0.06f * (1f - hop) * hopSquash(hop)

    // Soft shadow on the ground.
    drawOval(
        color = Color.Black.copy(alpha = 0.28f * (1f - hop * 0.5f)),
        topLeft = Offset(cx - rx * 0.8f, size.height / 2f + ry + r * 0.1f),
        size = Size(rx * 1.6f, r * 0.22f),
    )

    withTransform({
        translate(cx, cy)
        scale(sx, sy, pivot = Offset(0f, ry))
    }) {
        val body = superellipse(rx, ry)
        val top = lerp(BASE_TOP, color, 0.28f)
        val bottom = lerp(BASE_BOTTOM, color, 0.82f)
        drawPath(body, Brush.linearGradient(listOf(top, bottom), Offset(rx * 0.7f, -ry * 0.85f), Offset(-rx * 0.8f, ry * 0.9f)))
        drawPath(
            body,
            Brush.radialGradient(
                0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f),
                center = Offset.Zero, radius = r * 1.25f,
            ),
        )
        drawPath(
            body,
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(rx * 0.34f, -ry * 0.46f), radius = r * 0.42f,
            ),
        )

        clipPath(body) {
            if (mood == Mood.HAPPY || breathLevel > 0.3f) {
                val yo = sin(yaw) * rx * 0.8f
                for (sd in listOf(-1f, 1f)) {
                    drawOval(
                        Color(0xFFFF7896).copy(alpha = 0.4f),
                        topLeft = Offset(sd * rx * 0.55f + yo - r * 0.17f, ry * 0.2f - r * 0.1f),
                        size = Size(r * 0.34f, r * 0.2f),
                    )
                }
            }
            drawEyes(mood, r, rx, ry, yaw, pitch, open, breathLevel)
            if (mood == Mood.FOCUS) drawGlasses(r, rx, ry, yaw, pitch)
        }
    }
}

private fun hopSquash(hop: Float) = if (hop < 0.05f) 0f else 1f - abs(hop - 0.5f) * 2f

private class Eye(val x: Float, val y: Float, val fx: Float, val fy: Float, val side: Float)

private fun eyes(rx: Float, ry: Float, yaw: Float, pitch: Float): List<Eye> = listOf(-1f, 1f).mapNotNull { sd ->
    val eyeYaw = sd * EYE_SP + yaw
    val eyePitch = EYE_P + pitch
    val cp = cos(eyePitch)
    if (cos(eyeYaw) * cp <= 0.04f) null
    else Eye(
        x = sin(eyeYaw) * cp * rx,
        y = -sin(eyePitch) * ry,
        fx = max(0.18f, cos(eyeYaw)),
        fy = max(0.18f, cp),
        side = sd,
    )
}

private fun DrawScope.drawEyes(mood: Mood, r: Float, rx: Float, ry: Float, yaw: Float, pitch: Float, open: Float, breath: Float) {
    val ew = r * EYE_W
    val eh = r * EYE_H
    for (e in eyes(rx, ry, yaw, pitch)) {
        withTransform({
            translate(e.x, e.y)
            scale(e.fx, e.fy, pivot = Offset.Zero)
        }) {
            when {
                breath > 0.5f || mood == Mood.BREATHING || mood == Mood.SLEEPY -> {
                    // closed: a soft ∪, as the island's "closed" eyes.
                    val rad = ew * 0.78f
                    drawArc(
                        INK, startAngle = 27f, sweepAngle = 126f, useCenter = false,
                        topLeft = Offset(-rad, -eh * 0.08f - rad), size = Size(rad * 2, rad * 2),
                        style = Stroke(width = ew * 0.36f, cap = StrokeCap.Round),
                    )
                }
                mood == Mood.HAPPY -> {
                    val rad = ew * 0.82f
                    drawArc(
                        INK, startAngle = 201.6f, sweepAngle = 136.8f, useCenter = false,
                        topLeft = Offset(-rad, eh * 0.18f - rad), size = Size(rad * 2, rad * 2),
                        style = Stroke(width = ew * 0.5f, cap = StrokeCap.Round),
                    )
                }
                else -> {
                    val hh = max(eh * open, ew * 0.3f)
                    val corner = min(ew / 2f, hh / 2f)
                    drawRoundRect(INK, topLeft = Offset(-ew / 2f, -hh / 2f), size = Size(ew, hh), cornerRadius = CornerRadius(corner, corner))
                }
            }
        }
    }
}

/** The wardrobe's round glasses, simplified: two lenses, a bridge and the arms. */
private fun DrawScope.drawGlasses(r: Float, rx: Float, ry: Float, yaw: Float, pitch: Float) {
    val list = eyes(rx, ry, yaw, pitch)
    val d = r * 0.56f
    val frame = Color(0xFF9A5A1A)
    if (list.size == 2) {
        val (l, rr) = list
        val bridge = Path().apply {
            moveTo(l.x + d / 2f * l.fx, l.y - d * 0.08f)
            quadraticTo((l.x + rr.x) / 2f, (l.y + rr.y) / 2f - d * 0.3f, rr.x - d / 2f * rr.fx, rr.y - d * 0.08f)
        }
        drawPath(bridge, Color(0xFF8A4B12), style = Stroke(width = r * 0.055f, cap = StrokeCap.Round))
    }
    for (e in list) {
        drawLine(
            Color(0xFF8A4B12),
            Offset(e.x + e.side * d / 2f * e.fx, e.y - d * 0.1f),
            Offset(e.side * rx * 1.05f, e.y - d * 0.25f),
            strokeWidth = r * 0.05f, cap = StrokeCap.Round,
        )
        withTransform({
            translate(e.x, e.y)
            scale(e.fx, e.fy, pivot = Offset.Zero)
        }) {
            drawCircle(Color(0x2EBEE1FF), radius = d / 2f, center = Offset.Zero)
            drawCircle(frame, radius = d / 2f, center = Offset.Zero, style = Stroke(width = r * 0.065f))
            drawArc(
                Color.White.copy(alpha = 0.55f), startAngle = 198f, sweepAngle = 63f, useCenter = false,
                topLeft = Offset(-(d / 2f - r * 0.03f), -(d / 2f - r * 0.03f)),
                size = Size(d - r * 0.06f, d - r * 0.06f),
                style = Stroke(width = r * 0.03f, cap = StrokeCap.Round),
            )
        }
    }
}
