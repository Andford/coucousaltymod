// A copy of all of Mochi on the phone, in one .zip: your settings, tasks,
// diary (with its photos), moods, sleep, breathing sessions, streaks and
// Mochi's look. Restoring it replaces what's here. Nothing leaves the phone
// unless you save the file somewhere yourself.

package mx.loperstack.mochi.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Backup {
    private const val PREFS = "mochi"
    private const val ENTRY = "mochi-backup.json"

    /** Everything to `out`; true if it worked. */
    fun export(context: Context, out: Uri): Boolean = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = JSONObject()
        for ((k, v) in prefs.all) {
            val typed = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> null
            }
            if (typed != null) values.put(k, typed)
        }
        val root = JSONObject().put("app", "mochi").put("version", 1).put("at", System.currentTimeMillis()).put("prefs", values)
        context.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY))
                zip.write(root.toString().toByteArray())
                zip.closeEntry()
                DiaryImages.dir(context).listFiles()?.filter { isImageName(it.name) }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("diary/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: return false
        true
    }.getOrDefault(false)

    /** Replaces everything with the copy at `src`; false if it isn't a Mochi copy. */
    fun restore(context: Context, src: Uri): Boolean = runCatching {
        var json: JSONObject? = null
        val photos = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        context.contentResolver.openInputStream(src)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    when {
                        name == ENTRY -> json = JSONObject(zip.readBytes().decodeToString())
                        name.startsWith("diary/") && isImageName(name.removePrefix("diary/")) ->
                            File(photos, name.removePrefix("diary/")).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val root = json ?: return false
        if (root.optString("app") != "mochi") return false
        val values = root.optJSONObject("prefs") ?: return false
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        for (k in values.keys()) {
            val o = values.optJSONObject(k) ?: continue
            when (o.optString("t")) {
                "b" -> e.putBoolean(k, o.optBoolean("v"))
                "i" -> e.putInt(k, o.optInt("v"))
                "l" -> e.putLong(k, o.optLong("v"))
                "f" -> e.putFloat(k, o.optDouble("v").toFloat())
                "s" -> e.putString(k, o.optString("v"))
                "ss" -> o.optJSONArray("v")?.let { a -> e.putStringSet(k, (0 until a.length()).map { a.optString(it) }.toSet()) }
            }
        }
        e.commit()
        val dir = DiaryImages.dir(context)
        photos.listFiles()?.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
        photos.deleteRecursively()
        Store.reload()
        true
    }.getOrDefault(false)
}
