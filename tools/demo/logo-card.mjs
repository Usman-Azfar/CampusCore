// Renders the CampusCore logo on a white rounded card with a transparent outside (for the GitHub README).
import { spawn } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

const [logoPath, outPath] = process.argv.slice(2);
const logo = readFileSync(logoPath).toString("base64");
const W = 1559, H = 460, PADX = 90, PADY = 70;            // logo is 1559 x 460
const CW = W + 2 * PADX, CH = H + 2 * PADY;
const html = `<!doctype html><html><head><style>
  html, body { margin: 0; background: transparent; }
  .card { width: ${CW}px; height: ${CH}px; box-sizing: border-box; padding: ${PADY}px ${PADX}px;
          background: #ffffff; border-radius: 48px; }
  img { display: block; width: ${W}px; height: ${H}px; }
</style></head><body><div class="card"><img src="data:image/png;base64,${logo}"></div></body></html>`;

const PORT = 9334;
const chrome = spawn("C:/Program Files/Google/Chrome/Application/chrome.exe", ["--headless=new", `--remote-debugging-port=${PORT}`,
    "--no-first-run", "--hide-scrollbars", `--user-data-dir=${process.env.TEMP}/cc-logo-profile`, "about:blank"], { stdio: "ignore" });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let target;
for (let i = 0; i < 50 && !target; i++) { await sleep(200); try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find(t => t.type === "page"); } catch { } }
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.onopen = r);
let id = 0; const waiting = new Map();
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && waiting.has(m.id)) { waiting.get(m.id)(m.result); waiting.delete(m.id); } };
const send = (method, params = {}) => new Promise(r => { const i = ++id; waiting.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });

await send("Page.enable");
await send("Emulation.setDeviceMetricsOverride", { width: CW, height: CH, deviceScaleFactor: 1, mobile: false });
await send("Emulation.setDefaultBackgroundColorOverride", { color: { r: 0, g: 0, b: 0, a: 0 } });
await send("Page.setDocumentContent", { frameId: (await send("Page.getFrameTree")).frameTree.frame.id, html });
await sleep(800);
const shot = await send("Page.captureScreenshot", { format: "png", clip: { x: 0, y: 0, width: CW, height: CH, scale: 1 } });
writeFileSync(outPath, Buffer.from(shot.data, "base64"));
ws.close(); chrome.kill();
console.log(`wrote ${outPath} (${CW} x ${CH})`);
