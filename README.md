# Bitly — URL shortener

A full-stack implementation of the [hellointerview Bitly breakdown](https://www.hellointerview.com/learn/system-design/problem-breakdowns/bitly):
Spring Boot 4 backend, Next.js 16 + Tailwind 4 frontend, DynamoDB + Redis (LocalStack/Docker locally, AWS in production),
with JUnit, Vitest, Postman and browser e2e tests.

```
 Browser ──► Next.js :3001 ──/api/*──► Spring Boot :8090 ──► Redis :6380      (counter INCRBY + redirect cache)
    │                                        │
    └──── GET /{short_code} ─────────────────┘ ──────────► DynamoDB (LocalStack :4567)  urls table, TTL
```

## Quick start

```bash
./scripts/local.sh start     # LocalStack + Redis in Docker, backend, frontend; waits until healthy
open http://localhost:3001
./scripts/local.sh test      # JUnit (unit + Testcontainers), Vitest, Postman/newman, browser e2e
./scripts/local.sh stop      # or: down | status | logs [backend|frontend] | reset
```

Requires Docker, Java 25, Maven and Node 22+. Everything in containers: `docker compose --profile app up -d --build`.

## API

| Method | Path | Result |
|---|---|---|
| `POST` | `/urls` | `201` `{short_url, short_code, long_url, custom_alias, created_at, expiration_date, expired}` |
| `GET` | `/{short_code}` | `302` + `Location`; `404` unknown; `410` expired |
| `GET` | `/urls/{short_code}` | `200` metadata (expired links included, flagged `expired: true`) |

```bash
curl -i -X POST localhost:8090/urls -H 'Content-Type: application/json' \
  -d '{"long_url":"https://example.com/very/long","custom_alias":"promo","expiration_date":"2030-01-01T00:00:00Z"}'
curl -i localhost:8090/promo
```

Errors are RFC 9457 `application/problem+json` (`400` validation, `409` alias taken).

## How the design maps to code

| Design decision (hellointerview) | Where |
|---|---|
| **Counter + base62**, 7 chars = 3.5 trillion codes | `shortcode/ShortCodeGenerator`, `Base62` |
| **Counter batching**: each instance reserves 1000 values with one atomic Redis `INCRBY` | `RedisCounterService`, `ShortCodeGenerator.nextCounterValue` |
| Predictable codes → bijective scramble `(n·P + C) mod 62⁷`, so uniqueness is preserved | `ShortCodeGenerator.encode` |
| **Uniqueness safety net** (UNIQUE constraint) → DynamoDB `attribute_not_exists` conditional put | `UrlRepository.saveIfAbsent` |
| Custom aliases share the namespace; a generated code that hits an alias is retried | `UrlService.saveWithGeneratedCode` |
| **302 not 301** (keeps control; expiry/deletion take effect) + `Cache-Control: no-store` | `RedirectController` |
| **Cache-aside** for the 1000:1 read path, TTL = min(24h, time to expiry) | `UrlCache` |
| Availability over consistency: a Redis outage degrades to a DynamoDB read, not an error | `UrlCache.get/put` |
| **Expiration**: `410 Gone`; DynamoDB TTL on `expires_at` replaces the cleanup cron | `UrlService.resolve`, `UrlTableInitializer` |
| Read/write service split | `RedirectController` (read) vs `UrlController` (write), deployable separately |

## Tests

| Suite | Command | What |
|---|---|---|
| Backend unit (65) | `cd backend && mvn test` | base62, batching, scramble injectivity, concurrency, cache TTL, validation, service (Mockito), MVC contracts |
| Backend integration (5) | `cd backend && mvn verify` | Testcontainers LocalStack DynamoDB + Redis: create → redirect → cache, alias conflict, expiry → 410 |
| Frontend unit (26) | `cd frontend && npm test` | Vitest + Testing Library: validation, API client, form flows, localStorage |
| API (42 assertions) | `npx newman run postman/Bitly.postman_collection.json -e postman/Bitly-Local.postman_environment.json` | Every endpoint, status code and error; import both files into Postman to run interactively |
| Browser e2e | `cd frontend && npm run e2e` | Headless Chrome through the real UI, desktop and mobile/dark screenshots |

The Postman collection is generated from `postman/generate_collection.py`; edit it there and re-run the script.

## Real AWS

Run with `SPRING_PROFILES_ACTIVE=aws`: there is no endpoint override, credentials come from the default provider chain, and the
table is not auto-created. Provision a DynamoDB table `bitly-urls` (hash key `short_code` S, TTL on `expires_at`) and an
ElastiCache Redis/Valkey cluster, then set `BASE_URL`, `REDIS_HOST`, `URLS_TABLE` and `AWS_REGION`.
