// Rachas: your habits, each on the days you choose and, if you like, at a
// time with an encouraging reminder. Plus Ajustes → Notificaciones, where all
// of Mochi's notifications are set.

package mx.loperstack.mochi.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import mx.loperstack.mochi.data.DayMark
import mx.loperstack.mochi.data.HABIT_COLORS
import mx.loperstack.mochi.data.HABIT_EMOJIS
import mx.loperstack.mochi.data.Habit
import mx.loperstack.mochi.data.NotifySettings
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.WEEK
import mx.loperstack.mochi.data.best
import mx.loperstack.mochi.data.doneOn
import mx.loperstack.mochi.data.hhmm
import mx.loperstack.mochi.data.scheduleText
import mx.loperstack.mochi.data.scheduledOn
import mx.loperstack.mochi.data.streak
import mx.loperstack.mochi.data.toggle
import mx.loperstack.mochi.data.week
import mx.loperstack.mochi.mochi.Haptics
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.wellness.MochiNotifier
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksScreen(habits: List<Habit>, color: Color, actions: Actions) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    var editing by remember { mutableStateOf<Habit?>(null) }
    val cal = remember(now / 3_600_000) { Calendar.getInstance() }
    val today = habits.filter { it.scheduledOn(cal) }
    val doneToday = today.count { it.doneOn(now) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Rachas", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Label(
                            when {
                                habits.isEmpty() -> "Hábitos pequeños, cambios grandes"
                                today.isEmpty() -> "Hoy es día libre ☁️"
                                doneToday == today.size -> "¡Todo listo hoy! 🔥"
                                else -> "$doneToday de ${today.size} hoy"
                            },
                        )
                    }
                    Mochi(
                        Modifier.width(96.dp).height(80.dp), color = color,
                        mood = if (today.isNotEmpty() && doneToday == today.size) Mood.HAPPY else Mood.IDLE,
                        jump = actions.jump, interactive = true,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (habits.isEmpty()) EmptyStreaks { editing = blankHabit() }
            }
            items(habits, key = { it.id }) { h ->
                HabitCard(h, now, edit = { editing = h }) {
                    val wasDone = h.doneOn(now)
                    Store.setHabits(Store.state.value.habits.map { if (it.id == h.id) it.toggle(now) else it })
                    if (!wasDone) {
                        Haptics.success(context); Sounds.play("pop"); actions.jump++
                    } else Haptics.tick(context)
                }
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { Haptics.click(context); editing = blankHabit() },
            icon = { androidx.compose.material3.Icon(Glyph.Plus, contentDescription = null) },
            text = { Text("Nueva racha", fontWeight = FontWeight.Bold) },
            containerColor = Ink.VioletDeep, contentColor = Ink.Text,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    editing?.let { h ->
        ModalBottomSheet(
            onDismissRequest = { editing = null }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            HabitEditor(h, isNew = habits.none { it.id == h.id }, close = { editing = null })
        }
    }
}

private fun blankHabit() = Habit(UUID.randomUUID().toString(), "", createdAt = System.currentTimeMillis())

@Composable
private fun EmptyStreaks(create: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink.Card).clickable(onClick = create).padding(22.dp),
    ) {
        Text("🔥", fontSize = 34.sp)
        Spacer(Modifier.height(6.dp))
        Text("Empieza tu primera racha", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Label("Tomar agua, leer, caminar, meditar… Elige los días y, si quieres, una hora para que Mochi te lo recuerde con ánimo.", Ink.Dim2)
    }
}

