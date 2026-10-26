// The app's pages. The index (every page without its body) is in memory and
// every screen listens to it; a body is read when you open its page and
// written a moment after you stop typing. Writes go to the backend one after
// another, so the screen never waits for the disk and nothing lands out of order.

import { type Backend, copyRows, pageOfRow, rowOf } from "./backend";
import { captureOf, factsOf, plainOf } from "./blocks";
import { type CopyRow, type Page, type PageKind, INBOX_ID, Index, between, copyJson, copyOf, dayKey, defaultSchema, newId, page as mkPage, searchScore, snippet } from "./model";
import { type Draft, flatten } from "./templates";

type Listener = (ix: Index) => void;

export interface Hit {
  page: Page;
  snippet: string;
}

class StoreImpl {
  private backend: Backend | null = null;
  private ix = new Index();
  private listeners = new Set<Listener>();
  private chain: Promise<unknown> = Promise.resolve();
  ready = false;

  get index(): Index {
    return this.ix;
  }

  get now(): number {
    return Date.now();
  }

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private set(changed: Page[], removed: Set<string> = new Set()) {
    const ids = new Set(changed.map((p) => p.id));
    this.ix = new Index([...this.ix.pages.filter((p) => !ids.has(p.id) && !removed.has(p.id)), ...changed]);
    for (const l of this.listeners) l(this.ix);
  }

  private write(job: (b: Backend) => Promise<unknown>): Promise<unknown> {
    const b = this.backend;
    if (!b) return Promise.resolve();
    this.chain = this.chain.then(() => job(b)).catch((e) => console.error("[notas] write", e));
    return this.chain;
  }

  /** Waits for every pending write. */
  flush(): Promise<unknown> {
    return this.chain;
  }

  async init(backend: Backend) {
    this.backend = backend;
    const rows = await backend.load();
    this.ix = new Index(rows.map(pageOfRow));
    this.ready = true;
    if (!this.ix.page(INBOX_ID)) this.put(mkPage({ id: INBOX_ID, title: "Bandeja", icon: "inbox", color: 0xff94a3b8, created: this.now, updated: this.now }), "");
    else for (const l of this.listeners) l(this.ix);
  }

  page(id: string | null | undefined) {
    return this.ix.page(id);
  }

  /** A new page (or a replaced one), with its body. */
  private put(p: Page, body: string) {
    const f = factsOf(body);
    const full = { ...p, preview: f.preview, steps: f.steps, done: f.done };
    this.set([full]);
    this.write((b) => b.upsert([rowOf(full, body)]));
  }

  /** Changes a page's details (title, icon, values, columns…), not its body. */
  update(p: Page, touch = true) {
    const q = touch ? { ...p, updated: this.now } : p;
    this.set([q]);
    this.write((b) => b.upsert([rowOf(q)]));
  }

  private lastOrd(parent: string | null) {
    const kids = this.ix.children(parent);
    return kids.length ? Math.max(...kids.map((k) => k.ord)) : undefined;
  }

  newPage(parent: string | null, o: { kind?: PageKind; title?: string; body?: string; props?: Record<string, string>; first?: boolean; view?: string } = {}): Page {
    const kind = o.kind ?? "DOC";
    const parentPage = this.page(parent);
    const siblings = this.ix.children(parent);
    const ord = o.first ? between(undefined, siblings[0]?.ord) : between(this.lastOrd(parent), undefined);
    const collection = kind === "COLLECTION";
    const table = o.view === "table";
    const color = collection ? 0xfffbbf24 : parentPage && parentPage.id !== INBOX_ID ? parentPage.color : 0xffa78bfa;
    const p = mkPage({
      id: newId(),
      parent,
      title: o.title ?? "",
      icon: collection ? (table ? "table" : "board") : "doc",
      color,
      shape: collection ? 3 : 0,
      kind,
      ord,
      created: this.now,
      updated: this.now,
      opened: kind === "DOC" && parentPage?.kind !== "COLLECTION" ? this.now : 0,
      props: o.props ?? {},
      schema: collection ? defaultSchema(table ? "table" : "board") : null,
    });
    this.put(p, o.body ?? "");
    return this.page(p.id) ?? p;
  }

  /** A thought caught in a hurry: into the Bandeja (or another page), the newest first. */
  capture(text: string, parent = INBOX_ID): Page | null {
    if (!text.trim()) return null;
    const [title, body] = captureOf(text);
    return this.newPage(parent, { title, body, first: true });
  }

  /** Builds a template's pages; the top one. */
  create(d: Draft): Page {
    const all = flatten(d).map(({ page: p, body }) => {
      const f = factsOf(body);
      return { page: { ...p, preview: f.preview, steps: f.steps, done: f.done }, body };
    });
    this.set(all.map((a) => a.page));
    this.write((b) => b.upsert(all.map((a) => rowOf(a.page, a.body))));
    return this.page(d.page.id) ?? d.page;
  }

  /** The page's text (after any pending write). */
  async body(id: string): Promise<string> {
    await this.flush();
    try {
      return (await this.backend?.body(id)) ?? "";
    } catch {
      return "";
    }
  }

