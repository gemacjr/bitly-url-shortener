#!/usr/bin/env python3
"""Generates Bitly.postman_collection.json (Postman v2.1). Edit here, then re-run:  python3 postman/generate_collection.py"""
import json
import pathlib

HERE = pathlib.Path(__file__).parent


def script(lines):
    return {"type": "text/javascript", "exec": lines}


def req(name, method, path, body=None, tests=(), pre=(), follow=True):
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": [{"key": "Content-Type", "value": "application/json"}] if body is not None else [],
            "url": {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": [p for p in path.split("/") if p]},
        },
        "event": [],
    }
    if body is not None:
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2), "options": {"raw": {"language": "json"}}}
    if pre:
        item["event"].append({"listen": "prerequest", "script": script(list(pre))})
    if tests:
        item["event"].append({"listen": "test", "script": script(list(tests))})
    if not follow:
        item["protocolProfileBehavior"] = {"followRedirects": False}
    return item


def problem(status, detail_contains):
    return [
        f'pm.test("status is {status}", () => pm.response.to.have.status({status}));',
        'pm.test("body is an RFC 9457 ProblemDetail", () => {',
        '    pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");',
        f'    pm.expect(pm.response.json().status).to.eql({status});',
        f'    pm.expect(pm.response.json().detail).to.include({json.dumps(detail_contains)});',
        '});',
    ]


write_path = {
    "name": "1. Write path (POST /urls)",
    "item": [
        req("Health check", "GET", "/actuator/health", tests=[
            'pm.test("backend is UP", () => {',
            '    pm.response.to.have.status(200);',
            '    pm.expect(pm.response.json().status).to.eql("UP");',
            '});',
        ]),
        req("Create short URL (generated code)", "POST", "/urls",
            body={"long_url": "https://www.example.com/some/very/long/url?utm_source=postman"},
            tests=[
                'pm.test("status is 201 Created", () => pm.response.to.have.status(201));',
                'const body = pm.response.json();',
                'pm.test("short_code is 7 base62 chars", () => pm.expect(body.short_code).to.match(/^[0-9a-zA-Z]{7}$/));',
                'pm.test("short_url is base_url + code", () => pm.expect(body.short_url).to.eql(pm.environment.get("shortBaseUrl") + "/" + body.short_code));',
                'pm.test("echoes long_url, never expires", () => {',
                '    pm.expect(body.long_url).to.eql("https://www.example.com/some/very/long/url?utm_source=postman");',
                '    pm.expect(body.expiration_date).to.be.null;',
                '    pm.expect(body.custom_alias).to.be.false;',
                '});',
                'pm.test("Location header points at metadata", () => pm.expect(pm.response.headers.get("Location")).to.eql("/urls/" + body.short_code));',
                'pm.collectionVariables.set("shortCode", body.short_code);',
            ]),
        req("Create another -> different code", "POST", "/urls",
            body={"long_url": "https://www.example.com/some/very/long/url?utm_source=postman"},
            tests=[
                'pm.test("status is 201", () => pm.response.to.have.status(201));',
                'pm.test("same long URL still gets a new, unique code", () =>',
                '    pm.expect(pm.response.json().short_code).to.not.eql(pm.collectionVariables.get("shortCode")));',
            ]),
        req("Create with custom alias", "POST", "/urls",
            body={"long_url": "https://www.hellointerview.com/learn/system-design/problem-breakdowns/bitly", "custom_alias": "{{alias}}"},
            pre=['pm.collectionVariables.set("alias", "pm-" + Date.now().toString(36));'],
            tests=[
                'pm.test("status is 201", () => pm.response.to.have.status(201));',
                'pm.test("short_code is the alias", () => {',
                '    pm.expect(pm.response.json().short_code).to.eql(pm.collectionVariables.get("alias"));',
                '    pm.expect(pm.response.json().custom_alias).to.be.true;',
                '});',
            ]),
        req("Custom alias already taken -> 409", "POST", "/urls",
            body={"long_url": "https://example.org", "custom_alias": "{{alias}}"},
            tests=problem(409, "already taken")),
    ],
}