@Composable
private fun HabitCard(h: Habit, now: Long, edit: () -> Unit, tick: () -> Unit) {
    val accent = Color(h.color)
    val done = h.doneOn(now)
    val scheduled = h.scheduledOn(Calendar.getInstance().apply { timeInMillis = now })
    val streak = h.streak(now)
    val pop by animateFloatAsState(if (done) 1f else 0.9f, spring(dampingRatio = 0.4f, stiffness = 400f), label = "pop")
    val ring by animateColorAsState(if (done) accent else Ink.Hairline, label = "ring")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Ink.Card)
            .border(1.dp, if (done) accent.copy(alpha = 0.5f) else Ink.Hairline, RoundedCornerShape(26.dp))
            .clickable(onClick = edit).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Text(h.emoji, fontSize = 24.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(h.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Label(h.scheduleText() + if (h.time != null && h.remind) " · 🔔" else "", Ink.Dim2, 12.sp)
            }
            Box(
                Modifier.size(46.dp).scale(pop).clip(CircleShape).background(if (done) accent else Color.Transparent)
                    .border(2.dp, ring, CircleShape).clickable(enabled = scheduled, onClick = tick),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (done) "✓" else if (scheduled) "" else "–", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (done) Ink.Ground else Ink.Dim2)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (streak > 0) "🔥 $streak" else "🔥 0", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (streak > 0) Color(0xFFFB923C) else Ink.Dim2)
            Label(if (streak == 1) " día" else " días", Ink.Dim2, 12.sp)
            Label("   ·   mejor ${h.best(now)}", Ink.Dim2, 12.sp)
            Spacer(Modifier.weight(1f))
            // The last seven days.
            val marks = h.week(now)
            val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -6) }
            for (m in marks) {
                val letter = WEEK.first { it.first == c.get(Calendar.DAY_OF_WEEK) }.second
                c.add(Calendar.DAY_OF_YEAR, 1)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 4.dp)) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            when (m) {
                                DayMark.DONE -> accent
                                DayMark.MISSED -> Ink.Hairline
                                DayMark.TODAY -> Color.Transparent
                                DayMark.OFF -> Color.Transparent
                            },
                        ).border(
                            1.5.dp, when (m) { DayMark.TODAY -> accent; DayMark.OFF -> Ink.Hairline.copy(alpha = 0.4f); else -> Color.Transparent }, CircleShape,
                        ),
                    )
                    Text(letter, fontSize = 9.sp, color = Ink.Dim2)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditor(start: Habit, isNew: Boolean, close: () -> Unit) {
    val context = LocalContext.current
    var h by remember { mutableStateOf(start) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text(if (isNew) "Nueva racha" else "Editar racha", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = h.name, onValueChange = { h = h.copy(name = it.take(60)) }, singleLine = true,
            placeholder = { Text("Ej. Tomar 2 L de agua", color = Ink.Dim2) },
            leadingIcon = { Text(h.emoji, fontSize = 22.sp) },
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle("Ícono")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (e in HABIT_EMOJIS) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (e == h.emoji) Color(h.color).copy(alpha = 0.35f) else Ink.Card)
                        .clickable { Haptics.tick(context); h = h.copy(emoji = e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 20.sp) }
            }
        }
        SectionTitle("Color")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in HABIT_COLORS) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(3.dp, if (c == h.color) Ink.Text else Color.Transparent, CircleShape)
                        .clickable { Haptics.tick(context); h = h.copy(color = c) },
                )
            }
        }
        SectionTitle("Días")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((day, letter) in WEEK) {
                val on = h.days.isEmpty() || day in h.days
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (on) Color(h.color) else Ink.Card)
                        .clickable {
                            Haptics.tick(context)
                            val all = WEEK.map { it.first }.toSet()
                            val cur = if (h.days.isEmpty()) all else h.days
                            val next = if (day in cur) cur - day else cur + day
                            h = h.copy(days = if (next.size == 7 || next.isEmpty()) emptySet() else next)
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(letter, fontWeight = FontWeight.Bold, color = if (on) Ink.Ground else Ink.Dim) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Label(if (h.days.isEmpty()) "Todos los días" else "Solo los días marcados; los demás no rompen la racha.", Ink.Dim2, 12.sp)
        SectionTitle("Hora (opcional)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (h.time == null) "A cualquier hora" else hhmm(h.time!!), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Label(if (h.time == null) "Sin hora fija" else "Toca para cambiarla", Ink.Dim2, 12.sp)
            }
            Switch(
                checked = h.time != null,
                onCheckedChange = { on ->
                    if (on) TimePickerDialog(context, { _, hh, mm -> h = h.copy(time = hh * 60 + mm) }, 8, 0, true).show()
                    else h = h.copy(time = null)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet),
            )
        }
        if (h.time != null) {
            Text(
                "Cambiar hora", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp).clickable {
                    val t = h.time ?: 480
                    TimePickerDialog(context, { _, hh, mm -> h = h.copy(time = hh * 60 + mm) }, t / 60, t % 60, true).show()
                },
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow("Recordatorio", "Mochi te avisa a esa hora con ánimo, si aún no lo marcas", h.remind) { h = h.copy(remind = it) }
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton(if (isNew) "Crear racha" else "Guardar", Modifier.fillMaxWidth(), Color(h.color), Ink.Ground) {
            if (h.name.isBlank()) return@PrimaryButton
            val list = Store.state.value.habits
            Store.setHabits(if (isNew) list + h.copy(name = h.name.trim()) else list.map { if (it.id == h.id) h.copy(name = h.name.trim()) else it })
            Haptics.success(context)
            close()
        }
        if (!isNew) {
            Text(
                "Borrar racha", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp).clickable {
                    Store.setHabits(Store.state.value.habits.filter { it.id != h.id })
                    Haptics.thud(context)
                    close()
                },
            )
        }
    }
}

