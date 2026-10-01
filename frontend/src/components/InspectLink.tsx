"use client";

import { useState, type FormEvent } from "react";
import { ApiError, getShortUrl, type ShortUrl } from "@/lib/api";
import { formatExpiry } from "./LinkRow";

/** Look up where a short link goes without following it (GET /urls/{code}). */
export function InspectLink() {
  const [input, setInput] = useState("");
  const [link, setLink] = useState<ShortUrl | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    // Accept either "abc1234" or a full "http://host/abc1234".
    const code = input.trim().replace(/\/+$/, "").split("/").pop() ?? "";
    if (!code) return;
    setError(null);
    setLink(null);
    try {
      setLink(await getShortUrl(code));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't reach the server");
    }
  }

  return (
    <section className="rounded-2xl border border-line p-4 sm:p-6">
      <h2 className="font-semibold">Inspect a link</h2>
      <p className="mb-3 text-sm text-muted">See where a short link points before you click it.</p>
      <form onSubmit={onSubmit} className="flex gap-2">
        <label htmlFor="inspect" className="sr-only">
          Short link or code
        </label>
        <input
          id="inspect"
          placeholder="aB3xY9z or http://localhost:8090/aB3xY9z"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          className="field min-w-0 flex-1 font-mono text-sm"
        />
        <button type="submit" className="rounded-xl border border-line px-4 font-medium hover:bg-surface-2">
          Inspect
        </button>
      </form>
      {error && (
        <p role="alert" className="mt-3 text-sm text-danger">
          {error}
        </p>
      )}
      {link && (
        <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-muted">Code</dt>
          <dd className="font-mono">{link.short_code}</dd>
          <dt className="text-muted">Goes to</dt>
          <dd className="break-all">{link.long_url}</dd>
          <dt className="text-muted">Created</dt>
          <dd>{new Date(link.created_at).toLocaleString()}</dd>
          <dt className="text-muted">Status</dt>
          <dd className={link.expired ? "text-danger" : ""}>{formatExpiry(link)}</dd>
        </dl>
      )}
    </section>
  );
}
