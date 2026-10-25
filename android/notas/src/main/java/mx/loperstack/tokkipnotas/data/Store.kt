// Where your pages live: a small SQLite database on the phone (notas.db).
// The index — every page without its body — is in memory as a StateFlow; a
// body is read when you open its page and written a moment after you stop
// typing, on a thread of its own, so the screen never waits for the disk.

package mx.loperstack.tokkipnotas.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

private class Helper(context: Context) : SQLiteOpenHelper(context, "notas.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE pages (id TEXT PRIMARY KEY, parent TEXT, title TEXT NOT NULL DEFAULT '', icon TEXT NOT NULL DEFAULT 'doc',
               color INTEGER NOT NULL DEFAULT 0, shape INTEGER NOT NULL DEFAULT 0, kind TEXT NOT NULL DEFAULT 'DOC', ord REAL NOT NULL DEFAULT 0,
               created INTEGER NOT NULL DEFAULT 0, updated INTEGER NOT NULL DEFAULT 0, opened INTEGER NOT NULL DEFAULT 0,
               favorite INTEGER NOT NULL DEFAULT 0, trashed INTEGER NOT NULL DEFAULT 0, props TEXT NOT NULL DEFAULT '', schema TEXT NOT NULL DEFAULT '',
               preview TEXT NOT NULL DEFAULT '', steps INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, body TEXT NOT NULL DEFAULT '')""",
        )
        db.execSQL("CREATE INDEX pages_parent ON pages(parent)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
}

private const val META = "id,parent,title,icon,color,shape,kind,ord,created,updated,opened,favorite,trashed,props,schema,preview,steps,done"

object Store {
    private var helper: Helper? = null
    private val db get() = helper!!.writableDatabase
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "notas-db").apply { isDaemon = true } }
    private val _index = MutableStateFlow(Index())
    val index: StateFlow<Index> = _index.asStateFlow()
    val now get() = System.currentTimeMillis()

    fun init(context: Context) {
        if (helper != null) return
        helper = Helper(context.applicationContext)
        runCatching {
            // The Papelera keeps things 30 days.
            db.delete("pages", "trashed > 0 AND trashed < ?", arrayOf((now - 30 * DAY_MS).toString()))
            _index.value = load()
            if (_index.value.page(INBOX_ID) == null) put(Page(INBOX_ID, null, "Bandeja", "inbox", 0xFF94A3B8, 0, created = now, updated = now), "")
        }
    }

    private fun pageOf(c: Cursor) = Page(
        id = c.getString(0), parent = if (c.isNull(1)) null else c.getString(1), title = c.getString(2), icon = c.getString(3),
        color = c.getLong(4), shape = c.getInt(5), kind = runCatching { PageKind.valueOf(c.getString(6)) }.getOrDefault(PageKind.DOC),
        ord = c.getDouble(7), created = c.getLong(8), updated = c.getLong(9), opened = c.getLong(10), favorite = c.getInt(11) != 0,
        trashed = c.getLong(12), props = propsOf(c.getString(13)), schema = schemaOf(c.getString(14)), preview = c.getString(15),
        steps = c.getInt(16), done = c.getInt(17),
    )

    private fun load(): Index {
        val out = ArrayList<Page>()
        db.rawQuery("SELECT $META FROM pages", null).use { c -> while (c.moveToNext()) out += pageOf(c) }
        return Index(out)
    }

    private fun values(p: Page) = ContentValues().apply {
        put("id", p.id); put("parent", p.parent); put("title", p.title); put("icon", p.icon); put("color", p.color); put("shape", p.shape)
        put("kind", p.kind.name); put("ord", p.ord); put("created", p.created); put("updated", p.updated); put("opened", p.opened)
        put("favorite", if (p.favorite) 1 else 0); put("trashed", p.trashed); put("props", propsJson(p.props)); put("schema", schemaJson(p.schema))
        put("preview", p.preview); put("steps", p.steps); put("done", p.done)
    }

    private fun write(block: SQLiteDatabase.() -> Unit) = io.execute { runCatching { db.block() } }

    private fun setPages(changed: List<Page>, removed: Set<String> = emptySet()) {
        val ids = changed.map { it.id }.toSet()
        _index.value = Index(_index.value.pages.filter { it.id !in ids && it.id !in removed } + changed)
    }

    fun page(id: String?) = _index.value.page(id)

    /** A new page (or a replaced one), with its body. */
    private fun put(p: Page, body: String) {
        val f = factsOf(body)
        val full = p.copy(preview = f.preview, steps = f.steps, done = f.done)
        setPages(listOf(full))
        write { insertWithOnConflict("pages", null, values(full).apply { put("body", body) }, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    /** Changes a page's details (title, icon, values, columns…), not its body. */
    fun update(p: Page, touch: Boolean = true) {
        val q = if (touch) p.copy(updated = now) else p
        setPages(listOf(q))
        write { update("pages", values(q), "id=?", arrayOf(q.id)) }
    }

    private fun lastOrd(parent: String?) = _index.value.children(parent).maxOfOrNull { it.ord }

    fun newPage(parent: String?, kind: PageKind = PageKind.DOC, title: String = "", body: String = "", props: Map<String, String> = emptyMap(), first: Boolean = false): Page {
        val parentPage = page(parent)
        val siblings = _index.value.children(parent)
        val ord = if (first) between(null, siblings.firstOrNull()?.ord) else between(lastOrd(parent), null)
        val color = if (kind == PageKind.COLLECTION) 0xFFFBBF24 else parentPage?.takeIf { it.id != INBOX_ID }?.color ?: 0xFFA78BFA
        val p = Page(
            newId(), parent, title, if (kind == PageKind.COLLECTION) "board" else "doc", color, if (kind == PageKind.COLLECTION) 3 else 0, kind, ord,
            now, now, if (kind == PageKind.DOC && parentPage?.kind != PageKind.COLLECTION) now else 0, props = props,
            schema = if (kind == PageKind.COLLECTION) defaultSchema() else null,
        )
        put(p, body)
        return p
    }

    /** A thought caught in a hurry: into the Bandeja (or another page), the newest first. */
    fun capture(text: String, parent: String = INBOX_ID): Page? {
        if (text.isBlank()) return null
        val (title, body) = captureOf(text)
        return newPage(parent, title = title, body = body, first = true)
    }

    /** Builds a template's pages; the top one. */
    fun create(d: Draft): Page {
        for ((p, body) in d.flatten()) put(p, body)
        return page(d.page.id) ?: d.page
    }

    /** The page's text. Blocking (it waits for pending writes): call it off the screen's thread. */
    fun body(id: String): String = runCatching {
        io.submit<String> { db.rawQuery("SELECT body FROM pages WHERE id=?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.getString(0) else "" } }.get()
    }.getOrDefault("")

    fun saveBody(id: String, title: String, body: String) {
        val old = page(id) ?: return
        val f = factsOf(body)
        val p = old.copy(title = title, preview = f.preview, steps = f.steps, done = f.done, updated = now)
        setPages(listOf(p))
        write {
            execSQL(
                "UPDATE pages SET title=?, body=?, preview=?, steps=?, done=?, updated=? WHERE id=?",
                arrayOf(p.title, body, p.preview, p.steps, p.done, p.updated, id),
            )
        }
    }

    fun opened(id: String) {
        val p = page(id) ?: return
        update(p.copy(opened = now), touch = false)
    }

    /** Moves a page under another (at the end); not into itself. */
    fun move(id: String, parent: String?): Boolean {
        val p = page(id) ?: return false
        if (parent != null && _index.value.isInside(parent, id)) return false
        update(p.copy(parent = parent, ord = between(lastOrd(parent), null)))
        return true
    }

    /** To the Papelera, with everything inside it. */
    fun trash(id: String) {
        if (id == INBOX_ID) return
        val t = now
        val all = _index.value.subtree(id).filter { it.trashed == 0L }.map { it.copy(trashed = t) }
        setPages(all)
        write { for (p in all) execSQL("UPDATE pages SET trashed=? WHERE id=?", arrayOf(t, p.id)) }
    }

    /** Back from the Papelera (what went with it comes back too). If its parent is gone, to the top. */
    fun restore(id: String) {
        val p = page(id) ?: return
        val t = p.trashed
        val back = _index.value.subtree(id).filter { it.trashed == t }.map { it.copy(trashed = 0) }.map {
            if (it.id == id && (page(it.parent)?.trashed ?: 0L) != 0L) it.copy(parent = null) else it
        }
        setPages(back)
        write { for (q in back) update("pages", values(q), "id=?", arrayOf(q.id)) }
    }

    /** Gone for good, with everything inside. */
    fun purge(id: String) {
        val ids = _index.value.subtree(id).map { it.id }.toSet()
        setPages(emptyList(), ids)
        write { for (i in ids) delete("pages", "id=?", arrayOf(i)) }
    }

    fun emptyTrash() { for (p in _index.value.trash) purge(p.id) }

    /** Pages matching `query`, best first, with a piece of their text. Blocking. */
    fun search(query: String): List<Pair<Page, String>> = runCatching {
        if (query.isBlank()) return emptyList()
        val ix = _index.value
        io.submit<List<Pair<Page, String>>> {
            val out = ArrayList<Triple<Page, String, Int>>()
            db.rawQuery("SELECT id,title,body FROM pages WHERE trashed=0", null).use { c ->
                while (c.moveToNext()) {
                    val p = ix.page(c.getString(0)) ?: continue
                    if (p.id == INBOX_ID) continue
                    val plain = plainOf(c.getString(2))
                    val values = p.props.values.joinToString(" ")
                    val s = searchScore(query, c.getString(1), "$plain $values")
                    if (s > 0) out += Triple(p, snippet(query, plain), s)
                }
            }
            out.sortedWith(compareByDescending<Triple<Page, String, Int>> { it.third }.thenByDescending { it.first.updated }).take(50).map { it.first to it.second }
        }.get()
    }.getOrDefault(emptyList())

    // ── Copies ────────────────────────────────────────────────────────────────

    /** Everything, with bodies. Blocking. */
    fun exportJson(): JSONObject = io.submit<JSONObject> {
        val pages = JSONArray()
        db.rawQuery("SELECT $META,body FROM pages", null).use { c ->
            while (c.moveToNext()) {
                val p = pageOf(c)
                pages.put(
                    JSONObject().put("id", p.id).put("parent", p.parent ?: JSONObject.NULL).put("title", p.title).put("icon", p.icon)
                        .put("color", p.color).put("shape", p.shape).put("kind", p.kind.name).put("ord", p.ord).put("created", p.created)
                        .put("updated", p.updated).put("opened", p.opened).put("favorite", p.favorite).put("trashed", p.trashed)
                        .put("props", propsJson(p.props)).put("schema", schemaJson(p.schema)).put("body", c.getString(18)),
                )
            }
        }
        JSONObject().put("app", "tokkipnotas").put("version", 1).put("at", now).put("pages", pages)
    }.get()

    /** Pages from a copy (replacing everything when `replace`). How many came in, or -1. Blocking. */
    fun importJson(root: JSONObject, replace: Boolean): Int {
        if (root.optString("app") != "tokkipnotas") return -1
        val a = root.optJSONArray("pages") ?: return -1
        val list = (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Page(
                id = o.optString("id"), parent = if (o.isNull("parent")) null else o.optString("parent"), title = o.optString("title"),
                icon = o.optString("icon", "doc"), color = o.optLong("color"), shape = o.optInt("shape"),
                kind = runCatching { PageKind.valueOf(o.optString("kind")) }.getOrDefault(PageKind.DOC), ord = o.optDouble("ord"),
                created = o.optLong("created"), updated = o.optLong("updated"), opened = o.optLong("opened"), favorite = o.optBoolean("favorite"),
                trashed = o.optLong("trashed"), props = propsOf(o.optString("props")), schema = schemaOf(o.optString("schema")),
            ) to o.optString("body")
        }.filter { it.first.id.isNotEmpty() }
        bulk(list, replace)
        return list.size
    }

    /** Tokkip's old Cuaderno. How many pages came in. Blocking. */
    fun importTokkip(root: JSONObject): Int {
        val list = fromTokkip(root)
        bulk(list, replace = false)
        return list.size
    }

    private fun bulk(list: List<Pair<Page, String>>, replace: Boolean) {
        io.submit {
            db.beginTransaction()
            try {
                if (replace) db.delete("pages", null, null)
                for ((p, body) in list) {
                    val f = factsOf(body)
                    db.insertWithOnConflict("pages", null, values(p.copy(preview = f.preview, steps = f.steps, done = f.done)).apply { put("body", body) }, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.get()
        _index.value = io.submit<Index> { load() }.get()
        if (_index.value.page(INBOX_ID) == null) put(Page(INBOX_ID, null, "Bandeja", "inbox", 0xFF94A3B8, 0, created = now, updated = now), "")
    }
}
