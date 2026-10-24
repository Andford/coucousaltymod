// Diario → Dinero: your week against its budget (weekdays and weekend apart),
// what Tokkip noticed about your money, the month's income and spending with
// its days and categories, the last six weeks, and every movement. "Anotar"
// opens a quick sheet; the tune button, your budget. All on the phone.

package mx.loperstack.mochi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.mochi.data.AppData
import mx.loperstack.mochi.data.Budget
import mx.loperstack.mochi.data.EXPENSE_CATEGORIES
import mx.loperstack.mochi.data.INCOME_CATEGORIES
import mx.loperstack.mochi.data.MONEY_NOTE_LEN
import mx.loperstack.mochi.data.MoneyCategory
import mx.loperstack.mochi.data.MoneyEntry
import mx.loperstack.mochi.data.MoneyKind
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.WeekSummary
import mx.loperstack.mochi.data.WeekendFrom
import mx.loperstack.mochi.data.categoryOf
import mx.loperstack.mochi.data.dayKey
import mx.loperstack.mochi.data.money
import mx.loperstack.mochi.data.moneyNotes
import mx.loperstack.mochi.data.monthSummary
import mx.loperstack.mochi.data.parseMoney
import mx.loperstack.mochi.data.recentWeeks
import mx.loperstack.mochi.data.weekSummary
import mx.loperstack.mochi.mochi.Haptics
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.mochi.Squircle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val MX = Locale("es", "MX")
private val SPEND = Color(0xFFF87171)
private val EARN = Color(0xFF4ADE80)

fun categoryIcon(key: String): ImageVector = when (key) {
    "food" -> Icons.Rounded.Restaurant
    "cart" -> Icons.Rounded.ShoppingCart
    "coffee" -> Icons.Rounded.LocalCafe
    "car" -> Icons.Rounded.DirectionsCar
    "home" -> Icons.Rounded.Home
    "party" -> Icons.Rounded.Celebration
    "bag" -> Icons.Rounded.ShoppingBag
    "sub" -> Icons.Rounded.Subscriptions
    "health" -> Icons.Rounded.MedicalServices
    "school" -> Icons.Rounded.School
    "gift" -> Icons.Rounded.CardGiftcard
    "work" -> Icons.Rounded.Work
    "store" -> Icons.Rounded.Storefront
    "spark" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.MoreHoriz
}

private fun weekLabel(w: WeekSummary): String {
    val f = SimpleDateFormat("d MMM", MX)
    return "Semana del ${f.format(Date(w.start))} al ${f.format(Date(w.end - 1))}"
}

