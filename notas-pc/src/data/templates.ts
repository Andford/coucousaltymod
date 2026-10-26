// Templates, so you never start from a blank page — the same eight as on
// Android (Templates.kt): each builds a little tree of pages.

import { type Page, type PageKind, type Schema, GREY, defaultSchema, newId, page } from "./model";

/** A page to create, with its body and its own pages. */
export interface Draft {
  page: Page;
  body: string;
  children: Draft[];
}

export interface Template {
  id: string;
  name: string;
  about: string;
  icon: string;
  color: number;
  shape: number;
}

export const TEMPLATES: Template[] = [
  { id: "blank", name: "Página en blanco", about: "Solo tú y la hoja", icon: "doc", color: 0xffa78bfa, shape: 0 },
  { id: "project", name: "Proyecto", about: "El porqué, lo siguiente y un tablero de tareas", icon: "rocket", color: 0xff60a5fa, shape: 1 },
  { id: "board", name: "Tablero", about: "Por hacer · Haciendo · Hecho", icon: "board", color: 0xfffbbf24, shape: 3 },
  { id: "class", name: "Apuntes de clase", about: "Tema, ideas clave, dudas y resumen", icon: "study", color: 0xff34d399, shape: 4 },
  { id: "ideas", name: "Lluvia de ideas", about: "Todo afuera sin filtro, luego escoges", icon: "idea", color: 0xfff472b6, shape: 2 },
  { id: "meeting", name: "Reunión", about: "Quiénes, temas y acuerdos", icon: "people", color: 0xff22d3ee, shape: 5 },
  { id: "reading", name: "Lista de lecturas y pelis", about: "Una tabla con estado, autor y calificación", icon: "book", color: 0xfff97316, shape: 8 },
  { id: "trip", name: "Viaje", about: "Maleta, itinerario y pendientes", icon: "travel", color: 0xff34d399, shape: 7 },
];

function mk(parent: string | null, title: string, t: Template, now: number, kind: PageKind = "DOC", schema: Schema | null = null, props: Record<string, string> = {}, ord = 0): Page {
  return page({ id: newId(), parent, title, icon: t.icon, color: t.color, shape: t.shape, kind, ord, created: now, updated: now, props, schema });
}

const d = (p: Page, body = "", children: Draft[] = []): Draft => ({ page: p, body, children });

/** The pages a template makes, under `parent`. */
export function build(t: Template, parent: string | null, now: number, ord = 0): Draft {
  const root = mk(parent, t.id === "blank" ? "" : t.name, t, now, "DOC", null, {}, ord);
  switch (t.id) {
    case "project": {
      const boardPage = mk(root.id, "Tareas", { ...t, icon: "board" }, now, "COLLECTION", defaultSchema("board"));
      const first = d(mk(boardPage.id, "Escribir el primer paso", { ...t, icon: "doc" }, now, "DOC", null, { status: "todo" }));
      return d(
        root,
        "! ¿Por qué te importa? Escríbelo aquí: cuando se ponga pesado, te lo va a recordar.\n" +
          "## Lo siguiente\n[ ] Lo más pequeño que puedas hacer hoy\n" +
          `## Tareas\n@@${boardPage.id}\n## Notas\n`,
        [d(boardPage, "", [first])],
      );
    }
    case "board":
      return d({ ...root, kind: "COLLECTION", schema: defaultSchema("board") });
    case "class":
      return d(root, "## Tema\n\n## Ideas clave\n• \n## Dudas\n[ ] \n## En mis palabras\n! Resúmelo como se lo contarías a alguien.");
    case "ideas":
      return d(root, "! Sin filtro: todo vale. Ya después escoges.\n• \n## Las que me laten\n[ ] ");
    case "meeting":
      return d(root, "## Quiénes\n• \n## Temas\n1. \n## Acuerdos\n[ ] ");
    case "reading":
      return d({
        ...root,
        kind: "COLLECTION",
        schema: {
          props: [
            {
              id: "status",
              name: "Estado",
              type: "SELECT",
              options: [
                { id: "todo", name: "Pendiente", color: GREY },
                { id: "doing", name: "Leyendo / viendo", color: 0xfffbbf24 },
                { id: "done", name: "Terminado", color: 0xff34d399 },
              ],
            },
            { id: "author", name: "Autor", type: "TEXT", options: [] },
            { id: "stars", name: "Calificación", type: "NUMBER", options: [] },
            {
              id: "tags",
              name: "Etiquetas",
              type: "MULTI",
              options: [
                { id: "book", name: "Libro", color: 0xffa78bfa },
                { id: "movie", name: "Peli", color: 0xfff472b6 },
                { id: "series", name: "Serie", color: 0xff60a5fa },
              ],
            },
          ],
          view: "table",
          groupBy: "status",
          sortBy: "",
          sortDesc: false,
        },
      });
    case "trip":
      return d(root, "## Maleta\n[ ] Cargadores\n[ ] Identificación\n## Itinerario\n1. \n## Pendientes antes de salir\n[ ] ");
    default:
      return d(root);
  }
}

/** Every page of a draft, top-down, with its body. */
export function flatten(dr: Draft): { page: Page; body: string }[] {
  return [{ page: dr.page, body: dr.body }, ...dr.children.flatMap(flatten)];
}
