# Inline media previews — 2026-11-17

The share window now displays the selected source's image, text card or video player on Android, web and in the iOS main app, Share Extension and Action Extension. Select **Show preview**, then **Play** for video.

The player is a local, escaped document. Provider pages and scripts do not execute inside it. Video has no active `src` before Play; `preload="none"` is also set. Pausing removes the source, aborts outstanding media requests and retains the playback position for the next Play. Hiding or closing the preview releases it too. Video streams directly through the media engine, without downloading a complete file first or creating a media blob. Media engines choose their forward buffers; this is not a fixed byte/second buffer ceiling. Explicit full-file sharing remains a separate download action.

Metadata requests stop at the HTML head or 128 KiB, use timeouts and limit provider redirects. Direct video responses are handled from headers without consuming their body. Canonical verification remains separate from displaying a card; numeric character references, canonical post slugs and harmless query changes are handled for display. HTML player URLs explicitly marked as `text/html` are not treated as video streams. Unavailable media shows an error rather than opening an external page.

## Validation

| Platform | Executed | Result |
|---|---|---|
| Android | Shared/Android tests, APK build, real Instagram metadata and native share-window display; controlled partial-video playback on `emulator-5556`, logical display 0 | Passed. Before Play: no active source. After Play: `readyState=4`, playback time advancing. After tapping native Pause: source removed, player hidden and pending request canceled. |
| Web | Chrome DOM tests, production bundle, Hosting/service-worker tests; real-browser CORS fallback and partial-video network check | Passed. Zero video requests before Play. Playback succeeded after receiving only 65,536 of a 90,950-byte fixture. Closing the window removed the frame and canceled the remainder. The image/player is visibly inside the share window. |
| iOS | Swift syntax parsing and existing Swift preference/ranking/health tests; project/source inclusion and framework guard inspection | Passed for these checks. **Full iOS compilation and device/simulator playback were not executed:** this Linux host lacks Apple's SDK and the cached framework is obsolete. A fresh Shared framework is required, including the new `loadPreview` callback. |

**173 automated tests passed:** 156 Kotlin/Chrome tests, four metadata-endpoint tests and 13 PWA/Hosting/service-worker tests. Swift checks are additional and are not counted in that total.

The shared catalog still contains 50 sources. The new metadata endpoint's actual bounded HTTPS transport reached **47/50** with HTTP 200/206. The remaining failures were local DNS for `kkinstagram.com` and `zzinstagram.com`, and a timeout for `vxthreads.net`. Of the responding samples, 44 returned title/media tags, two returned text/title cards and one returned direct MP4 headers. These are metadata/transport checks, not playback certification for every provider, post or codec. Some external media endpoints can reject a client, expire or fail independently of their metadata page.

Evidence: [final build](inline-preview-validation/verified-build.log), [Kotlin/Chrome suites](inline-preview-validation/test-results.json), [endpoint tests](inline-preview-validation/endpoint-tests.log), [endpoint packaging](inline-preview-validation/endpoint-package.log), [static tests](inline-preview-validation/static-test.log), [Swift checks](inline-preview-validation/swift-tests.log), [all-source live checks](inline-preview-validation/endpoint-live-sources.json), [Android network observations](inline-preview-validation/android-player-network.json), [browser network observations](inline-preview-validation/browser-player-network.json) and [browser screenshot](inline-preview-validation/browser-preview.png).

Partial-video tests use a controlled network fixture to prove playback before the complete file arrives and cancellation of the remainder. Android taps and UI discovery use PolyScreen. ADB was used for build/install, diagnostic port forwarding and cleanup; validation-only WebView debugging and CDP interception were confined to the disposable APK. Production source does not enable WebView debugging.

## Web deployment

Most providers block readable browser metadata requests through CORS. The web app now falls back to `/api/preview`, backed by the catalog-restricted `previewMetadata` Firebase function. It returns bounded metadata only and never proxies or caches video. The live deployment workflow packages and deploys the function before Hosting. Endpoint packaging was tested both from the full repository and a functions-only deployment bundle.

**No Firebase deployment was performed in this session.** The function must be deployed for the web fallback to work on the hosted site. Cloud Functions must be enabled and the deployment account must have function-deployment permissions; the existing Hosting-only account may need additional permissions. See [deployment instructions](../../functions/README.md).

Task-owned validation apps, diagnostic forwarding, browser/server processes and disposable build files are removed after validation; the emulator's previous app is restored.
