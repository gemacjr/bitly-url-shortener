import { describe, expect, it } from "vitest";
import { toIsoInstant, validateForm } from "./validation";

const NOW = new Date("2030-01-01T12:00:00Z");
const base = { longUrl: "https://example.com", customAlias: "", expiresAt: "" };

describe("validateForm", () => {
  it("accepts a plain http(s) URL", () => {
    expect(validateForm(base, NOW)).toEqual({});
    expect(validateForm({ ...base, longUrl: "http://a.b/c?d=e" }, NOW)).toEqual({});
  });

  it.each([
    ["", "Enter a URL to shorten"],
    ["   ", "Enter a URL to shorten"],
    ["example.com", "That doesn't look like a valid URL"],
    ["ftp://example.com", "URL must start with http:// or https://"],
    ["javascript:alert(1)", "URL must start with http:// or https://"],
  ])("rejects long URL %j", (longUrl, message) => {
    expect(validateForm({ ...base, longUrl }, NOW).longUrl).toBe(message);
  });

  it.each(["ab", "has space", "a/b", "x".repeat(33)])("rejects malformed alias %j", (customAlias) => {
    expect(validateForm({ ...base, customAlias }, NOW).customAlias).toMatch(/3–32/);
  });

  it("rejects reserved aliases case-insensitively", () => {
    expect(validateForm({ ...base, customAlias: "URLS" }, NOW).customAlias).toBe('"URLS" is reserved');
  });

  it("accepts a valid alias", () => {
    expect(validateForm({ ...base, customAlias: "my_link-1" }, NOW)).toEqual({});
  });

  it("requires expiration to be in the future", () => {
    expect(validateForm({ ...base, expiresAt: "2029-12-31T00:00" }, NOW).expiresAt).toBe(
      "Expiration must be in the future",
    );
    expect(validateForm({ ...base, expiresAt: "2031-01-01T00:00" }, NOW)).toEqual({});
  });
});

describe("toIsoInstant", () => {
  it("converts datetime-local to an ISO instant, and empty to undefined", () => {
    expect(toIsoInstant("")).toBeUndefined();
    expect(toIsoInstant("2031-01-01T00:00")).toBe(new Date("2031-01-01T00:00").toISOString());
  });
});
