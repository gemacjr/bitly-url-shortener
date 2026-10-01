"use client";

import { useState } from "react";

export function CopyButton({ text, className = "" }: { text: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard blocked (insecure origin / permissions); the link is still selectable
    }
  }

  return (
    <button
      type="button"
      onClick={copy}
      className={`rounded-lg border border-line px-3 py-1.5 text-sm font-medium transition hover:bg-surface-2 active:scale-95 ${className}`}
      aria-label={`Copy ${text}`}
    >
      {copied ? "Copied" : "Copy"}
    </button>
  );
}
