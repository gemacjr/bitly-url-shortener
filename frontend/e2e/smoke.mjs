// End-to-end smoke test: drives the real UI in headless Chrome against the running stack.
// Needs ./scripts/local.sh start (frontend :3001, backend :8090). Screenshots land in e2e/screenshots.
import { chromium } from "playwright-core";
import { mkdirSync } from "node:fs";

const S = new URL("./screenshots", import.meta.url).pathname;
mkdirSync(S, { recursive: true });
const BASE = process.env.BASE_URL ?? "http://localhost:3001";
const errors = [];
const step = (m) => console.log("✓", m);
const fail = (m) => { throw new Error(m); };

const browser = await chromium.launch({ channel: "chrome", headless: true }).catch(() => chromium.launch({ headless: true }));

async function page(opts = {}) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, ...opts });
  const p = await ctx.newPage();
  p.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
  p.on("console", (m) => {
    // The alias-conflict step deliberately triggers a 409; the browser logs every 4xx fetch.
    if (m.type() === "error" && !m.text().includes("status of 409")) errors.push(`console: ${m.text()}`);
  });
  return p;
}

try {
  // 1. Shorten a URL
  let p = await page();
  await p.goto(BASE, { waitUntil: "networkidle" });
  await p.screenshot({ path: `${S}/1-home.png` });
  await p.getByLabel("Long URL").fill("https://www.example.com/some/very/long/url");
  await p.getByRole("button", { name: "Shorten" }).click();
  const result = p.getByTestId("result");
  await result.waitFor();
  const shortUrl = await result.getByRole("link").first().textContent();
  if (!/^http:\/\/localhost:8090\/[0-9a-zA-Z]{7}$/.test(shortUrl)) fail(`unexpected short url ${shortUrl}`);
  await p.screenshot({ path: `${S}/2-shortened.png`, fullPage: true });
  step(`shortened -> ${shortUrl}`);

  // 2. The short link 302s to the long URL (checked without following it off-site)
  const res = await p.request.get(shortUrl, { maxRedirects: 0 });
  if (res.status() !== 302 || res.headers().location !== "https://www.example.com/some/very/long/url") {
    fail(`redirect was ${res.status()} -> ${res.headers().location}`);
  }
  step("short link redirects with 302");

  // 3. Custom alias + conflict shown inline
  const alias = `e2e-${Date.now().toString(36)}`;
  for (const attempt of [1, 2]) {
    await p.getByLabel("Long URL").fill("https://example.org/alias");
    if (attempt === 1) await p.getByRole("button", { name: /custom alias/i }).click();
    await p.getByLabel(/Custom alias/).fill(alias);
    await p.getByRole("button", { name: "Shorten" }).click();
    if (attempt === 1) await p.getByTestId("result").getByText(`http://localhost:8090/${alias}`).waitFor();
  }
  await p.getByRole("alert").getByText("already taken").waitFor();
  await p.screenshot({ path: `${S}/3-alias-conflict.png`, fullPage: true });
  step("custom alias works; second use shows 409 inline");

  // 4. Inspect a link
  await p.getByLabel("Short link or code").fill(`http://localhost:8090/${alias}`);
  await p.getByRole("button", { name: "Inspect" }).click();
  await p.getByText("https://example.org/alias", { exact: true }).last().waitFor();
  step("inspect shows destination");

  // 5. Recent list survives reload (localStorage)
  await p.reload();
  await p.getByText("Recent in this browser").waitFor();
  step("recent links persist across reload");

  // 6. Mobile + dark mode render
  p = await page({ viewport: { width: 390, height: 844 }, colorScheme: "dark" });
  await p.goto(BASE, { waitUntil: "networkidle" });
  await p.getByRole("button", { name: /custom alias/i }).click();
  const overflow = await p.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  if (overflow) fail("horizontal overflow on mobile");
  await p.screenshot({ path: `${S}/4-mobile-dark.png`, fullPage: true });
  step("mobile dark mode renders without horizontal scroll");

  if (errors.length) fail(`browser errors:\n${errors.join("\n")}`);
  console.log(`\nAll e2e checks passed. Screenshots in ${S}`);
} finally {
  await browser.close();
}
