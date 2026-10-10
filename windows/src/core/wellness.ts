// The Wellness pill (coucousaltymod): a task list, a Pomodoro timer and guided
// breathing, all local. This file is the pure part — state, rules, storage —
// so it can be tested without a page. The card is views/wellness.ts, the
// clock and the alerts island/wellness.ts, Mochi's breathing mochi/engine.ts.
//
// Everything lives in this window's localStorage (as the plan usage does):
// nothing leaves the PC, and a missing or broken store just starts empty.

export const WELLNESS_ID = "integration_wellness";
export const WELLNESS_COLOR = "#A78BFA";

export type WellnessTab = "tasks" | "focus" | "breathe";

// ── Tasks ─────────────────────────────────────────────────────────────────────

export interface Task {
  id: string;
  text: string;
  done: boolean;
  /** Unix ms. */
  createdAt: number;
}

/** Longest task text kept; the card shows one line anyway. */
export const TASK_MAX_LEN = 200;
/** A list that grows past this drops its oldest finished tasks. */
export const TASK_LIMIT = 100;

export function addTask(tasks: readonly Task[], text: string, now: number, id: string): Task[] {
  const clean = text.replace(/\s+/g, " ").trim().slice(0, TASK_MAX_LEN);
  if (!clean) return [...tasks];
  const next = [...tasks, { id, text: clean, done: false, createdAt: now }];
  while (next.length > TASK_LIMIT) {
    const i = next.findIndex((t) => t.done);
    if (i < 0) break;
    next.splice(i, 1);
  }
  return next;
}

export function toggleTask(tasks: readonly Task[], id: string): Task[] {
  return tasks.map((t) => (t.id === id ? { ...t, done: !t.done } : t));
}

export function removeTask(tasks: readonly Task[], id: string): Task[] {
  return tasks.filter((t) => t.id !== id);
}

export function clearDone(tasks: readonly Task[]): Task[] {
  return tasks.filter((t) => !t.done);
}

/** Pending first (oldest on top), then the finished ones. */
export function orderedTasks(tasks: readonly Task[]): Task[] {
  return [...tasks.filter((t) => !t.done), ...tasks.filter((t) => t.done)];
}

export function pendingCount(tasks: readonly Task[]): number {
  return tasks.filter((t) => !t.done).length;
}

/** What a stored value becomes: only well-formed tasks survive. */
export function parseTasks(raw: unknown): Task[] {
  if (!Array.isArray(raw)) return [];
  const out: Task[] = [];
  for (const t of raw) {
    if (!t || typeof t !== "object") continue;
    const o = t as Record<string, unknown>;
    if (typeof o.id !== "string" || typeof o.text !== "string" || !o.text.trim()) continue;
    out.push({
      id: o.id,
      text: o.text.slice(0, TASK_MAX_LEN),
      done: o.done === true,
      createdAt: typeof o.createdAt === "number" ? o.createdAt : 0,
    });
  }
  return out.slice(-TASK_LIMIT);
}

// ── Pomodoro ──────────────────────────────────────────────────────────────────

export type PomodoroPhase = "idle" | "focus" | "break" | "longBreak";

export interface PomodoroConfig {
  focusMin: number;
  breakMin: number;
  longBreakMin: number;
  /** A long break after this many focus blocks. */
  cyclesBeforeLong: number;
}

export const DEFAULT_POMODORO: PomodoroConfig = { focusMin: 25, breakMin: 5, longBreakMin: 15, cyclesBeforeLong: 4 };

export interface Pomodoro {
  phase: PomodoroPhase;
  /** Unix ms the running phase ends; null while paused or idle. */
  endsAt: number | null;
  /** Seconds left, while paused. */
  pausedLeft: number | null;
  /** Focus blocks finished since the last long break. */
  completed: number;
  /** Focus blocks finished today (for the card). */
  today: number;
  /** The day `today` counts (YYYY-MM-DD, local). */
  day: string;
}

export const IDLE_POMODORO: Pomodoro = { phase: "idle", endsAt: null, pausedLeft: null, completed: 0, today: 0, day: "" };

