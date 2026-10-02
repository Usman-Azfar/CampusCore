// Full-page screenshots of CampusCore pages with headless Chrome over the DevTools protocol (no extra packages).
// Usage (from the repository root, app running): node tools/demo/shoot.mjs tools/demo/<portal>-pages.json   pages.json: { base, outDir, login: {username, password} | null, pages: [{file, path, wait?, js?}] }
import { spawn } from "node:child_process";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const cfg = JSON.parse(readFileSync(process.argv[2], "utf8"));
const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const PORT = 9333, WIDTH = 1440, HEIGHT = 900, SCALE = 1.5;
const sleep = ms => new Promise(r => setTimeout(r, ms));
mkdirSync(cfg.outDir, { recursive: true });

// 1. Log in over HTTP and keep the session cookie
let session = null;
if (cfg.login) {
    const res = await fetch(cfg.base + "/login", {
        method: "POST", redirect: "manual",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams(cfg.login).toString(),
    });
    const m = /JSESSIONID=([^;]+)/.exec(res.headers.get("set-cookie") || "");
    if (!m) throw new Error("Login failed (no session cookie)");
    session = m[1];
}

// 2. Start headless Chrome
const chrome = spawn(CHROME, ["--headless=new", `--remote-debugging-port=${PORT}`, "--no-first-run", "--hide-scrollbars",
    "--disable-extensions", `--user-data-dir=${cfg.profileDir}`, `--window-size=${WIDTH},${HEIGHT}`, "about:blank"], { stdio: "ignore" });
let target;
for (let i = 0; i < 50 && !target; i++) {
    await sleep(200);
    try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find(t => t.type === "page"); } catch { }
}
if (!target) throw new Error("Chrome did not start");

// 3. DevTools protocol over WebSocket
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((ok, fail) => { ws.onopen = ok; ws.onerror = fail; });
let nextId = 1;
const pending = new Map(), listeners = [];
ws.onmessage = ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) { const { ok, fail } = pending.get(msg.id); pending.delete(msg.id); msg.error ? fail(new Error(msg.error.message)) : ok(msg.result); }
    else if (msg.method) listeners.forEach(l => l(msg));
};
const send = (method, params = {}) => new Promise((ok, fail) => { const id = nextId++; pending.set(id, { ok, fail }); ws.send(JSON.stringify({ id, method, params })); });
const once = method => new Promise(ok => { const l = m => { if (m.method === method) { listeners.splice(listeners.indexOf(l), 1); ok(m.params); } }; listeners.push(l); });

await send("Page.enable");
await send("Network.enable");
if (session) await send("Network.setCookie", { name: "JSESSIONID", value: session, domain: new URL(cfg.base).hostname, path: new URL(cfg.base).pathname.replace(/\/$/, "") || "/", httpOnly: true });

const results = [];
for (const p of cfg.pages) {
    await send("Emulation.setDeviceMetricsOverride", { width: WIDTH, height: HEIGHT, deviceScaleFactor: SCALE, mobile: false });
    const loaded = once("Page.loadEventFired");
    await send("Page.navigate", { url: cfg.base + p.path });
    await loaded;
    await sleep(p.wait ?? 700);
    if (p.js) { await send("Runtime.evaluate", { expression: p.js, awaitPromise: true }); await sleep(500); }
    const info = await send("Runtime.evaluate", { expression: "JSON.stringify({h: Math.ceil(Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)), title: document.title, url: location.pathname + location.search})", returnByValue: true });
    const { h, title, url } = JSON.parse(info.result.value);
    // Make the viewport as tall as the page so fixed elements (sidebar) render along the whole height
    await send("Emulation.setDeviceMetricsOverride", { width: WIDTH, height: Math.max(HEIGHT, h), deviceScaleFactor: SCALE, mobile: false });
    await sleep(300);
    const shot = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
    const file = join(cfg.outDir, p.file);
    writeFileSync(file, Buffer.from(shot.data, "base64"));
    results.push(`${p.file}  ${WIDTH}x${Math.max(HEIGHT, h)} css px  "${title}"  ${url}`);
}

ws.close();
chrome.kill();
console.log(results.join("\n"));
