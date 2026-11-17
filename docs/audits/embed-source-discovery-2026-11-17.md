# Additional embed source discovery — 2026-11-17

Research focused on new public providers for existing Fukaha platforms, especially Roblox, Facebook, Twitch, Snapchat, Tumblr, and Weibo. Validated candidates were subsequently integrated into the catalog as described below. Candidate behavior below was checked against first-party documentation and bounded requests to the provider itself.

## Verified candidates

| Platform | Candidate | Why it fits | Live evidence and limits |
|---|---|---|---|
| Facebook | `https://facecot.com` | The [provider's own page](https://www.facecot.com/) documents replacing `facebook.com` with `facecot.com`, preserving the remaining path. It lists public posts, Reels, videos, and share links. | Three public video URLs returned HTTP 200 with post-specific `og:title`, `og:url`, `og:image`, `og:video`, and MP4 player tags. No authentication or API key was requested. Playback in a chat client was not exercised. |
| Facebook | `https://facebed.seria.moe` | Current [FixTweetBot source](https://github.com/Kyrela/FixTweetBot/blob/main/src/websites.py) uses this hostname for Facebook. [Seria's facebed repository](https://github.com/seriaati/facebed) is a fork of the facebed project. It is a separate deployment of an existing project, rather than a new rendering implementation. | Homepage and two public video URLs returned HTTP 200. Post responses included real title/description, canonical Facebook `og:url`, and MP4 `og:video`/player stream tags. The canonical URL may expand a Facebook share link to a reel ID. |
| Roblox | `https://www.fxroblox.com` | The [operator's page](https://www.fxroblox.com/) documents `roblox.com` → `fxroblox.com` and credits “false.” This can fill the gap left by the removed `fixroblox.com`. | A game and a user profile returned HTTP 200 with correct titles/descriptions and Roblox CDN `twitter:image` URLs. The bare host redirects to `www.fxroblox.com` while preserving the path. These responses omit `og:url` and `og:image`: they have preview tags, but Fukaha's current stricter per-link checker would report missing metadata. |

### Requests used

Requests used the Discord crawler user agent `Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)`, followed redirects, verified HTTPS, and limited responses to 512 KiB with a 5-second connection timeout and 18-second overall timeout.

| URL | HTTP | Observed result |
|---|---|---|
| [Facecot reel](https://facecot.com/reel/578721235067082) | 200 | Post-specific title, thumbnail, and MP4 metadata |
| [Facecot share link](https://facecot.com/share/r/19xGa5YKiR) | 200 | Post-specific title, thumbnail, and MP4 metadata |
| [Facecot watch link](https://facecot.com/watch/?v=1664876787784263) | 200 | Post-specific title, thumbnail, and MP4 metadata |
| [Seria facebed share link](https://facebed.seria.moe/share/r/19xGa5YKiR) | 200 | Canonical Facebook reel URL and MP4 metadata |
| [Seria facebed reel](https://facebed.seria.moe/reel/578721235067082) | 200 | Post-specific title and MP4 metadata |
| [FxRoblox game](https://www.fxroblox.com/games/920587237/Adopt-Me) | 200 | Adopt Me title/description and game thumbnail |
| [FxRoblox user](https://www.fxroblox.com/users/1/profile) | 200 | Roblox account title/description and avatar thumbnail |

The current [facebed.com deployment](https://facebed.com/share/r/19xGa5YKiR) also returned a valid Facebook preview for that share link. Its previous audit's login-wall sample did not establish that the service was broken.

## Do not add as independent fallback providers

- **embed.rip / embed.pw:** the [operator's routing list](https://embed.rip/) explicitly forwards Twitch to `fxtwitch.seria.moe`, Tumblr to `tpmblr.com`, Facebook to `facebed.com`, Spotify to `fxspotify.com`, and several other platforms to already known providers. They offer convenient spelling, not independent upstream availability.
- **2oblox.com:** the [FxRoblox owner page](https://www.fxroblox.com/) documents this alternate, but the attempted game request failed local DNS resolution. Add the working `www.fxroblox.com` hostname first; the alias needs independent DNS verification before inclusion.
- **RoEmbed:** its [operator documentation](https://roembed.com/) and [author's Roblox forum announcement](https://devforum.roblox.com/t/embed-3d-roblox-avatars-catalog-items-on-your-website/4647807) describe interactive 3D avatar/catalog-item iframes. Its `/item/ID` and `/avatar/ID` interface is not a path-preserving social-preview replacement for Roblox game/profile URLs.
- **Embedded:** the [operator's site](https://www.embedded.gallery/) describes installing a Discord bot. Its Tumblr/Facebook feature pages do not establish a public domain-swap endpoint usable by Fukaha.
- **The removed fxfacebook deployment:** its [maintainer README](https://github.com/seriaati/fxfacebook/blob/main/README.md) calls the external-downloader dependency unreliable and recommends facebed. It was not restored just because a list still mentions it.

## Platforms where this pass found no new independent drop-in provider

This is a finding about the sources searched, not proof that no provider exists.

- **Twitch:** current [maintainer integrations](https://github.com/seriaati/embed-fixer/blob/main/embed_fixer/fixes.py) and the [embed.rip routing list](https://embed.rip/) still point to `fxtwitch.seria.moe`. No additional independently hosted HTTPS host-swap service was verified.
- **Snapchat and Weibo:** current [FixTweetBot platform code](https://github.com/Kyrela/FixTweetBot/blob/main/src/websites.py) uses its EmbedEZ integration for both. The [EmbedEZ Weibo page](https://d.embedez.com/weibo) documents `weiboez.com`, which is already in Fukaha. No new independent drop-in host was verified.
- **Tumblr:** [FixTweetBot](https://github.com/Kyrela/FixTweetBot/blob/main/src/websites.py) and [embed.rip](https://embed.rip/) still use `tpmblr.com`. [embeds.now](https://embeds.now/) advertises its own Tumblr support and documented `tb` short-host routing; the parent task is testing its per-platform hosts separately.

## Integration notes

The successful responses establish sample-level preview metadata, not blanket uptime or working playback for every source link. Retain the existing defaults until explicit replacement is warranted; add working independent candidates as options. FxRoblox's missing canonical OG tag must remain visible as a checker limitation, rather than being mistaken for a failed source or silently loosening the checker.


## Integrated additions

Added 13 entries to the cleaned 37-entry catalog, for 50 active entries. These represent five provider/deployment groups; the nine embeds.now rows share one backend and are not nine independent fallbacks.

| Platform | Added host | Primary documentation / evidence |
|---|---|---|
| Instagram | `oginstagram.com` | [Maintainer repository](https://github.com/seirenkr/OGInstagram) documents domain replacement. Two NASA Reels returned canonical Instagram URLs, creator-specific titles, images, and video metadata. |
| Facebook | `facecot.com` | [Operator documentation](https://www.facecot.com/); tested Reel and share URLs. |
| Facebook | `facebed.seria.moe` | [Maintainer fork](https://github.com/seriaati/facebed) and [integration source](https://github.com/Kyrela/FixTweetBot/blob/main/src/websites.py); tested two videos. |
| Roblox | `www.fxroblox.com` | [Operator documentation](https://www.fxroblox.com/); tested game and user paths. The bare `fxroblox.com` is retained as an alternate spelling. |
| Spotify | `sp.embeds.now` | Two tracks returned title, album art, and video preview metadata. |
| Instagram | `ig.embeds.now` | Two NASA Reels returned canonical Instagram URLs and playable-media metadata. |
| Threads | `th.embeds.now` | Public Zuckerberg post returned author, canonical Threads URL, and image metadata. |
| Reddit | `r.embeds.now` | Public flag post returned its title, original URL, and image. |
| Bluesky | `bs.embeds.now` | Maintainer-example post returned its author, original post URL, and image. |
| X | `x.embeds.now` | Public video post returned author, original post URL, image, and video metadata. |
| Facebook | `fb.embeds.now` | Two public video/share routes returned canonical URLs, images, and video metadata. |
| TikTok | `tt.embeds.now` | Public video returned creator/title, original URL, image, and video metadata. |
| Pinterest | `pi.embeds.now` | Public pin returned original pin URL, title, and image. |

The [embeds.now operator documentation](https://embeds.now/) and [machine-readable guide](https://embeds.now/llms.txt) publish the per-platform subdomains and domain-swap behavior. This fits the existing host-replacement interface without introducing a new URL format. Its [changelog](https://embeds.now/changelog) also describes a fair-use limit for non-preview clients. No accounts, keys, or bot installations were used for the checks.

Existing defaults remain as selected after cleanup. Roblox now defaults to `www.fxroblox.com` so its source picker and embedding are available again. A saved preference for retired `fixroblox.com` falls back to this new provider. The generic cleaned-official-link fallback is still covered for catalogs with no compatible source.

### Rejected or not yet verified in this pass

- `toinstagram.com`: [its homepage](https://toinstagram.com/) advertises an InstaFix service, but both tested Reels redirected directly to Instagram; no independent preview was established.
- `instagram7.com` and `fkinstagram.com`: could not resolve from this environment. The [current revived project's README](https://github.com/Bl0ck154/InstaFix-Revived) lists fkinstagram.com as an independently operated instance; neither hostname was added without a successful live check.
- `y.outube.duckdns.org`: the [FixYouTube repository](https://github.com/birabittoh/fixyoutube-go) documents it, but both tested watch URLs served a default SWAG hosting page rather than video metadata.
- `tb.embeds.now`: the provider documents Tumblr support, but the tested post returned a generic card. This single unverified sample does not prove the service is broken; it was left out pending a successful public-post test.

Raw request summaries are in [the discovery evidence](embed-source-discovery-2026-11-17.json). HTTPS was verified; requests were bounded to 512 KiB and used the full Discord crawler user agent. Media query signatures were omitted from the saved evidence. These checks establish metadata for the tested URLs, not all-post success or playback in every chat client.

## Validation

- Resource JSON and embedded Kotlin catalog payloads match, with no duplicate primary or alternate hosts.
- Every new entry has at least one recorded public-post response with a specific title and media tag.
- All 89 shared tests passed (12 suites, zero failures/errors), including selection, stored-preference migration, and cleaned-link fallback. See [test output](embed-source-discovery-test.log).