// ── Ajustes → Notificaciones ──────────────────────────────────────────────────

@Composable
fun NotificationSettings(n: NotifySettings) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    fun save(x: NotifySettings) = Store.setNotify(x)
    fun pick(start: Int, set: (Int) -> Unit) =
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, start / 60, start % 60, true).show()

    if (!granted) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.VioletDeep)
                .clickable { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔔", fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Permitir notificaciones", fontWeight = FontWeight.Bold)
                Label("Para que Mochi te escriba", Ink.Dim)
            }
            Text("Permitir", color = Ink.Text, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
    }

    @Composable
    fun Item(emoji: String, title: String, why: String, on: Boolean, time: String?, kind: String, set: (Boolean) -> Unit, editTime: (() -> Unit)?) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(Ink.Card).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Label(why, Ink.Dim2, 12.sp)
                }
                Switch(checked = on, onCheckedChange = set, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
            }
            if (on) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (time != null && editTime != null) {
                        Text(
                            time, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink.Text,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.CardHigh).clickable(onClick = editTime)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Probar", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                            Haptics.click(context)
                            if (!granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else MochiNotifier.preview(context, kind)
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    Item("☀️", "Buenos días", "Una frase para empezar, según cómo dormiste", n.morning, hhmm(n.morningAt), "morning", { save(n.copy(morning = it)) }) {
        pick(n.morningAt) { save(n.copy(morningAt = it)) }
    }
    Item("📅", "Eventos", "Un aviso antes de cada evento de tu calendario", n.events, null, "event", { save(n.copy(events = it)) }, null)
    if (n.events) {
        Row(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in listOf(5, 10, 15, 30)) Segment("$m min antes", n.eventLead == m) { save(n.copy(eventLead = m)) }
        }
    }
    Item("✅", "Tareas pendientes", "Un empujoncito si te quedan tareas", n.tasks, hhmm(n.tasksAt), "tasks", { save(n.copy(tasks = it)) }) {
        pick(n.tasksAt) { save(n.copy(tasksAt = it)) }
    }
    Item("🌙", "Buenas noches", "Una pregunta para reflexionar y escribir en tu diario", n.night, hhmm(n.nightAt), "night", { save(n.copy(night = it)) }) {
        pick(n.nightAt) { save(n.copy(nightAt = it)) }
    }
    Item("🔥", "Rachas", "Ánimo a la hora de cada racha que aún no marcas", n.streaks, null, "habit", { save(n.copy(streaks = it)) }, null)
}
