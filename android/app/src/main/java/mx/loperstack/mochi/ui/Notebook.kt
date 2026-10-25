// Cuaderno: your notes and projects, with Tokkip. Made for a head that jumps
// around: one field at the top to get a thought out of your head (it waits
// in the Bandeja), what you left half-done right below with its next step to
// tick, and projects as little boxes with their own colour, shape and icon.
// A note is a list of lines — text, headings, bullets and steps to tick —
// and a step can go to Tareas. The editor fills the screen, without the
// bottom bar, so there's only the note in front of you.
//
// Only titles and previews live in memory (data/NotebookDb.kt); a note's
// text is read when you open it and saved a moment after you stop typing.

package mx.loperstack.mochi.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.loperstack.mochi.data.Block
import mx.loperstack.mochi.data.BlockKind
import mx.loperstack.mochi.data.HABIT_COLORS
import mx.loperstack.mochi.data.NoteHead
import mx.loperstack.mochi.data.Notebook
import mx.loperstack.mochi.data.NotebookIndex
import mx.loperstack.mochi.data.Project
import mx.loperstack.mochi.data.blocksOf
import mx.loperstack.mochi.data.bodyOf
import mx.loperstack.mochi.data.fold
import mx.loperstack.mochi.data.halfDone
import mx.loperstack.mochi.data.progress
import mx.loperstack.mochi.mochi.Haptics
import mx.loperstack.mochi.mochi.MShapes
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.MochiEngine
import mx.loperstack.mochi.mochi.PolygonShape
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.mochi.Squircle

/** Where you are in the Cuaderno; kept while you visit other tabs, so you come back to the same note. */
object NotebookNav {
    var note by mutableStateOf<String?>(null)
    var project by mutableStateOf<String?>(null)
    var searching by mutableStateOf(false)
    /** The editor fills the screen: the bottom bar steps aside. */
    val editing get() = note != null
}

val PROJECT_ICONS: List<Pair<String, ImageVector>> = listOf(
    "idea" to Icons.Rounded.Lightbulb, "rocket" to Icons.Rounded.RocketLaunch, "art" to Icons.Rounded.Brush, "code" to Icons.Rounded.Code,
    "book" to Icons.Rounded.MenuBook, "study" to Icons.Rounded.School, "work" to Icons.Rounded.Work, "home" to Icons.Rounded.Home,
    "travel" to Icons.Rounded.Flight, "gym" to Icons.Rounded.FitnessCenter, "music" to Icons.Rounded.MusicNote, "photo" to Icons.Rounded.CameraAlt,
    "food" to Icons.Rounded.Restaurant, "shop" to Icons.Rounded.ShoppingCart, "money" to Icons.Rounded.Savings, "pet" to Icons.Rounded.Pets,
    "heart" to Icons.Rounded.Favorite, "spark" to Icons.Rounded.AutoAwesome,
)

fun projectIcon(key: String): ImageVector = PROJECT_ICONS.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Lightbulb

private fun shapeOf(i: Int) = MShapes.ALL[Math.floorMod(i, MShapes.ALL.size)]

private val INK_ON = Color.Black.copy(alpha = 0.72f)

// ── The screen ────────────────────────────────────────────────────────────────

@Composable
fun NotebookScreen(color: Color, actions: Actions) {
    val nb by Notebook.index.collectAsState()
    val nav = NotebookNav
    BackHandler(enabled = nav.note != null || nav.project != null || nav.searching) {
        when {
            nav.note != null -> nav.note = null
            nav.searching -> nav.searching = false
            else -> nav.project = null
        }
    }
    // A project or note that's gone (deleted elsewhere) closes itself.
    LaunchedEffect(nb, nav.note, nav.project) {
        if (nav.note != null && nb.notes.none { it.id == nav.note }) nav.note = null
        if (nav.project != null && nb.projects.none { it.id == nav.project }) nav.project = null
    }
    val view = when {
        nav.note != null -> "n:" + nav.note
        nav.searching -> "s"
        nav.project != null -> "p:" + nav.project
        else -> ""
    }
    AnimatedContent(view, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nb") { v ->
        when {
            v.startsWith("n:") -> NoteEditor(v.removePrefix("n:"), nb, actions)
            v == "s" -> SearchView(nb)
            v.startsWith("p:") -> nb.projects.firstOrNull { it.id == v.removePrefix("p:") }?.let { ProjectView(it, nb, color, actions) }
            else -> NotebookHome(nb, color, actions)
        }
    }
}

