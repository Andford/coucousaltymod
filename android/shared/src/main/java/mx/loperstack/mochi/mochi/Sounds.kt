// Tokkip's own sounds (tools/make_sounds.py, synthesised), played with a
// SoundPool so they start instantly, on the phone and on the watch alike.

package mx.loperstack.mochi.mochi

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import mx.loperstack.mochi.shared.R

object Sounds {
    /** The names the PC uses, mapped to the copied WAVs. */
    private val RAW = mapOf(
        "annoyed" to R.raw.annoyed, "approval" to R.raw.approval, "approve" to R.raw.approve,
        "blip" to R.raw.blip, "close" to R.raw.close, "error" to R.raw.error, "finish" to R.raw.finish,
        "greet" to R.raw.greet, "love" to R.raw.love, "open" to R.raw.open, "peek" to R.raw.peek,
        "pop" to R.raw.pop, "proud" to R.raw.proud, "question" to R.raw.question, "sleep" to R.raw.sleep,
        "tick" to R.raw.tick, "wink" to R.raw.wink, "work" to R.raw.work, "yawn" to R.raw.yawn,
        "dizzy" to R.raw.dizzy, "slap" to R.raw.slap, "gulp" to R.raw.gulp,
    )

    private var pool: SoundPool? = null
    private val loaded = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()
    private val pending = mutableSetOf<Int>()

    /** On by default; the app can turn Mochi's sounds off. */
    @Volatile var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { pool, id, status ->
            if (status == 0) {
                ready += id
                if (pending.remove(id)) pool.play(id, 0.8f, 0.8f, 1, 0, 1f)
            }
        }
        pool = p
        val app = context.applicationContext
        for ((name, res) in RAW) loaded[name] = p.load(app, res, 1)
    }

    fun play(name: String, volume: Float = 0.8f) {
        if (!enabled) return
        val p = pool ?: return
        val id = loaded[name] ?: return
        if (id in ready) p.play(id, volume, volume, 1, 0, 1f) else pending += id
    }
}