/** How the week is going, as a colour: calm, careful, over. */
private fun paceColor(w: WeekSummary): Color = when {
    !w.hasBudget -> Ink.VioletSoft
    w.spent > w.budget.weekly -> SPEND
    w.used - w.elapsed >= 0.15f -> Color(0xFFFBBF24)
    else -> EARN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(data: AppData, color: Color, actions: Actions) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    var editing by remember { mutableStateOf<MoneyEntry?>(null) }
    var budgeting by remember { mutableStateOf(false) }
    val w = remember(data.money, data.budget, now / 60_000) { weekSummary(data.money, data.budget, now) }
    val month = remember(data.money, now / 3_600_000) { monthSummary(data.money, now) }
    val weeks = remember(data.money, data.budget, now / 3_600_000) { recentWeeks(data.money, data.budget, now) }
    val notes = remember(data.money, data.budget, data.logs, now / 3_600_000) {
        moneyNotes(data.money, data.budget, data.logs.filter { it.averageMood > 0 }.associate { it.day to it.averageMood }, now).sortedByDescending { it.weight }.take(2)
    }
    val recent = remember(data.money) { data.money.sortedByDescending { it.at }.take(60).groupBy { dayKey(it.at) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ScreenTitle("Dinero")
                        Label(weekLabel(w))
                    }
                    CircleButton(Icons.Rounded.Tune, "Presupuesto") { budgeting = true }
                }
                Spacer(Modifier.height(14.dp))
                if (data.money.isEmpty() && !w.hasBudget) EmptyMoney(color, start = { budgeting = true }, add = { editing = blankEntry(now) })
                else WeekCard(w)
            }
            if (notes.isNotEmpty()) item {
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Label("TOKKIP NOTÓ", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    for ((i, n) in notes.withIndex()) {
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(34.dp).clip(Squircle(12.dp)).background(Ink.VioletDeep), contentAlignment = Alignment.Center) {
                                Icon(noteIcon(n.icon), null, tint = Ink.OnVioletDeep, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                                Text(n.tip, fontSize = 13.sp, color = Ink.Dim, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
            if (data.money.isNotEmpty()) item {
                Spacer(Modifier.height(12.dp))
                MonthCard(month, now)
                Spacer(Modifier.height(12.dp))
                WeeksCard(weeks, data.budget)
                if (month.byCategory.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    CategoriesCard(month.byCategory, month.spent, data.budget, w)
                }
                Spacer(Modifier.height(20.dp))
                Text("Movimientos", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.height(8.dp))
            }
            items(recent.entries.toList(), key = { it.key }) { (day, list) ->
                DayGroup(day, list) { editing = it }
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { Haptics.click(context); editing = blankEntry(System.currentTimeMillis()) },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Anotar", fontWeight = FontWeight.Bold) },
            containerColor = Ink.VioletDeep, contentColor = Ink.Text, shape = Squircle(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    editing?.let { e ->
        ModalBottomSheet(
            onDismissRequest = { editing = null }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { EntryEditor(e, isNew = data.money.none { it.id == e.id }, actions) { editing = null } }
    }
    if (budgeting) {
        ModalBottomSheet(
            onDismissRequest = { budgeting = false }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { BudgetEditor(data.budget) { budgeting = false } }
    }
}

private fun blankEntry(now: Long) = MoneyEntry(UUID.randomUUID().toString().take(12), now, 0)

@Composable
private fun EmptyMoney(color: Color, start: () -> Unit, add: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Mochi(Modifier.width(130.dp).height(108.dp), color = color, interactive = true, greet = true)
        }
        Text("Lleva tus cuentas con Tokkip", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(
            "Ponle presupuesto a tu semana y a tu fin de semana, anota lo que gastas y lo que te entra, y Tokkip te dirá cómo vas. Todo se queda en tu teléfono.",
            fontSize = 14.sp, color = Ink.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            ExpressiveButton("Mi presupuesto", icon = Icons.Rounded.Tune, onClick = start)
            ExpressiveButton("Anotar", icon = Icons.Rounded.Add, container = Ink.CardHigh, content = Ink.Text, onClick = add)
        }
    }
}

/** A ring: the week's budget used, with a tick where the week is. */
@Composable
private fun BudgetRing(w: WeekSummary, modifier: Modifier) {
    val track = Ink.Track
    val tick = Ink.Text
    val fill = paceColor(w)
    Canvas(modifier) {
        val sw = size.minDimension * 0.12f
        val inset = sw / 2
        val arc = Size(size.width - sw, size.height - sw)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
        val used = w.used.coerceIn(0f, 1f)
        if (used > 0f) drawArc(fill, -90f, 360f * used, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        // Where the week is.
        val a = Math.toRadians((-90 + 360 * w.elapsed).toDouble())
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val o1 = Offset(c.x + ((r - sw * 1.4f) * Math.cos(a)).toFloat(), c.y + ((r - sw * 1.4f) * Math.sin(a)).toFloat())
        val o2 = Offset(c.x + ((r + sw * 0.2f) * Math.cos(a)).toFloat(), c.y + ((r + sw * 0.2f) * Math.sin(a)).toFloat())
        drawLine(tick, o1, o2, strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

@Composable
private fun WeekCard(w: WeekSummary) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                BudgetRing(w, Modifier.size(112.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (w.hasBudget) "${(w.used * 100).toInt()} %" else "—", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Label("usado", Ink.Dim2, 11.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (w.hasBudget) {
                    Label(if (w.left >= 0) "TE QUEDAN" else "TE PASASTE POR", paceColor(w), 11.sp, FontWeight.Bold)
                    Text(money(kotlin.math.abs(w.left)), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Label("de ${money(w.budget.weekly)} · gastaste ${money(w.spent)}", Ink.Dim2, 12.sp)
                    if (w.left > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("Unos ${money(w.perDay)} por día", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink.VioletSoft)
                    }
                } else {
                    Label("GASTASTE ESTA SEMANA", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                    Text(money(w.spent), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Label("Ponle presupuesto con el botón de arriba", Ink.Dim2, 12.sp)
                }
            }
        }
        if (w.hasBudget) {
            Spacer(Modifier.height(16.dp))
            SplitBar("Entre semana", w.weekdaySpent, w.weekdayBudget, w.weekdayUsed)
            if (w.budget.weekend > 0) {
                Spacer(Modifier.height(10.dp))
                SplitBar("Fin de semana · desde ${w.budget.weekendFrom.label.lowercase(MX)}", w.weekendSpent, w.budget.weekend, w.weekendUsed)
            }
        }
        if (w.income > 0) {
            Spacer(Modifier.height(10.dp))
            Label("Te entraron ${money(w.income)} esta semana", EARN, 12.sp, FontWeight.Bold)
        }
    }
}

@Composable
private fun SplitBar(title: String, spent: Long, budget: Long, used: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${money(spent)} de ${money(budget)}", fontSize = 12.sp, color = if (used > 1f) SPEND else Ink.Dim)
    }
    Spacer(Modifier.height(5.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Ink.Track)) {
        if (used > 0f) Box(
            Modifier.fillMaxWidth(used.coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape)
                .background(if (used > 1f) SPEND else if (used > 0.85f) Color(0xFFFBBF24) else Ink.VioletSoft),
        )
    }
}

@Composable
private fun MonthCard(m: mx.loperstack.mochi.data.MonthSummary, now: Long) {
    val name = SimpleDateFormat("MMMM", MX).format(Date(m.start)).replaceFirstChar { it.uppercase(MX) }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            m.saved?.let { s ->
                Text(
                    if (s >= 0) "Guardas ${(s * 100).toInt()} %" else "En rojo",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s >= 0) EARN else SPEND,
                    modifier = Modifier.clip(CircleShape).background((if (s >= 0) EARN else SPEND).copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Figure("Ingresos", money(m.income), EARN, Modifier.weight(1f))
            Figure("Gastos", money(m.spent), SPEND, Modifier.weight(1f))
            Figure("Balance", money(m.balance), if (m.balance >= 0) Ink.Text else SPEND, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Label("Gasto por día", Ink.Dim2, 12.sp)
        Spacer(Modifier.height(6.dp))
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val todayIdx = if (Calendar.getInstance().apply { timeInMillis = m.start }.get(Calendar.MONTH) == today.get(Calendar.MONTH)) today.get(Calendar.DAY_OF_MONTH) - 1 else -1
        val bar = Ink.VioletSoft
        val dim = Ink.Track
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val n = m.daily.size
            val top = (m.daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
            val step = size.width / n
            for ((i, v) in m.daily.withIndex()) {
                val h = if (v == 0L) 3f else (size.height - 4f) * v / top + 3f
                drawRoundRect(
                    if (i > todayIdx && todayIdx >= 0) dim.copy(alpha = 0.5f) else if (v == 0L) dim else bar,
                    Offset(i * step + step * 0.18f, size.height - h), Size(step * 0.64f, h), CornerRadius(step * 0.3f),
                )
            }
        }
        if (m.incomeBy.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("Entradas: " + m.incomeBy.joinToString(" · ") { "${categoryOf(it.first).label} ${money(it.second)}" }, Ink.Dim2, 12.sp)
        }
    }
}

@Composable
private fun Figure(title: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(Squircle(16.dp)).background(Ink.CardHigh).padding(12.dp)) {
        Label(title, Ink.Dim2, 11.sp, FontWeight.Bold)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
    }
}

/** The last six weeks' spending, with the budget as a dashed line. */
@Composable
private fun WeeksCard(weeks: List<Pair<Long, Long>>, b: Budget) {
    val f = SimpleDateFormat("d MMM", MX)
    Panel(Modifier.fillMaxWidth()) {
        Text("Tus últimas semanas", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        val bar = Ink.VioletSoft
        val over = SPEND
        val line = Ink.Dim
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val top = maxOf(weeks.maxOf { it.second }, b.weekly).coerceAtLeast(1L) * 1.1f
            val step = size.width / weeks.size
            for ((i, wk) in weeks.withIndex()) {
                val h = (size.height * wk.second / top).coerceAtLeast(3f)
                drawRoundRect(
                    if (b.weekly > 0 && wk.second > b.weekly) over else bar.copy(alpha = if (i == weeks.lastIndex) 1f else 0.6f),
                    Offset(i * step + step * 0.2f, size.height - h), Size(step * 0.6f, h), CornerRadius(10f),
                )
            }
            if (b.weekly > 0) {
                val y = size.height - size.height * b.weekly / top
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for ((s, v) in weeks) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(money(v), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(f.format(Date(s)), fontSize = 10.sp, color = Ink.Dim2, maxLines = 1)
            }
        }
        if (b.weekly > 0) Label("La línea es tu presupuesto semanal (${money(b.weekly)})", Ink.Dim2, 11.sp)
    }
}

@Composable
private fun CategoriesCard(by: List<Pair<String, Long>>, total: Long, b: Budget, w: WeekSummary) {
    Panel(Modifier.fillMaxWidth()) {
        Text("En qué se va este mes", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        for ((id, cents) in by.take(8)) {
            val c = categoryOf(id)
            val limit = b.limits[id]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(c, 34)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(money(cents), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Ink.Track)) {
                        Box(Modifier.fillMaxWidth((cents.toFloat() / total.coerceAtLeast(1)).coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(Color(c.color)))
                    }
                    if (limit != null) {
                        val wk = w.byCategory[id] ?: 0
                        Label("Esta semana ${money(wk)} de ${money(limit)}", if (wk > limit) SPEND else Ink.Dim2, 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(c: MoneyCategory, size: Int) {
    Box(Modifier.size(size.dp).clip(Squircle((size / 3).dp)).background(Color(c.color).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(c.icon), null, tint = Color(c.color), modifier = Modifier.size((size * 0.56f).dp))
    }
}

@Composable
private fun DayGroup(day: String, list: List<MoneyEntry>, open: (MoneyEntry) -> Unit) {
    val ms = mx.loperstack.mochi.data.dayStart(day)
    val today = dayKey(System.currentTimeMillis())
    val yesterday = dayKey(System.currentTimeMillis() - 86_400_000)
    val title = when (day) {
        today -> "Hoy"
        yesterday -> "Ayer"
        else -> SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(ms)).replaceFirstChar { it.uppercase(MX) }
    }
    val net = list.sumOf { if (it.kind == MoneyKind.GASTO) -it.cents else it.cents }
    Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink.Dim, modifier = Modifier.weight(1f))
            Text(money(net, sign = true), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (net >= 0) EARN else Ink.Dim)
        }
        for (e in list) {
            val c = categoryOf(e.category)
            Row(
                Modifier.fillMaxWidth().clip(Squircle(14.dp)).clickable { open(e) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(c, 38)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.note.ifBlank { c.label }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Label((if (e.note.isBlank()) "" else c.label + " · ") + SimpleDateFormat("HH:mm", MX).format(Date(e.at)), Ink.Dim2, 12.sp)
                }
                Text(
                    if (e.kind == MoneyKind.GASTO) money(-e.cents) else money(e.cents, sign = true),
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (e.kind == MoneyKind.GASTO) Ink.Text else EARN,
                )
            }
        }
    }
}

// ── Anotar ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntryEditor(start: MoneyEntry, isNew: Boolean, actions: Actions, close: () -> Unit) {
    val context = LocalContext.current
    var e by remember { mutableStateOf(start) }
    var amount by remember { mutableStateOf(if (start.cents > 0) (start.cents / 100.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(Locale.US, it) } else "") }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isNew) runCatching { focus.requestFocus() } }
    val cats = if (e.kind == MoneyKind.GASTO) EXPENSE_CATEGORIES else INCOME_CATEGORIES
    val cents = parseMoney(amount)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        ScreenTitle(if (isNew) "Anotar" else "Editar", size = 30.sp)
        Spacer(Modifier.height(12.dp))
        PillRow {
            Pill("Gasto", e.kind == MoneyKind.GASTO) { e = e.copy(kind = MoneyKind.GASTO, category = "otros") }
            Pill("Ingreso", e.kind == MoneyKind.INGRESO) { e = e.copy(kind = MoneyKind.INGRESO, category = "sueldo") }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
            singleLine = true, placeholder = { Text("0", color = Ink.Dim2, fontSize = 34.sp) },
            prefix = { Text("$", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = if (e.kind == MoneyKind.GASTO) Ink.Text else EARN),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = Squircle(20.dp), modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        SectionTitle(if (e.kind == MoneyKind.GASTO) "¿En qué?" else "¿De dónde?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in cats) {
                val on = e.category == c.id
                Row(
                    Modifier.clip(Squircle(14.dp)).background(if (on) Color(c.color).copy(alpha = 0.28f) else Ink.Card)
                        .clickable { Haptics.tick(context); e = e.copy(category = c.id) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(categoryIcon(c.icon), null, tint = Color(c.color), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(c.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        SectionTitle("Nota")
        OutlinedTextField(
            value = e.note, onValueChange = { e = e.copy(note = it.take(MONEY_NOTE_LEN)) }, singleLine = true,
            placeholder = { Text(if (e.kind == MoneyKind.GASTO) "Ej. Tacos con los cuates" else "Ej. Pago de la página web", color = Ink.Dim2) },
            shape = Squircle(18.dp), modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle("Cuándo")
        val todayKey = dayKey(System.currentTimeMillis())
        val yesterdayKey = dayKey(System.currentTimeMillis() - 86_400_000)
        val k = dayKey(e.at)
        PillRow {
            Pill("Hoy", k == todayKey) { e = e.copy(at = onDay(e.at, System.currentTimeMillis())) }
            Pill("Ayer", k == yesterdayKey) { e = e.copy(at = onDay(e.at, System.currentTimeMillis() - 86_400_000)) }
            Pill(if (k != todayKey && k != yesterdayKey) SimpleDateFormat("d MMM", MX).format(Date(e.at)) else "Otro día", k != todayKey && k != yesterdayKey) { picking = true }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpressiveButton(if (isNew) "Guardar" else "Guardar cambios", big = true, modifier = Modifier.weight(1f)) {
                val c = cents ?: run { Haptics.thud(context); return@ExpressiveButton }
                val saved = e.copy(cents = c)
                Store.setMoney(Store.state.value.money.filter { it.id != saved.id } + saved)
                if (isNew) { Sounds.play(if (saved.kind == MoneyKind.INGRESO) "approve" else "pop"); actions.jump++ }
                Haptics.success(context)
                close()
            }
            if (!isNew) CircleButton(Icons.Rounded.DeleteOutline, "Borrar") {
                Store.setMoney(Store.state.value.money.filter { it.id != e.id })
                Sounds.play("blip")
                close()
            }
        }
        if (amount.isNotEmpty() && cents == null) Label("Escribe una cantidad, por ejemplo 150 o 89.50", SPEND, 12.sp)
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcNoonOf(e.at))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc -> e = e.copy(at = onDay(e.at, localNoonOfUtc(utc))) }
                    picking = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

/** `time`'s hour and minute on `day`'s date. */
private fun onDay(time: Long, day: Long): Long {
    val t = Calendar.getInstance().apply { timeInMillis = time }
    return Calendar.getInstance().apply {
        timeInMillis = day
        set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, t.get(Calendar.MINUTE))
    }.timeInMillis
}

/** The date picker speaks UTC midnights. */
private fun utcNoonOf(local: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 12, 0)
    }.timeInMillis
}

private fun localNoonOfUtc(utc: Long): Long {
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0)
    }.timeInMillis
}

// ── Presupuesto ───────────────────────────────────────────────────────────────

private fun pesos(cents: Long) = if (cents <= 0) "" else (cents / 100).toString()

@Composable
private fun MoneyField(value: String, set: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { set(it.filter(Char::isDigit).take(9)) }, singleLine = true,
        prefix = { Text("$") }, placeholder = { Text(placeholder, color = Ink.Dim2) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = Squircle(18.dp), modifier = modifier,
    )
}

@Composable
private fun BudgetEditor(start: Budget, close: () -> Unit) {
    val context = LocalContext.current
    var weekly by remember { mutableStateOf(pesos(start.weekly)) }
    var weekend by remember { mutableStateOf(pesos(start.weekend)) }
    var weekStart by remember { mutableStateOf(start.weekStart) }
    var from by remember { mutableStateOf(start.weekendFrom) }
    var goal by remember { mutableStateOf(start.saveGoal.toFloat()) }
    var limits by remember { mutableStateOf(start.limits.mapValues { pesos(it.value) }) }
    val w = (weekly.toLongOrNull() ?: 0) * 100
    val we = (weekend.toLongOrNull() ?: 0) * 100
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        ScreenTitle("Tu presupuesto", size = 30.sp)
        Spacer(Modifier.height(4.dp))
        Label("Lo que quieres gastar a la semana. El de fin de semana es una parte del semanal, apartada para esos días.", Ink.Dim2, 12.sp)
        SectionTitle("Por semana")
        MoneyField(weekly, { weekly = it }, "Ej. 2500", Modifier.fillMaxWidth())
        SectionTitle("Para el fin de semana")
        MoneyField(weekend, { weekend = it }, "Ej. 900", Modifier.fillMaxWidth())
        if (w > 0) Label(
            if (we > w) "El de fin de semana no puede ser mayor que el semanal"
            else "Entre semana te quedan ${money(w - we)}" + if (we > 0) " y ${money(we)} para el fin" else "",
            if (we > w) SPEND else Ink.Dim2, 12.sp,
        )
        SectionTitle("El fin de semana empieza")
        PillRow { for (f in WeekendFrom.entries) Pill(f.label, from == f) { from = f } }
        SectionTitle("La semana empieza en")
        PillRow {
            Pill("Lunes", weekStart == Calendar.MONDAY) { weekStart = Calendar.MONDAY }
            Pill("Domingo", weekStart == Calendar.SUNDAY) { weekStart = Calendar.SUNDAY }
        }
        SectionTitle("Meta de ahorro del mes")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(value = goal, onValueChange = { goal = (Math.round(it / 5f) * 5).toFloat() }, valueRange = 0f..50f, modifier = Modifier.weight(1f))
            Text(if (goal <= 0f) "Sin meta" else "${goal.toInt()} %", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        Label("Qué parte de lo que te entra quieres guardar. Tokkip te dirá cómo vas.", Ink.Dim2, 12.sp)
        SectionTitle("Límites por semana (opcional)")
        for (c in EXPENSE_CATEGORIES) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(c, 32)
                Spacer(Modifier.width(10.dp))
                Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                MoneyField(limits[c.id] ?: "", { v -> limits = limits + (c.id to v) }, "Sin límite", Modifier.width(140.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        ExpressiveButton("Guardar", big = true, modifier = Modifier.fillMaxWidth()) {
            if (we > w && w > 0) { Haptics.thud(context); return@ExpressiveButton }
            Store.setBudget(
                Budget(
                    weekly = w, weekend = if (w > 0) we else 0, weekStart = weekStart, weekendFrom = from,
                    limits = limits.mapValues { (it.value.toLongOrNull() ?: 0) * 100 }.filterValues { it > 0 },
                    saveGoal = goal.toInt(),
                ),
            )
            Haptics.success(context)
            Sounds.play("approve")
            close()
        }
    }
}

// ── Inicio ────────────────────────────────────────────────────────────────────

/** Inicio's money line: what's left this week and per day. Hidden until you use Dinero. */
@Composable
fun MoneyHomeCard(now: Long, open: () -> Unit) {
    val d by Store.state.collectAsState()
    if (d.money.isEmpty() && d.budget.weekly <= 0) return
    val w = remember(d.money, d.budget, now / 60_000) { weekSummary(d.money, d.budget, now) }
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth().clickable(onClick = open), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                BudgetRing(w, Modifier.size(54.dp))
                Icon(Icons.Rounded.Savings, null, tint = Ink.Text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label("DINERO ESTA SEMANA", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                if (w.hasBudget) {
                    Text(if (w.left >= 0) "Te quedan ${money(w.left)}" else "Te pasaste por ${money(-w.left)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Label(if (w.left > 0) "Unos ${money(w.perDay)} por día · gastaste ${money(w.spent)}" else "Gastaste ${money(w.spent)} de ${money(w.budget.weekly)}", Ink.Dim2, 12.sp)
                } else {
                    Text("Gastaste ${money(w.spent)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Label("Ponle presupuesto a tu semana en Dinero", Ink.Dim2, 12.sp)
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Abrir Dinero", tint = Ink.Dim)
        }
    }
}
