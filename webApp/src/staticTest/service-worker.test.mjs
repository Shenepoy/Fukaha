import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import test from "node:test";

const source = readFileSync(new URL("../jsMain/resources/sw.js", import.meta.url), "utf8");
const origin = "https://fukaha.test";

function worker() {
  const handlers = {};
  const entries = new Map([
    ["/index.html", new Response('<script src="/fukaha.old.js"></script>')],
    ["/fukaha.old.js", new Response("/* old bundle */")],
  ]);
  const key = (request) => new URL(typeof request === "string" ? request : request.url, origin).pathname;
  let online = true;
  const cache = {
    match: async (request) => entries.get(key(request))?.clone(),
    put: async (request, response) => entries.set(key(request), response),
  };
  vm.runInNewContext(source, {
    self: { location: { origin }, addEventListener: (name, fn) => { handlers[name] = fn; } },
    URL, Response,
    Request: class { constructor(url) { this.url = new URL(typeof url === "string" ? url : url.url, origin).href; } },
    caches: { open: async () => cache, match: cache.match },
    fetch: async () => {
      if (!online) throw new Error("offline");
      const response = new Response('<script src="/fukaha.new.js"></script>');
      Object.defineProperty(response, "type", { value: "basic" });
      return response;
    },
  });
  return {
    entries,
    goOffline() { online = false; },
    async request(path, mode = "navigate") {
      let result;
      const pending = [];
      handlers.fetch({
        request: { url: origin + path, method: "GET", mode },
        respondWith: (promise) => { result = promise; },
        waitUntil: (promise) => pending.push(promise),
      });
      const response = await result;
      await Promise.all(pending);
      return response;
    },
  };
}

test("an interrupted upgrade cannot replace the active shell with a different bundle version", async () => {
  const sw = worker();
  await sw.request("/");
  sw.goOffline();
  const offline = await sw.request("/?text=shared");
  assert.equal(await offline.text(), '<script src="/fukaha.old.js"></script>');
});

test("offline missing scripts never fall back to HTML", async () => {
  const sw = worker();
  sw.goOffline();
  await assert.rejects(sw.request("/fukaha.missing.js", "cors"), /offline/);
});

test("privacy and support navigations serve their own cached documents offline", async () => {
  const sw = worker();
  for (const page of ["privacy", "support"]) {
    sw.entries.set(`/${page}.html`, new Response(`<h1>${page}</h1>`));
  }
  sw.goOffline();
  for (const page of ["privacy", "support"]) {
    assert.equal(await (await sw.request(`/${page}.html`)).text(), `<h1>${page}</h1>`);
  }
});
