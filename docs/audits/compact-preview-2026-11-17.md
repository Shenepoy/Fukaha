# Compact previews and preview preference — 2026-11-17

Removed the caption, text cards, separate Play row and fixed 340-point player height. Previews now contain only image/video content with a centered Play overlay in a responsive 16:9 frame. A single Show/Close control sits above the frame; successful previews have no footer or status paragraph. Errors appear as a temporary overlay. Existing lazy video loading and pause/close cancellation remain intact.

Added **Media previews** to Settings on Android, web and iOS. It defaults on for existing/new users, persists through each platform's settings store and hides all preview controls when off. Embed-link generation and sharing remain available. Web also cancels any pending preview check when the setting is disabled. iOS main-app, Share Extension and Action Extension views use the same saved preference.

Validation: **170 tests passed** (157 Kotlin/Chrome and 13 PWA/static checks). Android APK and web production builds passed. Swift syntax and existing Swift helper tests passed; the new iOS integration test awaits execution on an Apple SDK host, as full iOS runtime validation remains unavailable here.

Android device validation used the isolated `app.fukaha.validation` package on `emulator-5556`, logical display 0. The saved off setting survived an app restart; the share sheet retained the embed link and omitted Show preview. After re-enabling, the frame measured 408×229 CSS pixels, with a centered 64-pixel Play control and no caption/overflow. A zero-height initial Android WebView viewport found during visual inspection was fixed by synchronizing the document height to its actual viewport after layout and on resize.

Chrome validation measured a 457×257 frame with the image filling its height, a centered Play control and no caption/footer. Reloading with previews disabled retained embed-link sharing, showed no player/control and issued no media requests.

Evidence: [build](compact-preview-validation/verified-build.log), [suites](compact-preview-validation/test-results.json), [Swift checks](compact-preview-validation/swift-tests.log), [Android layout](compact-preview-validation/android-layout.json), [Android UI](compact-preview-validation/android-ui.json), [browser checks](compact-preview-validation/browser-layout-and-setting.json), [browser screenshot](compact-preview-validation/browser-compact-preview.png).

All emulator input used PolyScreen. ADB handled install/diagnostic forwarding/cleanup; CDP inspected layout in a disposable APK with WebView debugging enabled only in the temporary checkout. Task apps, forwarding, servers and temporary files were removed after validation and the previous emulator app restored.
