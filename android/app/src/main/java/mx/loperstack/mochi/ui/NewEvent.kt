// "Nuevo evento": a title, a day and a time, how long, a reminder and the
// calendar — saved straight into your Google calendar on this phone.

package mx.loperstack.mochi.ui

import mx.loperstack.mochi.mochi.Squircle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.mochi.data.CalendarWrite
import mx.loperstack.mochi.data.WritableCalendar
import mx.loperstack.mochi.mochi.Sounds
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")

/** The next whole half hour. */
private fun nextSlot(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    val m = get(Calendar.MINUTE)
    add(Calendar.MINUTE, if (m < 30) 30 - m else 60 - m)
}.timeInMillis

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = if (on) Ink.Ground else Ink.Text,
        modifier = Modifier.clip(Squircle(16.dp)).background(if (on) Ink.VioletSoft else Ink.Card)
            .border(1.dp, if (on) Ink.VioletSoft else Ink.Hairline, Squircle(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/**
 * @param onSaved told after the event is in the calendar (reload the agenda).
 */
@Composable
fun NewEventSheet(actions: Actions, onSaved: () -> Unit) {
    val context = LocalContext.current
    val calendars = remember { CalendarWrite.calendars(context) }
    var title by remember { mutableStateOf("") }
    var begin by remember { mutableLongStateOf(nextSlot(System.currentTimeMillis())) }
    var minutes by remember { mutableIntStateOf(60) }
    var allDay by remember { mutableStateOf(false) }
    var reminder by remember { mutableIntStateOf(10) }
    var calIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val cal: WritableCalendar? = calendars.getOrNull(calIndex)

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Nuevo evento", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(200) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("¿Qué es?", color = Ink.Dim2) }, singleLine = true, shape = Squircle(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
            ),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(SimpleDateFormat("EEE d MMM", MX).format(Date(begin)).replaceFirstChar { it.uppercase(MX) }, false) {
                val c = Calendar.getInstance().apply { timeInMillis = begin }
                DatePickerDialog(context, { _, y, m, d ->
                    begin = Calendar.getInstance().apply { timeInMillis = begin; set(y, m, d) }.timeInMillis
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
            if (!allDay) {
                Pill(SimpleDateFormat("HH:mm", MX).format(Date(begin)), false) {
                    val c = Calendar.getInstance().apply { timeInMillis = begin }
                    TimePickerDialog(context, { _, h, m ->
                        begin = Calendar.getInstance().apply { timeInMillis = begin; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.timeInMillis
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                }
            }
            Spacer(Modifier.weight(1f))
            Label("Todo el día", Ink.Dim)
            Switch(checked = allDay, onCheckedChange = { allDay = it }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
        }
        if (!allDay) {
            Spacer(Modifier.height(14.dp))
            Label("Duración", Ink.Dim2, 12.sp, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((m, label) in listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 90 to "1 h 30", 120 to "2 h")) {
                    Pill(label, minutes == m) { minutes = m }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Label("Aviso", Ink.Dim2, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((m, label) in listOf(-1 to "Sin aviso", 0 to "Al empezar", 10 to "10 min antes", 30 to "30 min")) {
                Pill(label, reminder == m) { reminder = m }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (cal != null) {
            Row(
                Modifier.fillMaxWidth().clip(Squircle(16.dp)).background(Ink.Card)
                    .clickable(enabled = calendars.size > 1) { calIndex = (calIndex + 1) % calendars.size }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cal.color?.let { Color(it) } ?: Ink.Violet))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(cal.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Label(if (cal.google) "${cal.account} · Google Calendar" else cal.account, Ink.Dim2, 12.sp)
                }
                if (calendars.size > 1) Label("Cambiar", Ink.VioletSoft)
            }
        } else {
            Label("No hay calendarios donde escribir en este teléfono.", Ink.Red)
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Label(error, Ink.Red)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Guardar en el calendario", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.Ground) {
            val c = cal ?: return@PrimaryButton
            if (title.isBlank()) { error = "Ponle un nombre."; return@PrimaryButton }
            val id = CalendarWrite.insert(context, c.id, title, begin, begin + minutes * 60_000L, allDay, reminder)
            if (id == null) {
                error = "El calendario no aceptó el evento."
            } else {
                actions.jump++
                Sounds.play("approve")
                onSaved()
            }
        }
    }
}
