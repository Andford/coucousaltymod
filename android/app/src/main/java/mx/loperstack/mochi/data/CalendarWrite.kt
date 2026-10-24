// New events from Mochi go into a calendar on this phone — your Google
// calendar by default — and Google Calendar's own sync uploads them, so they
// show on the PC, the web and everywhere else. No sign-in: WRITE_CALENDAR only.

package mx.loperstack.mochi.data

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

data class WritableCalendar(val id: Long, val name: String, val account: String, val color: Int?, val primary: Boolean, val google: Boolean)

/** All-day events are stored at UTC midnights: the local day's, and the next. */
fun allDayRange(localDay: Long): Pair<Long, Long> {
    val local = Calendar.getInstance().apply { timeInMillis = localDay }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }
    val start = utc.timeInMillis
    return start to start + 24 * 3600_000L
}

/** A colour an event can take: Google's keyed palette ("1"…"11"), or a plain colour elsewhere. */
data class EventColor(val key: String?, val color: Int, val name: String)

/** Google Calendar's own names for its event colours, by key. */
private val GOOGLE_NAMES = mapOf(
    "1" to "Lavanda", "2" to "Salvia", "3" to "Uva", "4" to "Flamenco", "5" to "Plátano", "6" to "Mandarina",
    "7" to "Pavo real", "8" to "Grafito", "9" to "Arándano", "10" to "Albahaca", "11" to "Tomate",
)

/** For calendars with no palette (on the phone only). */
private val PLAIN = listOf(
    0xFF7986CB to "Lavanda", 0xFF33B679 to "Salvia", 0xFF8E24AA to "Uva", 0xFFE67C73 to "Flamenco", 0xFFF6BF26 to "Plátano",
    0xFFF4511E to "Mandarina", 0xFF039BE5 to "Pavo real", 0xFF616161 to "Grafito", 0xFF3F51B5 to "Arándano", 0xFF0B8043 to "Albahaca", 0xFFD50000 to "Tomate",
)

object CalendarWrite {
    /** The colours an event in `calendarId` can take: its account's palette (Google), or a plain one. */
    fun colors(context: Context, calendarId: Long): List<EventColor> {
        var account: String? = null
        var type: String? = null
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
                "${CalendarContract.Calendars._ID} = ?", arrayOf(calendarId.toString()), null,
            )?.use { c -> if (c.moveToFirst()) { account = c.getString(0); type = c.getString(1) } }
        }
        val out = mutableListOf<EventColor>()
        if (account != null && type != null) runCatching {
            context.contentResolver.query(
                CalendarContract.Colors.CONTENT_URI,
                arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR),
                "${CalendarContract.Colors.ACCOUNT_NAME} = ? AND ${CalendarContract.Colors.ACCOUNT_TYPE} = ? AND ${CalendarContract.Colors.COLOR_TYPE} = ?",
                arrayOf(account, type, CalendarContract.Colors.TYPE_EVENT.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0)
                    out += EventColor(key, c.getInt(1), GOOGLE_NAMES[key] ?: "Color $key")
                }
            }
        }
        if (out.isNotEmpty()) return out.sortedBy { it.key?.toIntOrNull() ?: 99 }
        return PLAIN.map { (c, n) -> EventColor(null, c.toInt(), n) }
    }

    /** Gives an event a colour, or back to its calendar's (`color` null). True if it worked. */
    fun setColor(context: Context, eventId: Long, color: EventColor?): Boolean = runCatching {
        val values = ContentValues().apply {
            when {
                color == null -> { putNull(CalendarContract.Events.EVENT_COLOR_KEY); putNull(CalendarContract.Events.EVENT_COLOR) }
                color.key != null -> put(CalendarContract.Events.EVENT_COLOR_KEY, color.key)
                else -> put(CalendarContract.Events.EVENT_COLOR, color.color)
            }
        }
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /** Calendars you can add to, your Google primary first. */
    fun calendars(context: Context): List<WritableCalendar> {
        val out = mutableListOf<WritableCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(6) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR || c.getInt(7) == 0) continue
                    out += WritableCalendar(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendario",
                        account = c.getString(2) ?: "",
                        color = if (c.isNull(3)) null else c.getInt(3),
                        primary = c.getInt(4) == 1,
                        google = c.getString(5) == "com.google",
                    )
                }
            }
        }
        return out.sortedWith(compareByDescending<WritableCalendar> { it.google && it.primary }.thenByDescending { it.google }.thenBy { it.name })
    }

    /**
     * Adds an event; returns its id, or null if the calendar said no.
     * `reminderMin` < 0: no reminder.
     */
    fun insert(context: Context, calendarId: Long, title: String, begin: Long, end: Long, allDay: Boolean, reminderMin: Int, color: EventColor? = null): Long? {
        val (start, finish) = if (allDay) allDayRange(begin) else begin to end
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.trim().take(200))
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, finish)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, if (reminderMin >= 0) 1 else 0)
            when {
                color?.key != null -> put(CalendarContract.Events.EVENT_COLOR_KEY, color.key)
                color != null -> put(CalendarContract.Events.EVENT_COLOR, color.color)
            }
        }
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull() ?: return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        if (reminderMin >= 0) {
            runCatching {
                context.contentResolver.insert(
                    CalendarContract.Reminders.CONTENT_URI,
                    ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, id)
                        put(CalendarContract.Reminders.MINUTES, reminderMin)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    },
                )
            }
        }
        return id
    }
}
