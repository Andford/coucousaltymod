// The Wellness pill's clock and its moments (coucousaltymod): what happens when
// a Pomodoro block or a breathing session ends, and every action the card
// offers. The rules are in core/wellness.ts, the card in views/wellness.ts.
//
// The clock only runs while something is moving (a running Pomodoro, a
// breathing session): one tick a second, and the page is told only while the
// island is open, so a hidden island costs nothing.

import {
  Wellness, WELLNESS_ID, addTask, breathFrame, clearDone, finishPhase, isPaused, isRunning, newTaskId, pause,
  removeTask, resetPomodoro, resume, startBreathing, startFocus, tickPomodoro, toggleTask, wellnessBusy,
  type PomodoroEvent, type WellnessTab,
} from "../core/wellness";
import { State } from "../core/state";
import { Sound } from "../core/sound";
import type { BotEmoteName, IslandViewName } from "../core/layout";

export interface WellnessHost {
  emote(name: BotEmoteName): void;
  /** Opens the island on this view (an alert). */
  alert(view: IslandViewName): void;
  setPinned(on: boolean): void;
  ensureRunning(): void;
}

let host: WellnessHost | null = null;
let timer: ReturnType<typeof setInterval> | null = null;
/** The breathing session pinned the island open, and must let it go. */
let pinnedForBreath = false;

export function registerWellness(h: WellnessHost) {
  host = h;
  // A Pomodoro that ran while Coucou was closed: catch up, quietly.
  const now = Date.now();
  if (isRunning(Wellness.pomodoro) && (Wellness.pomodoro.endsAt ?? 0) <= now) {
    Wellness.pomodoro = resetPomodoro(Wellness.pomodoro);
    Wellness.savePomodoro();
  }
  syncClock();
}

function syncClock() {
  const busy = wellnessBusy(Date.now());
  if (busy && timer == null) timer = setInterval(tick, 1000);
  else if (!busy && timer != null) {
    clearInterval(timer);
    timer = null;
  }
}

function tick() {
  const now = Date.now();
  const { next, event } = tickPomodoro(Wellness.pomodoro, now);
  if (event) {
    Wellness.pomodoro = next;
    Wellness.savePomodoro();
    announce(event);
  }
  const b = breathFrame(Wellness.breathing, now);
  if (b.done) finishBreathing(true);
  syncClock();
  if (State.mode === "expanded" || event) State.notify();
}

/** A phase ended on its own: Mochi says so, and the island opens on the card. */
function announce(event: PomodoroEvent) {
  if (!event) return;
  Sound.play(event === "focusDone" ? "finish" : "greet");
  showCard();
  host?.emote(event === "focusDone" ? "proud" : "happy");
}

function showCard() {
  if (State.paused) return;
  Wellness.tab = Wellness.breathing.startedAt != null ? "breathe" : "focus";
  if (State.tasks.some((t) => t.id === WELLNESS_ID)) State.setFocus(WELLNESS_ID);
  host?.alert("overview");
}

function finishBreathing(completed: boolean) {
  Wellness.breathing = { ...Wellness.breathing, startedAt: null };
  if (pinnedForBreath) {
    pinnedForBreath = false;
    host?.setPinned(false);
  }
  if (completed) {
    Sound.play("love");
    host?.emote("love");
  }
  syncClock();
  State.notify();
}

// ── What the card can do ──────────────────────────────────────────────────────

export const WellnessControl = {
  setTab(tab: WellnessTab) {
    if (Wellness.tab === tab) return;
    Wellness.tab = tab;
    Wellness.saveTab();
    Sound.play("blip");
    State.notify();
  },

  addTask(text: string): boolean {
    const now = Date.now();
    const before = Wellness.tasks.length;
    Wellness.tasks = addTask(Wellness.tasks, text, now, newTaskId(now));
    if (Wellness.tasks.length === before) return false;
    Wellness.saveTasks();
    Sound.play("pop");
    State.notify();
    return true;
  },

  toggleTask(id: string) {
    const was = Wellness.tasks.find((t) => t.id === id);
    if (!was) return;
    Wellness.tasks = toggleTask(Wellness.tasks, id);
    Wellness.saveTasks();
    if (!was.done) {
      // Done: Mochi's little jump.
      Sound.play("approve");
      host?.emote("happy");
    } else {
      Sound.play("blip");
    }
    State.notify();
  },

  removeTask(id: string) {
    Wellness.tasks = removeTask(Wellness.tasks, id);
    Wellness.saveTasks();
    Sound.play("blip");
    State.notify();
  },

  clearDone() {
    Wellness.tasks = clearDone(Wellness.tasks);
    Wellness.saveTasks();
    Sound.play("blip");
    State.notify();
  },

  /** Start, pause or resume — the card's main button. */
  playPause() {
    const now = Date.now();
    const p = Wellness.pomodoro;
    if (p.phase === "idle") {
      Wellness.pomodoro = startFocus(p, now);
      Sound.play("work");
      host?.emote("proud");
    } else if (isPaused(p)) {
      Wellness.pomodoro = resume(p, now);
      Sound.play("blip");
    } else {
      Wellness.pomodoro = pause(p, now);
      Sound.play("blip");
    }
    Wellness.savePomodoro();
    syncClock();
    State.notify();
  },

  /** Ends the phase now; a skipped focus block does not count. */
  skip() {
    if (Wellness.pomodoro.phase === "idle") return;
    const { next } = finishPhase(Wellness.pomodoro, Date.now(), undefined, false);
    Wellness.pomodoro = next;
    Wellness.savePomodoro();
    Sound.play("blip");
    syncClock();
    State.notify();
  },

  reset() {
    Wellness.pomodoro = resetPomodoro(Wellness.pomodoro);
    Wellness.savePomodoro();
    Sound.play("blip");
    syncClock();
    State.notify();
  },

  startBreathing(minutes: number) {
    Wellness.breathing = startBreathing(minutes, Date.now());
    Sound.play("open");
    // The island stays open for the whole session, as a watch's would.
    if (!State.isPinned) {
      pinnedForBreath = true;
      host?.setPinned(true);
    }
    host?.ensureRunning();
    syncClock();
    State.notify();
  },

  stopBreathing() {
    if (Wellness.breathing.startedAt == null) return;
    finishBreathing(false);
    Sound.play("close");
  },
};
