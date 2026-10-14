// Diario: how you feel today (five Mochi faces) and the day's log — short
// lines with their time, like an anime's daily log — plus the month in colours.

package mx.loperstack.mochi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.mochi.data.DayLog
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.dayKey
import mx.loperstack.mochi.data.dayNumber
import mx.loperstack.mochi.data.dayStart
import mx.loperstack.mochi.data.streak
import mx.loperstack.mochi.data.withMood
import mx.loperstack.mochi.data.withNote
import mx.loperstack.mochi.data.withoutNote
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.wellness.MOODS
import mx.loperstack.mochi.wellness.moodOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")
private val Mono = FontFamily.Monospace
private val Paper = Color(0xFF101115)

private fun moodLine(mood: Int) = when (mood) {
    5 -> "¡Día brillante! Guárdalo bien."
    4 -> "Qué bueno. Mochi está contento contigo."
    3 -> "Un día tranquilo también cuenta."
    2 -> "Ánimo. Escribir lo que pesa ayuda."
    1 -> "Lo siento. ¿Respiramos un minuto juntos?"
    else -> "¿Cómo va tu día?"
}

private fun moodSound(mood: Int) = when (mood) {
    5 -> "proud"; 4 -> "love"; 3 -> "blip"; 2 -> "yawn"; else -> "annoyed"
}

