"use client";

import { useState, type FormEvent } from "react";
import { ApiError, shortenUrl, type ShortUrl } from "@/lib/api";
import { addRecent, useRecentLinks } from "@/lib/recent";
import { toIsoInstant, validateForm, type FormErrors, type FormValues } from "@/lib/validation";
import { CopyButton } from "./CopyButton";
import { LinkRow, formatExpiry } from "./LinkRow";

const EMPTY: FormValues = { longUrl: "", customAlias: "", expiresAt: "" };

export function ShortenForm() {
  const [values, setValues] = useState<FormValues>(EMPTY);
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showOptions, setShowOptions] = useState(false);
  const [result, setResult] = useState<ShortUrl | null>(null);
  const recent = useRecentLinks();

  function update<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    setValues((v) => ({ ...v, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setServerError(null);
    const found = validateForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      if (found.customAlias || found.expiresAt) setShowOptions(true);
      return;
    }

    setSubmitting(true);
    try {
      const link = await shortenUrl({
        longUrl: values.longUrl.trim(),
        customAlias: values.customAlias.trim(),
        expirationDate: toIsoInstant(values.expiresAt),
      });
      setResult(link);
      addRecent(link);
      setValues(EMPTY);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Couldn't reach the server. Is the backend running?";
      if (err instanceof ApiError && err.status === 409) {
        setErrors({ customAlias: message });
        setShowOptions(true);
      } else {
        setServerError(message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-8">
      <form onSubmit={onSubmit} noValidate className="rounded-2xl border border-line bg-surface p-4 shadow-sm sm:p-6">
        <label htmlFor="longUrl" className="mb-2 block text-sm font-medium">
          Long URL
        </label>
        <div className="flex flex-col gap-3 sm:flex-row">
          <input
            id="longUrl"
            type="url"
            inputMode="url"
            autoComplete="off"
            placeholder="https://www.example.com/some/very/long/url"
            value={values.longUrl}
            onChange={(e) => update("longUrl", e.target.value)}
            aria-invalid={!!errors.longUrl}
            aria-describedby={errors.longUrl ? "longUrl-error" : undefined}
            className="field flex-1"
          />
          <button
            type="submit"
            disabled={submitting}
            className="rounded-xl bg-accent px-6 py-3 font-semibold text-accent-fg transition hover:brightness-110 active:scale-[0.98] disabled:opacity-60"
          >
            {submitting ? "Shortening…" : "Shorten"}
          </button>
        </div>
        {errors.longUrl && (
          <p id="longUrl-error" role="alert" className="mt-2 text-sm text-danger">
            {errors.longUrl}
          </p>
        )}

        <button
          type="button"
          onClick={() => setShowOptions((s) => !s)}
          aria-expanded={showOptions}
          className="mt-4 text-sm font-medium text-muted hover:text-foreground"
        >
          {showOptions ? "− Hide options" : "+ Custom alias & expiration"}
        </button>

        {showOptions && (
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="customAlias" className="mb-2 block text-sm font-medium">
                Custom alias <span className="font-normal text-muted">(optional)</span>
              </label>
              <input
                id="customAlias"
                placeholder="my-link"
                value={values.customAlias}
                onChange={(e) => update("customAlias", e.target.value)}
                aria-invalid={!!errors.customAlias}
                className="field w-full font-mono"
              />
              {errors.customAlias && (
                <p role="alert" className="mt-2 text-sm text-danger">
                  {errors.customAlias}
                </p>
              )}
            </div>
            <div>
              <label htmlFor="expiresAt" className="mb-2 block text-sm font-medium">
                Expires at <span className="font-normal text-muted">(optional)</span>
              </label>
              <input
                id="expiresAt"
                type="datetime-local"
                value={values.expiresAt}
                onChange={(e) => update("expiresAt", e.target.value)}
                aria-invalid={!!errors.expiresAt}
                className="field w-full"
              />
              {errors.expiresAt && (
                <p role="alert" className="mt-2 text-sm text-danger">
                  {errors.expiresAt}
                </p>
              )}
            </div>
          </div>
        )}

        {serverError && (
          <p role="alert" className="mt-4 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
            {serverError}
          </p>
        )}
      </form>

      {result && (
        <section
          aria-live="polite"
          className="rounded-2xl border border-accent/40 bg-accent/5 p-4 sm:p-6"
          data-testid="result"
        >
          <p className="text-sm text-muted">Your short link</p>
          <div className="mt-1 flex flex-wrap items-center gap-3">
            <a
              href={result.short_url}
              target="_blank"
              rel="noreferrer"
              className="break-all font-mono text-xl font-bold text-accent hover:underline sm:text-2xl"
            >
              {result.short_url}
            </a>
            <CopyButton text={result.short_url} />
          </div>
          <p className="mt-2 truncate text-sm text-muted" title={result.long_url}>
            → {result.long_url}
          </p>
          <p className="mt-1 text-xs text-muted">{formatExpiry(result)}</p>
        </section>
      )}

      {recent.length > 0 && (
        <section>
          <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-muted">Recent in this browser</h2>
          <ul className="divide-y divide-line">
            {recent.map((link) => (
              <LinkRow key={link.short_code} link={link} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
