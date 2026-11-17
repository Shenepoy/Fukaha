# Embed source platform validation — 2026-11-17

All 50 catalog choices pass Android persistence/preparation and real-browser picker/persistence/copy tests. This does **not** establish that all providers render every post on Android, web and iOS. Full iOS execution is pending a current framework built with Apple's SDK.

## Platform coverage

| Platform | Executed | Result and limit |
|---|---|---|
| Android | Shared host tests, Android DataStore round trips for all 50 sources, debug APK build, isolated emulator app | Passed. PolyScreen verified OGInstagram selection, preparation, live metadata check (HTTP 200), and copying the generated link then pasting it back. This is representative native UI coverage; all 50 were exercised through persistence/preparation in automated tests. |
| Web | Chrome DOM tests selecting, persisting, preparing and copying all 50 sources; production bundle build; live fetches using the preview checker's browser options | All selection/copy tests passed. Only 1/50 live responses was readable by browser JavaScript; 49 failed or were blocked. Most failures were CORS. These are preview-check limitations, not evidence that the copied URLs fail in chat applications. |
| iOS | Swift preference/ranking/health tests on Linux, including all 50 catalog source codec round trips; facade and framework compatibility review | Swift tests passed. Added an all-source iOS facade callback test, but **did not run it**. No Xcode/Apple SDK is installed. The cached framework contains retired providers, lacks new ones and has an outdated exported interface; the existing preparation guard correctly rejects it. Current iOS app/extension build and device tests remain pending. |

The Gradle run completed with **149 tests, zero failures/errors**: shared Android-host 92, Android app 11, web 46. Kotlin/Native iOS tests are not included in this total. See [suite results](platform-validation/test-results.json), [build output](platform-validation/build.log), [Swift results](platform-validation/swift-tests.log), [iOS framework guard](platform-validation/ios-framework-guard.log) and [Android UI observations](platform-validation/android-ui-checks.json).

## Fix made

The preview checker used a host helper that stripped `www.` while checking the final response against literal hostnames. An unchanged response from the newly configured `www.fxroblox.com` could therefore be reported as an unrelated redirect. It now preserves configured hostnames using Ktor URL parsing. A regression test covers the primary host, its configured alias and rejection of an unrelated suffix host.

FxRoblox still lacks the canonical/OG tags required by the strict preview classifier, despite returning a title and Twitter image. It can remain unverified in that checker. Other providers also omit tags required by that classifier, or redirect to another host. Sharing the generated URL and verifying it inside Fukaha are different checks.

## Live provider results

Bounded HTTPS requests used the full Discord crawler user agent, followed redirects and retained TLS verification. Across the latest sample for every source:

- 47/50 returned HTTP 200: 44 had a preview title and image/video tags, two had title metadata without media tags, and one returned direct MP4 media.
- `kkinstagram.com` and `zzinstagram.com` failed this machine's DNS. The earlier audit resolved both through independent public resolvers and obtained previews with DNS overrides; they remain locally unverified in this sweep.
- `vxthreads.net` repeatedly timed out from this network. This is an availability concern, but does not establish global retirement.
- Fresh Tumblr and Weibo samples returned title/media metadata, resolving earlier inconclusive samples. The Tumblr original also returned HTTP 200 with matching post content.

HTTP 200 and the presence of metadata do not guarantee a chat client's preview, video playback or success for other posts. These requests do not execute on iOS. No messages were sent to third-party chat services.

[Every tested input and generated URL](platform-validation/source-cases.json), [provider responses](platform-validation/live-provider-checks.json), [Tumblr original/provider rechecks](platform-validation/tumblr-rechecks.json), [Weibo recheck](platform-validation/supplemental-checks.json), [browser transport evidence](platform-validation/browser-live-checks.json) and [browser rechecks](platform-validation/browser-supplemental-checks.json) are retained.

