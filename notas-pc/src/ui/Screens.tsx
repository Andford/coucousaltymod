// The other screens: templates (a sheet), search (Ctrl+K), the Papelera and
// Ajustes (Tokkip's look, theme, sounds and your copies).

import { useEffect, useRef, useState } from "preact/hooks";
import { Download, FolderOpen, RotateCcw, Search, Trash2, Upload } from "lucide-preact";
import { type Index, shownTitle } from "../data/model";
import { type Hit, Store } from "../data/store";
import { TEMPLATES, build } from "../data/templates";
import { Tokkip } from "../tokkip/Tokkip";
import { DEFAULT_LOOK, SPECIES, loadLook, onLookChange, saveLook, type TokkipLook } from "../tokkip/art";
import { CIRCLE, clipFor } from "../tokkip/shapes";
import { play, setSoundsOn, soundsOn } from "../tokkip/sound";
import { Confirm, Modal, PageIcon, SectionLabel, focusOnMount } from "./kit";
import { Nav, Templates, ThemeState, Toast, type Theme } from "./state";

export function TemplateSheet({ parent }: { parent: string | null }) {
  const ix = Store.index;
  const where = parent ? ix.page(parent) : undefined;
  const make = (i: number) => {
    const t = TEMPLATES[i];
    const sibs = ix.children(parent);
    const ord = sibs.length ? Math.max(...sibs.map((s) => s.ord)) + 1 : 0;
    const p = Store.create(build(t, parent, Date.now(), ord));
    Templates.close();
    play("pop");
    Nav.page(p.id);
  };
  return (
    <Modal title={where ? `Nueva página en «${shownTitle(where)}»` : "Nueva página"} onClose={Templates.close} wide>
      <p class="dim">Escoge con qué empezar. Todo se puede cambiar después.</p>
      <div class="template-grid">
        {TEMPLATES.map((t, i) => (
          <button key={t.id} class="template-tile" onClick={() => make(i)} ref={i === 0 ? focusOnMount : undefined}>
            <PageIcon p={t} size={40} />
            <b>{t.name}</b>
            <span class="dim2 small">{t.about}</span>
          </button>
        ))}
      </div>
    </Modal>
  );
}

// ── Search ────────────────────────────────────────────────────────────────────