export function dayKey(nowMs: number): string {
  const d = new Date(nowMs);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function phaseMinutes(phase: PomodoroPhase, cfg: PomodoroConfig = DEFAULT_POMODORO): number {
  switch (phase) {
    case "focus": return cfg.focusMin;
    case "break": return cfg.breakMin;
    case "longBreak": return cfg.longBreakMin;
    default: return cfg.focusMin;
  }
}

/** Seconds left in the phase (the full focus block while idle). */
export function pomodoroLeft(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): number {
  if (p.phase === "idle") return cfg.focusMin * 60;
  if (p.pausedLeft != null) return p.pausedLeft;
  if (p.endsAt == null) return phaseMinutes(p.phase, cfg) * 60;
  return Math.max(0, Math.ceil((p.endsAt - nowMs) / 1000));
}

/** 0…1 of the phase gone by. */
export function pomodoroProgress(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): number {
  if (p.phase === "idle") return 0;
  const total = phaseMinutes(p.phase, cfg) * 60;
  return Math.min(1, Math.max(0, 1 - pomodoroLeft(p, nowMs, cfg) / total));
}

export function isRunning(p: Pomodoro): boolean {
  return p.phase !== "idle" && p.endsAt != null && p.pausedLeft == null;
}

export function isPaused(p: Pomodoro): boolean {
  return p.phase !== "idle" && p.pausedLeft != null;
}

function withDay(p: Pomodoro, nowMs: number): Pomodoro {
  const day = dayKey(nowMs);
  return p.day === day ? p : { ...p, day, today: 0 };
}

export function startFocus(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): Pomodoro {
  return { ...withDay(p, nowMs), phase: "focus", endsAt: nowMs + cfg.focusMin * 60_000, pausedLeft: null };
}

export function pause(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): Pomodoro {
  if (!isRunning(p)) return p;
  return { ...p, pausedLeft: pomodoroLeft(p, nowMs, cfg), endsAt: null };
}

export function resume(p: Pomodoro, nowMs: number): Pomodoro {
  if (!isPaused(p)) return p;
  return { ...p, endsAt: nowMs + (p.pausedLeft ?? 0) * 1000, pausedLeft: null };
}

/** Back to idle; the day's count stays. */
export function resetPomodoro(p: Pomodoro): Pomodoro {
  return { ...p, phase: "idle", endsAt: null, pausedLeft: null, completed: 0 };
}

export type PomodoroEvent = "focusDone" | "breakDone" | null;

/**
 * Ends the phase now (its time ran out, or "skip"): a focus block leads to a
 * break — a long one every `cyclesBeforeLong` — and a break back to focus.
 * Only a focus block that ran out on its own counts as done.
 */
export function finishPhase(
  p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO, counted = true,
): { next: Pomodoro; event: PomodoroEvent } {
  const base = withDay(p, nowMs);
  if (p.phase === "focus") {
    const completed = counted ? base.completed + 1 : base.completed;
    const long = counted && completed >= cfg.cyclesBeforeLong;
    const phase: PomodoroPhase = long ? "longBreak" : "break";
    return {
      next: {
        ...base,
        phase,
        completed: long ? 0 : completed,
        today: counted ? base.today + 1 : base.today,
        endsAt: nowMs + phaseMinutes(phase, cfg) * 60_000,
        pausedLeft: null,
      },
      event: "focusDone",
    };
  }
  if (p.phase === "break" || p.phase === "longBreak") {
    return { next: { ...base, phase: "focus", endsAt: nowMs + cfg.focusMin * 60_000, pausedLeft: null }, event: "breakDone" };
  }
  return { next: p, event: null };
}

/** The clock's job: a running phase whose time is up ends. */
export function tickPomodoro(
  p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO,
): { next: Pomodoro; event: PomodoroEvent } {
  if (!isRunning(p) || (p.endsAt ?? 0) > nowMs) return { next: p, event: null };
  return finishPhase(p, nowMs, cfg);
}

export function parsePomodoro(raw: unknown): Pomodoro {
  if (!raw || typeof raw !== "object") return { ...IDLE_POMODORO };
  const o = raw as Record<string, unknown>;
  const phase = ["idle", "focus", "break", "longBreak"].includes(o.phase as string) ? (o.phase as PomodoroPhase) : "idle";
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : null);
  return {
    phase,
    endsAt: phase === "idle" ? null : num(o.endsAt),
    pausedLeft: phase === "idle" ? null : num(o.pausedLeft),
    completed: Math.max(0, num(o.completed) ?? 0),
    today: Math.max(0, num(o.today) ?? 0),
    day: typeof o.day === "string" ? o.day : "",
  };
}

