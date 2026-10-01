import type { ShortUrl } from "@/lib/api";
import { CopyButton } from "./CopyButton";

export function formatExpiry(link: Pick<ShortUrl, "expiration_date" | "expired">): string {
  if (!link.expiration_date) return "Never expires";
  const when = new Date(link.expiration_date).toLocaleString();
  return link.expired ? `Expired ${when}` : `Expires ${when}`;
}

export function LinkRow({ link }: { link: ShortUrl }) {
  return (
    <li className="flex items-center gap-3 py-3">
      <div className="min-w-0 flex-1">
        <a
          href={link.short_url}
          target="_blank"
          rel="noreferrer"
          className="font-mono text-sm font-semibold text-accent hover:underline"
        >
          {link.short_url.replace(/^https?:\/\//, "")}
        </a>
        <p className="truncate text-sm text-muted" title={link.long_url}>
          {link.long_url}
        </p>
      </div>
      <span className="hidden shrink-0 text-xs text-muted sm:block">{formatExpiry(link)}</span>
      <CopyButton text={link.short_url} />
    </li>
  );
}