export function SearchView({ onClose }: { onClose: () => void }) {
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<Hit[]>([]);
  const [sel, setSel] = useState(0);
  const seq = useRef(0);
  useEffect(() => {
    const n = ++seq.current;
    const t = setTimeout(async () => {
      const r = await Store.search(q);
      if (n === seq.current) {
        setHits(r);
        setSel(0);
      }
    }, 120);
    return () => clearTimeout(t);
  }, [q]);
  const recent = Store.index.recent.slice(0, 6);
  const list: { id: string; title: string; sub: string }[] = q.trim()
    ? hits.map((h) => ({ id: h.page.id, title: shownTitle(h.page), sub: h.snippet }))
    : recent.map((p) => ({ id: p.id, title: shownTitle(p), sub: p.preview }));
  const open = (id: string) => {
    onClose();
    Nav.page(id);
  };
  return (
    <div class="scrim top" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div class="palette" role="dialog" aria-label="Buscar">
        <div class="palette-input">
          <Search size={18} />
          <input
            ref={focusOnMount}
            value={q}
            placeholder="Buscar en todo"
            onInput={(e) => setQ(e.currentTarget.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape") onClose();
              if (e.key === "ArrowDown") {
                e.preventDefault();
                setSel((s) => Math.min(s + 1, list.length - 1));
              }
              if (e.key === "ArrowUp") {
                e.preventDefault();
                setSel((s) => Math.max(s - 1, 0));
              }
              if (e.key === "Enter" && list[sel]) open(list[sel].id);
            }}
          />
          <kbd>Esc</kbd>
        </div>
        <div class="palette-list">
          {!q.trim() && list.length > 0 && <div class="slash-head">Abiertas hace poco</div>}
          {q.trim() && !list.length && <div class="dim pad">Nada con «{q}».</div>}
          {list.map((r, i) => {
            const p = Store.page(r.id)!;
            return (
              <button key={r.id} class={`palette-row ${i === sel ? "on" : ""}`} onMouseEnter={() => setSel(i)} onClick={() => open(r.id)}>
                <PageIcon p={p} size={26} />
                <span class="palette-text">
                  <b>{r.title}</b>
                  {r.sub && <small>{r.sub}</small>}
                </span>
                <span class="dim2 small">{Store.index.path(r.id).map(shownTitle).join(" / ")}</span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ── Papelera ──────────────────────────────────────────────────────────────────

export function TrashView({ ix }: { ix: Index }) {
  const list = ix.trash;
  const [emptying, setEmptying] = useState(false);
  return (
    <div class="page-scroll">
      <div class="page-inner">
        <h1 class="screen-title">Papelera</h1>
        <p class="dim">Lo que tiras se queda aquí 30 días, con todo lo que tenía dentro.</p>
        {list.length === 0 ? (
          <p class="dim2 empty">Vacía.</p>
        ) : (
          <>
            <SectionLabel
              right={
                <button class="link danger" onClick={() => setEmptying(true)}>
                  Vaciar
                </button>
              }
            >
              {list.length === 1 ? "1 página" : `${list.length} páginas`}
            </SectionLabel>
            <div class="inbox-list">
              {list.map((p) => {
                const inside = ix.subtree(p.id).length - 1;
                const days = Math.max(0, 30 - Math.floor((Date.now() - p.trashed) / 86_400_000));
                return (
                  <div class="inbox-row" key={p.id}>
                    <div class="page-link static">
                      <PageIcon p={p} size={26} />
                      <span class="page-link-title">{shownTitle(p)}</span>
                      <span class="dim2 small">
                        {inside > 0 ? `${inside} dentro · ` : ""}
                        {days === 1 ? "se borra mañana" : `se borra en ${days} días`}
                      </span>
                    </div>
                    <button
                      class="btn ghost small"
                      onClick={() => {
                        Store.restore(p.id);
                        Toast.show("De vuelta");
                      }}
                    >
                      <RotateCcw size={14} /> Recuperar
                    </button>
                    <button class="icon-btn" title="Borrar para siempre" onClick={() => Store.purge(p.id)}>
                      <Trash2 size={15} />
                    </button>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>
      {emptying && <Confirm title="¿Vaciar la Papelera?" text="Se borra para siempre." ok="Vaciar" danger onClose={() => setEmptying(false)} onOk={() => Store.emptyTrash()} />}
    </div>
  );
}

// ── Ajustes ───────────────────────────────────────────────────────────────────

const BODIES = ["#F7EAD3", "#F5F6F8", "#C4B5FD", "#86EFAC", "#FBCFE8", "#FDE68A", "#BAE6FD", "#D6D3D1"];

export function SettingsView() {
  const [look, setLook] = useState<TokkipLook>(loadLook());
  const [sounds, setSounds] = useState(soundsOn());
  const [hop, setHop] = useState(0);
  const theme = ThemeState.use();
  const [where, setWhere] = useState("");
  const [restoring, setRestoring] = useState(false);
  useEffect(() => onLookChange(setLook), []);
  useEffect(() => {
    Store.where().then(setWhere);
  }, []);
  const change = (next: Partial<TokkipLook>) => {
    const l = { ...look, ...next };
    saveLook(l);
    setLook(l);
    setHop((h) => h + 1);
    play("greet", 0.4);
  };
  const restore = async (replace: boolean) => {
    const n = await Store.importCopy(replace);
    if (n == null) return;
    if (n < 0) Toast.show("Ese archivo no es una copia de Tokkip Notas");
    else {
      Toast.show(n === 1 ? "Llegó 1 página" : `Llegaron ${n} páginas`);
      play("level_up", 0.5);
      Nav.home();
    }
  };
  const themes: [Theme, string][] = [
    ["system", "Como Windows"],
    ["dark", "Oscuro"],
    ["light", "Claro"],
  ];
  return (
    <div class="page-scroll">
      <div class="page-inner">
        <h1 class="screen-title">Ajustes</h1>

        <SectionLabel>Tokkip</SectionLabel>
        <div class="panel settings-tokkip">
          <Tokkip mood="happy" size={110} jump={hop} />
          <div class="grow">
            <p class="dim">Te acompaña mientras escribes. El mismo de Tokkip, a tu gusto.</p>
            <div class="pills">
              {SPECIES.map((s) => (
                <button key={s.id} class={look.species === s.id ? "on" : ""} onClick={() => change({ species: s.id })}>
                  {s.label}
                </button>
              ))}
            </div>
            <div class="swatches">
              {BODIES.map((c) => {
                const on = look.body.toLowerCase() === c.toLowerCase();
                return <button key={c} aria-label={c} class={`swatch ${on ? "on" : ""}`} style={{ background: c, clipPath: on ? clipFor(0) : CIRCLE }} onClick={() => change({ body: c, accent: look.accent || DEFAULT_LOOK.accent })} />;
              })}
            </div>
          </div>
        </div>

        <SectionLabel>Aspecto</SectionLabel>
        <div class="panel">
          <div class="setting-row">
            <b>Tema</b>
            <div class="seg">
              {themes.map(([t, label]) => (
                <button key={t} class={theme === t ? "on" : ""} onClick={() => ThemeState.set(t)}>
                  {label}
                </button>
              ))}
            </div>
          </div>
          <label class="setting-row">
            <span>
              <b>Sonidos</b>
              <small class="dim2">Un pop al guardar una idea, un check al marcar un paso.</small>
            </span>
            <input
              type="checkbox"
              class="switch"
              checked={sounds}
              onChange={(e) => {
                setSoundsOn(e.currentTarget.checked);
                setSounds(e.currentTarget.checked);
                if (e.currentTarget.checked) play("pop");
              }}
            />
          </label>
        </div>

        <SectionLabel>Tus notas</SectionLabel>
        <div class="panel">
          <p class="dim">
            Todo vive en esta PC, sin cuentas ni internet. Una copia es un archivo que guardas donde quieras (USB, Drive…) y abres aquí o en Tokkip Notas de tu teléfono o tablet: es el mismo formato.
          </p>
          <div class="btn-row">
            <button
              class="btn primary"
              onClick={async () => {
                const name = await Store.exportCopy();
                if (name) Toast.show(`Copia guardada: ${name}`);
              }}
            >
              <Download size={16} /> Guardar una copia
            </button>
            <button class="btn" onClick={() => setRestoring(true)}>
              <Upload size={16} /> Abrir una copia
            </button>
          </div>
          {where && (
            <p class="dim2 small where">
              <FolderOpen size={13} /> {where} · una copia automática al día en «backups»
            </p>
          )}
        </div>
        <p class="dim2 small foot">Tokkip Notas para Windows {__APP_VERSION__} · sin internet, sin cuentas.</p>
      </div>
      {restoring && (
        <Modal
          title="¿Cómo la traigo?"
          onClose={() => setRestoring(false)}
          footer={
            <>
              <button class="btn ghost" onClick={() => setRestoring(false)}>
                Cancelar
              </button>
              <button
                class="btn danger"
                onClick={() => {
                  setRestoring(false);
                  restore(true);
                }}
              >
                Reemplazar
              </button>
              <button
                class="btn primary"
                onClick={() => {
                  setRestoring(false);
                  restore(false);
                }}
              >
                Juntar
              </button>
            </>
          }
        >
          <p class="dim">«Juntar» suma la copia a lo que ya tienes (si una página ya está, se queda la de la copia). «Reemplazar» deja solo lo de la copia.</p>
        </Modal>
      )}
    </div>
  );
}