| Platform | Host | Latest provider observation | Browser preview transport |
|---|---|---|---|
| bilibili | `vxbilibili.com` | 200 · title + media tags | Blocked/failed |
| bilibili | `fxbilibili.seria.moe` | 200 · title + media tags | Blocked/failed |
| bluesky | `bskx.app` | 200 · title + media tags | Blocked/failed |
| bluesky | `bsyy.app` | 200 · title + media tags | Blocked/failed |
| bluesky | `bskye.app` | 200 · title + media tags | Blocked/failed |
| bluesky | `vxbsky.app` | 200 · title + media tags | Blocked/failed |
| bluesky | `fxbsky.app` | 200 · title tags only | Blocked/failed |
| bluesky | `xbsky.app` | 200 · title + media tags | Blocked/failed |
| bluesky | `bs.embeds.now` | 200 · title + media tags | Blocked/failed |
| deviantart | `fixdeviantart.com` | 200 · title + media tags | Blocked/failed |
| facebook | `facebed.com` | 200 · title + media tags | Blocked/failed |
| facebook | `facecot.com` | 200 · title + media tags | Blocked/failed |
| facebook | `facebed.seria.moe` | 200 · title + media tags | Blocked/failed |
| facebook | `fb.embeds.now` | 200 · title + media tags | Blocked/failed |
| imgur | `imgurez.com` | 200 · title + media tags | Blocked/failed |
| instagram | `uuinstagram.com` | 200 · title + media tags | Blocked/failed |
| instagram | `eeinstagram.com` | 200 · title + media tags | Blocked/failed |
| instagram | `vxinstagram.com` | 200 · title + media tags | Blocked/failed |
| instagram | `kkinstagram.com` | Local DNS failure | Blocked/failed |
| instagram | `zzinstagram.com` | Local DNS failure | Blocked/failed |
| instagram | `oginstagram.com` | 200 · title + media tags | Blocked/failed |
| instagram | `ig.embeds.now` | 200 · title + media tags | Blocked/failed |
| pinterest | `pinterestez.com` | 200 · title + media tags | Blocked/failed |
| pinterest | `pi.embeds.now` | 200 · title + media tags | Blocked/failed |
| pixiv | `phixiv.net` | 200 · title + media tags | Blocked/failed |
| reddit | `redditez.com` | 200 · title + media tags | Blocked/failed |
| reddit | `vxreddit.com` | 200 · title + media tags | Blocked/failed |
| reddit | `r.embeds.now` | 200 · title + media tags | Blocked/failed |
| roblox | `www.fxroblox.com` | 200 · title + media tags | Blocked/failed |
| snapchat | `snapchatez.com` | 200 · title + media tags | Blocked/failed |
| spotify | `fxspotify.com` | 200 · title + media tags | Blocked/failed |
| spotify | `sp.embeds.now` | 200 · title + media tags | Blocked/failed |
| threads | `fixthreads.seria.moe` | 200 · title + media tags | Readable (HTTP 200) |
| threads | `vxthreads.net` | Connection timeout | Blocked/failed |
| threads | `th.embeds.now` | 200 · title + media tags | Blocked/failed |
| tiktok | `tnktok.com` | 200 · title + media tags | Blocked/failed |
| tiktok | `tiktxk.com` | 200 · title + media tags | Blocked/failed |
| tiktok | `tiktokez.com` | 200 · title + media tags | Blocked/failed |
| tiktok | `kktiktok.com` | 200 · direct MP4 (download capped) | Blocked/failed |
| tiktok | `fixtiktok.com` | 200 · title + media tags | Blocked/failed |
| tiktok | `tt.embeds.now` | 200 · title + media tags | Blocked/failed |
| tumblr | `tpmblr.com` | 200 · title + media tags | Blocked/failed |
| tumblr | `txtumblr.com` | 200 · title + media tags | Blocked/failed |
| twitch | `fxtwitch.seria.moe` | 200 · title + media tags | Blocked/failed |
| weibo | `weiboez.com` | 200 · title + media tags | Blocked/failed |
| x | `fixvx.com` | 200 · title + media tags | Blocked/failed |
| x | `fixupx.com` | 200 · title tags only | Blocked/failed |
| x | `twitterez.com` | 200 · title + media tags | Blocked/failed |
| x | `x.embeds.now` | 200 · title + media tags | Blocked/failed |
| youtube | `koutube.com` | 200 · title + media tags | Blocked/failed |

## Review findings

### Standards

No additional documented-standard findings were reported in the focused platform reviews. Disposable build jobs used `with-temp-build`; emulator interaction used PolyScreen with the discovered serial and display. ADB was used only for build/install diagnostics and cleanup. The isolated validation app was uninstalled, the previous emulator app restored, task servers stopped and task temporary files removed.

### Spec

One checker defect was fixed (configured `www` host mismatch). Two limits remain material to the requested cross-platform claim: browser CORS/redirect restrictions, and the inability to run the current iOS build here. Existing automatic health-based fallback also differs: Android/web preparation can use cached source health, while the iOS facade uses static defaults unless a preference is explicitly supplied. Explicit source selection follows the shared catalog; the added iOS callback test still needs execution on an Apple SDK host.

Review outcome: Standards — no additional findings. Spec — one defect fixed; platform validation limits remain as described above.
