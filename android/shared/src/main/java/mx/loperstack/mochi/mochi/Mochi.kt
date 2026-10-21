// Mochi in Jetpack Compose — the PC's Mochi (MochiEngine, a port of
// windows/src/mochi/engine.ts), drawn on a Canvas.
//
// Smooth and cheap: the engine runs on the frame clock only while something
// moves, and between blinks the loop sleeps. A frame only redraws the Canvas,
// it never recomposes the screen around it.

package mx.loperstack.mochi.mochi

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withTimeoutOrNull
import mx.loperstack.mochi.wellness.Breathing
import kotlin.math.max
import kotlin.math.tanh

/** What Mochi is up to. */
enum class Mood { IDLE, HAPPY, FOCUS, BREATHING, SLEEPY }

/**
 * White first — the PC's own Mochi — then the PC's pill colours, pastels and
 * deep tones. Any other colour can be picked by hand (the app's colour sheet).
 */
val MOCHI_PALETTE = listOf(
    // The PC's own, and its pill colours.
    0xFFF5F6F8, 0xFFF4505E, 0xFFF29B38, 0xFFFACC15, 0xFF4ADE80, 0xFF2DD4BF,
    0xFF38BDF8, 0xFF818CF8, 0xFFA78BFA, 0xFFC084FC, 0xFFE879F9, 0xFFFB7185,
    // Pastels.
    0xFFFECACA, 0xFFFED7AA, 0xFFFEF08A, 0xFFBBF7D0, 0xFFA5F3FC, 0xFFBFDBFE,
    0xFFDDD6FE, 0xFFF5D0FE, 0xFFFBCFE8, 0xFFE7E5E4, 0xFFD6D3D1, 0xFFCBD5E1,
    // Deep tones.
    0xFFB91C1C, 0xFFC2410C, 0xFFA16207, 0xFF15803D, 0xFF0F766E, 0xFF0369A1,
    0xFF1D4ED8, 0xFF4338CA, 0xFF6D28D9, 0xFFA21CAF, 0xFFBE185D, 0xFF334155,
).map { Color(it) }

/**
 * Mochi, drawn to fill the composable's width (keep it about 1.2 : 1).
 *
 * @param color white is the PC's Mochi; any other colour tints him.
 * @param breathing a running session: he closes his eyes and grows with each inhale.
 * @param jump change it to make him hop (a task done).
 * @param interactive taps slap him, as on the PC — three quick ones and he gets
 *   dizzy and says so; a long press gets hearts.
 * @param greet wave hello when he first appears.
 * @param dancing music is playing: he bounces to it, with happy eyes.
 * @param face fixed eyes (the diary's moods).
 */
@Composable
fun Mochi(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF5F6F8),
    mood: Mood = Mood.IDLE,
    breathing: Breathing? = null,
    jump: Int = 0,
    interactive: Boolean = false,
    greet: Boolean = false,
    dancing: Boolean = false,
    face: MochiEngine.Eye? = null,
    wear: Boolean = face == null,
    /** Your own engine, to drive him from outside (the Mimos screen). */
    engine: MochiEngine? = null,
    /** A long sigh when he appears (the breathing screen). */
    sigh: Boolean = false,
) {
    val own = remember { MochiEngine() }
    val engine = engine ?: own
    var frame by remember { mutableLongStateOf(0L) }
    SideEffect { engine.configure(color, mood, breathing, dancing, face, wear) }

    LaunchedEffect(jump) { if (jump > 0) engine.cheer() }

    LaunchedEffect(engine) {
        engine.configure(color, mood, breathing, dancing, face, wear)
        if (greet) engine.greet()
        if (sigh) { kotlinx.coroutines.delay(350); engine.sigh() }
        var last = System.nanoTime()
        while (true) {
            if (engine.busy) {
                withFrameNanos { }
            } else {
                withTimeoutOrNull(engine.idleWaitMs()) { engine.wakeups.receive() }
                withFrameNanos { }
            }
            val n = System.nanoTime()
            engine.update((n - last) / 1e9f)
            last = n
            frame++
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val touch = if (interactive) {
        Modifier.pointerInput(engine) {
            fun toLook(o: Offset) = Offset(
                tanh((o.x - size.width / 2f) / (size.width * 0.5f)),
                -tanh((o.y - size.height / 2f) / (size.height * 0.5f)),
            )
            detectTapGestures(
                onPress = { o ->
                    engine.lookAt(toLook(o))
                    tryAwaitRelease()
                    engine.lookAt(null)
                },
                onTap = {
                    val wasDizzy = engine.dizzy.value
                    engine.slap()
                    if (!wasDizzy) { if (engine.dizzy.value) Haptics.spin(context) else Haptics.thud(context) }
                },
                onLongPress = {
                    engine.triggerEmote(MochiEngine.Emote.LOVE)
                    Sounds.play("love")
                    Haptics.success(context)
                },
            )
        }
    } else Modifier

    Box(modifier) {
        Canvas(Modifier.matchParentSize().then(touch)) {
            if (frame < 0) return@Canvas // read: redraw on each engine frame
            engine.draw(this)
        }
        if (engine.dizzy.value) DizzyBubble()
    }
}

/** The PC's "confused" card, above Mochi: he took too many hits. */
@Composable
private fun DizzyBubble() {
    val small = LocalConfiguration.current.screenWidthDp < 300
    Popup(popupPositionProvider = AboveAnchor) {
        Column(
            Modifier.widthIn(max = if (small) 170.dp else 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF22A1422))
                .border(1.dp, Color(0x80F472B6), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            BasicText(
                "Demasiados golpes a la vez.",
                style = TextStyle(color = Color.White, fontSize = if (small) 13.sp else 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            BasicText(
                if (small) "Dame un segundo…" else "Dame un segundo — de vuelta en tres segundos.",
                style = TextStyle(color = Color(0xFFF9A8D4), fontSize = if (small) 11.sp else 13.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.top - popupContentSize.height + anchorBounds.height / 10).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Whether music is playing on this device (any app: Spotify, YouTube Music…),
 * checked every 2 s while the screen shows — the frame clock pauses the loop
 * when the app is in the background.
 */
@Composable
fun rememberMusicPlaying(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playing by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        while (true) {
            withFrameNanos { }
            val now = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
            if (now != playing) playing = now
            kotlinx.coroutines.delay(2000)
        }
    }
    return playing
}

/** Mochi at rest as a bitmap — for home screen widgets. */
fun mochiBitmap(
    widthPx: Int, heightPx: Int, color: Color, mood: Mood = Mood.IDLE, face: MochiEngine.Eye? = null,
    wear: Boolean = face == null,
    /** A pose for animated widgets: vertical squash (1 = rest) and lift (px of the unit box). */
    squash: Float = 1f, lift: Float = 0f,
    /** Empty room above him, as a share of the height, so hats and ears (and a hop) fit. */
    headroom: Float = 0f,
): Bitmap {
    val image = ImageBitmap(widthPx, heightPx)
    val engine = MochiEngine().apply {
        configure(color, mood, null, false, face, wear)
        settle()
        pose(squash, lift)
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(widthPx.toFloat(), heightPx.toFloat())) {
        val top = heightPx * headroom.coerceIn(0f, 0.6f)
        val inner = heightPx - top
        val side = ((widthPx - inner * 1.2f) / 2f).coerceAtLeast(0f)
        inset(side, top, side, 0f) { engine.draw(this) }
    }
    return image.asAndroidBitmap()
}
