import { useSyncExternalStore } from "react";
import type { ShortUrl } from "./api";

/** Links created in this browser. There are no accounts (auth is out of scope in the design). */
const KEY = "bitly:recent";
const MAX = 10;
const EMPTY: ShortUrl[] = [];

const listeners = new Set<() => void>();
// useSyncExternalStore needs a stable snapshot: re-parse only when the raw string changes.
let cachedRaw: string | null = null;
let cached: ShortUrl[] = EMPTY;

export function loadRecent(): ShortUrl[] {
  let raw: string | null;
  try {
    raw = localStorage.getItem(KEY);
  } catch {
    return EMPTY;
  }
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    try {
      cached = raw ? (JSON.parse(raw) as ShortUrl[]) : EMPTY;
    } catch {
      cached = EMPTY;
    }
  }
  return cached;
}

export function addRecent(link: ShortUrl): ShortUrl[] {
  const next = [link, ...loadRecent().filter((l) => l.short_code !== link.short_code)].slice(0, MAX);
  try {
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // storage full / disabled: nothing to remember
  }
  listeners.forEach((l) => l());
  return next;
}

function subscribe(onChange: () => void) {
  listeners.add(onChange);
  window.addEventListener("storage", onChange); // other tabs
  return () => {
    listeners.delete(onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** Empty on the server and during hydration, then the stored list. */
export function useRecentLinks(): ShortUrl[] {
  return useSyncExternalStore(subscribe, loadRecent, () => EMPTY);
}
