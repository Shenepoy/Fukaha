# Automatic previews — 2026-11-17

When Media previews is enabled, opening the share window now loads the selected source's image/video preview automatically on Android, web and iOS. Videos still have no active source until Play. The existing Settings switch disables the preview UI and metadata requests. Failed loads offer Retry; closing a loaded preview keeps it hidden until it is reopened or a new share window opens.

Web preview completion updates only its own region, preserving URL rows and avoiding an automatic focus/scroll change. New-link preparation cancels the old request and rejects late results. Source changes and returning from Settings also start the appropriate preview. iOS cancels on disappearance/backgrounding and resumes loading when the visible window becomes active again.

Validation: **176 tests passed** (163 Kotlin/Chrome, 13 PWA/static checks). Tests verify automatic display without a preview-button click, metadata-only fetching, no reload loop after closing, loading on reopening, honoring the off switch, and cancellation/rejection of stale results. Android APK and web production builds passed. Swift syntax passed; full iOS device/framework validation still requires Apple's SDK.

Updated and launched the app on emulator-5556, display 0, preserving its test URL. The preview appeared automatically, and Play remained visible/unpressed. Updated the existing web test instance at http://localhost:5173/; its preview likewise appeared without a Show preview click. Both remain available for user testing. Disposable build directories were removed by the build wrapper.

Evidence: [build](auto-preview-validation/build.log), [test suites](auto-preview-validation/tests.json), [Android state](auto-preview-validation/android-state.json), [web state](auto-preview-validation/web-state.json), [screenshot](auto-preview-validation/web-automatic-preview.png), [Swift syntax](auto-preview-validation/swift-syntax.log).