private fun openNote(id: String) {
    Notebook.opened(id)
    NotebookNav.note = id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookHome(nb: NotebookIndex, color: Color, actions: Actions) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    var newProject by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<NoteHead?>(null) }
    var showDone by remember { mutableStateOf(false) }
    val half = remember(nb, now / 600_000) { nb.halfDone(now, 5) }
    val active = nb.active
    val inbox = nb.inbox.sortedWith(compareByDescending<NoteHead> { it.pinned }.thenByDescending { it.updated })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ScreenTitle("Cuaderno")
                    Label(
                        when {
                            nb.notes.isEmpty() -> "Lo que traes en la cabeza, en un lugar"
                            half.isNotEmpty() -> "Retoma donde te quedaste"
                            else -> "${nb.notes.size} notas · ${active.size} proyectos"
                        },
                        Ink.Dim2, 13.sp,
                    )
                }
                CircleButton(Icons.Rounded.Search, "Buscar") { NotebookNav.searching = true }
            }
            Spacer(Modifier.height(14.dp))
            CaptureCard(color, hint = "¿Qué traes en la cabeza?", tokkip = true, inboxCount = nb.inbox.size) { text ->
                if (Notebook.capture(text) != null) { Sounds.play("pop"); Haptics.success(context); actions.jump++ }
            }
            Spacer(Modifier.height(10.dp))
            PillRow(Modifier.padding(horizontal = 2.dp)) {
                Pill("Nota", false, Icons.Rounded.EditNote) { openNote(Notebook.newNote(null)) }
                Pill("Proyecto", false, Icons.Rounded.Add) { newProject = true }
            }
        }
        if (half.isNotEmpty()) {
            item { SectionLabel("LO QUE DEJASTE A MEDIAS") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(half, key = { it.id }) { h -> HalfCard(h, Notebook.project(h.project), actions) }
                }
            }
        }
        item {
            SectionLabel("PROYECTOS")
            if (active.isEmpty()) {
                EmptyHint(Icons.Rounded.RocketLaunch, "Un proyecto es una cajita para algo que quieres sacar adelante: sus notas y sus pasos, juntos.") { newProject = true }
            } else {
                for (row in (active.map<Project, Project?> { it } + listOf(null)).chunked(2)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (p in row) {
                            if (p != null) ProjectTile(p, nb, Modifier.weight(1f)) { NotebookNav.project = p.id }
                            else NewProjectTile(Modifier.weight(1f)) { newProject = true }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SectionLabel(if (inbox.isEmpty()) "BANDEJA" else "BANDEJA · ${inbox.size}")
            if (inbox.isEmpty()) Label("Vacía. Lo que anotes arriba llega aquí, y luego lo acomodas en un proyecto.", Ink.Dim2, 13.sp, FontWeight.Normal)
        }
        items(inbox, key = { it.id }) { h ->
            NoteRow(h, null, onMove = { moving = h }) { openNote(h.id) }
        }
        if (nb.finished.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp).clip(Squircle(14.dp)).clickable { showDone = !showDone }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("TERMINADOS · ${nb.finished.size}", Ink.VioletSoft, 12.sp, FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(if (showDone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.Dim)
                }
            }
            if (showDone) items(nb.finished, key = { "d" + it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(16.dp)).background(Ink.Card)
                        .clickable { NotebookNav.project = p.id }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeIcon(projectIcon(p.icon), shape = shapeOf(p.shape), container = Color(p.color).copy(alpha = 0.5f), tint = INK_ON, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, fontWeight = FontWeight.Bold, color = Ink.Dim, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.TaskAlt, null, tint = Ink.Green, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
    if (newProject) {
        ModalBottomSheet(
            onDismissRequest = { newProject = false }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ProjectEditor(null) { p ->
                newProject = false
                if (p != null) { Sounds.play("pop"); NotebookNav.project = p.id }
            }
        }
    }
    moving?.let { h ->
        MoveSheet(h, nb) { moving = null }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Ink.VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun EmptyHint(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Squircle(22.dp)).background(Ink.Card).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeIcon(icon, shape = MShapes.Sunny, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Ink.Dim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** The quick capture: one field, Enter or the arrow saves it. */
@Composable
fun CaptureCard(color: Color, hint: String, tokkip: Boolean, inboxCount: Int = 0, compact: Boolean = false, save: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var saved by remember { mutableLongStateOf(0L) }
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(saved) { if (saved != 0L) { delay(2200); saved = 0L } }
    fun send() {
        if (text.isBlank()) return
        save(text)
        text = ""
        saved = System.currentTimeMillis()
        jump++
    }
    Row(
        Modifier.fillMaxWidth().clip(Squircle(26.dp)).background(Ink.Card).padding(if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tokkip) {
            Mochi(Modifier.size(58.dp, 50.dp), color = color, jump = jump, face = if (saved != 0L) MochiEngine.Eye.HAPPY else null)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(4000) },
                placeholder = { Text(hint, color = Ink.Dim2) },
                maxLines = 5, shape = Squircle(18.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                    focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
                ),
            )
            val sub = when {
                saved != 0L -> "Guardado en la Bandeja ✓"
                tokkip && inboxCount >= 6 -> "Tienes $inboxCount en la Bandeja: ¿las acomodamos?"
                else -> null
            }
            if (sub != null) Text(sub, color = if (saved != 0L) Ink.GreenSoft else Ink.Dim2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        CircleButton(Icons.Rounded.Send, "Guardar", container = if (text.isBlank()) Ink.CardHigh else Ink.VioletSoft, tint = if (text.isBlank()) Ink.Dim else Ink.OnViolet) { send() }
    }
}

/** A half-done note: its project, title and next step, which you can tick from here. */
@Composable
private fun HalfCard(h: NoteHead, project: Project?, actions: Actions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = project?.let { Color(it.color) } ?: Ink.VioletSoft
    Column(
        Modifier.width(230.dp).height(176.dp).clip(Squircle(24.dp)).background(Ink.Card).clickable { openNote(h.id) }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (project != null) ShapeIcon(projectIcon(project.icon), shape = shapeOf(project.shape), container = accent, tint = INK_ON, size = 26.dp)
            else ShapeIcon(Icons.Rounded.Inbox, shape = MShapes.Circle, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Label(project?.name ?: "Bandeja", accent, 12.sp, FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(h.shownTitle, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (h.next.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepDot(false, accent) {
                    scope.launch {
                        val done = withContext(Dispatchers.IO) { Notebook.tickNext(h.id) }
                        if (done != null) { Sounds.play("task_done"); Haptics.success(context); actions.jump++ }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(h.next, fontSize = 13.sp, color = Ink.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Label("${h.done} de ${h.steps} pasos", Ink.Dim2, 11.sp)
        } else {
            Spacer(Modifier.height(8.dp))
            Text(h.preview, fontSize = 13.sp, color = Ink.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A step's check: an empty circle that turns into a cookie when done. */
@Composable
private fun StepDot(done: Boolean, accent: Color, size: Int = 26, onClick: () -> Unit) {
    val shape = PolygonShape(if (done) MShapes.Cookie9 else MShapes.Circle)
    Box(
        Modifier.size(size.dp).clip(shape).background(if (done) accent else Color.Transparent)
            .border(2.dp, if (done) accent else Ink.Dim2, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.TaskAlt, null, tint = INK_ON, modifier = Modifier.size((size * 0.6f).dp))
    }
}

@Composable
private fun ProgressBar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(CircleShape).background(Ink.Track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).clip(CircleShape).background(color))
    }
}

@Composable
private fun ProjectTile(p: Project, nb: NotebookIndex, modifier: Modifier, onClick: () -> Unit) {
    val pr = remember(nb, p) { nb.progress(p) }
    val accent = Color(p.color)
    Column(modifier.height(164.dp).clip(Squircle(26.dp)).background(Ink.Card).clickable(onClick = onClick).padding(14.dp)) {
        ShapeIcon(projectIcon(p.icon), shape = shapeOf(p.shape), container = accent, tint = INK_ON, size = 48.dp)
        Spacer(Modifier.height(10.dp))
        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        if (pr.steps > 0) {
            ProgressBar(pr.frac, accent, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Label(if (pr.complete) "¡Todos los pasos!" else "${pr.done} de ${pr.steps} pasos", if (pr.complete) Ink.GreenSoft else Ink.Dim2, 12.sp)
        } else Label(if (pr.notes == 0) "Vacío por ahora" else if (pr.notes == 1) "1 nota" else "${pr.notes} notas", Ink.Dim2, 12.sp)
    }
}

@Composable
private fun NewProjectTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(164.dp).clip(Squircle(26.dp)).border(2.dp, Ink.Hairline, Squircle(26.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeIcon(Icons.Rounded.Add, shape = MShapes.Clover4, size = 44.dp)
        Spacer(Modifier.height(8.dp))
        Label("Nuevo proyecto", Ink.Dim, 13.sp, FontWeight.Bold)
    }
}

@Composable
private fun NoteRow(h: NoteHead, project: Project?, onMove: (() -> Unit)? = null, onClick: () -> Unit) {
    val accent = project?.let { Color(it.color) } ?: Ink.VioletSoft
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Squircle(20.dp)).background(Ink.Card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (h.pinned) { Icon(Icons.Rounded.PushPin, null, tint = accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                Text(h.shownTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (h.preview.isNotBlank() && h.preview != h.title) {
                Spacer(Modifier.height(2.dp))
                Text(h.preview, fontSize = 13.sp, color = Ink.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            }
            if (h.steps > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(h.done.toFloat() / h.steps, accent, Modifier.width(80.dp))
                    Spacer(Modifier.width(8.dp))
                    Label("${h.done}/${h.steps}", Ink.Dim2, 12.sp)
                }
            }
        }
        if (onMove != null) {
            Spacer(Modifier.width(8.dp))
            CircleButton(Icons.Rounded.DriveFileMove, "Acomodar en un proyecto", size = 40.dp, onClick = onMove)
        }
    }
}

// ── A project ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectView(p: Project, nb: NotebookIndex, color: Color, actions: Actions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pr = remember(nb, p) { nb.progress(p) }
    val notes = remember(nb, p.id) { nb.notesOf(p.id).sortedWith(compareByDescending<NoteHead> { it.pinned }.thenByDescending { it.updated }) }
    val accent = Color(p.color)
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var party by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.ArrowBack, "Volver") { NotebookNav.project = null }
                Spacer(Modifier.weight(1f))
                CircleButton(Icons.Rounded.Edit, "Editar proyecto") { editing = true }
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleButton(Icons.Rounded.MoreVert, "Más") { menu = true }
                    DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Ink.CardHigh) {
                        DropdownMenuItem(
                            text = { Text(if (p.doneAt > 0) "Reabrir proyecto" else "Darlo por terminado") },
                            leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                            onClick = {
                                menu = false
                                if (p.doneAt > 0) Notebook.finishProject(p.id, false) else { Notebook.finishProject(p.id, true); party = true }
                            },
                        )
                        DropdownMenuItem(text = { Text("Borrar proyecto") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(projectIcon(p.icon), shape = shapeOf(p.shape), container = accent, tint = INK_ON, size = 72.dp, spin = pr.complete)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ScreenTitle(p.name, size = 28.sp)
                    if (p.doneAt > 0) Label("Terminado ✓", Ink.GreenSoft, 13.sp, FontWeight.Bold)
                }
            }
            if (p.why.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("«${p.why}»", color = Ink.Dim, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (pr.steps > 0) {
                Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${(pr.frac * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                        Spacer(Modifier.width(8.dp))
                        Text("${pr.done} de ${pr.steps} pasos", color = Ink.Dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(pr.frac, accent, Modifier.fillMaxWidth())
                    val next = pr.next
                    if (next != null && p.doneAt == 0L) {
                        Spacer(Modifier.height(16.dp))
                        Label("LO SIGUIENTE", Ink.VioletSoft, 11.sp, FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepDot(false, accent, 30) {
                                scope.launch {
                                    val done = withContext(Dispatchers.IO) { Notebook.tickNext(next.id) }
                                    if (done != null) { Sounds.play("task_done"); Haptics.success(context); actions.jump++ }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clickable { openNote(next.id) }) {
                                Text(next.next, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Label("en ${next.shownTitle}", Ink.Dim2, 12.sp)
                            }
                            CircleButton(Icons.Rounded.TaskAlt, "Mandar a Tareas", size = 40.dp) {
                                if (actions.addTask(next.next)) android.widget.Toast.makeText(context, "Mandado a Tareas", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (pr.complete && p.doneAt == 0L) {
                        Spacer(Modifier.height(16.dp))
                        Text("¡Ya marcaste todos los pasos! ¿Lo damos por terminado?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        ExpressiveButton("Terminar proyecto", icon = Icons.Rounded.TaskAlt, container = accent, content = INK_ON) {
                            Notebook.finishProject(p.id, true); party = true
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (p.doneAt == 0L) {
                CaptureCard(color, hint = "Algo para ${p.name}…", tokkip = false, compact = true) { text ->
                    if (Notebook.capture(text, p.id) != null) { Sounds.play("pop"); Haptics.tick(context) }
                }
                Spacer(Modifier.height(4.dp))
                Text("Tip: en una nota, escribe «[] » al inicio de una línea para hacerla un paso.", color = Ink.Dim2, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(if (notes.isEmpty()) "NOTAS" else "NOTAS · ${notes.size}", Ink.VioletSoft, 12.sp, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Pill("Nota", true, Icons.Rounded.Add) { openNote(Notebook.newNote(p.id)) }
            }
            if (notes.isEmpty()) Label("Todavía no hay notas. Empieza con una lista de pasos: lo más pequeño que se te ocurra.", Ink.Dim2, 13.sp, FontWeight.Normal)
        }
        items(notes, key = { it.id }) { h -> NoteRow(h, p) { openNote(h.id) } }
        item { Spacer(Modifier.height(120.dp)) }
    }

    if (editing) {
        ModalBottomSheet(
            onDismissRequest = { editing = false }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { ProjectEditor(p) { editing = false } }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false }, containerColor = Ink.CardHigh,
            title = { Text("¿Borrar «${p.name}»?") },
            text = { Text(if (notes.isEmpty()) "No tiene notas." else "Sus ${notes.size} notas pueden volver a la Bandeja o borrarse con él.") },
            confirmButton = {
                TextButton(onClick = { deleting = false; Notebook.deleteProject(p.id, withNotes = false); NotebookNav.project = null }) {
                    Text(if (notes.isEmpty()) "Borrar" else "Notas a la Bandeja", color = Ink.VioletSoft)
                }
            },
            dismissButton = {
                if (notes.isNotEmpty()) TextButton(onClick = { deleting = false; Notebook.deleteProject(p.id, withNotes = true); NotebookNav.project = null }) {
                    Text("Borrar todo", color = Ink.Red)
                } else TextButton(onClick = { deleting = false }) { Text("Cancelar", color = Ink.Dim) }
            },
        )
    }
    if (party) {
        Celebration(
            seed = p.name.hashCode(), color = color, title = "¡Proyecto terminado!", badge = p.name.uppercase(),
            body = "Lo empezaste y lo terminaste. Con todo lo que pasa por tu cabeza, eso es enorme: tómate un momento para sentirlo.",
            foot = if (pr.steps > 0) "${pr.done} pasos · +3 de cariño para Tokkip" else "+3 de cariño para Tokkip",
            button = "¡Lo logré!",
        ) { party = false }
    }
}

/** New project, or change one: name, why, colour, shape and icon. Gives back what was saved (null = nothing). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectEditor(editing: Project?, done: (Project?) -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var why by remember { mutableStateOf(editing?.why ?: "") }
    var color by remember { mutableLongStateOf(editing?.color ?: HABIT_COLORS[(System.currentTimeMillis() / 1000 % HABIT_COLORS.size).toInt()]) }
    var shape by remember { mutableIntStateOf(editing?.shape ?: 0) }
    var icon by remember { mutableStateOf(editing?.icon ?: "idea") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(projectIcon(icon), shape = shapeOf(shape), container = Color(color), tint = INK_ON, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            ScreenTitle(if (editing == null) "Nuevo proyecto" else "Tu proyecto", size = 26.sp)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) }, singleLine = true,
            placeholder = { Text("Ej. Pintar mi cuarto", color = Ink.Dim2) }, label = { Text("Nombre") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = Squircle(18.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = why, onValueChange = { why = it.take(160) }, maxLines = 3,
            placeholder = { Text("Ej. Quiero un lugar que me dé calma", color = Ink.Dim2) }, label = { Text("¿Por qué te importa? (opcional)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = Squircle(18.dp), modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle("Color")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in HABIT_COLORS) {
                Box(
                    Modifier.size(40.dp).clip(PolygonShape(if (c == color) MShapes.Cookie9 else MShapes.Circle)).background(Color(c)).clickable { color = c },
                )
            }
        }
        SectionTitle("Forma")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MShapes.ALL.forEachIndexed { i, s ->
                Box(Modifier.size(40.dp).clip(PolygonShape(s)).background(if (i == shape) Color(color) else Ink.Track).clickable { shape = i })
            }
        }
        SectionTitle("Ícono")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, img) in PROJECT_ICONS) {
                val on = key == icon
                Box(
                    Modifier.size(44.dp).clip(Squircle(14.dp)).background(if (on) Color(color) else Ink.Card).clickable { icon = key },
                    contentAlignment = Alignment.Center,
                ) { Icon(img, null, tint = if (on) INK_ON else Ink.Dim, modifier = Modifier.size(22.dp)) }
            }
        }
        Spacer(Modifier.height(22.dp))
        ExpressiveButton(if (editing == null) "Crear proyecto" else "Guardar", Modifier.fillMaxWidth(), big = true) {
            if (name.isBlank()) return@ExpressiveButton
            val saved = if (editing == null) Notebook.newProject(name, color, shape, icon, why)
            else editing.copy(name = name.trim(), why = why.trim(), color = color, shape = shape, icon = icon, updated = System.currentTimeMillis()).also { Notebook.saveProject(it) }
            done(saved)
        }
    }
}

/** Acomodar: send a note to a project (or back to the Bandeja). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(h: NoteHead, nb: NotebookIndex, close: () -> Unit) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, containerColor = Ink.CardHigh) {
        if (creating) {
            ProjectEditor(null) { p ->
                if (p != null) { Notebook.move(h.id, p.id); Sounds.play("pop") }
                close()
            }
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            ScreenTitle("¿A dónde va?", size = 26.sp)
            Label(h.shownTitle, Ink.Dim2, 13.sp)
            Spacer(Modifier.height(12.dp))
            MoveRow("Bandeja", { ShapeIcon(Icons.Rounded.Inbox, shape = MShapes.Circle, size = 36.dp) }, h.project == null) {
                Notebook.move(h.id, null); Haptics.tick(context); close()
            }
            for (p in nb.active) {
                MoveRow(p.name, { ShapeIcon(projectIcon(p.icon), shape = shapeOf(p.shape), container = Color(p.color), tint = INK_ON, size = 36.dp) }, h.project == p.id) {
                    Notebook.move(h.id, p.id); Sounds.play("pop"); Haptics.tick(context); close()
                }
            }
            MoveRow("Nuevo proyecto…", { ShapeIcon(Icons.Rounded.Add, shape = MShapes.Clover4, size = 36.dp) }, false) { creating = true }
        }
    }
}

@Composable
private fun MoveRow(label: String, icon: @Composable () -> Unit, on: Boolean, go: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(18.dp)).background(if (on) Ink.VioletDeep else Ink.Card)
            .clickable(onClick = go).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// ── Search ────────────────────────────────────────────────────────────────────

@Composable
private fun SearchView(nb: NotebookIndex) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Pair<NoteHead, String>>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(180)
        results = withContext(Dispatchers.IO) { Notebook.search(query) }
    }
    val projects = remember(query, nb) { if (query.isBlank()) emptyList() else nb.projects.filter { fold(it.name).contains(fold(query.trim())) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.ArrowBack, "Volver") { NotebookNav.searching = false }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                    placeholder = { Text("Buscar en tu Cuaderno", color = Ink.Dim2) },
                    trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Borrar", modifier = Modifier.clickable { query = "" }) },
                    shape = Squircle(18.dp), modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (query.isNotBlank() && results.isEmpty() && projects.isEmpty()) Label("Nada con «$query».", Ink.Dim2, 14.sp)
        }
        items(projects, key = { "p" + it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(18.dp)).background(Ink.Card)
                    .clickable { NotebookNav.searching = false; NotebookNav.project = p.id }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeIcon(projectIcon(p.icon), shape = shapeOf(p.shape), container = Color(p.color), tint = INK_ON, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Label("Proyecto", Ink.Dim2, 12.sp)
            }
        }
        items(results, key = { it.first.id }) { (h, snip) ->
            val p = nb.projects.firstOrNull { it.id == h.project }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(18.dp)).background(Ink.Card)
                    .clickable { NotebookNav.searching = false; openNote(h.id) }.padding(14.dp),
            ) {
                Text(h.shownTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (snip.isNotBlank()) Text(snip, color = Ink.Dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Label(p?.name ?: "Bandeja", p?.let { Color(it.color) } ?: Ink.Dim2, 11.sp, FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── The editor ────────────────────────────────────────────────────────────────

/** Every line starts with this invisible mark, so a backspace at the start of a line can be told apart. */
private const val ZW = '​'

private var blockKeys = 0L

private class EditBlock(kind: BlockKind, text: String) {
    val key = blockKeys++
    var kind by mutableStateOf(kind)
    var value by mutableStateOf(TextFieldValue(ZW + text, TextRange(1 + text.length)))
    val focus = FocusRequester()
    val text: String get() = value.text.removePrefix(ZW.toString())
    fun set(text: String, cursor: Int = text.length) { value = TextFieldValue(ZW + text, TextRange(1 + cursor.coerceIn(0, text.length))) }
}

private fun nextKind(k: BlockKind) = when (k) {
    BlockKind.TODO, BlockKind.DONE -> BlockKind.TODO
    BlockKind.BULLET -> BlockKind.BULLET
    else -> BlockKind.TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(id: String, nb: NotebookIndex, actions: Actions) {
    val context = LocalContext.current
    val head = nb.notes.firstOrNull { it.id == id } ?: return
    val project = nb.projects.firstOrNull { it.id == head.project }
    val accent = project?.let { Color(it.color) } ?: Ink.VioletSoft
    var title by remember(id) { mutableStateOf(TextFieldValue(head.title)) }
    var blocks by remember(id) { mutableStateOf<SnapshotStateList<EditBlock>?>(null) }
    var version by remember(id) { mutableIntStateOf(0) }
    var focused by remember(id) { mutableLongStateOf(-1L) }
    var focusTo by remember(id) { mutableLongStateOf(-1L) }
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(id) {
        val body = withContext(Dispatchers.IO) { Notebook.body(id) }
        blocks = mutableStateListOf<EditBlock>().apply { addAll(blocksOf(body).map { EditBlock(it.kind, it.text) }) }
        // A brand-new note: straight to typing.
        if (head.title.isEmpty() && body.isEmpty()) runCatching { delay(120); titleFocus.requestFocus() }
    }
    fun current(): Pair<String, String>? {
        val bs = blocks ?: return null
        return title.text.trim() to bodyOf(bs.map { Block(it.kind, it.text) })
    }
    fun save() { current()?.let { (t, b) -> Notebook.save(id, t, b) } }
    LaunchedEffect(version) { if (version > 0) { delay(650); save() } }
    DisposableEffect(id) {
        onDispose {
            val c = current()
            // An empty note doesn't stay behind as clutter.
            if (c != null && c.first.isEmpty() && c.second.isBlank()) Notebook.delete(id) else save()
        }
    }
    LaunchedEffect(focusTo) {
        if (focusTo >= 0) {
            delay(30)
            blocks?.firstOrNull { it.key == focusTo }?.let { runCatching { it.focus.requestFocus() } }
            focusTo = -1
        }
    }
    fun changed() { version++ }

    fun onChange(i: Int, new: TextFieldValue) {
        val bs = blocks ?: return
        val b = bs.getOrNull(i) ?: return
        val old = b.value
        var t = new.text
        if (!t.startsWith(ZW)) {
            if (ZW + t == old.text && old.selection.start <= 1) {
                // Backspace at the start of the line.
                when {
                    b.kind != BlockKind.TEXT -> b.kind = BlockKind.TEXT
                    i > 0 -> {
                        val prev = bs[i - 1]
                        val join = prev.text.length
                        prev.set(prev.text + t, join)
                        bs.removeAt(i)
                        focusTo = prev.key
                    }
                }
                if (i < bs.size && bs[i] === b) b.set(b.text, 0)
                changed()
                return
            }
            t = ZW + t.replace(ZW.toString(), "")
        }
        val content = t.substring(1)
        if (content.contains('\n')) {
            val parts = content.split('\n')
            if (parts.size == 2 && parts[0].isEmpty() && parts[1].isEmpty() && b.kind != BlockKind.TEXT) {
                // Enter on an empty bullet or step: back to plain text.
                b.kind = BlockKind.TEXT
                b.set("")
                changed()
                return
            }
            b.set(parts[0])
            val kind = nextKind(b.kind)
            val added = parts.drop(1).map { part ->
                val parsed = blocksOf(part).first()
                EditBlock(if (parsed.kind != BlockKind.TEXT) parsed.kind else kind, parsed.text)
            }
            if (parts.size == 2) added.first().set(added.first().text, 0)
            bs.addAll(i + 1, added)
            focusTo = added.last().key
            changed()
            return
        }
        // Little shortcuts at the start of a plain line.
        if (b.kind == BlockKind.TEXT) {
            val shortcut = when {
                content.startsWith("[] ") -> BlockKind.TODO to 3
                content.startsWith("[ ] ") -> BlockKind.TODO to 4
                content.startsWith("- ") || content.startsWith("* ") -> BlockKind.BULLET to 2
                content.startsWith("# ") -> BlockKind.HEADING to 2
                else -> null
            }
            if (shortcut != null) {
                b.kind = shortcut.first
                b.set(content.substring(shortcut.second), 0)
                changed()
                return
            }
        }
        val sel = TextRange(new.selection.start.coerceAtLeast(1), new.selection.end.coerceAtLeast(1))
        b.value = if (t == new.text) new.copy(selection = sel) else TextFieldValue(t, TextRange(sel.start.coerceAtMost(t.length), sel.end.coerceAtMost(t.length)))
        if (t != old.text) changed()
    }

    fun setKind(k: BlockKind) {
        val bs = blocks ?: return
        val b = bs.firstOrNull { it.key == focused } ?: bs.lastOrNull() ?: return
        b.kind = if (b.kind == k || (k == BlockKind.TODO && b.kind == BlockKind.DONE)) BlockKind.TEXT else k
        Haptics.tick(context)
        changed()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Top: back, where it lives, pin and more.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.Rounded.ArrowBack, "Volver") { NotebookNav.note = null }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f, fill = false).clip(CircleShape).background(Ink.CardHigh).clickable { moving = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (project != null) ShapeIcon(projectIcon(project.icon), shape = shapeOf(project.shape), container = accent, tint = INK_ON, size = 22.dp)
                else Icon(Icons.Rounded.Inbox, null, tint = Ink.Dim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(project?.name ?: "Bandeja", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(0.01f))
            CircleButton(
                Icons.Rounded.PushPin, if (head.pinned) "Desfijar" else "Fijar", size = 44.dp,
                container = if (head.pinned) accent else Ink.CardHigh, tint = if (head.pinned) INK_ON else Ink.Dim,
            ) { Notebook.pin(id, !head.pinned); Haptics.tick(context) }
            Spacer(Modifier.width(6.dp))
            Box {
                CircleButton(Icons.Rounded.MoreVert, "Más", size = 44.dp) { menu = true }
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Ink.CardHigh) {
                    DropdownMenuItem(
                        text = { Text("Pasos pendientes a Tareas") }, leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                        onClick = {
                            menu = false
                            val todo = blocks.orEmpty().filter { it.kind == BlockKind.TODO && it.text.isNotBlank() }
                            val sent = todo.count { actions.addTask(it.text.trim()) }
                            android.widget.Toast.makeText(context, if (sent == 0) "No hay pasos pendientes" else "$sent a Tareas", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menu = false
                            val c = current() ?: return@DropdownMenuItem
                            val text = listOf(c.first, c.second.replace("[ ] ", "☐ ").replace("[x] ", "☑ ")).filter { it.isNotBlank() }.joinToString("\n\n")
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir nota"))
                            }
                        },
                    )
                    DropdownMenuItem(text = { Text("Borrar nota") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; deleting = true })
                }
            }
        }
        val bs = blocks
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Box {
                if (title.text.isEmpty()) Text("Título", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Dim2)
                BasicTextField(
                    value = title, onValueChange = { title = TextFieldValue(it.text.replace("\n", "").take(120), it.selection, it.composition); changed() },
                    textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text, fontFamily = DisplayFont),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onNext = { bs?.firstOrNull()?.let { focusTo = it.key } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (bs != null) {
                bs.forEachIndexed { i, b ->
                    androidx.compose.runtime.key(b.key) {
                        BlockRow(b, accent, onToggle = {
                            val checking = b.kind == BlockKind.TODO
                            b.kind = if (checking) BlockKind.DONE else BlockKind.TODO
                            if (checking) { Sounds.play("task_done", 0.6f); Haptics.success(context); actions.jump++ } else Haptics.tick(context)
                            changed()
                        }, onFocus = { focused = b.key }) { onChange(i, it) }
                    }
                }
            }
            // Tapping below the text goes to the end (a new line if the last one has something).
            Box(
                Modifier.fillMaxWidth().heightIn(min = 240.dp).clickable(MutableInteractionSource(), null) {
                    val list = blocks ?: return@clickable
                    val last = list.lastOrNull()
                    if (last == null || last.text.isNotEmpty() || last.kind != BlockKind.TEXT) {
                        val n = EditBlock(BlockKind.TEXT, "")
                        list.add(n); focusTo = n.key
                    } else focusTo = last.key
                },
            )
        }
        // The line's kind, and the keyboard.
        val fb = bs?.firstOrNull { it.key == focused }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(CircleShape).background(Ink.CardHigh).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.Notes, "Texto", fb?.kind == BlockKind.TEXT) { setKind(BlockKind.TEXT) }
            ToolButton(Icons.Rounded.Title, "Título", fb?.kind == BlockKind.HEADING) { setKind(BlockKind.HEADING) }
            ToolButton(Icons.Rounded.FormatListBulleted, "Viñeta", fb?.kind == BlockKind.BULLET) { setKind(BlockKind.BULLET) }
            ToolButton(Icons.Rounded.CheckBox, "Paso", fb?.isStep == true) { setKind(BlockKind.TODO) }
            Spacer(Modifier.weight(1f))
            if (fb?.kind == BlockKind.TODO && fb.text.isNotBlank()) {
                ToolButton(Icons.Rounded.TaskAlt, "A Tareas", false) {
                    if (actions.addTask(fb.text.trim())) android.widget.Toast.makeText(context, "Mandado a Tareas", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            ToolButton(Icons.Rounded.KeyboardHide, "Listo", false) { keyboard?.hide(); focusManager.clearFocus(); save() }
        }
    }

    if (moving) MoveSheet(head, nb) { moving = false }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false }, containerColor = Ink.CardHigh,
            title = { Text("¿Borrar esta nota?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { deleting = false; blocks = null; Notebook.delete(id); NotebookNav.note = null }) { Text("Borrar", color = Ink.Red) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar", color = Ink.Dim) } },
        )
    }
}

private val EditBlock.isStep get() = kind == BlockKind.TODO || kind == BlockKind.DONE

@Composable
private fun ToolButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (on) Ink.VioletSoft else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) Ink.OnViolet else Ink.Text, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun BlockRow(b: EditBlock, accent: Color, onToggle: () -> Unit, onFocus: () -> Unit, onChange: (TextFieldValue) -> Unit) {
    val style = when (b.kind) {
        BlockKind.HEADING -> TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text, lineHeight = 28.sp)
        BlockKind.DONE -> TextStyle(fontSize = 17.sp, color = Ink.Dim2, textDecoration = TextDecoration.LineThrough, lineHeight = 25.sp)
        else -> TextStyle(fontSize = 17.sp, color = Ink.Text, lineHeight = 25.sp)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (b.kind == BlockKind.HEADING) 6.dp else 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (b.kind) {
            BlockKind.TODO, BlockKind.DONE -> {
                Box(Modifier.padding(top = 1.dp, end = 10.dp)) { StepDot(b.kind == BlockKind.DONE, accent, 24, onToggle) }
            }
            BlockKind.BULLET -> {
                Box(Modifier.padding(top = 9.dp, start = 6.dp, end = 12.dp).size(8.dp).clip(PolygonShape(MShapes.Clover4)).background(accent))
            }
            else -> {}
        }
        BasicTextField(
            value = b.value, onValueChange = onChange, textStyle = style, cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).focusRequester(b.focus).onFocusChanged { if (it.isFocused) onFocus() },
        )
    }
}

// ── On Inicio ─────────────────────────────────────────────────────────────────

/** Cuaderno on Inicio: jot something down, and what you left half-done with its next step. */
@Composable
fun NotebookHomeCard(now: Long, color: Color, actions: Actions, open: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nb by Notebook.index.collectAsState()
    val half = remember(nb, now / 600_000) { nb.halfDone(now, 2).filter { it.next.isNotBlank() } }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.padding(horizontal = 6.dp).clip(Squircle(12.dp)).clickable { NotebookNav.note = null; NotebookNav.project = null; open() }, verticalAlignment = Alignment.CenterVertically) {
        Text("Cuaderno", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Ver el Cuaderno", tint = Ink.Dim)
    }
    Spacer(Modifier.height(8.dp))
    CaptureCard(color, hint = "Anota algo rápido…", tokkip = false, compact = true) { text ->
        if (Notebook.capture(text) != null) { Sounds.play("pop"); Haptics.success(context); actions.jump++ }
    }
    for (h in half) {
        val p = Notebook.project(h.project)
        val accent = p?.let { Color(it.color) } ?: Ink.VioletSoft
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(Squircle(20.dp)).background(Ink.Card)
                .clickable { openNote(h.id); NotebookNav.project = h.project; open() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDot(false, accent, 28) {
                scope.launch {
                    val done = withContext(Dispatchers.IO) { Notebook.tickNext(h.id) }
                    if (done != null) { Sounds.play("task_done"); Haptics.success(context); actions.jump++ }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.next, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Label("${p?.name ?: h.shownTitle} · ${h.done} de ${h.steps}", accent, 12.sp)
            }
        }
    }
}
