import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ShortenForm } from "./ShortenForm";
import { ApiError, shortenUrl, type ShortUrl } from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return { ...actual, shortenUrl: vi.fn() };
});

const created: ShortUrl = {
  short_url: "http://localhost:8090/aB3xY9z",
  short_code: "aB3xY9z",
  long_url: "https://example.com/very/long",
  custom_alias: false,
  created_at: "2030-01-01T12:00:00Z",
  expiration_date: null,
  expired: false,
};

describe("ShortenForm", () => {
  beforeEach(() => {
    vi.mocked(shortenUrl).mockReset();
  });

  it("shortens a URL, shows the result and remembers it", async () => {
    vi.mocked(shortenUrl).mockResolvedValue(created);
    render(<ShortenForm />);

    await userEvent.type(screen.getByLabelText("Long URL"), "https://example.com/very/long");
    await userEvent.click(screen.getByRole("button", { name: "Shorten" }));

    const result = await screen.findByTestId("result");
    expect(result).toHaveTextContent("http://localhost:8090/aB3xY9z");
    expect(result).toHaveTextContent("Never expires");
    expect(shortenUrl).toHaveBeenCalledWith({
      longUrl: "https://example.com/very/long",
      customAlias: "",
      expirationDate: undefined,
    });
    expect(screen.getByText("Recent in this browser")).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("bitly:recent")!)).toHaveLength(1);
    expect(screen.getByLabelText("Long URL")).toHaveValue("");
  });

  it("shows client-side validation errors without calling the API", async () => {
    render(<ShortenForm />);

    await userEvent.type(screen.getByLabelText("Long URL"), "not a url");
    await userEvent.click(screen.getByRole("button", { name: "Shorten" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("That doesn't look like a valid URL");
    expect(shortenUrl).not.toHaveBeenCalled();
  });

  it("sends custom alias and shows a 409 next to the alias field", async () => {
    vi.mocked(shortenUrl).mockRejectedValue(new ApiError(409, "custom_alias 'promo' is already taken"));
    render(<ShortenForm />);

    await userEvent.type(screen.getByLabelText("Long URL"), "https://example.com");
    await userEvent.click(screen.getByRole("button", { name: /custom alias/i }));
    await userEvent.type(screen.getByLabelText(/Custom alias/), "promo");
    await userEvent.click(screen.getByRole("button", { name: "Shorten" }));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("custom_alias 'promo' is already taken"),
    );
    expect(vi.mocked(shortenUrl).mock.calls[0][0].customAlias).toBe("promo");
    expect(screen.getByLabelText(/Custom alias/)).toHaveAttribute("aria-invalid", "true");
  });

  it("shows a friendly message when the backend is unreachable", async () => {
    vi.mocked(shortenUrl).mockRejectedValue(new TypeError("fetch failed"));
    render(<ShortenForm />);

    await userEvent.type(screen.getByLabelText("Long URL"), "https://example.com");
    await userEvent.click(screen.getByRole("button", { name: "Shorten" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't reach the server");
  });
});
