//
// Fukaha iOS container app and settings screen
//

import Foundation
import SwiftUI

@main
struct FukahaApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .background(FukahaKeyboardDismissal().allowsHitTesting(false))
        }
    }
}

struct ContentView: View {
    @State private var settings = SettingsSnapshot.load()
    @State private var pendingUpdate: PendingAppUpdate?
    @State private var updateChecking = false
    @State private var updateMessage: String?
    @State private var quickShareText: String?
    @State private var quickShareDetent: PresentationDetent = .medium
    @State private var updateChannel: IosUpdateChannel?
    @Environment(\.openURL) private var openURL
    @State private var quickShareClient: FukahaClient?

    private var isArabic: Bool {
        switch settings.language {
        case "Arabic": return true
        case "English", "Japanese", "SimplifiedChinese", "Spanish": return false
        default:
            return Locale.current.language.languageCode?.identifier == "ar"
        }
    }

    var body: some View {
        FukahaSettingsScreen(
            settings: $settings,
            onClearCache: clearCache,
            onCheckUpdates: { Task { await runUpdateCheck(manual: true) } },
            onOpenQuickLink: {
                quickShareClient?.close()
                quickShareClient = FukahaClient()
                quickShareDetent = .medium
                quickShareText = $0
            },
            updateChecking: updateChecking,
            updateChannel: updateChannel,
        )
        .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
        .preferredColorScheme(preferredColorScheme)
        .tint(Color.fukahaPrimary)
        .task {
            updateChannel = await IosUpdateChannel.current()
            await runUpdateCheck(manual: false)
        }
        .sheet(item: $pendingUpdate) { update in
            UpdateAvailableView(
                update: update,
                isArabic: isArabic,
                onSkip: {
                    settings.skippedUpdateVersion = update.version
                    settings.save()
                    pendingUpdate = nil
                },
                onDismiss: { pendingUpdate = nil },
                onInstall: {
                    openURL(update.storeURL) { accepted in
                        if !accepted {
                            pendingUpdate = nil
                            updateMessage = isArabic ? "تعذّر فتح App Store" : "Could not open the App Store"
                        }
                    }
                },
            )
        }
        .sheet(isPresented: Binding(
            get: { quickShareText != nil },
            set: { if !$0 { dismissQuickShare() } },
        )) {
            Group {
                if let text = quickShareText, let quickShareClient {
                    FukahaQuickShareView(
                        text: text,
                        settings: settings,
                        client: quickShareClient,
                        onDismiss: dismissQuickShare,
                    )
                }
            }
            .presentationDetents([.medium, .large], selection: $quickShareDetent)
            .presentationDragIndicator(.visible)
        }
        .alert(updateMessage ?? "", isPresented: Binding(
            get: { updateMessage != nil },
            set: { if !$0 { updateMessage = nil } },
        )) {
            Button(isArabic ? "حسناً" : "OK", role: .cancel) { updateMessage = nil }
        }
    }

    private var preferredColorScheme: ColorScheme? {
        switch settings.theme {
        case "Light": return .light
        case "Dark": return .dark
        default: return nil
        }
    }

    private func dismissQuickShare() {
        quickShareClient?.close()
        quickShareClient = nil
        quickShareText = nil
    }

    private func clearCache() {
        do {
            try FukahaMediaCache.clear()
            updateMessage = isArabic ? "تم مسح ملفات الوسائط المؤقتة" : "Media cache cleared"
        } catch {
            updateMessage = error.localizedDescription
        }
    }

    private func currentVersion() -> String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    @MainActor
    private func runUpdateCheck(manual: Bool) async {
        guard !updateChecking else { return }
        updateChecking = true
        defer { updateChecking = false }
        if manual && (updateChannel == nil || updateChannel == .unknown) {
            updateChannel = await IosUpdateChannel.current(allowRefresh: true)
        }
        guard !Task.isCancelled, let channel = updateChannel else { return }
        switch channel {
        case .unknown:
            if manual {
                updateMessage = isArabic
                    ? "تعذّر تحديد مصدر التثبيت. تحقق من اتصالك وتسجيل الدخول إلى App Store، ثم حاول مجدداً."
                    : "Could not determine the installation source. Check your connection and App Store sign-in, then try again."
            }
            return
        case .testFlight:
            if manual {
                openURL(URL(string: "itms-beta://")!) { accepted in
                    if !accepted {
                        updateMessage = isArabic
                            ? "افتح تطبيق TestFlight لتحديث النسخة التجريبية."
                            : "Open the TestFlight app to update this beta build."
                    }
                }
            }
            return
        case .localBuild:
            if manual {
                updateMessage = isArabic
                    ? "هذه نسخة مثبتة يدوياً. ثبّت نسخة أحدث من المصدر الذي حصلت عليها منه."
                    : "This build was installed manually. Install a newer build from the same source."
            }
            return
        case .appStore:
            break
        }
        if !manual {
            guard settings.checkUpdatesOnLaunch,
                  IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: settings.lastUpdateCheckEpochMs)
            else { return }
        }
        do {
            let result = try await IosAppUpdateChecker().check(
                bundleID: Bundle.main.bundleIdentifier ?? "",
                currentVersion: currentVersion()
            )
            try Task.checkCancellation()
            // Only successful Apple responses count toward the daily check interval.
            settings.lastUpdateCheckEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
            settings.save()
            switch result {
            case .available(let update):
                if manual || update.version != settings.skippedUpdateVersion {
                    pendingUpdate = update
                }
            case .upToDate:
                if manual {
                    updateMessage = isArabic ? "لديك أحدث إصدار متاح في App Store" : "You have the latest App Store version"
                }
            case .requiresNewerOS(let version):
                if manual {
                    updateMessage = isArabic
                        ? "يتطلب الإصدار الأحدث iOS \(version) أو أحدث."
                        : "The latest release requires iOS \(version) or later."
                }
            case .notListed:
                if manual {
                    updateMessage = isArabic
                        ? "فكها غير متاح حالياً في App Store في منطقتك."
                        : "Fukaha is not currently listed in the App Store in your region."
                }
            }
        } catch is CancellationError {
            // A cancelled view task should not display a failure or postpone the next check.
        } catch {
            if manual && !Task.isCancelled {
                updateMessage = isArabic
                    ? "تعذّر البحث عن تحديثات. تحقق من اتصالك وحاول مجدداً."
                    : "Could not check for updates. Check your connection and try again."
            }
        }
    }
}

struct UpdateAvailableView: View {
    let update: PendingAppUpdate
    let isArabic: Bool
    let onSkip: () -> Void
    let onDismiss: () -> Void
    let onInstall: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("v\(update.version)")
                        .font(.headline)
                    Text(update.changelog.isEmpty
                         ? (isArabic ? "لا توجد ملاحظات لهذا الإصدار." : "No release notes for this version.")
                         : update.changelog)
                        .font(.body)
                        .foregroundStyle(.secondary)
                    Text(isArabic ? "يتم تنزيل التحديث وتثبيته عبر App Store." : "Download and install this update through the App Store.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                    Button(isArabic ? "التحديث عبر App Store" : "Update in App Store", action: onInstall)
                        .buttonStyle(.borderedProminent)
                    Button(isArabic ? "تخطَّ هذا الإصدار" : "Skip this version", action: onSkip)
                        .padding(.top, 8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .navigationTitle(isArabic ? "يتوفر تحديث" : "Update available")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isArabic ? "لاحقاً" : "Later", action: onDismiss)
                }
            }
        }
    }
}
