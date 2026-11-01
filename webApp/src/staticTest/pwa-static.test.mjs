import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const repoRoot = resolve(webRoot, "..");
const repositoryVersion = readFileSync(resolve(repoRoot, "VERSION"), "utf8").trim();
const resources = resolve(webRoot, "src/jsMain/resources");
const kotlinRoot = resolve(webRoot, "src/jsMain/kotlin/app/fukaha/web");
const read = (path) => readFileSync(path, "utf8");
const manifest = JSON.parse(read(resolve(resources, "manifest.webmanifest")));
const index = read(resolve(resources, "index.html"));
const serviceWorker = read(resolve(resources, "sw.js"));
const styles = read(resolve(resources, "styles.css"));
const productionWebpack = read(resolve(webRoot, "webpack.config.d/production-assets.js"));
const navigationWebpack = read(resolve(webRoot, "webpack.config.d/navigation-fallback.js"));
const androidBuild = read(resolve(repoRoot, "composeApp/build.gradle.kts"));
const webBuild = read(resolve(webRoot, "build.gradle.kts"));
const webVersionSource = read(resolve(kotlinRoot, "WebAppVersion.kt"));
const iosProject = read(resolve(repoRoot, "iosApp/project.yml"));

function sourceAsset(url) {
  return resolve(resources, url.replace(/^\//, ""));
}

function namedAssignments(block) {
  return new Set(
    [...block.matchAll(/^\s{12}(\w+)\s*=/gm)].map((match) => match[1]),
  );
}

test("manifest is installable and every referenced icon exists", () => {
  assert.equal(manifest.id, "/");
  assert.equal(manifest.start_url, "/");
  assert.equal(manifest.scope, "/");
  assert.equal(manifest.display, "standalone");
  assert.equal(manifest.share_target.action, "/");
  assert.equal(manifest.share_target.method, "GET");
  assert.deepEqual(manifest.share_target.params, {
    url: "url",
    text: "text",
    title: "title",
  });
  assert.ok(/^#[0-9A-F]{6}$/i.test(manifest.theme_color));
  assert.ok(/^#[0-9A-F]{6}$/i.test(manifest.background_color));
  assert.ok(manifest.icons.some((icon) => icon.sizes === "192x192"));
  assert.ok(manifest.icons.some((icon) => icon.sizes === "512x512"));
  assert.ok(manifest.icons.some((icon) => icon.purpose === "maskable"));
  for (const icon of manifest.icons) {
    assert.ok(existsSync(sourceAsset(icon.src)), `missing manifest asset ${icon.src}`);
  }
});

test("Android, iOS, and web release metadata share the root CalVer", () => {
  const webVersion = webVersionSource.match(/WEB_APP_VERSION\s*=\s*"([^"]+)"/)?.[1];

  assert.match(repositoryVersion, /^[0-9]{2}\.[0-9]{2}\.[0-9]+$/);
  assert.match(androidBuild, /val appVersionFile = rootProject\.file\("VERSION"\)/);
  assert.match(androidBuild, /versionName\s*=\s*appVersion/);
  assert.equal(webVersion, repositoryVersion);
  assert.match(webBuild, /val appVersionFile = rootProject\.file\("VERSION"\)/);
  assert.match(webBuild, /version\s*=\s*appVersion/);
  assert.match(webBuild, /WebAppVersion\.kt/);
  assert.ok(iosProject.includes(`MARKETING_VERSION: ${repositoryVersion}`));
  assert.equal("version" in manifest, false, "Web App Manifest has no standard version member");
  assert.match(serviceWorker, /const CACHE = "fukaha-shell-v2"/);
});

test("index local assets, theme color, locale bootstrap, and service worker agree", () => {
  const localRefs = [...index.matchAll(/(?:href|src)="(\/[^"]+)"/g)].map((match) => match[1]);
  for (const ref of localRefs) {
    if (ref === "/fukaha.js") continue;
    assert.ok(existsSync(sourceAsset(ref)), `missing index asset ${ref}`);
  }
  assert.match(index, new RegExp(`name="theme-color" content="${manifest.theme_color}"`));
  assert.match(index, /Arabic:\s*"ar"/);
  assert.match(index, /SimplifiedChinese:\s*"zh-CN"/);
  assert.match(index, /language === "Arabic" \? "rtl" : "ltr"/);
  assert.match(index, /navigator\.languages \|\| \[navigator\.language \|\| "en"\]/);
  assert.match(index, /languagechange/);
  assert.match(index, /<script src="\/fukaha\.js"><\/script>/);
  assert.match(read(resolve(kotlinRoot, "Main.kt")), /serviceWorker\?\.register\("\/sw\.js"\)/);
});

test("service worker shell contains existing static assets and production rewrite anchors", () => {
  const shellBlock = serviceWorker.match(/const SHELL = \[([\s\S]*?)\];/)?.[1];
  assert.ok(shellBlock, "service worker SHELL list is missing");
  const shellUrls = [...shellBlock.matchAll(/"([^"]+)"/g)].map((match) => match[1]);
  for (const url of shellUrls) {
    if (url === "/" || url === "/fukaha.js") continue;
    assert.ok(existsSync(sourceAsset(url)), `missing service-worker shell asset ${url}`);
  }
  assert.ok(shellUrls.includes("/index.html"));
  assert.ok(shellUrls.includes("/manifest.webmanifest"));
  assert.ok(shellUrls.includes("/icons/icon.svg"));
  // Navigation fallback behavior is exercised by service-worker.test.mjs.
  assert.match(serviceWorker, /const cacheKey = request\.mode === "navigate"/);
  assert.match(serviceWorker, /url\.origin !== self\.location\.origin/);

  assert.match(productionWebpack, /filename:\s*"fukaha\.\[contenthash:12\]\.js"/);
  assert.match(
    productionWebpack,
    /chunkFilename:\s*"fukaha\.\[name\]\.\[contenthash:12\]\.js"/,
  );
  assert.match(webBuild, /tasks\.named\("jsBrowserDistribution"\)/);
  assert.match(webBuild, /\.replace\("\/fukaha\.js", bundlePath\)/);
  assert.match(webBuild, /\.replace\("fukaha-shell-v2", "fukaha-shell-\$buildHash"\)/);
});

test("development history fallback serves document paths without rewriting resource requests", () => {
  assert.match(navigationWebpack, /config\.devServer\.historyApiFallback\s*=/);
  assert.match(navigationWebpack, /disableDotRule:\s*true/);
  assert.match(navigationWebpack, /index:\s*"\/index\.html"/);

  const webpackConfig = Function(
    "config",
    `${navigationWebpack}\nreturn config;`,
  )({});
  const rewrite = webpackConfig.devServer.historyApiFallback.rewrites[0].to;
  const destinationFor = (pathname, destination, accept) =>
    rewrite({
      parsedUrl: { pathname },
      request: { headers: { "sec-fetch-dest": destination, accept } },
    });

  for (const navigation of ["/foo", "/settings", "/nested/path/", "/release.v2"]) {
    assert.equal(destinationFor(navigation, "document", "text/html"), "/index.html");
  }
  for (const [path, destination] of [
    ["/fukaha.012345abcdef.js", "script"],
    ["/styles.css", "style"],
    ["/icons/icon-192.png", "image"],
    ["/manifest.webmanifest", "manifest"],
    ["/sw.js", "serviceworker"],
  ]) {
    assert.equal(destinationFor(path, destination, "*/*"), path);
  }
  assert.equal(destinationFor("/legacy-navigation", undefined, "text/html"), "/index.html");
});

test("production hashing keeps the document and offline shell on the same emitted bundle", () => {
  const hashedBundle = "/fukaha.012345abcdef.js";
  const productionIndex = index.replaceAll("/fukaha.js", hashedBundle);
  const productionServiceWorker = serviceWorker
    .replaceAll("/fukaha.js", hashedBundle)
    .replaceAll("fukaha-shell-v2", "fukaha-shell-012345abcdef");

  assert.match(productionIndex, /<script src="\/fukaha\.012345abcdef\.js"><\/script>/);
  assert.doesNotMatch(productionIndex, /src="\/fukaha\.js"/);
  assert.match(productionServiceWorker, /"\/fukaha\.012345abcdef\.js"/);
  assert.match(productionServiceWorker, /const CACHE = "fukaha-shell-012345abcdef"/);
  assert.doesNotMatch(productionServiceWorker, /"\/fukaha\.js"/);
});

test("all five localized Strings constructors have exact field parity", () => {
  const source = read(resolve(kotlinRoot, "Strings.kt"));
  const constructor = source.match(/class Strings\(([\s\S]*?)^\) \{/m)?.[1];
  assert.ok(constructor, "Strings constructor not found");
  const fields = new Set(
    [...constructor.matchAll(/^\s+(?:private\s+)?val\s+(\w+):\s+String/gm)]
      .map((match) => match[1]),
  );
  assert.ok(fields.size > 90, "unexpectedly small Strings surface");

  const locales = ["EN", "AR", "JA", "ZH_CN", "ES"];
  for (const locale of locales) {
    const block = source.match(
      new RegExp(`val ${locale} = Strings\\(([\\s\\S]*?)^        \\)`, "m"),
    )?.[1];
    assert.ok(block, `missing ${locale} Strings block`);
    assert.deepEqual(namedAssignments(block), fields, `${locale} field parity changed`);
  }

  const options = read(resolve(kotlinRoot, "WebUiLogic.kt"));
  const menuLanguages = [...options.matchAll(/LanguageOption\(AppLanguage\.(\w+)/g)]
    .map((match) => match[1]);
  assert.deepEqual(menuLanguages, [
    "Arabic",
    "English",
    "Japanese",
    "SimplifiedChinese",
    "Spanish",
  ]);
});

test("responsive modal, stable top-bar, RTL arrows, and reduced motion remain encoded", () => {
  assert.match(styles, /grid-template-columns:[\s\S]*\[navigation\][\s\S]*\[theme\]/);
  assert.match(styles, /\.top-app-bar-placeholder\s*\{[\s\S]*visibility:\s*hidden/);
  assert.match(styles, /\[dir="rtl"\] \.icon-flip \.icon\s*\{[\s\S]*scaleX\(-1\)/);
  assert.match(styles, /env\(safe-area-inset-top/);
  assert.match(styles, /--app-bar-height:\s*96px/);
  assert.match(styles, /\.shell-share\s*\{[\s\S]*border-radius:[^;]*0 0/);
  assert.match(styles, /@media \(min-width: 600px\)[\s\S]*\.shell-share,[\s\S]*border-radius:\s*var\(--shape-xl\)/);
  assert.match(styles, /@media \(prefers-reduced-motion: reduce\)[\s\S]*animation:\s*none !important/);

  const main = read(resolve(kotlinRoot, "Main.kt"));
  assert.ok(
    (main.match(/prefers-reduced-motion: reduce/g) || []).length >= 3,
    "theme, language, and exit paths must all honor reduced motion",
  );
  assert.match(main, /clearThemeTransition\(page\)/);
  assert.match(main, /clearLanguageTransition\(page\)/);
  assert.match(main, /shouldUseViewTransitions/);
  assert.match(main, /installSystemAppearanceListeners/);
});

test("fixer rows distinguish pointer focus from visible keyboard focus", () => {
  assert.match(
    styles,
    /\.fixer-row:focus,\s*\.fixer-option:focus\s*\{\s*outline:\s*none/,
  );
  const focusGeometry = styles.match(
    /\.fixer-row:focus-visible,\s*\.fixer-option:focus-visible\s*\{[\s\S]*?outline:\s*(\d+)px solid var\(--primary\)[\s\S]*?outline-offset:\s*(-\d+)px/,
  );
  assert.ok(focusGeometry, "fixer focus ring geometry is missing");
  const ringWidth = Number(focusGeometry[1]);
  const ringInnerEdge = Math.abs(Number(focusGeometry[2]));
  assert.equal(ringWidth, 3);
  assert.equal(ringInnerEdge, 4);

  const rowGeometry = styles.match(
    /\.fixer-row\s*\{[\s\S]*?padding:\s*\d+px\s+(\d+)px/,
  );
  const optionGeometry = styles.match(
    /\.fixer-option\s*\{[\s\S]*?padding:\s*\d+px\s+(\d+)px[\s\S]*?border:\s*(\d+)px solid/,
  );
  const selectedGeometry = styles.match(
    /\.fixer-option-selected\s*\{[\s\S]*?border:\s*(\d+)px solid[\s\S]*?padding:\s*\d+px\s+(\d+)px/,
  );
  assert.ok(rowGeometry && optionGeometry && selectedGeometry, "fixer content geometry is missing");
  assert.equal(Number(rowGeometry[1]) - ringInnerEdge, 12);
  assert.equal(Number(optionGeometry[1]) + Number(optionGeometry[2]) - ringInnerEdge, 13);
  assert.equal(Number(selectedGeometry[1]) + Number(selectedGeometry[2]) - ringInnerEdge, 13);
  assert.match(
    styles,
    /\.fixer-row:focus-visible::after,\s*\.fixer-option:focus-visible::after\s*\{[\s\S]*?background:\s*var\(--primary\)/,
  );
  assert.match(styles, /\.fixer-row:first-child\s*\{[\s\S]*?border-start-start-radius/);
  assert.match(styles, /\.fixer-row:last-child\s*\{[\s\S]*?border-end-end-radius/);
});

test("Firebase hosting keeps dynamic shell revalidated and hashed bundles immutable", () => {
  const firebase = JSON.parse(read(resolve(repoRoot, "firebase.json")));
  assert.equal(firebase.hosting.public, "webApp/build/dist/js/productionExecutable");
  assert.ok(!firebase.hosting.redirects, "path canonicalization must not use a catch-all redirect");
  assert.deepEqual(firebase.hosting.rewrites, [{ source: "**", destination: "/index.html" }]);

  // Firebase serves an existing static file before evaluating rewrites. These deployed resource
  // paths therefore remain themselves; only a path with no emitted file reaches the SPA shell.
  const emittedAssets = new Set([
    "/index.html",
    "/styles.css",
    "/manifest.webmanifest",
    "/sw.js",
    "/icons/icon.svg",
    "/icons/icon-192.png",
    "/fukaha.012345abcdef.js",
    "/fukaha.236.abcdef012345.js",
  ]);
  const hostedDestination = (path) =>
    emittedAssets.has(path) ? path : firebase.hosting.rewrites[0].destination;
  for (const asset of emittedAssets) {
    assert.equal(hostedDestination(asset), asset, `${asset} must bypass the SPA rewrite`);
  }
  for (const navigation of ["/foo", "/settings", "/nested/path", "/nested/path/"]) {
    assert.equal(hostedDestination(navigation), "/index.html");
  }

  const headers = new Map(
    firebase.hosting.headers.map((entry) => [
      entry.source,
      new Map(entry.headers.map((header) => [header.key, header.value])),
    ]),
  );
  assert.equal(
    headers.get("**")?.get("Permissions-Policy"),
    "clipboard-read=(self), clipboard-write=(self)",
  );
  assert.equal(
    headers.get("/fukaha.*.js")?.get("Cache-Control"),
    "public,max-age=31536000,immutable",
  );
  for (const path of ["**/*.html", "/manifest.webmanifest", "/sw.js", "/styles.css"]) {
    assert.equal(
      headers.get(path)?.get("Cache-Control"),
      "public,max-age=0,must-revalidate",
      `${path} must revalidate`,
    );
  }
});
