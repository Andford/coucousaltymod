// Templates, so you never start from a blank page: each one builds a little
// tree of pages (a project with its board, a class's notes, a reading list…).

package mx.loperstack.tokkipnotas.data

/** A page to create, with its body and its own pages. */
data class Draft(val page: Page, val body: String = "", val children: List<Draft> = emptyList())

data class Template(val id: String, val name: String, val about: String, val icon: String, val color: Long, val shape: Int)

val TEMPLATES = listOf(
    Template("blank", "Página en blanco", "Solo tú y la hoja", "doc", 0xFFA78BFA, 0),
    Template("project", "Proyecto", "El porqué, lo siguiente y un tablero de tareas", "rocket", 0xFF60A5FA, 1),
    Template("board", "Tablero", "Por hacer · Haciendo · Hecho", "board", 0xFFFBBF24, 3),
    Template("class", "Apuntes de clase", "Tema, ideas clave, dudas y resumen", "study", 0xFF34D399, 4),
    Template("ideas", "Lluvia de ideas", "Todo afuera sin filtro, luego escoges", "idea", 0xFFF472B6, 2),
    Template("meeting", "Reunión", "Quiénes, temas y acuerdos", "people", 0xFF22D3EE, 5),
    Template("reading", "Lista de lecturas y pelis", "Una tabla con estado, autor y calificación", "book", 0xFFF97316, 8),
    Template("trip", "Viaje", "Maleta, itinerario y pendientes", "travel", 0xFF34D399, 7),
)

private fun page(parent: String?, title: String, t: Template, now: Long, kind: PageKind = PageKind.DOC, schema: Schema? = null, props: Map<String, String> = emptyMap(), ord: Double = 0.0) =
    Page(newId(), parent, title, t.icon, t.color, t.shape, kind, ord, now, now, 0, props = props, schema = schema)

/** The pages a template makes, under `parent`. */
fun build(t: Template, parent: String?, now: Long, ord: Double = 0.0): Draft {
    val root = page(parent, if (t.id == "blank") "" else t.name, t, now, ord = ord)
    return when (t.id) {
        "project" -> {
            val board = page(root.id, "Tareas", t.copy(icon = "board"), now, PageKind.COLLECTION, defaultSchema("board"))
            val first = Draft(page(board.id, "Escribir el primer paso", t.copy(icon = "doc"), now, props = mapOf("status" to "todo")))
            Draft(
                root,
                "! ¿Por qué te importa? Escríbelo aquí: cuando se ponga pesado, te lo va a recordar.\n" +
                    "## Lo siguiente\n[ ] Lo más pequeño que puedas hacer hoy\n" +
                    "## Tareas\n@@${board.id}\n## Notas\n",
                listOf(Draft(board, children = listOf(first))),
            )
        }
        "board" -> Draft(root.copy(kind = PageKind.COLLECTION, schema = defaultSchema("board")))
        "class" -> Draft(root, "## Tema\n\n## Ideas clave\n• \n## Dudas\n[ ] \n## En mis palabras\n! Resúmelo como se lo contarías a alguien.")
        "ideas" -> Draft(root, "! Sin filtro: todo vale. Ya después escoges.\n• \n## Las que me laten\n[ ] ")
        "meeting" -> Draft(root, "## Quiénes\n• \n## Temas\n1. \n## Acuerdos\n[ ] ")
        "reading" -> Draft(
            root.copy(
                kind = PageKind.COLLECTION,
                schema = Schema(
                    props = listOf(
                        Prop("status", "Estado", PropType.SELECT, listOf(Opt("todo", "Pendiente", GREY), Opt("doing", "Leyendo / viendo", 0xFFFBBF24), Opt("done", "Terminado", 0xFF34D399))),
                        Prop("author", "Autor", PropType.TEXT),
                        Prop("stars", "Calificación", PropType.NUMBER),
                        Prop("tags", "Etiquetas", PropType.MULTI, listOf(Opt("book", "Libro", 0xFFA78BFA), Opt("movie", "Peli", 0xFFF472B6), Opt("series", "Serie", 0xFF60A5FA))),
                    ),
                    view = "table", groupBy = "status",
                ),
            ),
        )
        "trip" -> Draft(root, "## Maleta\n[ ] Cargadores\n[ ] Identificación\n## Itinerario\n1. \n## Pendientes antes de salir\n[ ] ")
        else -> Draft(root)
    }
}

/** Every page of a draft, top-down, with its body. */
fun Draft.flatten(): List<Pair<Page, String>> = listOf(page to body) + children.flatMap { it.flatten() }
