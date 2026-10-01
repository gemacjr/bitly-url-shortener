import { describe, expect, it } from "vitest";
import type { ShortUrl } from "./api";
import { addRecent, loadRecent } from "./recent";

const link = (code: string): ShortUrl => ({
  short_url: `http://s/${code}`,
  short_code: code,
  long_url: "https://example.com",
  custom_alias: false,
  created_at: "2030-01-01T00:00:00Z",
  expiration_date: null,
  expired: false,
});

describe("recent links", () => {
  it("puts newest first, de-duplicates and caps at 10", () => {
    for (let i = 0; i < 12; i++) addRecent(link(`c${i}`));
    const list = addRecent(link("c5"));

    expect(list).toHaveLength(10);
    expect(list[0].short_code).toBe("c5");
    expect(list.filter((l) => l.short_code === "c5")).toHaveLength(1);
    expect(loadRecent()).toEqual(list);
  });

  it("survives corrupted storage", () => {
    localStorage.setItem("bitly:recent", "{not json");
    expect(loadRecent()).toEqual([]);
  });

  it("returns the same snapshot object while storage is unchanged", () => {
    addRecent(link("a"));
    expect(loadRecent()).toBe(loadRecent());
  });
});