private fun firstOfMonth(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun DiaryScreen(logs: List<DayLog>, color: Color, actions: Actions, goBreathe: () -> Unit = {}) {
    val now = rememberNow(60_000)
    val today = dayKey(now)
    var selected by remember { mutableStateOf(today) }
    var month by remember { mutableLongStateOf(firstOfMonth(now)) }
    val log = logs.firstOrNull { it.day == selected } ?: DayLog(selected)
    val run = streak(logs, now)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Diario", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (run > 0) {
                Text(
                    "🔥 $run ${if (run == 1) "día" else "días"}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDBA74),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x26F97316)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LogCard(log, dayNumber(logs, selected), selected == today, color, actions, goBreathe)
        Spacer(Modifier.height(16.dp))
        MonthGrid(logs, month, selected, today, onSelect = { selected = it }, onMonth = { delta ->
            month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, delta) }.timeInMillis
        })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LogCard(log: DayLog, number: Int, isToday: Boolean, color: Color, actions: Actions, goBreathe: () -> Unit) {
    var text by remember(log.day) { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            val at = if (isToday) System.currentTimeMillis() else dayStart(log.day) + 12 * 3600_000L
            Store.setLogs(withNote(Store.state.value.logs, log.day, at, text))
            text = ""
            actions.jump++
            Sounds.play("pop")
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper)
            .border(1.dp, Ink.VioletLine, RoundedCornerShape(22.dp))
            .drawBehind {
                // Ruled paper.
                val step = 30.dp.toPx()
                var y = 96.dp.toPx()
                while (y < size.height - 8.dp.toPx()) {
                    drawLine(Color(0x0DFFFFFF), Offset(16.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y), 1f)
                    y += step
                }
            }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◆ REGISTRO DIARIO", fontFamily = Mono, fontSize = 11.sp, color = Ink.Dim2, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("DÍA %03d".format(number), fontFamily = Mono, fontSize = 13.sp, color = Ink.VioletSoft, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(dayStart(log.day))).replaceFirstChar { it.uppercase(MX) },
            fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (m in MOODS) {
                val on = log.mood == m.value
                Column(
                    Modifier.weight(1f).alpha(if (log.mood == 0 || on) 1f else 0.4f).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color(m.color).copy(alpha = 0.16f) else Color.Transparent)
                        .clickable {
                            Store.setLogs(withMood(Store.state.value.logs, log.day, if (on) 0 else m.value))
                            if (!on) { Sounds.play(moodSound(m.value)); if (m.value >= 4) actions.jump++ }
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Mochi(Modifier.width(52.dp).height(44.dp), color = Color(m.color), face = m.face)
                    Text(m.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color(m.color) else Ink.Dim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            moodLine(log.mood), fontSize = 13.sp, color = moodOf(log.mood)?.let { Color(it.color) } ?: Ink.Dim,
            modifier = if (log.mood == 1) Modifier.clickable(onClick = goBreathe) else Modifier,
        )
        Spacer(Modifier.height(14.dp))
        if (log.notes.isEmpty()) {
            Text(if (isToday) "> sin entradas todavía_" else "> sin entradas", fontFamily = Mono, fontSize = 13.sp, color = Ink.Dim2)
        }
        for (n in log.notes) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text(SimpleDateFormat("HH:mm", MX).format(Date(n.at)), fontFamily = Mono, fontSize = 13.sp, color = Ink.VioletSoft, fontWeight = FontWeight.Bold)
                Text(" ▸ ", fontFamily = Mono, fontSize = 13.sp, color = Ink.Dim2)
                Text(n.text, fontSize = 15.sp, color = Ink.Text, modifier = Modifier.weight(1f))
                Text(
                    "×", fontSize = 16.sp, color = Ink.Dim2,
                    modifier = Modifier.clip(CircleShape).clickable {
                        Store.setLogs(withoutNote(Store.state.value.logs, log.day, n.at))
                        Sounds.play("blip")
                    }.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(500) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (isToday) "Escribe una línea del día…" else "Agregar a este día…", color = Ink.Dim2) },
            shape = RoundedCornerShape(16.dp), maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = { IconButton(onClick = { submit() }) { Icon(Glyph.Plus, contentDescription = "Guardar", tint = Ink.Violet) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text("Solo se guarda en este teléfono.", fontSize = 11.sp, color = Ink.Dim2)
    }
}

@Composable
private fun MonthGrid(logs: List<DayLog>, month: Long, selected: String, today: String, onSelect: (String) -> Unit, onMonth: (Int) -> Unit) {
    val byDay = logs.associateBy { it.day }
    val cal = Calendar.getInstance().apply { timeInMillis = month }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday first
    val prefix = SimpleDateFormat("yyyy-MM-", Locale.US).format(Date(month))
    val counts = IntArray(6)
    for (d in 1..daysInMonth) byDay["$prefix%02d".format(d)]?.let { counts[it.mood]++ }

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 22.sp, color = Ink.Dim, modifier = Modifier.clip(CircleShape).clickable { onMonth(-1) }.padding(horizontal = 12.dp))
            Text(
                SimpleDateFormat("MMMM yyyy", MX).format(Date(month)).replaceFirstChar { it.uppercase(MX) },
                fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text("›", fontSize = 22.sp, color = Ink.Dim, modifier = Modifier.clip(CircleShape).clickable { onMonth(1) }.padding(horizontal = 12.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            for (d in listOf("L", "M", "M", "J", "V", "S", "D")) {
                Text(d, fontSize = 11.sp, color = Ink.Dim2, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = lead + daysInMonth
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (wd in 0..6) {
                    val d = week * 7 + wd - lead + 1
                    if (d < 1 || d > daysInMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        continue
                    }
                    val key = "$prefix%02d".format(d)
                    val entry = byDay[key]
                    val mood = moodOf(entry?.mood ?: 0)
                    val future = key > today
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(9.dp))
                            .background(mood?.let { Color(it.color).copy(alpha = 0.75f) } ?: Color(0x0FFFFFFF))
                            .border(
                                if (key == selected) 2.dp else if (key == today) 1.dp else 0.dp,
                                if (key == selected) Ink.Text else if (key == today) Ink.VioletSoft else Color.Transparent,
                                RoundedCornerShape(9.dp),
                            )
                            .clickable(enabled = !future) { onSelect(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$d", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (future) Ink.Dim2.copy(alpha = 0.5f) else if (mood != null) Color(0xFF0B0C0E) else Ink.Dim)
                        if (entry != null && entry.notes.isNotEmpty()) {
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(4.dp).clip(CircleShape).background(if (mood != null) Color(0xAA0B0C0E) else Ink.VioletSoft))
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (m in MOODS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(m.color)))
                    Text(" ${counts[m.value]}", fontSize = 13.sp, color = Ink.Dim, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
