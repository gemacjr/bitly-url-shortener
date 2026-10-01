import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, getShortUrl, shortenUrl } from "./api";

function mockFetch(status: number, body: unknown) {
  const fn = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe("shortenUrl", () => {
  it("POSTs snake_case JSON to the proxied backend and omits empty options", async () => {
    const fetchMock = mockFetch(201, { short_code: "aB3xY9z" });

    await shortenUrl({ longUrl: "https://example.com", customAlias: "" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/urls");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ long_url: "https://example.com" });
  });

  it("sends alias and expiration when provided", async () => {
    const fetchMock = mockFetch(201, {});
    await shortenUrl({ longUrl: "https://e.com", customAlias: "promo", expirationDate: "2031-01-01T00:00:00.000Z" });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      long_url: "https://e.com",
      custom_alias: "promo",
      expiration_date: "2031-01-01T00:00:00.000Z",
    });
  });

  it("turns a ProblemDetail into an ApiError carrying status and detail", async () => {
    mockFetch(409, { title: "Conflict", status: 409, detail: "custom_alias 'promo' is already taken" });

    const err = await shortenUrl({ longUrl: "https://e.com", customAlias: "promo" }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.message).toBe("custom_alias 'promo' is already taken");
  });

  it("falls back to a generic message for non-JSON errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("Bad Gateway", { status: 502 })));
    await expect(shortenUrl({ longUrl: "https://e.com" })).rejects.toThrow("Request failed (502)");
  });
});

describe("getShortUrl", () => {
  it("URL-encodes the code", async () => {
    const fetchMock = mockFetch(200, {});
    await getShortUrl("a b");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/urls/a%20b");
  });
});