// ── Breathing ─────────────────────────────────────────────────────────────────

/** The sessions offered, in minutes. */
export const BREATHE_MINUTES = [1, 5, 10] as const;
/** Seconds in, out: a calm 6 breaths a minute. */
export const INHALE_S = 4;
export const EXHALE_S = 6;
const CYCLE_S = INHALE_S + EXHALE_S;

export interface Breathing {
  /** Unix ms the session started; null when none runs. */
  startedAt: number | null;
  minutes: number;
}

export const IDLE_BREATHING: Breathing = { startedAt: null, minutes: 1 };

export interface BreathFrame {
  active: boolean;
  phase: "in" | "out";
  /** 0…1 through the current inhale or exhale. */
  phaseProgress: number;
  /** How full Mochi is: 0 empty … 1 full, eased. */
  fill: number;
  /** Seconds left in the session. */
  left: number;
  /** Breaths taken so far. */
  breaths: number;
  done: boolean;
}

const easeInOut = (x: number) => 0.5 - 0.5 * Math.cos(Math.PI * Math.min(1, Math.max(0, x)));

export function startBreathing(minutes: number, nowMs: number): Breathing {
  return { startedAt: nowMs, minutes };
}

export function breathFrame(b: Breathing, nowMs: number): BreathFrame {
  if (b.startedAt == null) {
    return { active: false, phase: "in", phaseProgress: 0, fill: 0, left: 0, breaths: 0, done: false };
  }
  const total = b.minutes * 60;
  const elapsed = Math.max(0, (nowMs - b.startedAt) / 1000);
  const done = elapsed >= total;
  const t = Math.min(elapsed, total);
  const inCycle = t % CYCLE_S;
  const phase = inCycle < INHALE_S ? "in" : "out";
  const phaseProgress = phase === "in" ? inCycle / INHALE_S : (inCycle - INHALE_S) / EXHALE_S;
  const fill = done ? 0 : phase === "in" ? easeInOut(phaseProgress) : 1 - easeInOut(phaseProgress);
  return {
    active: !done,
    phase,
    phaseProgress,
    fill,
    left: Math.max(0, Math.ceil(total - elapsed)),
    breaths: Math.floor(t / CYCLE_S),
    done,
  };
}

// ── Formatting ────────────────────────────────────────────────────────────────

/** 1500 → "25:00". */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

// ── The page's copy, and its storage ─────────────────────────────────────────

const STORE = { tasks: "coucou.wellness.tasks", pomodoro: "coucou.wellness.pomodoro", tab: "coucou.wellness.tab" };

function read(key: string): unknown {
  try {
    const raw = globalThis.localStorage?.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown) {
  try {
    globalThis.localStorage?.setItem(key, JSON.stringify(value));
  } catch {
    /* storage is a nicety: the session goes on without it */
  }
}

function parseTab(raw: unknown): WellnessTab {
  return raw === "focus" || raw === "breathe" ? raw : "tasks";
}

export const Wellness = {
  tasks: parseTasks(read(STORE.tasks)),
  pomodoro: parsePomodoro(read(STORE.pomodoro)),
  breathing: { ...IDLE_BREATHING } as Breathing,
  tab: parseTab(read(STORE.tab)),

  saveTasks() {
    write(STORE.tasks, this.tasks);
  },
  savePomodoro() {
    write(STORE.pomodoro, this.pomodoro);
  },
  saveTab() {
    write(STORE.tab, this.tab);
  },
};

let seq = 0;
export function newTaskId(nowMs: number): string {
  seq = (seq + 1) % 1000;
  return `${nowMs.toString(36)}-${seq}-${Math.random().toString(36).slice(2, 6)}`;
}

/** Something on the card is moving: the clock must tick. */
export function wellnessBusy(nowMs: number): boolean {
  return isRunning(Wellness.pomodoro) || breathFrame(Wellness.breathing, nowMs).active;
}
