// Offline app shell for Fukaha. Cleaning links is pure local logic, so once the shell is
// cached the whole app works with no network — including a share that arrives offline.
//
// Strategy: immutable, versioned shell. Development keeps the fixed fukaha.js URL. The production
// webpack pass rewrites that URL and this cache name with the bundle's content hash, allowing
// immutable HTTP caching while each deploy still installs a fresh offline shell.
const CACHE = "fukaha-shell-v2";
// Cairo and the Material Symbols subset are versioned in their URLs by Google Fonts, so they
// are cached forever under a separate name and never revalidated. Without this the icon font
// would be missing offline and every glyph would fall back to its ligature name.
const FONT_CACHE = "fukaha-fonts-v1";
const FONT_HOSTS = ["fonts.googleapis.com", "fonts.gstatic.com"];
const STATIC_DOCUMENTS = ["/privacy.html", "/support.html"];
const SHELL = [
  "/",
  "/index.html",
  "/styles.css",
  "/fukaha.js",
  "/manifest.webmanifest",
  "/icons/icon.svg",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  ...STATIC_DOCUMENTS,
  "/legal.css",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(SHELL.map((url) => new Request(url, { cache: "reload" }))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  const keep = [CACHE, FONT_CACHE];
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => !keep.includes(key)).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);

  if (FONT_HOSTS.includes(url.hostname)) {
    event.respondWith(
      caches.open(FONT_CACHE).then((cache) => cache.match(request).then(
        (hit) =>
          hit ||
          fetch(request).then((response) => {
            const copy = response.clone();
            event.waitUntil(cache.put(request, copy));
            return response;
          })
      ))
    );
    return;
  }

  // Embedder probes are deliberately no-cors and must never be served from a cache.
  if (url.origin !== self.location.origin) return;

  // A share target navigation lands on "/?text=…", which never matches a cache entry of its
  // own, so the shell is served explicitly. The query string is read by the app, not the cache.
  const cacheKey = request.mode === "navigate"
    ? (STATIC_DOCUMENTS.includes(url.pathname) ? url.pathname : "/index.html")
    : request;

  event.respondWith(
    caches.open(CACHE).then((cache) => cache.match(cacheKey).then((hit) => {
      // Only a fully installed new worker can replace this shell. Revalidating
      // HTML here would pair a new document with the old cached script offline.
      if (hit) return hit;
      return fetch(request);
    }))
  );
});