  saveBody(id: string, title: string, body: string) {
    const old = this.page(id);
    if (!old) return;
    const f = factsOf(body);
    const p = { ...old, title, preview: f.preview, steps: f.steps, done: f.done, updated: this.now };
    this.set([p]);
    this.write((b) => b.upsert([rowOf(p, body)]));
  }

  opened(id: string) {
    const p = this.page(id);
    if (p) this.update({ ...p, opened: this.now }, false);
  }

  /** Moves a page under another (at the end, or at `ord`); not into itself. */
  move(id: string, parent: string | null, ord?: number): boolean {
    const p = this.page(id);
    if (!p || id === INBOX_ID) return false;
    if (parent !== null && this.ix.isInside(parent, id)) return false;
    this.update({ ...p, parent, ord: ord ?? between(this.lastOrd(parent), undefined) });
    return true;
  }

  /** Places a page right before `target` among target's siblings. */
  placeBefore(id: string, target: string): boolean {
    const t = this.page(target);
    if (!t || id === target) return false;
    const sibs = this.ix.children(t.parent).filter((s) => s.id !== id);
    const i = sibs.findIndex((s) => s.id === target);
    return this.move(id, t.parent, between(sibs[i - 1]?.ord, t.ord));
  }

  /** To the Papelera, with everything inside it. */
  trash(id: string) {
    if (id === INBOX_ID) return;
    const t = this.now;
    const all = this.ix
      .subtree(id)
      .filter((p) => p.trashed === 0)
      .map((p) => ({ ...p, trashed: t }));
    this.set(all);
    this.write((b) => b.upsert(all.map((p) => rowOf(p))));
  }

  /** Back from the Papelera (what went with it comes back too). If its parent is gone, to the top. */
  restore(id: string) {
    const p = this.page(id);
    if (!p) return;
    const t = p.trashed;
    const back = this.ix
      .subtree(id)
      .filter((q) => q.trashed === t)
      .map((q) => ({ ...q, trashed: 0 }))
      .map((q) => (q.id === id && (this.page(q.parent)?.trashed ?? 0) !== 0 ? { ...q, parent: null } : q));
    this.set(back);
    this.write((b) => b.upsert(back.map((q) => rowOf(q))));
  }

  /** Gone for good, with everything inside. */
  purge(id: string) {
    const ids = this.ix.subtree(id).map((p) => p.id);
    this.set([], new Set(ids));
    this.write((b) => b.remove(ids));
  }

  emptyTrash() {
    for (const p of this.ix.trash) this.purge(p.id);
  }

  /** Pages matching `query`, best first, with a piece of their text. */
  async search(query: string): Promise<Hit[]> {
    if (!query.trim() || !this.backend) return [];
    await this.flush();
    const rows = await this.backend.dump();
    const out: { page: Page; snippet: string; score: number }[] = [];
    for (const r of rows) {
      const p = this.ix.page(r.id);
      if (!p || p.trashed || p.id === INBOX_ID) continue;
      const plain = plainOf(r.body ?? "");
      const s = searchScore(query, p.title, `${plain} ${Object.values(p.props).join(" ")}`);
      if (s > 0) out.push({ page: p, snippet: snippet(query, plain), score: s });
    }
    return out
      .sort((a, b) => b.score - a.score || b.page.updated - a.page.updated)
      .slice(0, 50)
      .map(({ page: p, snippet: sn }) => ({ page: p, snippet: sn }));
  }

  // ── Copies ──────────────────────────────────────────────────────────────────

  async exportCopy(): Promise<string | null> {
    if (!this.backend) return null;
    await this.flush();
    const rows = await this.backend.dump();
    const list: CopyRow[] = rows.map((r) => ({ page: pageOfRow(r), body: r.body ?? "" }));
    return this.backend.saveCopy(copyJson(list, this.now), `tokkip-notas-${dayKey(this.now)}.json`);
  }

  /** Brings a copy in (replacing everything when `replace`): how many pages, -1 if it isn't one, null if cancelled. */
  async importCopy(replace: boolean, text?: string): Promise<number | null> {
    if (!this.backend) return null;
    const raw = text ?? (await this.backend.openCopy());
    if (raw == null) return null;
    const list = copyOf(raw);
    if (!list) return -1;
    const withFacts = list.map(({ page: p, body }) => {
      const f = factsOf(body);
      return { page: { ...p, preview: f.preview, steps: f.steps, done: f.done }, body };
    });
    await this.flush();
    await this.backend.bulk(copyRows(withFacts), replace);
    const rows = await this.backend.load();
    this.ix = new Index(rows.map(pageOfRow));
    if (!this.ix.page(INBOX_ID)) this.put(mkPage({ id: INBOX_ID, title: "Bandeja", icon: "inbox", color: 0xff94a3b8, created: this.now, updated: this.now }), "");
    for (const l of this.listeners) l(this.ix);
    return list.length;
  }

  where(): Promise<string> {
    return this.backend?.where() ?? Promise.resolve("");
  }
}

export const Store = new StoreImpl();
