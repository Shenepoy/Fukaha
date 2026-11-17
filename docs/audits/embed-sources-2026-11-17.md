# Embed source audit — 2026-11-17

Initial sweep completed at 2026-11-17 08:55 +03; targeted rechecks followed during this task.

Reviewed all 52 catalog entries (50 active, two already disabled). Removed 13 failing active sources and the two disabled Twitch entries. Also removed the dead Tumblr alias `fx.dissonant.dev` after both public resolvers confirmed NXDOMAIN; `tpmblr.com` remains. The catalog now contains 37 active sources. Both the resource JSON and embedded Kotlin copy were updated.

This report records the cleanup snapshot. A subsequent [discovery pass](embed-source-discovery-2026-11-17.md) adds newly validated alternatives.

## Method and limits

- Used bounded HTTP GETs against homepages and public post URLs, then checked returned metadata, redirect destinations, and transport failures.
- Retried failures with multiple public posts and the Discord crawler user agent `Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)`. The initial shorter user agent produced a false negative for vxTwitter; the full Discord, Telegram, and Twitter agents returned valid metadata. vxTwitter was retained.
- Confirmed NXDOMAIN with Google and Cloudflare DNS over HTTPS. Local DNS failures for kkinstagram.com and zzinstagram.com were contradicted by both resolvers; direct DNS-override requests reached working media/previews, so both sources were retained.
- Used maintainer examples or independently identified public posts for rechecks. A deleted/invalid sample, homepage redirect, 403 challenge, or single timeout alone was not used as proof of a dead service. rxyddit.com returned its own JSON upstream-scraping error on a valid post, rather than just a challenge page.
- Inspected metadata and direct media responses; this does not prove every post works or that every chat client will render it.
- Raw request summaries and DNS confirmation are in [the JSON evidence](embed-sources-2026-11-17.json). Expiring media query strings and echoed client network diagnostics were removed.

## Removed entries

| Platform | Host | Evidence |
|---|---|---|
| bilibili | `bilibliez.com` | NXDOMAIN confirmed by Google and Cloudflare DNS |
| bluesky | `cbsky.app` | NXDOMAIN confirmed by Google and Cloudflare DNS |
| facebook | `fxfb.seria.moe` | Repeated HTTP 502 on homepage and post requests |
| instagram | `ddinstagram.com` | NXDOMAIN confirmed by Google and Cloudflare DNS |
| instagram | `instagramez.com` | Redirects every request to an unrelated advertising host |
| instagram | `fxstagram.com` | NXDOMAIN confirmed by Google and Cloudflare DNS |
| reddit | `rxddit.com` | Repeated HTTP 502 for several public Reddit post URLs |
| reddit | `rxyddit.com` | Correct public post returns HTTP 403 and a JSON upstream scraping error instead of embed metadata |
| roblox | `fixroblox.com` | Homepage, game and profile URLs serve the same unrelated vortex app without preview metadata |
| spotify | `fixspotify.com` | Configured host returns 404; maintainer-documented open.fixspotify.com returns repeated 503 |
| threads | `fixthreads.net` | TLS validation fails repeatedly; maintainer has retired the original service |
| tiktok | `tfxktok.com` | Repeated HTTP 500 for two posts successfully rendered by other services |
| tiktok | `vxtiktok.com` | All post URLs return a service shutdown notice |
| twitch | `fxtwitch.tv` | Already disabled since 2024; no DNS address records |
| twitch | `txitch.tv` | Already disabled since 2024; no DNS address records |

## Default replacements

| Platform | Previous | Current |
|---|---|---|
| Instagram | ddinstagram.com | vxinstagram.com |
| Reddit | rxddit.com | vxreddit.com |
| Threads | fixthreads.net | fixthreads.seria.moe |
| Roblox | fixroblox.com | No embed source; share the cleaned official link |

The three replacement defaults returned post metadata during the rechecks. Saved preferences pointing at removed services fall back through the existing catalog selection logic. Roblox remains recognized as a platform so link cleaning continues; its empty source list is hidden by the Android, iOS, and web pickers.

## Retained with inconclusive post support

| Host | Reason retained |
|---|---|
| `facebed.com` | Homepage works; tested Facebook URL returns a login preview, so post support remains unverified. |
| `imgurez.com` | Redirects to EmbedEZ; the sample post could not be fetched. No evidence of a retired service. |
| `snapchatez.com` | Redirects to EmbedEZ; no independently verified public Spotlight sample was available. |
| `weiboez.com` | Redirects to EmbedEZ; sample lookup failed, so current post support remains unverified. |
| `vxthreads.net` | Repeated connection timeouts from this network; insufficient independent evidence to declare it retired. |
| `tpmblr.com` | Sample Tumblr URL returned post not found; this can be a deleted/invalid sample. |
| `txtumblr.com` | Sample Tumblr URL returned post not found; this can be a deleted/invalid sample. |

The other 30 remaining sources returned post metadata or a direct-media response for at least one tested public link. This is evidence for those samples, not a blanket uptime or playback guarantee.

## Maintainer evidence

- [vxTikTok maintainer notice](https://github.com/dylanpdx/vxtiktok/blob/main/README.md): the original service is no longer provided; the live endpoint returned a shutdown notice on both tested posts.
- [Original FixThreads retirement](https://github.com/milanmdev/fixthreads/blob/main/README.md): the original deployment is archived; the live host failed TLS validation. The [maintained seria fork](https://github.com/seriaati/fixthreads) returned metadata.
- [FixSpotify usage](https://github.com/gurrrrrrett3/fixspotify): the documented host is open.fixspotify.com. Tested that host before removal; it returned HTTP 503 for two tracks, while fxspotify.com returned a working preview.

## Validation

- Catalog JSON parses, and the resource and embedded Kotlin payloads match.
- Regression coverage checks retired stored preferences, valid defaults, and official-link fallback when a platform loses its last embed service.
- All 88 shared host tests passed (12 suites, zero failures/errors) from a disposable checkout; see [the test output](embed-sources-test.log). Full device UI checks are outside this catalog-only validation.