read_path = {
    "name": "2. Read path (GET /{short_code})",
    "item": [
        req("Redirect generated code -> 302", "GET", "/{{shortCode}}", follow=False, tests=[
            'pm.test("302 Found (not 301, so browsers do not cache it)", () => pm.response.to.have.status(302));',
            'pm.test("Location is the long URL", () =>',
            '    pm.expect(pm.response.headers.get("Location")).to.eql("https://www.example.com/some/very/long/url?utm_source=postman"));',
            'pm.test("Cache-Control: no-store", () => pm.expect(pm.response.headers.get("Cache-Control")).to.eql("no-store"));',
            'pm.test("redirect is fast (< 100ms target)", () => pm.expect(pm.response.responseTime).to.be.below(500));',
        ]),
        req("Redirect again (served from Redis cache)", "GET", "/{{shortCode}}", follow=False, tests=[
            'pm.test("still 302", () => pm.response.to.have.status(302));',
            'pm.test("cached redirect is fast", () => pm.expect(pm.response.responseTime).to.be.below(100));',
        ]),
        req("Redirect custom alias -> 302", "GET", "/{{alias}}", follow=False, tests=[
            'pm.test("302 to the aliased URL", () => {',
            '    pm.response.to.have.status(302);',
            '    pm.expect(pm.response.headers.get("Location")).to.eql("https://www.hellointerview.com/learn/system-design/problem-breakdowns/bitly");',
            '});',
        ]),
        req("Get link metadata", "GET", "/urls/{{shortCode}}", tests=[
            'pm.test("status is 200", () => pm.response.to.have.status(200));',
            'const body = pm.response.json();',
            'pm.test("has the full snake_case shape", () => pm.expect(body).to.have.all.keys(',
            '    "short_url", "short_code", "long_url", "custom_alias", "created_at", "expiration_date", "expired"));',
            'pm.test("not expired", () => pm.expect(body.expired).to.be.false);',
        ]),
        req("Unknown code -> 404", "GET", "/doesNotExist", follow=False, tests=problem(404, "not found")),
    ],
}

validation = {
    "name": "3. Validation",
    "item": [
        req("Missing long_url -> 400", "POST", "/urls", body={}, tests=problem(400, "longUrl")),
        req("Not an http(s) URL -> 400", "POST", "/urls", body={"long_url": "ftp://example.com/file"}, tests=problem(400, "http")),
        req("Invalid alias characters -> 400", "POST", "/urls",
            body={"long_url": "https://example.com", "custom_alias": "no spaces!"}, tests=problem(400, "custom_alias")),
        req("Reserved alias -> 400", "POST", "/urls",
            body={"long_url": "https://example.com", "custom_alias": "urls"}, tests=problem(400, "reserved")),
        req("Expiration in the past -> 400", "POST", "/urls",
            body={"long_url": "https://example.com", "expiration_date": "2000-01-01T00:00:00Z"}, tests=problem(400, "future")),
        req("Shortening our own short link -> 400", "POST", "/urls",
            body={"long_url": "{{shortBaseUrl}}/abc1234"}, tests=problem(400, "must not point at this URL shortener")),
    ],
}

expiration = {
    "name": "4. Expiration",
    "item": [
        req("Create link expiring in 2s", "POST", "/urls",
            body={"long_url": "https://example.com/flash-sale", "custom_alias": "{{expAlias}}", "expiration_date": "{{expiresAt}}"},
            pre=[
                'pm.collectionVariables.set("expAlias", "exp-" + Date.now().toString(36));',
                'pm.collectionVariables.set("expiresAt", new Date(Date.now() + 2000).toISOString());',
            ],
            tests=[
                'pm.test("status is 201", () => pm.response.to.have.status(201));',
                'pm.test("expiration_date is stored", () => pm.expect(pm.response.json().expiration_date).to.not.be.null);',
            ]),
        req("Before expiry -> 302", "GET", "/{{expAlias}}", follow=False, tests=[
            'pm.test("302 while still valid (and now cached with a TTL <= 2s)", () => pm.response.to.have.status(302));',
        ]),
        req("After expiry -> 410 Gone", "GET", "/{{expAlias}}", follow=False,
            pre=['// Wait past the expiration; the cache TTL was capped to the link lifetime, so Redis has dropped it too.',
                 'setTimeout(() => {}, 3000);'],
            tests=problem(410, "expired")),
        req("Metadata of expired link", "GET", "/urls/{{expAlias}}", tests=[
            'pm.test("metadata still readable, flagged expired", () => {',
            '    pm.response.to.have.status(200);',
            '    pm.expect(pm.response.json().expired).to.be.true;',
            '});',
        ]),
    ],
}

collection = {
    "info": {
        "name": "Bitly URL Shortener",
        "description": "API tests for the hellointerview Bitly design: POST /urls, GET /{short_code} (302), "
                       "custom aliases, expiration (410) and validation. Run: npx newman run "
                       "postman/Bitly.postman_collection.json -e postman/Bitly-Local.postman_environment.json",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": [write_path, read_path, validation, expiration],
    "variable": [
        {"key": "shortCode", "value": ""},
        {"key": "alias", "value": ""},
        {"key": "expAlias", "value": ""},
        {"key": "expiresAt", "value": ""},
    ],
}

environment = {
    "name": "Bitly Local",
    "values": [
        {"key": "baseUrl", "value": "http://localhost:8090", "enabled": True},
        {"key": "shortBaseUrl", "value": "http://localhost:8090", "enabled": True},
    ],
}

(HERE / "Bitly.postman_collection.json").write_text(json.dumps(collection, indent=2) + "\n")
(HERE / "Bitly-Local.postman_environment.json").write_text(json.dumps(environment, indent=2) + "\n")
print("wrote Bitly.postman_collection.json and Bitly-Local.postman_environment.json")
