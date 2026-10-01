import { InspectLink } from "@/components/InspectLink";
import { ShortenForm } from "@/components/ShortenForm";

export default function Home() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col px-4 py-10 sm:py-16">
      <header className="mb-10 flex items-center justify-between">
        <span className="text-2xl font-black tracking-tight">
          snip<span className="text-accent">.</span>
        </span>
        <a
          href="https://www.hellointerview.com/learn/system-design/problem-breakdowns/bitly"
          target="_blank"
          rel="noreferrer"
          className="text-sm text-muted hover:text-foreground"
        >
          System design ↗
        </a>
      </header>

      <main className="flex-1 space-y-10">
        <div>
          <h1 className="text-4xl font-black tracking-tight sm:text-5xl">
            Long links in,
            <br />
            <span className="text-accent">short links out.</span>
          </h1>
          <p className="mt-3 max-w-xl text-muted">
            Paste a URL to get a 7-character link. Add a custom alias or an expiration date if you need one.
          </p>
        </div>

        <ShortenForm />
        <InspectLink />
      </main>

      <footer className="mt-16 text-xs text-muted">
        Spring Boot · DynamoDB · Redis · Next.js. Redirects are 302 so expiration always takes effect.
      </footer>
    </div>
  );
}
