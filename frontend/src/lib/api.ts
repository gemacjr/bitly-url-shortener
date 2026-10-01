/** Mirrors the backend's snake_case wire format (hellointerview API). */
export type ShortUrl = {
  short_url: string;
  short_code: string;
  long_url: string;
  custom_alias: boolean;
  created_at: string;
  expiration_date: string | null;
  expired: boolean;
};

export type ShortenInput = {
  longUrl: string;
  customAlias?: string;
  /** ISO-8601 instant */
  expirationDate?: string;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const API_BASE = "/api";

async function parse<T>(res: Response): Promise<T> {
  if (res.ok) return (await res.json()) as T;
  // Spring returns RFC 9457 ProblemDetail: { title, status, detail }
  let detail = `Request failed (${res.status})`;
  try {
    const body = await res.json();
    detail = body.detail ?? body.title ?? detail;
  } catch {
    // non-JSON error (e.g. proxy can't reach backend)
  }
  throw new ApiError(res.status, detail);
}

export async function shortenUrl(input: ShortenInput): Promise<ShortUrl> {
  const res = await fetch(`${API_BASE}/urls`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      long_url: input.longUrl,
      custom_alias: input.customAlias || undefined,
      expiration_date: input.expirationDate || undefined,
    }),
  });
  return parse<ShortUrl>(res);
}

export async function getShortUrl(code: string): Promise<ShortUrl> {
  const res = await fetch(`${API_BASE}/urls/${encodeURIComponent(code)}`);
  return parse<ShortUrl>(res);
}
